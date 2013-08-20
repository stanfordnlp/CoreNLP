package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Reads a data file and strips away the name of the specific entity from the
 * label. Eg: I_ORG is reduced to I. This is useful if we would like to do NER
 * in two stages i.e. first mark all Named Entity chunks and then classify the
 * marked segments into one of the types.
 * 
 * @author Vijay Krishnan
 */

public class StripNamedEntityName {

  public static void strip(String inputFile, String outputFile)
      throws IOException {

    BufferedReader br = new BufferedReader(new FileReader(inputFile));
    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
    String line;

    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.equals("")) {
        bw.write(line + "\n");
        continue;
      }
      StringTokenizer st = new StringTokenizer(line);
      if (st.countTokens() < 4) {
        bw.write(line + "\n");
        continue;
      }
      bw.write(st.nextToken() + " " + st.nextToken() + " " + st.nextToken()
          + " ");
      String label = st.nextToken();

      if (label.contains("-")) {
        // System.out.println(label);
        StringTokenizer st1 = new StringTokenizer(label, "-");
        bw.write(st1.nextToken() + "\n");
      } else {
        bw.write(label + "\n");
      }

      // bw.write(line + "\n");
    }

    br.close();
    bw.close();
  }

  public static void main(String[] args) throws IOException {
    // TODO Auto-generated method stub
    StripNamedEntityName.strip(
        "/u/kvijay/data/ner/column_data/eng_transformed.train",
        "/u/kvijay/data/ner/column_data/eng_transformed_ne_stripped.train");
  }

}
