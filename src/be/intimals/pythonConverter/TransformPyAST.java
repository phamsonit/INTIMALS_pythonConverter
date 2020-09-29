package be.intimals.pythonConverter;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import org.w3c.dom.*;

import static be.intimals.pythonConverter.Utils.*;
import static java.lang.Character.isUpperCase;

public class TransformPyAST {
    private int id;
    private Document document;
    private List<String> pyFileContent;
    private String output;

    private static String ID = "ID";
    private static String LineNr = "LineNr";
    private static String EndLineNr = "EndLineNr";
    private static String ColNr = "ColNr";
    private static String EndColNr = "EndColNr";

    private Map<Integer, List<String>> linesVariables;

    /**
     * transform all Python XMLs to Intimals XML
     * @param dir : directory contains xml files exported by ...
     * @param outputDir : directory contains xml files for freqtals
     */
    public void transformPyAST(String dir, String outputDir){
        try {
            output = outputDir;
            ArrayList<String> files = new ArrayList<>();
            populateFileList(new File(dir), files);
            Collections.sort(files);
            for (String fi : files) {
                System.out.println("Transforming file: "+fi);
                transformOneAST(fi);
            }
        }catch (Exception e){
            System.out.println("Transforming python XML error");
            e.printStackTrace();
        }
    }

    /**
     * transform a Python XML to Intimals XML format
     * @param xmlFile :
     */
    private void transformOneAST(String xmlFile){
        try{
            // copy py file to output dir
            String pythonFile = xmlFile.substring(0,xmlFile.length()-3)+"py";
            String newPyFile = output+"/"+Paths.get(pythonFile).getFileName().toString();
            // String txtFile = newPyFile.substring(0,newPyFile.length()-2)+"txt";
            copyPyFile(pythonFile, newPyFile);

            // read python code
            pyFileContent = readPyFile(newPyFile);

            linesVariables = new HashMap<>();

            // read XML and added line number
            InputStream is = getXMLStream(xmlFile);
            document = PositionalXMLReader.readXML(is);
            is.close();

            // update xml document
            id = 0;
            updateNodes(document.getElementsByTagName("Module").item(0));

            // write DOM to xml file
            String xmlFileName =  Paths.get(xmlFile).getFileName().toString();
            String outputFile = output + "/" +xmlFileName;
            // write xml to file
            writeDomObjectToFile(document, outputFile);
            // format pretty xml
            XmlFormatter.format(outputFile, outputFile);
        }catch (Exception e){
            System.out.println("Convert one AST error " + e);
        }
    }

