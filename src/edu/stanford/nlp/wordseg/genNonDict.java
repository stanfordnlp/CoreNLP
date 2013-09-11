package edu.stanford.nlp.wordseg;
import java.io.*;
import java.util.*;
import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITEPLUS;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;

/**
 * a stand-alone script that generates NonDict (non words character bigrams)
 *
 * @author Pi-Chuan Chang
 **/
public class genNonDict {
  static Set<String> nonDict = new HashSet<String>();

  public static void main(String args[]) throws IOException{
    BufferedReader r =
      new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    String line;
    while ((line = r.readLine()) != null) {
      line = ChineseUtils.normalize(line);
      line = line.replaceAll(WHITEPLUS, " ");
      char[] chars = line.toCharArray();
      
      for(int i = 1; i < chars.length-1; i++) {
        int prev = i-1;
        int next = i+1;
        if (chars[i]==' ') {
          StringBuilder sb = new StringBuilder();
          sb.append(chars[prev]).append(chars[next]);
          //System.err.println(sb.toString());
          nonDict.add(sb.toString());
        }
      }
    }
    
    for (String entry : nonDict) {
      System.out.println(entry);
    }
  }
}
