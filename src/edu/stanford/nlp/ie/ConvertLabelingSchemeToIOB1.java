package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Converts a data file from any of the labelings namely IO, IOB1, IOB2, IOE1,
 * IOE2, IOBES to the default IOB1 labeling.  In principle a useful utility,
 * but really, it needs to be rewritten!!
 *
 * @author Vijay Krishnan
 */

public class ConvertLabelingSchemeToIOB1 {

  private static String delimChar = "\t";
  private static boolean abbreviate; // = false;

  private ConvertLabelingSchemeToIOB1() {}


  public static String[] separate_last_two_columns(String s, int columns) {
    s = s.trim();
    String[] retVal = new String[3];
    retVal[0] = "";
    retVal[1] = "";
    retVal[2] = "";

    String[] cols = s.split(delimChar);

    if (cols.length < columns) {
      retVal[0] = s;
      return retVal;
    }

    for (int i = 0; i < columns - 2; i++) {
      if (i != 0) {
        retVal[0] += delimChar;
      }
      retVal[0] += cols[i];
    }

    retVal[1] = cols[columns-2];
    retVal[2] = cols[columns-1];

    if (abbreviate) {
      if (retVal[2].equals("ORGANIZATION")) {
        retVal[2] = "ORG";
      } else if (retVal[2].equals("LOCATION")) {
        retVal[2] = "LOC";
      } else if (retVal[2].equals("PERSON")) {
        retVal[2] = "PER";
      }
    }

    // System.err.println("Mapped |" + s + "| to");
    // System.err.println("|" + retVal[0] + "| |" + retVal[1] + "| |" + retVal[2] + "|");
    return retVal;
  }

  public static String[] firstCharAndRest(String s) {
    String[] retVal = new String[2];
    retVal[0] = "";
    retVal[1] = "";

    try {
      if (s.length() > 1)
        retVal[0] = s.substring(0, 1);
      else
        retVal[0] = "";
    } catch (Exception e) {
      e.printStackTrace();
      retVal[0] = "";
    }

    try {
      if (s.length() > 2)
        retVal[1] = s.substring(1);
      else
        retVal[1] = "";

    } catch (Exception e) {
      e.printStackTrace();
      retVal[1] = "";
    }

    return retVal;
  }

  public static String new_label(String prevLabel, String currLabel,
      String nextLabel, String newScheme) {

    String[] prevLabelSplit = firstCharAndRest(prevLabel);
    String[] currLabelSplit = firstCharAndRest(currLabel);
   // String[] nextLabelSplit = firstCharAndRest(nextLabel);

    // System.err.println("Curr label is |" + currLabel + "|");
    if (currLabel.equalsIgnoreCase("O")) {
      return "O";
    } else if (currLabel.equals("")) {
      return "";
    } else if (currLabel.equals("-X-")) {
      return currLabel;
    }

    if (newScheme.equalsIgnoreCase("IO")){
      if (currLabel.contains("-"))
      return "I" + currLabelSplit[1];
      else
        return "I-" + currLabel;
    }

    if (newScheme.equalsIgnoreCase("IOB1"))
      return currLabel;

    if (newScheme.equalsIgnoreCase("IOB2")) {
      if (currLabelSplit[0].equalsIgnoreCase("I"))
        return currLabel;

      if ((prevLabelSplit[1]).equalsIgnoreCase(currLabelSplit[1]))
        return currLabel;
      else
        return "I" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOE1")) {
      if ((prevLabelSplit[0]).equalsIgnoreCase("E"))
        return "B" + currLabelSplit[1];
      else
        return "I" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOE2")) {
      if (!((prevLabelSplit[0]).equalsIgnoreCase("E")))
        return "I" + currLabelSplit[1];

      if (currLabelSplit[1].equalsIgnoreCase(prevLabelSplit[1]))
        return "B" + currLabelSplit[1];
      else
        return "I" + currLabelSplit[1];
    }

    if (newScheme.equalsIgnoreCase("IOBES")) {

      if ((currLabelSplit[0].equalsIgnoreCase("I"))
          || (currLabelSplit[0].equalsIgnoreCase("E")))
        return "I" + currLabelSplit[1];

      if (currLabelSplit[1].equalsIgnoreCase(prevLabelSplit[1]))
        return "B" + currLabelSplit[1];
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
    String[] prevSplit, currSplit = { "" }, nextSplit;

    prevLine = "   ";
    prevSplit = separate_last_two_columns(prevLine, columns);

    currLine = (br.readLine());
    if (currLine != null) {
      currLine = currLine.trim();
      currSplit = separate_last_two_columns(currLine, columns);
    }

    while ((nextLine = br.readLine()) != null) {

      nextLine = nextLine.trim();
      nextSplit = separate_last_two_columns(nextLine, columns);
      // System.out.println(currSplit[0] + " " + currSplit[1] + " " + temp++);
      // System.out.println(prevSplit[1] + " AA " +currSplit[1] + " BB " +
      // nextSplit[1]);
      bw.write(currSplit[0] + delimChar
          + new_label(prevSplit[1], currSplit[1], nextSplit[1], format) + delimChar
          + new_label(prevSplit[2], currSplit[2], nextSplit[2], format) + "\n");

      prevSplit[0] = currSplit[0];
      prevSplit[1] = currSplit[1];
      prevSplit[2] = currSplit[2];

      currSplit[0] = nextSplit[0];
      currSplit[1] = nextSplit[1];
      currSplit[2] = nextSplit[2];
    }
    nextLine = "   ";
    nextSplit = separate_last_two_columns(nextLine, columns);
    bw.write(currSplit[0] + delimChar
        + new_label(prevSplit[1], currSplit[1], nextSplit[1], format) + delimChar
        + new_label(prevSplit[2], currSplit[2], nextSplit[2], format) + "\n");

    br.close();
    bw.close();
  }

  /** Converts a data file from any of the labelings namely IO, IOB1, IOB2, IOE1,
   *  IOE2, IOBES to the default IOB1 labeling.
   *  Usage: java ConvertLabelingSchemeToIOB1 inFile outFile
   *           [IO|IOB1|IOE1|IOBES|...] [columns] [abbreviate]
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("usage: ConvertLabelingSchemeToIOB1 inFile outFile [IO|IOB1|IOE1|IOBES|...] [columns] [abbrev]");
      return;
    }

    // String inputFile = "/u/kvijay/data/ner/results/CMM_forward/IO.res";
    // String
    // outputFile="/u/kvijay/data/ner/results/CMM_forward/IO_converted_to_IOB1.res";
    int columns = 3;
    // String format = "IO";

    String inputFile = args[0];
    String outputFile = args[1];
    String format = args[2];

    if (args.length > 3) {
      columns = Integer.parseInt(args[3]);
    }
    if (args.length > 4) {
      abbreviate = true;
    }

    try {
      convertFormat(inputFile, outputFile, columns, format);
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("done");
  }

}
