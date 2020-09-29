/**
 * convert XML files exported by pyRegurgitator to XML files formatted by INTIMALS
 *
 * author: Hoang-Son PHAM
 */

import be.intimals.pythonConverter.*;

public class Main {

    public static void main(String[] args) {

        //TEST on sample data
//        args = new String[2];
//        args[0] = "sample_input";
//        args[1] = "sample_output";

        if (args.length != 2) {
            System.out.println("Usage:");
            System.out.println("java -jar pyConverter.jar SOURCE_DIR RESULT_DIR");
            System.out.println("SOURCE_DIR is a directory containing source files");
            System.out.println("RESULT_DIR is a directory containing results");
            System.exit(-1);
        } else{
            //input source files
            String inputDir = args[0];
            //output results files
            String outputDir = args[1];
            //do transformation
            TransformPyAST pyAST = new TransformPyAST();
            pyAST.transformPyAST(inputDir, outputDir);
        }
    }

}
