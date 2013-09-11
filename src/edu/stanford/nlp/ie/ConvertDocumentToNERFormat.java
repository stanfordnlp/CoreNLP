package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Converts the NER train data to the required format by replacing lines with
 * the DOCSTART with blank lines and throwing away pre-existing blank lines
 * 
 * @author Vijay Krishnan
 */

public class ConvertDocumentToNERFormat {

  public static void convert(String inputFile, String outputFile)
      throws IOException {

    BufferedReader br = new BufferedReader(new FileReader(inputFile));
    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
    String line;
    boolean beginning = true;

    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.equals(""))
        continue;

      if (line.contains("DOCSTART")) {
        if (beginning)
          beginning = false;
        else
          bw.write("\n");

        continue;
      }

      bw.write(line + "\n");
    }

    br.close();
    bw.close();

  }

  public static void main(String[] args) throws IOException {
    // TODO Auto-generated method stub
    String inputFile = args[0];
    String outputFile = args[1];
    ConvertDocumentToNERFormat.convert(inputFile, outputFile);

    System.out.println("test");

  }

}
