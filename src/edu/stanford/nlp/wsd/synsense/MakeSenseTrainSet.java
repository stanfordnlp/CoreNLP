package edu.stanford.nlp.wsd.synsense;

import java.io.*;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * 
 */
public class MakeSenseTrainSet {


  /*
   * Assume that args[0] has the filename and that args[1] has the word
   */
  public static void main(String[] args) {

    if (args.length < 3) {
      System.out.println("Usage: java MakeSenseTrainSet [file] [form0|form1|form2|form3|form4] [maxSentenceSize] [maxNumSentences]");
      System.exit(1);
    }
    try {
      File my_file = new File(args[0]);

      // get the word forms
      String[] wordForms = new String[5];
      int j = 0;
      StringTokenizer tok = new StringTokenizer(args[1], "|");
      while (tok.hasMoreTokens()) {
        wordForms[j++] = tok.nextToken();
      }

      int maxSentenceSize = Integer.parseInt(args[2]);
      int maxNumSentences = Integer.parseInt(args[3]);

      // a bunch of String constants we will reuse
      String wf = "<wf";
      String punc = "<punc";
      String verb = "pos=VB";
      String wnsn = "wnsn=";

      BufferedReader in = new BufferedReader(new FileReader(my_file));

      boolean hasQuote = false;
      String line;
      Vector<String> buffer = new Vector<String>();
      int wordIndex = -1;
      int wordStart = -1;
      int wordEnd = -1;
      String tempWord;
      int wordSense = -1;
      int numPrinted = 0;
      int tempSenseIndex;
      StringTokenizer tokSense;
      System.out.println(wordForms[0] + "|" + wordForms[1] + "|" + wordForms[2] + "|" + wordForms[3] + "|" + wordForms[4]);

      int i = 0;
      while ((line = in.readLine()) != null) {
        if (line.indexOf("</s>") >= 0) {
          // we're at the end of a sentence
          // check to see if we saw the target string
          if (wordIndex >= 0 && buffer.size() < maxSentenceSize && !hasQuote) {
            printBuffer(buffer);
            numPrinted++;
            // quit after getting enough sentences
            if (numPrinted >= maxNumSentences) {
              return;
            }
          }
          // in any case, dump the Vector buffer and continue
          buffer.clear();
          wordIndex = -1;
          wordSense = -1;
          hasQuote = false;

          // otherwise, does this line contain word/punctuation?
        } else if (line.indexOf(wf) >= 0 || line.indexOf(punc) >= 0) {
          // extract the word from the line
          wordStart = line.indexOf('>') + 1;
          wordEnd = line.indexOf('<', wordStart);
          tempWord = line.substring(wordStart, wordEnd);
          tempWord = replaceUnderscoreWithSpace(tempWord);
          // is this the first occurrence of the target word?
          if (wordIndex < 0 && line.indexOf(verb) >= 0 && (tempWord.equals(wordForms[0]) || tempWord.equals(wordForms[1]) || tempWord.equals(wordForms[2]) || tempWord.equals(wordForms[3]) || tempWord.equals(wordForms[4]))) {
            // yes it is, then mark it!
            tempSenseIndex = line.indexOf(wnsn);
            if (tempSenseIndex >= 0 && tempSenseIndex < line.length() - 6) {
              tokSense = new StringTokenizer(line.substring(tempSenseIndex + 5), "> ;");
              wordSense = Integer.parseInt(tokSense.nextToken());
              tempWord = tempWord + "^" + wordSense;
              wordIndex = i;
            }
          }
          if (tempWord.equals("\'\'") || tempWord.equals("``")) {
            hasQuote = true;
          }
          buffer.add(tempWord);
          i++;
        }
      }
    } catch (FileNotFoundException e) {
      System.out.println(e);
      System.out.println("Usage: java MakeTrainSet [file] [word]");
      System.exit(1);
    } catch (IOException e) {
      System.out.println(e);
      System.out.println("Usage: java MakeTrainSet [file] [word]");
      System.exit(1);
    }
  }


  /*
   * Used to print out the desired line.
   */
  private static void printBuffer(Vector<String> b) {
    Iterator<String> i = b.iterator();
    while (i.hasNext()) {
      System.out.print(i.next() + " ");
    }
    System.out.println();
  }

  private static String replaceUnderscoreWithSpace(String s) {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '_') {
        c = ' ';
      }
      result.append(c);
    }
    return result.toString();
  }

}
