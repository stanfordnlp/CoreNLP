
/* 
*   @Author:  Danqi Chen
*   @Email:  danqi@cs.stanford.edu
*   @Created:  2014-08-25
*   @Last Modified:  2014-08-25
*/

package edu.stanford.nlp.depparser.nn;

import edu.stanford.nlp.depparser.util.CommandLineUtils;

import java.util.*;
import java.io.*;

public class DependencyParser 
{
    public static void main(String[] args) 
    {
        Map<String, String> params = CommandLineUtils.simpleCommandLineParser(args);

    	String trainFile = params.get("trainFile");
    	String devFile = params.get("devFile");
        String testFile = params.get("testFile");
        String outFile = params.get("outFile");
    	String modelFile = params.get("model");
        String embedFile = params.get("embeddingFile");

        NNParser parser = new NNParser();

    	if (trainFile != null)
    		parser.train(trainFile, devFile, modelFile, embedFile);

    	if (testFile != null)
    		parser.test(testFile, modelFile, outFile);
    }
}
