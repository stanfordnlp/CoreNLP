package edu.stanford.nlp.parser;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * @author Dan Klein
 */

public class TYieldDiff {

  static int maxLength = -1;
  static boolean alignWords = true;
  static boolean suppressMatches = false;
  static boolean filterPunctuation = true;
  static boolean unlabeled = false;
  static boolean minimizeAnnotation = true;

  static Set<String> punctuationTagStrings = null;

  static boolean isBadString(String str) {
    if (punctuationTagStrings == null) {
      punctuationTagStrings = new HashSet<String>();
      punctuationTagStrings.add(",");
      punctuationTagStrings.add("(");
      punctuationTagStrings.add("-LRB-");
      punctuationTagStrings.add("-RRB-");
      punctuationTagStrings.add(")");
      punctuationTagStrings.add("''");
      punctuationTagStrings.add("``");
      punctuationTagStrings.add("#");
      punctuationTagStrings.add("$");
      punctuationTagStrings.add("0");
      punctuationTagStrings.add(":");
      punctuationTagStrings.add(".");
      punctuationTagStrings.add("XXX");
      punctuationTagStrings.add("-NONE-");
    }
    if (filterPunctuation) {
      return punctuationTagStrings.contains(str);
    }
    return false;
  }

  private static Map<Span, List<Label>> getSpanToLabelList(Tree t) {
    Map<Span, List<Label>> mLL = new HashMap<Span, List<Label>>();
    int start = 0;
    int end = t.yield().size();
    getSpanToLabelListHelper(t, start, end, mLL);
    return mLL;
  }

  private static void getSpanToLabelListHelper(Tree t, int start, int end, Map<Span, List<Label>> mLL) {
    Span span = new Span(start, end);
    Label label = t.label();
    List<Label> labelList = mLL.get(span);
    if (labelList == null) {
      labelList = new ArrayList<Label>(5);
    }
    labelList.add(label);
    mLL.put(span, labelList);
    Tree[] kids = t.children();
    if (kids != null) {
      for (int i = 0; i < kids.length; i++) {
        int kidSpanLength = kids[i].yield().size();
        if (!kids[i].isLeaf()) {
          getSpanToLabelListHelper(kids[i], start, start + kidSpanLength, mLL);
        }
        start += kidSpanLength;
      }
    }
  }


  public static String diffString(Tree t1, Tree t2) {
    // print t1 in the context of t2
    if (!t1.yield().equals(t2.yield())) {
      //return t1.toString();
    }

    // collect info about their span -> label sets
    Map<Span, List<Label>> spanToLabelList1 = getSpanToLabelList(t1);
    Map<Span, List<Label>> spanToLabelList2 = getSpanToLabelList(t2);

    // do a recursion of selective printing and padding
    StringBuffer sb = diffStringHelper(new StringBuffer(), t1, 0, t1.yield().size(), spanToLabelList1, spanToLabelList2);
    return sb.toString();
  }


  private static StringBuffer diffStringHelper(StringBuffer sb, Tree t1, int start, int end, Map<Span, List<Label>> sLL1, Map<Span, List<Label>> sLL2) {
    // check if this node needs to be displayed
    // NOTE: if there's *any* difference in the chain over
    //       the current span, the whole chain gets shown
    boolean showLabel = true;
    Span span = new Span(start, end);
    List<Label> l1 = sLL1.get(span);
    List<Label> l2 = sLL2.get(span);
    if (!t1.isLeaf() && l1 != null && l2 != null && (unlabeled || l1.equals(l2))) {
      showLabel = false;
    }
    if (showLabel) {
      sb.append(" ");
      if (!t1.isLeaf()) {
        sb.append("(");
      }
      if (t1.label() != null) {
        sb.append(t1.label());
      }
    }
    Tree[] kids = t1.children();
    if (kids != null) {
      for (int i = 0; i < kids.length; i++) {
        int kidSpanLength = kids[i].yield().size();
        diffStringHelper(sb, kids[i], start, start + kidSpanLength, sLL1, sLL2);
        start += kidSpanLength;
      }
    }
    if (showLabel) {
      if (!t1.isLeaf()) {
        sb.append(")");
      }
    }
    return sb;
  }

