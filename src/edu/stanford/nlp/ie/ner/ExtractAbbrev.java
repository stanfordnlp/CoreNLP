package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * The ExtractAbbrev class implements a simple algorithm for
 * extraction of abbreviations and their definitions from biomedical text.
 * Abbreviations (short forms) are extracted from the input file, and those abbreviations
 * for which a definition (long form) is found are printed out, along with that definition,
 * one per line.
 * <p/>
 * A file consisting of short-form/long-form pairs (tab separated) can be specified
 * in tandem with the -testlist option for the purposes of evaluating the algorithm.
 *
 * @author Ariel Schwartz
 * @version 03/12/03
 */
public class ExtractAbbrev {

  HashMap mTestDefinitions = new HashMap();
  HashMap mStats = new HashMap();
  int truePositives = 0, falsePositives = 0, falseNegatives = 0, trueNegatives = 0;
  char delimiter = '\t';
  boolean testMode = false;

  char open = '(';
  char close = ')';

  private boolean isValidShortForm(String str) {
    return (hasLetter(str) && (Character.isLetterOrDigit(str.charAt(0)) || (str.charAt(0) == '(')));
  }

  private boolean hasLetter(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (Character.isLetter(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private boolean hasCapital(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private void loadTrueDefinitions(String inFile) {
    String abbrString, defnString, str = "";
    Vector entry;
    HashMap definitions = mTestDefinitions;

    try {
      BufferedReader fin = new BufferedReader(new FileReader(inFile));
      while ((str = fin.readLine()) != null) {
        int j = str.indexOf(delimiter);
        abbrString = str.substring(0, j).trim();
        defnString = str.substring(j, str.length()).trim();
        entry = (Vector) definitions.get(abbrString);
        if (entry == null) {
          entry = new Vector();
        }
        entry.add(defnString);
        definitions.put(abbrString, entry);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(str);
    }
  }

  private boolean isTrueDefinition(String shortForm, String longForm) {
    Vector entry;
    Iterator itr;

    entry = (Vector) mTestDefinitions.get(shortForm);
    if (entry == null) {
      return false;
    }
    itr = entry.iterator();
    while (itr.hasNext()) {
      if (itr.next().toString().equalsIgnoreCase(longForm)) {
        return true;
      }
    }
    return false;
  }

  private Vector extractAbbrPairs(String inFile) {

    String str, tmpStr, longForm = "", shortForm = "";
    String currSentence = "";
    int openParenIndex, closeParenIndex = -1, sentenceEnd, newCloseParenIndex, tmpIndex = -1;
    boolean newParagraph = true;
    StringTokenizer shortTokenizer;
    Vector candidates = new Vector();

    try {
      BufferedReader fin = new BufferedReader(new FileReader(inFile));
      while ((str = fin.readLine()) != null) {
        //str = str.replace('[', '(');
        //str = str.replace(']', ')');
        if (str.length() == 0) {
          continue;
        }
        int index = str.indexOf(" ");
        String id = str.substring(0, index);
        currSentence = str.substring(index + 1);

        openParenIndex = currSentence.indexOf(" " + open);
        do {
          //System.out.println(currSentence+" " + openParenIndex);
          if (openParenIndex > -1) {
            openParenIndex++;
          } else {
            break;
          }
          sentenceEnd = currSentence.length() - 1;
          closeParenIndex = currSentence.indexOf(close, openParenIndex);
          if (closeParenIndex < 0) {
            break;
          } else {
            longForm = currSentence.substring(0, openParenIndex).trim();
            shortForm = currSentence.substring(openParenIndex + 1, closeParenIndex).trim();
          }
          if (shortForm.length() > 0 || longForm.length() > 0) {
            if (shortForm.length() > 1 && longForm.length() > 1) {
              if ((shortForm.indexOf(open) > -1) && ((newCloseParenIndex = currSentence.indexOf(close, closeParenIndex + 1)) > -1)) {
                shortForm = currSentence.substring(openParenIndex + 1, newCloseParenIndex);
                closeParenIndex = newCloseParenIndex;
              }
              if ((tmpIndex = shortForm.indexOf(", ")) > -1) {
                shortForm = shortForm.substring(0, tmpIndex);
              }
              if ((tmpIndex = shortForm.indexOf("; ")) > -1) {
                shortForm = shortForm.substring(0, tmpIndex);
              }
              shortTokenizer = new StringTokenizer(shortForm);
              if (shortTokenizer.countTokens() > 2 || shortForm.length() > longForm.length()) {
                // Long form in ( )
                tmpIndex = currSentence.lastIndexOf(" ", openParenIndex - 2);
                tmpStr = currSentence.substring(tmpIndex + 1, openParenIndex - 1);
                longForm = shortForm;
                shortForm = tmpStr;
                // ???
                if (!hasCapital(shortForm)) {
                  shortForm = "";
                }
              }
              if (isValidShortForm(shortForm)) {
                String[] pair = extractAbbrPair(shortForm.trim(), longForm.trim());

                if (pair != null)
                //System.out.println(id + "\t" + pair[0] + "\t" + pair[1]);
                {
                  System.out.println("SHORT " + pair[0] + "\nLONG " + pair[1]);
                }
              }
            }
            currSentence = currSentence.substring(closeParenIndex + 1);
            //System.out.println(currSentence);
          } else if (openParenIndex > -1) {
            if ((currSentence.length() - openParenIndex) > 200)
            // Matching close paren was not found
            {
              currSentence = currSentence.substring(openParenIndex + 1);
            }
            break; // Read next line
          }
          shortForm = "";
          longForm = "";
        } while ((openParenIndex = currSentence.indexOf(" " + open)) > -1);
      }
      fin.close();
    } catch (Exception ioe) {
      ioe.printStackTrace();
      System.out.println(currSentence);
      System.out.println(tmpIndex);
    }
    return candidates;
  }

  private String findBestLongForm(String shortForm, String longForm) {
    int sIndex;
    int lIndex;
    char currChar;

    sIndex = shortForm.length() - 1;
    lIndex = longForm.length() - 1;
    for (; sIndex >= 0; sIndex--) {
      currChar = Character.toLowerCase(shortForm.charAt(sIndex));
      if (!Character.isLetterOrDigit(currChar)) {
        continue;
      }
      while (((lIndex >= 0) && (Character.toLowerCase(longForm.charAt(lIndex)) != currChar)) || ((sIndex == 0) && (lIndex > 0) && (Character.isLetterOrDigit(longForm.charAt(lIndex - 1))))) {
        lIndex--;
      }
      if (lIndex < 0) {
        return null;
      }
      lIndex--;
    }
    lIndex = longForm.lastIndexOf(" ", lIndex) + 1;
    return longForm.substring(lIndex);
  }

  private String[] extractAbbrPair(String shortForm, String longForm) {
    //System.out.println(shortForm+"::"+longForm);
    String bestLongForm;
    StringTokenizer tokenizer;
    int longFormSize, shortFormSize;

    if (shortForm.length() == 1) {
      return null;
    }
    bestLongForm = findBestLongForm(shortForm, longForm);
    if (bestLongForm == null) {
      return null;
    }
    tokenizer = new StringTokenizer(bestLongForm, " \t\n\r\f-");
    longFormSize = tokenizer.countTokens();
    shortFormSize = shortForm.length();
    for (int i = shortFormSize - 1; i >= 0; i--) {
      if (!Character.isLetterOrDigit(shortForm.charAt(i))) {
        shortFormSize--;
      }
    }
    if (bestLongForm.length() < shortForm.length() || bestLongForm.indexOf(shortForm + " ") > -1 || bestLongForm.endsWith(shortForm) || longFormSize > shortFormSize * 2 || longFormSize > shortFormSize + 5 || shortFormSize > 10) {
      return null;
    }

    if (testMode) {
      if (isTrueDefinition(shortForm, bestLongForm)) {
        System.out.println(shortForm + delimiter + bestLongForm + delimiter + "TP");
        truePositives++;
      } else {
        falsePositives++;
        System.out.println(shortForm + delimiter + bestLongForm + delimiter + "FP");
      }
    } else {
      // System.out.println(shortForm + delimiter + bestLongForm);
    }
    String[] ans = {shortForm, bestLongForm};
    return ans;
  }


  private static void usage() {
    System.err.println("Usage: ExtractAbbrev [-options] <filename>");
    System.err.println("       <filename> contains text from which abbreviations are extracted");
    System.err.println("       -testlist <file> = list of true abbreviation definition pairs");
    System.err.println("       -usage or -help = this message");
    System.exit(1);
  }

  public static void main(String[] args) {
    String shortForm, longForm, defnString, str;
    ExtractAbbrev extractAbbrev = new ExtractAbbrev();
    Vector candidates;
    String[] candidate;
    String filename = null;
    String testList = null;

    //parse arguments
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-testlist")) {
        if (i == args.length - 1) {
          usage();
        }
        testList = args[++i];
        extractAbbrev.testMode = true;
      } else if (args[i].equals("-usage")) {
        usage();
      } else if (args[i].equals("-help")) {
        usage();
      } else if (args[i].equals("-parenType")) {
        extractAbbrev.open = args[i + 1].charAt(0);
        extractAbbrev.close = args[i + 1].charAt(1);
        i++;
      } else {
        filename = args[i];
        // Must be last arg
        if (i != args.length - 1) {
          usage();
        }
      }
    }
    if (filename == null) {
      usage();
    }

    if (extractAbbrev.testMode) {
      extractAbbrev.loadTrueDefinitions(testList);
    }
    extractAbbrev.extractAbbrPairs(filename);
    if (extractAbbrev.testMode) {
      System.out.println("TP: " + extractAbbrev.truePositives + " FP: " + extractAbbrev.falsePositives + " FN: " + extractAbbrev.falseNegatives + " TN: " + extractAbbrev.trueNegatives);
    }
  }
}


