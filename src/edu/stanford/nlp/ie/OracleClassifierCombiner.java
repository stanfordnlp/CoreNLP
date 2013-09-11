package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Random;

/**
 * Reads the different result files and writes to a new result file. The tags in
 * the new file correspond to the correct labeling if any one of the result file
 * provides the correct label else it corresponds to one of the resulting
 * labels. Used to find an upper bound on the accuracy of 
 * any combination of these different schemes
 * 
 * @author Vignesh Ganapathy
 * @author Vijay Krishnan
 */

public class OracleClassifierCombiner {

  public static String[] separate_last_two_columns(String s, int columns) {

    s = s.trim();
    String retVal[] = new String[3];
    retVal[0] = "";
    retVal[1] = "";
    retVal[2] = "";

    StringTokenizer st = new StringTokenizer(s);

    if (st.countTokens() < columns) {
      retVal[0] = s;
      return retVal;
    }

    for (int i = 0; i < columns - 2; i++)
      retVal[0] += st.nextToken() + " ";

    retVal[1] = st.nextToken();
    retVal[2] = st.nextToken();

    return retVal;
  }

  public static void getResult(String[] inputFiles, String outputFile,
      int columns) throws IOException {

    BufferedReader br[] = new BufferedReader[inputFiles.length];
    String line[] = new String[inputFiles.length];
    int i;

    Random rnum = new Random();
    String line_split[][] = new String[inputFiles.length][columns];

    for (i = 0; i < inputFiles.length; i++)
      br[i] = new BufferedReader(new FileReader(inputFiles[i]));

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

    boolean end = false;

    while (true) {

      for (i = 0; i < inputFiles.length; i++) {

        line[i] = br[i].readLine();
        if (line[i] != null) {
          String[] temp = separate_last_two_columns(line[i], columns);

          for (int j = 0; j < columns; j++) {
            line_split[i][j] = temp[j];
          }
        } else
          end = true;
      }

      if (end)
        break;

      for (i = 0; i < inputFiles.length; i++) {
        if ((line_split[i][columns - 1])
            .equalsIgnoreCase(line_split[i][columns - 2]))
          break;
      }

      if (i != inputFiles.length)
        bw.write(line[i] + "\n");
      else {
        bw.write(line[rnum.nextInt(inputFiles.length)] + "\n");
      }

    }

    for (i = 0; i < inputFiles.length; i++)
      br[i].close();

    bw.close();

  }

  public static void main(String[] args) throws IOException {
    // TODO Auto-generated method stub
    String[] inputFiles = new String[12];

    String baseDirectory = "/u/kvijay/data/ner/results/";

    if (args.length > 0)
      baseDirectory = args[0];

    // String testSet = ""; // or "_testb";
    String testSet = "";

    inputFiles[0] = "CMM_forward/IOB1" + testSet + "_converted_to_IOB1.res";
    inputFiles[1] = "CMM_forward/IOB2" + testSet + "_converted_to_IOB1.res";
    inputFiles[2] = "CMM_forward/IOBES" + testSet + "_converted_to_IOB1.res";
    inputFiles[3] = "CMM_forward/IO" + testSet + "_converted_to_IOB1.res";
    inputFiles[4] = "CMM_forward/IOE1" + testSet + "_converted_to_IOB1.res";
    inputFiles[5] = "CMM_forward/IOE2" + testSet + "_converted_to_IOB1.res";
    inputFiles[6] = "CMM_reverse/IOB1" + testSet + "_converted_to_IOB1.res";
    inputFiles[7] = "CMM_reverse/IOB2" + testSet + "_converted_to_IOB1.res";
    inputFiles[8] = "CMM_reverse/IOBES" + testSet + "_converted_to_IOB1.res";
    inputFiles[9] = "CMM_reverse/IO" + testSet + "_converted_to_IOB1.res";
    inputFiles[10] = "CMM_reverse/IOE1" + testSet + "_converted_to_IOB1.res";
    inputFiles[11] = "CMM_reverse/IOE2" + testSet + "_converted_to_IOB1.res";

    for (int i = 0; i < inputFiles.length; i++)
      inputFiles[i] = baseDirectory + inputFiles[i];

    String outputFile = baseDirectory + "CMM_result_of_12_labelings" + testSet
        + ".res";
    int columns = 3;

    if (args.length > 1)
      columns = Integer.parseInt(args[1]);

    OracleClassifierCombiner.getResult(inputFiles, outputFile, columns);

    System.out.println("done");
  }

}