  private static Pair<String, String> alignNonSpace(String s1, String s2) {
    s1 = " " + s1 + " ";
    s2 = " " + s2 + " ";
    int[][] bestAlignment = new int[s1.length()][s2.length()];
    int[][] backtrace = new int[s1.length()][s2.length()];
    for (int s1pos = 0; s1pos < s1.length(); s1pos++) {
      bestAlignment[s1pos][0] = 0;
      backtrace[s1pos][0] = 2;
    }
    for (int s2pos = 0; s2pos < s2.length(); s2pos++) {
      bestAlignment[0][s2pos] = 0;
      backtrace[0][s2pos] = 1;
    }
    backtrace[0][0] = 0;
    for (int s1pos = 1; s1pos < s1.length(); s1pos++) {
      for (int s2pos = 1; s2pos < s2.length(); s2pos++) {
        if (s1.charAt(s1pos) != ' ' && s2.charAt(s2pos) != ' ') {
          if (s1.charAt(s1pos) == s2.charAt(s2pos)) {
            if (s1.charAt(s1pos) == ')' || s1.charAt(s1pos) == '(' || (s1.charAt(s1pos) >= 'A' && s1.charAt(s1pos) <= 'Z')) {
              bestAlignment[s1pos][s2pos] = 100 + bestAlignment[s1pos - 1][s2pos - 1];
            } else {
              bestAlignment[s1pos][s2pos] = 1000 + bestAlignment[s1pos - 1][s2pos - 1];
            }
            backtrace[s1pos][s2pos] = 0;
          } else {
            bestAlignment[s1pos][s2pos] = 1 + bestAlignment[s1pos - 1][s2pos - 1];
            backtrace[s1pos][s2pos] = 0;
          }
        } else {
          bestAlignment[s1pos][s2pos] = bestAlignment[s1pos - 1][s2pos - 1];
          backtrace[s1pos][s2pos] = 0;
          if (s1.charAt(s1pos) == ' ') {
            if (bestAlignment[s1pos][s2pos - 1] > bestAlignment[s1pos][s2pos]) {
              bestAlignment[s1pos][s2pos] = bestAlignment[s1pos][s2pos - 1];
              backtrace[s1pos][s2pos] = 1;
            }
          }
          if (s2.charAt(s2pos) == ' ') {
            if (bestAlignment[s1pos - 1][s2pos] > bestAlignment[s1pos][s2pos]) {
              bestAlignment[s1pos][s2pos] = bestAlignment[s1pos - 1][s2pos];
              backtrace[s1pos][s2pos] = 2;
            }
          }
        }
      }
    }
    // reconstruct the strings
    StringBuffer sb1 = new StringBuffer();
    StringBuffer sb2 = new StringBuffer();
    int s1pos = s1.length() - 1;
    int s2pos = s2.length() - 1;
    while (s1pos >= 0 || s2pos >= 0) {
      //System.out.println(s1pos+" "+s2pos);
      if (backtrace[s1pos][s2pos] == 0) {
        sb1.append(s1.charAt(s1pos));
        sb2.append(s2.charAt(s2pos));
        s1pos--;
        s2pos--;
        continue;
      }
      if (backtrace[s1pos][s2pos] == 1) {
        sb1.append(' ');
        sb2.append(s2.charAt(s2pos));
        s2pos--;
        continue;
      }
      if (backtrace[s1pos][s2pos] == 2) {
        sb1.append(s1.charAt(s1pos));
        sb2.append(' ');
        s1pos--;
        continue;
      }
    }
    sb1 = new StringBuffer(sb1.substring(1, sb2.length() - 1));
    sb2 = new StringBuffer(sb2.substring(1, sb2.length() - 1));
    return new Pair<String, String>(sb1.reverse().toString(), sb2.reverse().toString());
  }

  public static void printDiff(Tree t1, Tree t2) {
    String tStr1;
    String tStr2;
    if (minimizeAnnotation) {
      tStr1 = diffString(t1, t2);
    } else {
      tStr1 = t1.toString();
    }
    if (minimizeAnnotation) {
      tStr2 = diffString(t2, t1);
    } else {
      tStr2 = t2.toString();
    }
    if (tStr1 == null) {
      tStr1 = " ";
    }
    if (tStr2 == null) {
      tStr2 = " ";
    }
    if (tStr1.equals(tStr2)) {
      if (!suppressMatches) {
        System.out.println("= " + tStr2);
      }
    } else {
      if (alignWords) {
        // add spaces as needed
        Pair<String, String> strPair = alignNonSpace(tStr1, tStr2);
        tStr1 = strPair.first;
        tStr2 = strPair.second;
      }
      //Pair strPair = alignNonSpace(t1.toString(), t2.toString());
      //System.out.println(strPair.first);
      //System.out.println(strPair.second);
      System.out.println("<" + tStr1);
      System.out.println(">" + tStr2);
    }
  }

  public static void main(String[] args) throws java.io.FileNotFoundException, java.io.IOException {

    if (args.length != 2 && args.length != 3) {
      System.err.println("Usage: Tdiff treeFile1 treeFile2 [maxLength]");
      System.exit(0);
    }

    String treeFileStr1 = args[0];
    String treeFileStr2 = args[1];

    if (args.length > 2) {
      maxLength = Integer.parseInt(args[2]);
    }

    TreeReader tR1 = new PennTreeReader(new BufferedReader(new FileReader(treeFileStr1)), new LabeledScoredTreeFactory(new StringLabelFactory()), new edu.stanford.nlp.trees.PruneNodesStripSubtagsTreeNormalizer(new Filter<Tree>() {
      /**
       * 
       */
      private static final long serialVersionUID = 3669635574979632179L;

      public boolean accept(Tree t) {
        Label l = t.label();
        if ((l != null) && (isBadString(l.toString()))) {
          return false;
        }
        return true;
      }
    }));
    TreeReader tR2 = new PennTreeReader(new BufferedReader(new FileReader(treeFileStr2)), new LabeledScoredTreeFactory(new StringLabelFactory()), new edu.stanford.nlp.trees.PruneNodesStripSubtagsTreeNormalizer(new Filter<Tree>() {
      /**
       * 
       */
      private static final long serialVersionUID = -2886338403801586219L;

      public boolean accept(Tree t) {
        Label l = t.label();
        if ((l != null) && (isBadString(l.toString()))) {
          return false;
        }
        return true;
      }
    }));

    Tree t1 = tR1.readTree();
    Tree t2 = tR2.readTree();

    while (t1 != null && t2 != null) {
      if (t1 == null) {
        System.err.println("Not enough trees in " + treeFileStr2 + ", exiting.");
        System.exit(0);
      }
      if (t2 == null) {
        System.err.println("Not enough trees in " + treeFileStr1 + ", exiting.");
        System.exit(0);
      }
      if (maxLength == -1 || t1.yield().size() <= maxLength || t2.yield().size() <= maxLength) {
        printDiff(t1, t2);
      }
      // else
      //	printDiff(t1,t1);
      t1 = tR1.readTree();
      t2 = tR2.readTree();
    }
  }
}
