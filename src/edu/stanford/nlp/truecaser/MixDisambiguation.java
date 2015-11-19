package edu.stanford.nlp.truecaser;

import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * This utility takes the tokens in a data file and picks the most
 * common casing of words.  It then outputs the most common case for
 * each word.
 *
 * @author Michel Galley
 */
public class MixDisambiguation {

  private static Map<String, Counter<String>> map = Generics.newHashMap();
  private static Map<String, String> highest = Generics.newHashMap();

  private MixDisambiguation() {} // static class

  public static void main(String[] args) throws IOException {
    boolean outputLowercase = true;
    for (String arg : args) {
      if (arg.equalsIgnoreCase("-noLowercase")) {
        outputLowercase = false;
        continue;
      }

      // everything else is considered a filename
      BufferedReader in = new BufferedReader(new FileReader(arg));
      for (String line; (line = in.readLine()) != null; ) {
        String[] toks = line.split(" ");
        for (String tok : toks) {
          String lctok = tok.toLowerCase();
          Counter<String> counter = map.get(lctok);
          if (counter == null) {
            counter = new ClassicCounter<>();
            map.put(lctok, counter);
          }
          counter.incrementCount(tok);
        }
      }
    }

    for (String k : map.keySet()) {
      Counter<String> counter = map.get(k);
      String maxstr = "";
      int maxcount = -1;
      for(String str : counter.keySet()) {
        int count = (int)counter.getCount(str);
        if (count > maxcount) {
          maxstr = str;
          maxcount = count;
        }
      }
      highest.put(k, maxstr);
    }

    for (String k : highest.keySet()) {
      String cased = highest.get(k);
      if (!outputLowercase && k.equals(cased)) {
        continue;
      }
      System.out.printf("%s\t%s\n", k, cased);
    }
  }
}