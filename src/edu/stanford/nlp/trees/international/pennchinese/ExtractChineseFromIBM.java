package edu.stanford.nlp.trees.international.pennchinese;

import java.io.*;
import java.util.regex.*;

/** This was used to get clean Chinese sentences out of IBM MT "bead" files
 *  for the April 2006 GALE dry run.
 */
public class ExtractChineseFromIBM {

  private static Pattern p1 = Pattern.compile("[\\[\\]][fb].*");
  private static Pattern p2 = Pattern.compile("\\$[A-Za-z]+_\\((.*?)\\|\\|.*?\\)");

  public static void main(String[] args) throws IOException {
    for (String arg : args) {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(arg), "UTF-8"));
      int sl = arg.lastIndexOf("/");
      String outFile;
      if (sl < 0) {
        outFile = arg;
      } else {
        outFile = "/scr/nlp/data/gale/ch-sent" + arg.substring(sl);
      }
      outFile += ".sent";
      PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8")));
      String line;
      while ((line = br.readLine()) != null) {
        if (p1.matcher(line).matches()) {
          continue;
        }
        Matcher m2 = p2.matcher(line);
        String out = line;
        if (m2.find()) {
          out = m2.replaceAll("$1");
        }
        pw.println(out);
      }
      br.close();
      pw.close();
    }
  }

}
