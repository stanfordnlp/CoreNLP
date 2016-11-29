package edu.stanford.nlp.coref;

import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.util.SystemUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Utilities for running coref evaluation scripts and printing the results
 * @author Heeyoung Lee
 * @author Kevin Clark
 */
public class CorefScorer {
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

  public static void printScoreSummary(String summary, Logger logger, boolean afterPostProcessing) {
    String[] lines = summary.split("\n");
    if(!afterPostProcessing) {
      for(String line : lines) {
        if(line.startsWith("Identification of Mentions")) {
          Redwood.log(line);
          return;
        }
      }
    } else {
      StringBuilder sb = new StringBuilder();
      for(String line : lines) {
        if(line.startsWith("METRIC")) sb.append(line);
        if(!line.startsWith("Identification of Mentions") && line.contains("Recall")) {
          sb.append(line).append("\n");
        }
      }
      Redwood.log(sb.toString());
    }
  }

  public static double getFinalConllScore(String summary) {
    Pattern f1 = Pattern.compile("Coreference:.*F1: (.*)%");
    Matcher f1Matcher = f1.matcher(summary);
    double[] F1s = new double[5];
    int i = 0;
    while (f1Matcher.find()) {
      F1s[i++] = Double.parseDouble(f1Matcher.group(1));
    }
    double finalScore = (F1s[0]+F1s[1]+F1s[3])/3;
    return finalScore;
  }

  public static void printFinalConllScore(String summary) {
    double finalScore = getFinalConllScore(summary);
    Redwood.log(
            "Final conll score ((muc+bcub+ceafe)/3) = " + (new DecimalFormat("#.##")).format(finalScore));
  }

  public static double getFinalConllScoreFromOutputDir(String corefOutputDir, String scorerPath) {
    File baseFolder = new File(corefOutputDir);
    File[] filesInBaseFolder = baseFolder.listFiles();
    String baseName = corefOutputDir;
    for (File outputFile : filesInBaseFolder) {
      String outputFileName = outputFile.getName();
      baseName = baseName + "/" + outputFileName.split("\\.")[0];
      break;
    }
    String goldOutput = baseName + ".gold.txt";
    String afterCorefOutput = baseName + ".coref.predicted.txt";
    try {
      String summary = CorefScorer.getEvalSummary(scorerPath, goldOutput, afterCorefOutput);
      double finalScore = getFinalConllScore(summary);
      return finalScore;
    } catch (IOException e) {
      Redwood.log("Error: failed to get coref score from directory");
      return -1;
    }
  }
}
