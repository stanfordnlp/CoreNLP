package edu.stanford.nlp.hcoref;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.util.SystemUtils;

public class Scorer {
  public static String getEvalSummary(String evalScript, 
      String goldFile, String predictFile) throws IOException {
    ProcessBuilder process = new ProcessBuilder(evalScript, "all", goldFile, predictFile, "none");
    StringOutputStream errSos = new StringOutputStream();
    StringOutputStream outSos = new StringOutputStream();
    PrintWriter out = new PrintWriter(outSos);
    PrintWriter err = new PrintWriter(errSos);
    SystemUtils.run(process, out, err);
    out.close();
    err.close();
    String summary = outSos.toString();
    String errStr = errSos.toString();
    if ( ! errStr.isEmpty()) {
      summary += "\nERROR: " + errStr;
    }
    Pattern pattern = Pattern.compile("\\d+\\.\\d\\d\\d+");
    DecimalFormat df = new DecimalFormat("#.##");
    Matcher matcher = pattern.matcher(summary);
    while(matcher.find()) {
      String number = matcher.group();
      summary = summary.replaceFirst(number, df.format(Double.parseDouble(number)));
    }
    return summary;
  }
}
