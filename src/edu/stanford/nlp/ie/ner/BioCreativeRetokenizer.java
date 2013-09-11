package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.StringTokenizer;

public class BioCreativeRetokenizer {

  public static void main(String[] args) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(args[0]));

    FileWriter out = new FileWriter(args[1]);

    String inLine = "";
    int i, j, k = 0;
    int len = 0;

    StringTokenizer st, st1;

    boolean b;

    while ((inLine = in.readLine()) != null) {
      k++;
      System.out.println("Processing line " + k + ".");

      st = new StringTokenizer(inLine, " ");

      while (st.hasMoreTokens()) {

        st1 = new StringTokenizer(st.nextToken(), "?:", true);

        while (st1.hasMoreTokens()) {
          out.write(st1.nextToken() + " ");
        }

      }

      out.write("\n");
    }

    out.close();

  }

}
