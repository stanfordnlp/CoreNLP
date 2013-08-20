package edu.stanford.nlp.wordseg;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.process.ChineseDocumentToSentenceProcessor;
import edu.stanford.nlp.util.StringUtils;

/**
 * a simple utility for detecting unseen characters (actually not just chinese..)
 * 
 * sample usage: 
 * <code>java edu.stanford.nlp.wordseg.UnkChineseCharDetector /u/nlp/data/gale/segtool/stanford-seg/data/ctb6.all</cdde>
 *
 * @author Pi-Chuan Chang
 */


public class UnkChineseCharDetector {
  private static ChineseDocumentToSentenceProcessor cdtos;
  static Set<Character> charDB = new HashSet<Character>();
  static Set<Character> unkDB = new HashSet<Character>();

  public static void main(String args[]) throws IOException {
    cdtos = new ChineseDocumentToSentenceProcessor("/u/nlp/data/chinesefactfinder/data/chinese-segmenter/norm.simp.utf8");
    for (String arg : args) {
      BufferedReader in
        = new BufferedReader(new FileReader(arg));
      String line;
      while((line = in.readLine()) != null) {
        line = cdtos.normalization(line);
        char[] chars = line.toCharArray();
        for (char ch : chars) {
          charDB.add(ch);
          //System.err.println("add "+ch);
        }
      }
    }
    System.err.println("After normalization, total seen char types="+charDB.size());

    InputStreamReader sr = new InputStreamReader(System.in);
    BufferedReader br    = new BufferedReader(sr);

    System.err.println("Input from STDIN...");
    while (true) {
      String line;
      try {
        line = br.readLine();
      }
      catch (IOException e) {
        break;
      }
      if (line==null) {
        StringBuilder sb = new StringBuilder();
        for (char c : unkDB) {
          sb.append(c).append("\n");
        }
        System.err.println("#seen="+charDB.size());
        System.err.println("#unk ="+unkDB.size());
        System.err.println(" unk ="+unkDB);
        System.err.println("Writing to unk.list");
        StringUtils.printToFile("unk.list", sb.toString(), false);
        System.err.println("Done");
        break;
      }
      if (line.equals("") || line.equals("report")) {
        System.err.println("#seen="+charDB.size());
        System.err.println("#unk ="+unkDB.size());
        System.err.println(" unk ="+unkDB);
      }
      line = cdtos.normalization(line);
      char[] chars = line.toCharArray();
      for (char ch : chars) {
        if (!charDB.contains(ch) && !unkDB.contains(ch)) {
          System.err.println("UNK Char: "+ch);
          unkDB.add(ch);
        }
      }
    }
  }
}
