package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.StringTokenizer;

/**
 * Converts a tagged, tokenized file to the CoNLL format.
 * <p>Usage: java edu.stanford.nlp.ie.ner.ConvertInput [input_file]</p>
 */
public class ConvertInput {

  /**
   * Usage: java edu.stanford.nlp.ie.ner.ConvertInput [input_file]
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java edu.stanford.nlp.ie.ner.ConvertInput <input_file>");
      System.exit(1);
    }
    try {
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      String line;
      while ((line = in.readLine()) != null) {
        StringTokenizer tok = new StringTokenizer(line, " ");
        System.out.println(tok.nextToken());
        while (tok.hasMoreElements()) {
          StringBuffer word = new StringBuffer(tok.nextToken());
          for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == '/' && i < word.length() - 1 && word.charAt(i + 1) != '/') {
              word.setCharAt(i, ' ');
            }
          }
          System.out.println(word.toString());
        }
        System.out.println();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
