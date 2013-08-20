package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Merges lines from each BioCreative text file into a single line per file.
 * Each file is broken into one line per sentence for the tagging phase.
 * This program reverses that process.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioCreativeLineMerger {
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: BioCreativeLineMerger <file>");
      System.exit(1);
    }
    String line;
    boolean first = true;
    try {
      BufferedReader br = new BufferedReader(new FileReader(args[0]));
      while ((line = br.readLine()) != null) {
        if (line.startsWith("@@")) {
          if (first) {
            first = false;
          } else {
            System.out.println();
          }

          int nameEndIndex;
          if ((nameEndIndex = line.indexOf(' ')) != -1) {
            String name = line.substring(0, nameEndIndex);
            int tagIndex;
            // get rid of tag on file id
            if ((tagIndex = name.indexOf('/')) != -1) {
              System.out.print(name.substring(0, tagIndex));
              System.out.print(line.substring(nameEndIndex));
            } else {
              System.out.print(line);
            }
          } else {
            System.out.print(line);
          }
          System.out.print(' ');
        } else {
          System.out.print(line);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
