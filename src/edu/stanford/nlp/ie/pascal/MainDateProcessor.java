package edu.stanford.nlp.ie.pascal;


import java.io.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date Preprocessor for GATE-annotated XML.
 * Normalizes date strings, adds an attribute to the <Date> XML tag
 * called "normalized=".
 *
 * @author Chris Cox
 */
public class MainDateProcessor {

  public static void main(String[] args) throws Exception {

    if (args[0].equalsIgnoreCase("-d")) {
      String suffix = ".date";
      if (args.length >= 4) {
        suffix = args[3];
      }
      String p = ".*";
      if (args.length >= 3) {
        p = args[2];
      }
      File dir = new File(args[1]);
      Pattern pattern = Pattern.compile(p);
      FilenameFilter ff = new DateFileFilter(pattern);
      File[] files = dir.listFiles(ff);
      for (int i = 0; i < files.length; i++) {
        FileReader infile = new FileReader(files[i]);
        BufferedWriter outfile = new BufferedWriter(new FileWriter(new File(files[i].getAbsolutePath() + suffix)));
        StringBuffer output = augmentDateTags(infile, files[i].getName());
        outfile.write(output.toString());
        infile.close();
        outfile.flush();
        outfile.close();
      }

    } else {

      for (int i = 0; i < args.length; i++) {
        FileReader infile = new FileReader(new File(args[i]));
        FileWriter outfile = new FileWriter(new File(args[i] + ".dateTagged"));
        System.err.println("Files created.");
        StringBuffer output = augmentDateTags(infile, args[i]);
        outfile.write(new String(output));
        outfile.close();

      }
    }
  }

  private static class DateFileFilter implements FilenameFilter {
    Pattern pattern;

    public DateFileFilter(Pattern p) {
      pattern = p;
    }

    public boolean accept(File dir, String name) {
      return pattern.matcher(name).matches();
    }
  }

  private static StringBuffer augmentDateTags(FileReader infile, String filename) {

    Pattern datePattern = Pattern.compile("(?:<Date(.*?)>)(.*?)(?:</Date>)", Pattern.DOTALL);
    //Pattern yearPattern = Pattern.compile("([1-2][0-9][0-9][0-9])");
    String inputAsString = new String();
    BufferedReader br = new BufferedReader(infile);
    String str;

    Map datesEncountered = new HashMap();

    try {
      str = br.readLine();
      while (str != null) {
        inputAsString = inputAsString.concat(str + "\n");
        str = br.readLine();
      }
    } catch (IOException e) {
      System.err.println("error" + e.getMessage());
    }

    Matcher m = datePattern.matcher(inputAsString);
    StringBuffer sb = new StringBuffer();
    //Matcher yearMatch = yearPattern.matcher(filename);
    //extracts the year from the filename.
    //	if(yearMatch.find()) {
    //  DateInstance.lastYearSet = new Integer((String)yearMatch.group()).intValue();
    //};
    DateInstance.lastYearSet = 1000;
    while (m.find()) {
      //            System.err.println("extracting next date.");
      String stringToWrite = extractNextDate(m, datesEncountered);
      //System.err.println("Appending to file: " + stringToWrite);
      m.appendReplacement(sb, "<Date$1 " + stringToWrite + ">)$2</Date>");

    }
    m.appendTail(sb);
    return sb;
  }

  private static String extractNextDate(Matcher m, Map datesEncountered) {

    Pattern tagExtraction = Pattern.compile("<.*?>", Pattern.DOTALL);
    Matcher tagExtractMatcher = tagExtraction.matcher(m.group(2));
    String nextString = tagExtractMatcher.replaceAll("");
    DateInstance di = new DateInstance();
    Calendar day;
    di.add(nextString);
    di.extractFields();
    day = di.getStartDate();
    String dayAsString = (day.get(Calendar.MONTH) + 1) + "/" + day.get(Calendar.DAY_OF_MONTH) + "/" + day.get(Calendar.YEAR);
    if (datesEncountered.containsKey(dayAsString)) {
      Integer tally = (Integer) datesEncountered.get(dayAsString);
      datesEncountered.put(dayAsString, Integer.valueOf(tally.intValue() + 1));
    } else {
      datesEncountered.put(dayAsString, Integer.valueOf(1));
    }
    String stringToWrite = new String("normalized=\"" + dayAsString + "\"");
    System.err.println("Normalized: " + dayAsString);
    stringToWrite = stringToWrite.concat(" range=\"" + di.isRange() + "\"");
    stringToWrite = stringToWrite.concat(" occurrence=\"" + datesEncountered.get(dayAsString) + "\"");
    return stringToWrite;
  }


  private static void printList(List l) {
    System.out.println("Printing list.  Found " + l.size() + " documents.");
    for (int i = 0; i < l.size(); i++) {
      List document = (List) l.get(i);
      System.out.print("Printing document " + i + ": ");
      for (int j = 0; j < document.size(); j++) {
        System.out.print(document.get(j) + " ");
      }
      System.out.print("\n");
    }
  }
}
