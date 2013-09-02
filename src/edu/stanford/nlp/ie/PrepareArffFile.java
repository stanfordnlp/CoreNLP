package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Read the different results files and writes to a new results file. The tags
 * in the new file are the ones that won the majority vote.
 * 
 * @author Vijay Krishnan
 */

public class PrepareArffFile {

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

  public static void prepareArffFile(String[] inputFiles, String outputFile,
      int columns) throws IOException {

    BufferedReader br[] = new BufferedReader[inputFiles.length];
    String line[] = new String[inputFiles.length];

    int num_votes[] = new int[inputFiles.length];

    String line_split[][] = new String[inputFiles.length][columns];

    for (int i = 0; i < inputFiles.length; i++)
      br[i] = new BufferedReader(new FileReader(inputFiles[i]));

    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

    boolean end = false;

    while (true) {
      String correctClass = "O";
      String predictedClass = "";
      String outputLine = "";
      int number_of_entries = 0;
      for (int i = 0; i < inputFiles.length; i++) {

        num_votes[i] = 0;

        line[i] = br[i].readLine();
        if (line[i] != null) {
          String[] temp = separate_last_two_columns(line[i], columns);

          for (int j = 0; j < columns; j++) {
            // System.out.println(i + " " + j);
            line_split[i][j] = temp[j];
          }

          if (i == 0)
            correctClass = line_split[0][columns - 2].trim();

          predictedClass = line_split[i][columns - 1].trim();
          if (predictedClass.equalsIgnoreCase("O")) {
            // bw.write("O,O,");
            outputLine += "O,O,";
            number_of_entries++;
          } else if (!predictedClass.equals("")) {
            StringTokenizer st = new StringTokenizer(predictedClass, "-");
            // bw.write(st.nextToken() + "," + st.nextToken() + ",");
            outputLine += st.nextToken() + "," + st.nextToken() + ",";
            number_of_entries++;
          }

        } else
          end = true;
      }

      if (end)
        break;

      if ((!outputLine.equals("")) && (number_of_entries == inputFiles.length))
        bw.write(outputLine + correctClass + "\n");

      // Now write the corresponding line into the arff file

      /*
       * // Now find majority vote.... for (int i = 0; i <
       * inputFiles.length;i++) for (int j = 0; j < inputFiles.length;j++){
       * 
       * if ( (line_split[i][columns -1]).equalsIgnoreCase(line_split[j][columns
       * -1])) num_votes[i]++; } // now find the max id... int max = 0; int
       * max_id = 0; for (int j = 0; j < inputFiles.length;j++){ if
       * (num_votes[j] > max){ max = num_votes[j] ; max_id = j; } }
       * bw.write(line[max_id] + "\n");
       */

    }

    for (int i = 0; i < inputFiles.length; i++)
      br[i].close();

    bw.close();
  }

  public static void main(String[] args) throws IOException {
    // TODO Auto-generated method stub
    String[] inputFiles = new String[13];

    String baseDirectory = "/u/kvijay/data/ner/results/";

    if (args.length > 0)
      baseDirectory = args[0];

    // String testSet = ""; // or "_testb";
    String testSet = "_testb";

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

    inputFiles[12] = "CMM_majority_vote_of_12_labelings" + testSet + ".res";

    for (int i = 0; i < inputFiles.length; i++)
      inputFiles[i] = baseDirectory + inputFiles[i];

    String outputFile = baseDirectory + "CMM" + testSet + ".arff";
    int columns = 3;

    if (args.length > 1)
      columns = Integer.parseInt(args[1]);

    PrepareArffFile.prepareArffFile(inputFiles, outputFile, columns);

    System.out.println("done");
  }

}