    /**
     * recursively update a node
     * @param node
     */
    private void updateNodes(Node node) {
        try {
            //if this is an internal node
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                //ignore manually added tags
                if(node.getNodeName().equals("nameDef") || node.getNodeName().equals("identifier")) return;

                //update attributes for this node
                updateAttribute(node);
                //increase node ID
                ++id;

                //special cases: if node is ClassDef or FunctionDef, then add an intermediate tag "name"
                if(node.getNodeName().equals("ClassDef") || node.getNodeName().equals("FunctionDef")) {
                    addNameTemp(node,"nameDef");
                }

                //special cases: cmpop node
                Set<String> specialCases = new HashSet<>(Arrays.asList("cmpop", "attr", "name"));
                if(specialCases.contains(node.getNodeName())){
                    treatCmpopNode(node);
                    return;
                }

                /**
                 * case 1: AST node contains a child which is an AST node
                 * then add an interNode child and move AST children nodes to this interNode node
                 *
                 * case 2: AST node contains multiple non AST nodes having the same name
                 * add InterAstNode to each attribute then add identifier to InterAstNode
                 *
                 * case 3: non-AST node contains children which are non AST nodes
                 * then change these non-AST children node to AST node and add an identifier to it
                 *
                 * otherwise: transform normally
                 */
                //special case: node label is body, then need to add intermediate nodes: Block -> statements
                if(node.getNodeName().equals("body") || node.getNodeName().equals("orelse")){
                    // increase line to lineNr + 1
                    increaseLineNr(node);
                    // add Block to body
                    addBlockStatements(node);
                }else{
                    //case 1
                    if(isAstNode(node) && containAstNode(node)){
                        addInterNode(node, "interNode");
                    }else{
                        //case 2
                        if(isAstNode(node) && hasRepeatedChildren(node)){
                            treatRepeatedChildren(node);
                        }else {
                            //case 3
                            if(!isAstNode(node) && !containAstNode(node) && node.getChildNodes().getLength() > 1){
                                changeToASTNode(node);
                            }else{
                                //recursively read children of the current node
                                NodeList nodeList = node.getChildNodes();
                                for (int i = 0; i < nodeList.getLength(); ++i) {
                                    updateNodes(nodeList.item(i));
                                }
                            }
                        }
                    }
                }
            }else {//this is a text content
                if(node.getNodeType() == Node.TEXT_NODE && !node.getTextContent().trim().isEmpty()){
                    String leaf = node.getTextContent();
                    //if this leaf (node) has a sibling it means that it is not a unique leaf of an XML tag
                    Node a = node.getNextSibling();
                    Node b = node.getPreviousSibling();
                    if(a == null && b == null){
                        // if its parent is an AST node we need to add and an additional "identifier"
                        if(isAstNode(node.getParentNode())) {
                            //add identifier
                            addIdentifier(node.getParentNode(), leaf);
                            //Clear text content
                            node.setNodeValue("");
                        }
                    }else{
                        //Clear a text content
                        node.setNodeValue("");
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * calculate line number for body node
     * @param node : body
     */
    private void increaseLineNr(Node node) {
        // get begin and end line numbers
        int lineNr = Integer.valueOf(node.getParentNode().getAttributes().getNamedItem(LineNr).getNodeValue());
        int endLineNr = Integer.valueOf(node.getParentNode().getAttributes().getNamedItem(EndLineNr).getNodeValue());
        // if the code has more than 1 line
        if(lineNr < endLineNr)
            // recalculate begin line number
            lineNr = Integer.valueOf(node.getAttributes().getNamedItem(LineNr).getNodeValue()) + 1;
        // update line number attributes
        ((Element)node).setAttribute(LineNr, String.valueOf(lineNr));
        ((Element)node).setAttribute(EndLineNr, String.valueOf(endLineNr));
    }

    /**
     * collect all variables of line of code
     * @param node
     */
    private void collectLinesVariables(Node node, String identifier) {
        //get line number of the current node
        int lineNr = Integer.valueOf(node.getAttributes().getNamedItem("LineNr").getNodeValue());
        //add variable name to this line number
        if (!linesVariables.containsKey(lineNr)) {
            List<String> temp = new LinkedList<>();
            temp.add(identifier);
            linesVariables.put(lineNr, temp);
        } else {
            List<String> temp = linesVariables.get(lineNr);
            temp.add(identifier);
            linesVariables.put(lineNr, temp);
        }
    }

    /**
     * updata attributes of a node
     * @param node : input node
     */
    private void updateAttribute(Node node) {
        //update ID for internal node
        ((Element)node).setAttribute(ID, String.valueOf(id));
        //find line and column numbers
        List<String> LCNumbers = findLineColNumbers(node, node.getTextContent().trim());
        //add ... to attributes
        ((Element)node).setAttribute(LineNr, LCNumbers.get(0));
        ((Element)node).setAttribute(EndLineNr, LCNumbers.get(1));
        ((Element)node).setAttribute(ColNr, LCNumbers.get(2));
        ((Element)node).setAttribute(EndColNr,LCNumbers.get(3)); //
    }

    /**
     * change a non-AST node to AST node
     * @param node : input node
     */
    private void changeToASTNode(Node node){
        //for each child, change its name to AST name format and add identifier
        NodeList nodeList = node.getChildNodes();
        for(int i=0; i<nodeList.getLength(); ++i){
            if(nodeList.item(i).getNodeType() == Node.ELEMENT_NODE){
                // change to AST node
                changeNodeToAST(nodeList.item(i));
                // update attribute for this node
                updateAttribute(nodeList.item(i));
                ++id;
                // add identifier
                String identifier = nodeList.item(i).getTextContent();
                // delete text content of this node
                nodeList.item(i).setTextContent("");
                // add an identifier node to this node
                addIdentifier(nodeList.item(i), identifier);
            }else{
                updateTextNode(nodeList.item(i));
            }
        }
    }

    /**
     * update an internal node
     * @param node : node
     * @param tagName :
     */
    private void addInterNode(Node node, String tagName) {
        //add intermediate node
        addIntermediateNode(node, tagName);

        //move children to intermediate node
        moveAllChildren(node, node.getChildNodes());

        //recursively read children of the intermediate node
        NodeList interChildren = node.getFirstChild().getChildNodes();
        for (int i = 0; i < interChildren.getLength(); ++i) {
            if(isAstNode(interChildren.item(i)))
                updateNodes(interChildren.item(i));
            else{
                // change to AST node
                changeNodeToAST(interChildren.item(i));
                // update this node
                updateNodes(interChildren.item(i));
            }
        }
    }

    private void changeNodeToAST(Node node){
        String oldNodeName = node.getNodeName();
        String newNodeName = Character.toUpperCase(oldNodeName.charAt(0)) +
                oldNodeName.substring(1, oldNodeName.length());
        //change node name
        document.renameNode(node, null, newNodeName);
    }
    /**
     * add Block -> statements to body
      * @param node : input node ~ body
     */
    private void addBlockStatements(Node node){
        //add Block as the first child of this node
        addIntermediateNode(node, "Block");
        //move children to Block
        moveAstNode(node, node.getChildNodes());

        //get Block node
        Node block = node.getFirstChild();

        //add statements to Block
        addIntermediateNode(block, "statements");
        //move children to statements
        moveAstNode(block, block.getChildNodes());

        //update all children of statements
        Node statements = block.getFirstChild();
        NodeList children = statements.getChildNodes();
        for(int i=0; i<children.getLength(); ++i){
            updateNodes(children.item(i));
        }


    }

    /**
     * if node has repeated attribute then for each attribute we add an InterAstNode and then add identifier to this
     * InterAstNode
     * @param node : node has repeated children
     */
    private void treatRepeatedChildren(Node node) {
        //change name for each child if it is in repeated list
        NodeList nodeList = node.getChildNodes();
        Map<String,Integer> oc = getRepeatedChildren(node);
        int count = 1;
        for(int i = 0; i<nodeList.getLength(); ++i){
            if(oc.containsKey(nodeList.item(i).getNodeName())){
                //change name
                document.renameNode(nodeList.item(i), null, nodeList.item(i).getNodeName()+String.valueOf(count));
                ++count;
                updateNodes(nodeList.item(i));
            }else{
                //System.out.println(nodeList.item(i).getNodeName() +" "+nodeList.item(i).getTextContent());
                updateNodes(nodeList.item(i));
            }
        }
    }

    /**
     * add intermediate node Name to cmpop node
     * @param node : cmpop node
     */
    private void treatCmpopNode(Node node){
        //store compare operator
        String identifier = node.getTextContent();
        //clear text content
        node.setTextContent("");
        //add intermediate Name
        addIntermediateNode(node, "Name");
        //add identifier to Name
        addIdentifier(node.getFirstChild(), identifier);
    }

    /**
     * @param node : input node
     * @return : return repeated children of a node
     */
    private Map<String, Integer> getRepeatedChildren(Node node) {
        Map<String,Integer> oc = new HashMap<>();
        NodeList childrenTemp = node.getChildNodes();
        for(int i=0; i<childrenTemp.getLength(); ++i){
            //add InterAstNode to this child
            if(!isAstNode(childrenTemp.item(i)) && childrenTemp.item(i).getNodeType()==Node.ELEMENT_NODE){
                if(oc.containsKey(childrenTemp.item(i).getNodeName())){
                    oc.put(childrenTemp.item(i).getNodeName(), oc.get(childrenTemp.item(i).getNodeName())+1);
                }else {
                    oc.put(childrenTemp.item(i).getNodeName(), 1);
                }
            }
        }
        oc.values().removeIf(value -> value < 2);
        return oc;
    }

    /**
     * return true if node has repeated children
     * @param node : input node
     * @return : true if there exist children that have the same name
     */
    private boolean hasRepeatedChildren(Node node){
        Map<String, Integer> oc = getRepeatedChildren(node);
        for(Map.Entry<String, Integer> entry : oc.entrySet()){
            if(entry.getValue() > 1){
                return true;
            }
        }
        return false;
    }

    /**
     * //move all children to the first child
     * @param node : node
     * @param nodeList : children
     */
    private void moveAllChildren(Node node, NodeList nodeList) {
        for(int i=1; i<nodeList.getLength(); ++i){
            if(nodeList.item(i).getNodeType() == Node.ELEMENT_NODE){
                node.getFirstChild().appendChild(nodeList.item(i));
                --i;
            }else{
                if(nodeList.item(i).getNodeType() == Node.TEXT_NODE) {
                    //System.out.println("deleting text ... " + nodeList.item(i).getTextContent().trim());
                    updateTextNode(nodeList.item(i));
                }
            }
        }
    }

    /**
     * //move all AST children to the first child of the node
     * @param node : node
     * @param nodeList : children
     */
    private void moveAstNode(Node node, NodeList nodeList) {
        for(int i=1; i<nodeList.getLength(); ++i){
            if(nodeList.item(i).getNodeType() == Node.ELEMENT_NODE && isAstNode(nodeList.item(i))){
                node.getFirstChild().appendChild(nodeList.item(i));
                --i;
            }else{
                if(nodeList.item(i).getNodeType() == Node.TEXT_NODE) {
                    //System.out.println("deleting text ... " + nodeList.item(i).getTextContent().trim());
                    updateTextNode(nodeList.item(i));
                }
            }
        }
    }

    /**
     * update a leaf node
     * @param node : xml node
     */
    private void updateTextNode(Node node) {
        Node a = node.getNextSibling();
        Node b = node.getPreviousSibling();
        if(a == null && b == null){
            //System.out.println("leaf node: "+ leaf);
            //addIdentifyNode(node,leaf);
        }else{
            //Clear a Text Node
            //System.out.println("deleted "+leaf);
            node.setNodeValue("");
        }
    }

    /**
     * return true if the first letter is Upper case (AST node)
     * @param node
     * @return : true if node is an AST node
     */
    private boolean isAstNode(Node node){
        return isUpperCase( node.getNodeName().charAt(0));
    }

    /**
     * if a node contains a child which is an AST node then return true
     * @param node
     * @return : true if this node contains AST node
     */
    private boolean containAstNode(Node node){
        boolean isASTNode = false;
        NodeList children = node.getChildNodes();
        for (int i=0; i<children.getLength(); ++i)
            if(isUpperCase(children.item(i).getNodeName().charAt(0))){
                isASTNode = true;
                break;
        }
        return isASTNode;
    }

    /**
     * add an intermediate node to a node
     * @param node : xml node
     * @param tagName : intermediate node name
     */
    private void addIntermediateNode(Node node, String tagName){
        try {
            //create new element "name" with attributes
            Element inter_child = document.createElement(tagName);
            inter_child.setAttribute(ID, String.valueOf(id));

            String lineNr = ((Element) node).getAttribute(LineNr);
            inter_child.setAttribute(LineNr, lineNr);

            String endLineNr = ((Element) node).getAttribute(EndLineNr);
            inter_child.setAttribute(EndLineNr, endLineNr);

            String colNr = ((Element) node).getAttribute(ColNr);
            inter_child.setAttribute(ColNr, colNr);

            String endColNr = ((Element) node).getAttribute(EndColNr);
            inter_child.setAttribute(EndColNr, endColNr);

            //insert inter_child before first child
            Node firstChild = node.getFirstChild();
            // insert before the first
            node.insertBefore(inter_child, firstChild);

            //increase node ID
            ++id;
        }catch (Exception e){
            System.out.println("add intermediate node error "+e);
        }
    }

    /**
     * add identifier into a node
     * @param node : node
     * @param identifier : identifier
     */
    private void addIdentifier(Node node, String identifier){
        try {
            //collect variable name
            collectLinesVariables(node, identifier.trim());

            //create new element "Identify" with attributes
            Element iden_child = document.createElement("identifier");
            iden_child.setAttribute(ID, String.valueOf(id));

            iden_child.setTextContent(identifier);

            String lineNr = ((Element) node).getAttribute(LineNr);
            iden_child.setAttribute(LineNr, lineNr);

            String endLineNr = ((Element) node).getAttribute(EndLineNr);
            iden_child.setAttribute(EndLineNr, endLineNr);

            String colNr = ((Element) node).getAttribute(ColNr);
            iden_child.setAttribute(ColNr, colNr);

            String endColNr = ((Element) node).getAttribute(EndColNr);
            iden_child.setAttribute(EndColNr, endColNr);

            node.appendChild(iden_child);

            //increase node ID
            ++id;
        }catch (Exception e){
            System.out.println("add identify node error "+e);
            e.printStackTrace();
        }
    }

    /**
     * add tag Name into class or method declaration
     * @param node : xml node
     * @param tagName : new child's name
     */
    private void addNameTemp(Node node, String tagName) {
        try {
            //create new element "name" with attributes
            Element name_child = document.createElement(tagName);
            name_child.setAttribute(ID, String.valueOf(id));

            String val = ((Element) node).getAttribute("name");
            name_child.setAttribute("name", val);

            // find line and column numbers
            List<String> LCNumbers = findLineColNumbers(node, val);

            name_child.setAttribute(LineNr, LCNumbers.get(0));
            name_child.setAttribute(EndLineNr, LCNumbers.get(1));

            name_child.setAttribute(ColNr, LCNumbers.get(2));
            name_child.setAttribute(EndColNr, LCNumbers.get(3));
            //get name of the class/method

            //insert "name" into its parent node
            Node firstChild = node.getFirstChild();
            node.insertBefore(name_child, firstChild);
            //increase node ID
            ++id;

            //add Name
            Node firstNameChild = node.getFirstChild();
            addIntermediateNode(firstNameChild, "Name");

            //add identifier
            addIdentifier(firstNameChild.getFirstChild(), val);
        }catch(Exception e){
            System.out.println("add tag name error");
            e.printStackTrace();
        }
    }

    /**
     * find line and column numbers of a text in input files
      * @param node : xml doc
     * @param identifier : string
     * @return : a list of string including LineNr, EndLineNr, ColNr, EndColNr
     */
    private List<String> findLineColNumbers(Node node, String identifier){
        List<String> results = new LinkedList<>();

        //get line number of this node in the xml file
        String lineNr = findXmlLineNumber(node);
        results.add(lineNr);

        int endLineNr = Integer.valueOf(lineNr) + countLines(identifier) - 1;
        results.add(String.valueOf(endLineNr));

        //get column number of this node from Python file
        String[] colNr = findPyColumnNr(Integer.valueOf(lineNr), identifier).split("-");
        results.add(colNr[0]);
        results.add(colNr[1]);

        return results;
    }

    /**
     * find column number of a text from the input python file
     * @param lineNr : line number
     * @param identifier : identifier
     * @return : return first and last position of the identifier in line id lineNr
     */
    private String findPyColumnNr(int lineNr, String identifier){
        int col = 1;
        //get a line in python file
        String line = pyFileContent.get(lineNr-1);
        //get variables from this line
        List<String> variables = new LinkedList<>();
        if(linesVariables.containsKey(lineNr))
            variables = linesVariables.get(lineNr);
        //find column number of this identifier
        if(line.contains(identifier)){
            col = findIdentifierColNr(line, identifier, variables);
        }
        //calculate end column number
        int endColNr;
        if(countLines(identifier) > 1){
            endColNr = line.length(); // =col
        }else{
            endColNr = col + identifier.trim().length()-1;
        }

        return String.valueOf(col)+"-"+String.valueOf(endColNr);
    }

    /**
     * find column of identifier in a string
     * @param inputString : input string
     * @param identifier : identifier
     * @param variables : this of identifiers have been visited
     * @return : the position of identifier in the string
     */
    private int findIdentifierColNr(String inputString, String identifier, List<String> variables) {
        //count number of times this identifier occur in the variables list
        int nbIdentifierOccur = countLiteral(variables, identifier);
        //find index of this identifier in the input string
        int index = inputString.indexOf(identifier);
        try{
            // if identifier is a combined operator
            Set<String> combinedOperators = new HashSet<>(Arrays.asList("+=","-=", "*=", "/=", ">=", "<=", "%", "==","!=",">","<","//"));
            if(combinedOperators.contains(identifier.trim())) {
                return ++index;
            }
            // if identifier is a operator
            Set<String> operators = new HashSet<>(Arrays.asList("+","-","*","/","**"));
            if(operators.contains(identifier)){
                index = getIndexOfOperator(inputString, identifier, nbIdentifierOccur, index);
                return ++index;
            }
            // if this is a variable name
            return getIndexOfVariable(inputString, identifier, nbIdentifierOccur, index);

        }catch (Exception e){
            e.printStackTrace();
        }
        return ++index;
    }

    private int getIndexOfVariable(String inputString, String identifier, int nbIdentifierOccur, int index) {
        if (nbIdentifierOccur > 0) {
            int nbIdentifierVisit = 0;
            while (nbIdentifierVisit < nbIdentifierOccur) {
                // increase index to next position
                index = inputString.indexOf(identifier, index + 1);
                //increase index if this identifier is in a substring of another substring
                while(isInString(inputString, identifier, index)){
                    index = inputString.indexOf(identifier, index + 1);
                }
                //increase number of time visiting
                ++nbIdentifierVisit;
            }
            return ++index;
        } else {
            //if identifier is in a substring, e.g, range (a, a+1)
            //ignore these substring until identifier is found
            while (isInString(inputString, identifier, index)){
                index = inputString.indexOf(identifier, index + 1);
            }
            return ++index;
        }
    }

    private int getIndexOfOperator(String inputString, String identifier, int nbIdentifierOccur, int index) {
        if(nbIdentifierOccur > 0){
            int nbIdentifierVisit = 0;
            // System.out.println("multiple operator "+identifier);
            while(nbIdentifierVisit < nbIdentifierOccur){
                // increase index
                index = inputString.indexOf(identifier, index + 1);
                // ignore -=, +=, ...
                while(isNotOperator(inputString, index)){
                    index = inputString.indexOf(identifier, index + 1);
                }
                // ignore **
                if(identifier.equals("*")) {
                    while (isNotPowerOperator(inputString, index)) {
                        index = inputString.indexOf(identifier, index + 2);
                    }
                }
                ++nbIdentifierVisit;
            }
        }else{
            // System.out.println("first operator "+identifier);
            while(isNotOperator(inputString, index)){
                index = inputString.indexOf(identifier, index + 1);
            }
            if(identifier.equals("*")){
                while(isNotPowerOperator(inputString, index)){
                    index = inputString.indexOf(identifier, index + 2);
                }
            }
        }
        return index;
    }

    /**
     * check identifier is in a substring or not
     * @param inputString : input string
     * @param identifier : identifier
     * @param index : index of identifier in the string
     * @return : true if identifier is included in a substring
     */
    private boolean isInString(String inputString, String identifier, int index){
        // identifier at the first line
        if(index == 0 && identifier.length() < inputString.length()){
            // last char is an alphabetic
            boolean lastChar = Character.isAlphabetic(inputString.charAt(index + identifier.length()));
            // last char is an _
            boolean lastCharDash = inputString.charAt(index + identifier.length()) == '_';
            return  lastChar || lastCharDash;
        }
        if(index > 0 && index + identifier.length() < inputString.length()) {
            // first char is an alphabetic
            boolean firstChar = Character.isAlphabetic(inputString.charAt(index - 1));
            // last char is an alphabetic
            boolean lastChar = Character.isAlphabetic(inputString.charAt(index + identifier.length())) ||
                    Character.isDigit(inputString.charAt(index + identifier.length()));
            // first char is an _
            boolean firstCharDash = inputString.charAt(index -1) == '_';
            // last char is an _
            boolean lastCharDash = inputString.charAt(index + identifier.length()) == '_';

            return  firstChar || lastChar || firstCharDash || lastCharDash;
        }else
            return false;
    }

    /**
     * check if an operator is included in +=, -=, *=, /=
     * @param inputString : input string
     * @param index : index
     * @return : return true if the character after index is equal to =
     */
    private boolean isNotOperator(String inputString, int index){
        return inputString.charAt(index+1) == '=';
    }

    /**
     * check if * is in ** or not
     * @param inputString : input string
     * @param index : index
     * @return : true if the character after index is equal to *
     */
    private boolean isNotPowerOperator(String inputString, int index){
        return inputString.charAt(index+1) == '*';
    }

    /**
     * find line number of a node from input XML file
     * @param node : xml node
     * @return : line number of xml node
     */
    private String findXmlLineNumber(Node node){
        return node.getUserData("lineNumber").toString();
    }

}