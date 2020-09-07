package edu.stanford.nlp.international.spanish.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.util.ConfusionMatrix;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

public class ConfusionMatrixTSV  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ConfusionMatrixTSV.class);

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.printf("Usage: java %s answers_file%n", ConfusionMatrixTSV.class.getName());
      System.exit(-1);
    }

    ConfusionMatrix<String> cm = new ConfusionMatrix<>();

    String answersFile = args[0];
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(answersFile), "UTF-8"));

    String line = br.readLine();
    for (; line != null; line = br.readLine()) {
      String[] tokens = line.split("\\s");
      if (tokens.length != 3) {
        System.err.printf("ignoring bad line");
        continue;
      }
      cm.add(tokens[2], tokens[1]);
    }

    System.out.println(cm.toString());
  }
}
