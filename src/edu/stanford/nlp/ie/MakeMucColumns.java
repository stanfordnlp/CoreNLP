package edu.stanford.nlp.ie;

import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.ling.Word;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Turns NER data from the MUC6/7 format into a simple column data format.
 *  Precisely, it produces 2 classes, giving words, PTB-tokenized, and the
 *  NER class.
 *
 *  @author Jenny Finkel
 *  @author Christopher Cox
 *  @author Christopher Manning
 */
public class MakeMucColumns {

  /**
   *  Usage: <code>
   *  java MakeMucColumns [-3|-deleteID|-deleteSpaces|-deleteAlternates] filename &gt; outputFile
   *  </code><br>
   *
   *  An example input file is the muc7 file training.ne.eng.keys.980205
   */
  public static void main(String[] args) throws Exception {

    Pattern sgml = Pattern.compile("<.*?>");
    Pattern begin = Pattern.compile("<(?:ENA|TI|NU)MEX.*?TYPE=\"(.*?)\".*?>");
    Pattern end = Pattern.compile("</(?:ENA|TI|NU)MEX>");
    Pattern beginIgnoreID = Pattern.compile("<(?:DOCID|STORYID|DOCNO|CODER).*?>");
    Pattern endIgnoreID = Pattern.compile("</(?:DOCID|STORYID|DOCNO|CODER).*?>");
    Pattern threePattern = Pattern.compile("PERSON|LOCATION|ORGANIZATION");
    int i = 0;
    boolean threeClasses = false;
    boolean deleteID = false;
    boolean deleteSpaces = false;
    boolean deleteAlternates = false;

    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equals("-3")) {
        threeClasses = true;
      } else if (args[i].equals("-deleteID")) {
        deleteID = true;
      } else if (args[i].equals("-deleteSpaces")) {
        deleteSpaces = true;
      } else if (args[i].equals("-deleteAlternates")) {
        deleteAlternates = true;
      } else {
        System.err.println("Unknown option: " + args[i]);
      }
      i++;
    }

    if (i >= args.length) {
      System.err.println("Usage: java MakeMucColumns filename > outputFile");
      System.exit(1);
    }
    String filename = args[i];
    PTBTokenizer<Word> ptb = PTBTokenizer.newPTBTokenizer(new FileReader(filename));
    List<Word> tokens = ptb.tokenize();
    String current = "O";
    int skipping = 0;
    for (Word w : tokens) {
      String word = w.word();
      if (sgml.matcher(word).matches()) {
        Matcher m = begin.matcher(word);
        if (m.matches()) {
          current = m.group(1);
          if (threeClasses && ! threePattern.matcher(current).matches()) {
            current = "O";
          } else if (deleteAlternates) {
            int ind = current.indexOf('|');
            if (ind > 0) {
              current = current.substring(0, ind);
            }
          }
        } else if (end.matcher(word).matches()) {
          current = "O";
        } else if (deleteID && beginIgnoreID.matcher(word).matches()) {
          skipping = 1;
        } else if (deleteID && endIgnoreID.matcher(word).matches()) {
          skipping = 0;
        } else if (word.equals("</DOC>")) {
          System.out.println();
        }
      } else {
        int spaceIndex = word.indexOf(' ');
        if (deleteSpaces && spaceIndex >= 0) {
          System.err.println("Deleting spaces in " + word);
          while (spaceIndex >= 0) {
            word = word.substring(0, spaceIndex)+word.substring(spaceIndex+1);
            spaceIndex = word.indexOf(' ');
          }
        }
        if (skipping == 0) {
          System.out.println(word+"\t"+current);
        } else {
          skipping++;
          if (skipping > 5) {
            System.err.println("Skipping too much at token " + word);
          }
        }
      }
    }
  } // end main


  private MakeMucColumns() {}

}

