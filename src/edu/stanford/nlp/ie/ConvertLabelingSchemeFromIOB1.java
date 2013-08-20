package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Converts a data file from the default IOB1 labeling to any of the labelings
 * namely IO, IOB1, IOB2, IOE1, IOE2, IOBES
 * 
 * @author Vijay Krishnan
 */

public class ConvertLabelingSchemeFromIOB1 {

  public static String[] separate_last_column(String s, int columns) {

    s = s.trim();
    String retVal[] = new String[2];
    retVal[0] = "";
    retVal[1] = "";

    StringTokenizer st = new StringTokenizer(s);

    if (st.countTokens() < columns) {
      retVal[0] = s;
      return retVal;
    }

    for (int i = 0; i < columns - 1; i++)
      retVal[0] += st.nextToken() + " ";

    retVal[1] = st.nextToken();

    return retVal;
  }

  public static String[] firstCharAndRest(String s) {
    String retVal[] = new String[2];
    retVal[0] = "";
    retVal[1] = "";

    try {
      if (s.length() > 1)
        retVal[0] = s.substring(0, 1);
      else
        retVal[0] = "";
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      retVal[0] = "";
    }

    try {
      if (s.length() > 2)
        retVal[1] = s.substring(1);
      else
        retVal[1] = "";

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      retVal[1] = "";
    }

    return retVal;
  }

  public static String new_label(String prevLabel, String currLabel,
      String nextLabel, String newScheme) {

    String[] prevLabelSplit = firstCharAndRest(prevLabel);
    String[] currLabelSplit = firstCharAndRest(currLabel);
    String[] nextLabelSplit = firstCharAndRest(nextLabel);

    if (currLabel.equalsIgnoreCase("O"))
      return "O";

    
    if ((currLabel.trim()).equalsIgnoreCase(""))
      return "";
    
    if (newScheme.equalsIgnoreCase("IO"))
      return "I" + currLabelSplit[1];

    if (newScheme.equalsIgnoreCase("IOB1"))
      return currLabel;

    if (newScheme.equalsIgnoreCase("IOB2")) {
      if (currLabelSplit[0].equalsIgnoreCase("B"))
        return currLabel;

      if ((prevLabelSplit[1]).equalsIgnoreCase(currLabelSplit[1]))
        return currLabel;
      else
        return "B" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOE1")) {
      if ((nextLabelSplit[0]).equalsIgnoreCase("B"))
        return "E" + currLabelSplit[1];
      else
        return "I" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOE2")) {
      if (!((nextLabelSplit[0]).equalsIgnoreCase("I")))
        return "E" + currLabelSplit[1];

      if (currLabelSplit[1].equalsIgnoreCase(nextLabelSplit[1]))
        return "I" + currLabelSplit[1];
      else
        return "E" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOBES")) {
      boolean isStart = ((currLabelSplit[0].equalsIgnoreCase("B")) || (!(prevLabelSplit[1])
          .equalsIgnoreCase(currLabelSplit[1])));

      boolean isEnd = ((!((nextLabelSplit[0]).equalsIgnoreCase("I"))) || (!(currLabelSplit[1]
          .equalsIgnoreCase(nextLabelSplit[1]))));

      if (isStart && isEnd)
        return "S" + currLabelSplit[1];
      else if (isStart)
        return "B" + currLabelSplit[1];
      else if (isEnd)
        return "E" + currLabelSplit[1];
      else
        return "I" + currLabelSplit[1];

    }

    return "ERROR in spec";

  }

  public static void convertFormat(String inputFile, String outputFile,
      int columns, String format) throws IOException {

    BufferedReader br = new BufferedReader(new FileReader(inputFile));
    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
    String prevLine, currLine, nextLine;
    String[] prevSplit, currSplit, nextSplit;

    prevLine = "   ";
    prevSplit = separate_last_column(prevLine, columns);

    currLine = (br.readLine()).trim();
    currSplit = separate_last_column(currLine, columns);

    //int temp = 0;

    while ((nextLine = br.readLine()) != null) {

      nextLine = nextLine.trim();
      nextSplit = separate_last_column(nextLine, columns);
      // System.out.println(currSplit[0] + " " + currSplit[1] + " " + temp++);
      // System.out.println(prevSplit[1] + " AA " +currSplit[1] + " BB " +
      // nextSplit[1]);
      bw.write(currSplit[0] + " "
          + new_label(prevSplit[1], currSplit[1], nextSplit[1], format) + "\n");

      prevSplit[0] = currSplit[0];
      prevSplit[1] = currSplit[1];

      currSplit[0] = nextSplit[0];
      currSplit[1] = nextSplit[1];

    }
    nextLine = "   ";
    nextSplit = separate_last_column(nextLine, columns);
    bw.write(currSplit[0] + " "
        + new_label(prevSplit[1], currSplit[1], nextSplit[1], format) + "\n");

    br.close();
    bw.close();
  }

  public static void main(String[] args) {
    // TODO Auto-generated method stub
     String inputFile =
     "/u/kvijay/data/ner/column_data/eng_transformed.testb";
     String
     outputFile="/u/kvijay/data/ner/column_data/eng_transformed_IO.testb";
    int columns = 4;
     String format = "IO";
//    String inputFile = args[0];
  //  String outputFile = args[1];
 //   String format = args[2];

    if (args.length > 3)
      columns = Integer.parseInt(args[3]);

    try {
      convertFormat(inputFile, outputFile, columns, format);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    System.out.println("done");

  }

}
