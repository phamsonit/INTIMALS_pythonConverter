package be.intimals.pythonConverter;

import org.w3c.dom.Document;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Utils {





    /**
     * collect file names from a directory
     * @param directory
     * @param list
     */
    public static void populateFileList(File directory, ArrayList<String> list){
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        ArrayList<String> fullNames = new ArrayList<>();
        for(int i=0; i<files.length; ++i)
            fullNames.add(files[i].getAbsolutePath());
        list.addAll(fullNames);
        File[] directories = directory.listFiles(File::isDirectory);
        for (File dir : directories) populateFileList(dir,list);
    }


    public static int countLiteral(List<String> list, String identifier){
        return Collections.frequency(list, identifier);
    }

    public static int countLines(String text){
        return text.split("\n").length;
    }

    /**
     * get a reading stream from input XML
     * @param fileName
     * @return
     */
    public static InputStream getXMLStream(String fileName) {
        String xmlString = xmlToString(fileName);
        InputStream is = new ByteArrayInputStream(xmlString.getBytes());
        return is;
    }

    /**
     * convert XML to a string
     * @param fileName
     * @return
     */
    private static String xmlToString(String fileName){
        String xmlString = "";
        try {
            Reader fileReader = new FileReader(fileName);
            BufferedReader bufReader = new BufferedReader(fileReader);
            StringBuilder sb = new StringBuilder();
            sb.append("<SourceFile Language=\"Python\" FullName=\""+fileName.substring(0,fileName.length()-4)+".py\">");
            String line = bufReader.readLine();
            while( line != null){
                sb.append(line).append("\n");
                line = bufReader.readLine();
            }
            sb.append("</SourceFile>");
            xmlString = sb.toString();
        }catch (Exception e){
            System.out.println("xml to string error");
        }
        return xmlString;
    }

    /**
     * white DOM object to file
     * @param doc
     * @param outputFile
     * @throws TransformerException
     */
    public static void writeDomObjectToFile(Document doc, String outputFile) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource domSource = new DOMSource(doc);
        StreamResult streamResult = new StreamResult(new File(outputFile));
        transformer.transform(domSource, streamResult);
    }


    /**
     * read Python source code
     * @param fileName
     * @return
     */
    public static List<String> readPyFile(String fileName){
        List<String> results = new LinkedList<>();
        try {
            Reader fileReader = new FileReader(fileName);
            BufferedReader bufReader = new BufferedReader(fileReader);
            String line = bufReader.readLine();
            while( line != null){
                results.add(line);
                line = bufReader.readLine();
            }
        }catch (Exception e){
            System.out.println("Read python file error");
        }
        return results;
    }

    /**
     * Copy python code to new file
     * @param fileName
     * @param newFileName
     */
    public static void copyPyFile(String fileName, String newFileName){
        try {
            StringBuilder sb = new StringBuilder();

            Reader fileReader = new FileReader(fileName);
            BufferedReader bufReader = new BufferedReader(fileReader);
            String line = bufReader.readLine();
            while( line != null){
                StringBuilder newString = new StringBuilder();
                for(int i=0; i<line.length(); ++i){
                    newString.append(line.charAt(i));
                }
                //append new line to sb
                sb.append(newString.toString());
                sb.append("\n");
                //read next line
                line = bufReader.readLine();
            }
            //write python code to new file
            FileWriter fw = new FileWriter(newFileName);
            fw.write(sb.toString());
            fw.flush();
            fw.close();
        }catch (Exception e){
            System.out.println("Read python file error");
        }
    }


}
