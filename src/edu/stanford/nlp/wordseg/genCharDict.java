package edu.stanford.nlp.wordseg;
import java.io.*;
import java.util.*;
import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITEPLUS;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;

/**
 * a stand-alone script that generates a list of seen characters
 *
 * @author Pi-Chuan Chang
 **/
public class genCharDict {
  static Set<Character> charDict = new HashSet<Character>();

  public static void main(String args[]) throws IOException{
    BufferedReader r =
      new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    String line;
    while ((line = r.readLine()) != null) {
      line = ChineseUtils.normalize(line);
      line = line.replaceAll(WHITEPLUS, "");
      char[] chars = line.toCharArray();
      
      for(int i = 0; i < chars.length; i++) {
        charDict.add(chars[i]);
      }
    }
    
    for (char c : charDict) {
      System.out.println(c);
    }
  }
}
