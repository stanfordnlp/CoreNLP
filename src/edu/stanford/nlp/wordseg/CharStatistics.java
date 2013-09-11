package edu.stanford.nlp.wordseg;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.stats.ClassicCounter;
import java.io.*;
import java.util.*;

public class CharStatistics {
  private static int charCount = 0;

  public static void main(String args[]) {

    printNonLetterDigit(args[0]);
    
    // for a document, print out the char types (and # of counts)
    /*
    Map typeMap = getTypeCounts(args[0]);
    for (int type : new TreeSet<Integer>(typeMap.keySet())) {
      Set set = (Set)typeMap.get(type);
      int size = set.size();
      System.err.println(type+"\t"+size);
      if (size < 100) {
        System.err.println(StringUtils.join(set, ":"));
      }
    }

    System.err.println("#char="+charCount);
    */


    // for a document, print out # of <type,1char?>
    //countMisc(args[0]);
  }  

  private static void printNonLetterDigit(String filename) {
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
      String thisLine;
      Set<Character> charset = new HashSet<Character>();

      while ((thisLine = br.readLine()) != null) { // while loop begins here
        for (Character c : thisLine.toCharArray()) {
          int type = Character.getType(c);
          switch (type) {
          case Character.UPPERCASE_LETTER:
          case Character.LOWERCASE_LETTER:
          case Character.OTHER_LETTER:
          case Character.DECIMAL_DIGIT_NUMBER:
            break;
          default:
            charset.add(c);
          }
        }
      }

      for (Character c : charset) {
        System.out.print(c);
      }
      System.out.println();

    } // end try
    catch (IOException e) {
      System.err.println("Error: " + e);
    }

  }


  @SuppressWarnings("unused")
  private static void countMisc(String filename) {
    ClassicCounter<Pair<Integer,Boolean>> typeIsolated = new ClassicCounter<Pair<Integer,Boolean>>();

    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String thisLine;
      while ((thisLine = br.readLine()) != null) { // while loop begins here
        thisLine = thisLine.trim();
        String[] words = thisLine.split("\\s+");
        for (String w : words) {
          if (w.length() > 1) { // more than one words! not isolated
            for (Character c : w.toCharArray()) {
              //System.err.println("adding:"+c+"\t"+Character.getType(c)+"\tfalse");
              typeIsolated.incrementCount(new Pair<Integer, Boolean>(Character.getType(c), false));
            }
          } else if (w.length() == 1) {
            char c = w.charAt(0);
            //System.err.println("adding:"+c+"\t"+Character.getType(c)+"\ttrue");
            typeIsolated.incrementCount(new Pair<Integer, Boolean>(Character.getType(c), true));
          } else {
            continue;
            //System.err.println("Something's wrong");
            //System.exit(-1);
          }
        }
      }
    } // end try
    catch (IOException e) {
      System.err.println("Error: " + e);
    }
    for (Pair<Integer,Boolean> p : typeIsolated.keySet()) {
      System.err.println(p+"\t"+typeIsolated.getCount(p));
    }
  }

  private static Map<Integer, Set<Character>> getTypeCounts(String filename) {
    Map<Integer, Set<Character>> typeMap = new HashMap<Integer,Set<Character>>();
    
    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String thisLine;
      while ((thisLine = br.readLine()) != null) { // while loop begins here
        for (Character c : thisLine.toCharArray()) {
          charCount++;
          int type = Character.getType(c);
          Set<Character> set = typeMap.get(type);
          if (set == null) {
            set = new HashSet<Character>();
            set.add(c);
            typeMap.put(type, set);
          } else {
            set.add(c);
            typeMap.put(type, set);
          }
        }
      } // end while 
    } // end try
    catch (IOException e) {
      System.err.println("Error: " + e);
    }
    return typeMap;
  }
}
