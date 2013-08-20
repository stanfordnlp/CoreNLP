package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

public class BioCreativeParenLabel {

  public static void main(String[] args) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(args[0]));
    FileWriter out = new FileWriter(args[1]);
    ;
    int level = 0;

    String line;

    while ((line = in.readLine()) != null) {
      // the line with the id should have no spaces
      if (line.indexOf(" ") < 0) {
        out.write(line + "\n");
        level = 0;
        continue;
      }

      // the blank line seperating sentences
      if (line.length() < 1) {
        out.write("\n");
        level = 0;
        continue;
      }

      if (line.charAt(0) == '(') {
        level++;
      }

      if (level == 0) {
        out.write(line.trim() + " N\n");
      } else {
        out.write(line.trim() + " Y\n");
      }

      if (line.charAt(0) == ')') {
        level--;
      }
    }
    out.close();
  }

}
