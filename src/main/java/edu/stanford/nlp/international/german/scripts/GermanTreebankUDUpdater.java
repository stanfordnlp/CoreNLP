package edu.stanford.nlp.international.german.scripts;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class GermanTreebankUDUpdater {

  public static LabeledScoredTreeFactory factory = new LabeledScoredTreeFactory();

  public static HashMap<String,String> wordToSplit = new HashMap<>();

  public static String taggerPath = "edu/stanford/nlp/models/pos-tagger/german-ud.tagger";

  public static String hyphenatedWordPatternString = "[ÄÖÜäöüẞßA-Za-z]+\\-[ÄÖÜäöüẞßA-Za-z]+";
  public static Pattern hyphenatedWordPattern = Pattern.compile(hyphenatedWordPatternString);

  static {
    wordToSplit.put("am", "an,dem");
    wordToSplit.put("Am", "An,dem");
    wordToSplit.put("ans", "an,das");
    wordToSplit.put("Ans", "An,das");
    wordToSplit.put("aufs", "auf,das");
    wordToSplit.put("Aufs", "Auf,das");
    wordToSplit.put("beim", "bei,dem");
    wordToSplit.put("Beim", "Bei,dem");
    wordToSplit.put("im", "in,dem");
    wordToSplit.put("Im", "In,dem");
    wordToSplit.put("ins", "in,das");
    wordToSplit.put("Ins", "In,das");
    wordToSplit.put("übers", "über,das");
    wordToSplit.put("Übers", "Über,das");
    wordToSplit.put("ums", "um,das");
    wordToSplit.put("Ums", "Um,das");
    wordToSplit.put("vom", "von,dem");
    wordToSplit.put("Vom", "Von,dem");
    wordToSplit.put("zum", "zu,dem");
    wordToSplit.put("Zum", "Zu,dem");
    wordToSplit.put("zur", "zu,der");
    wordToSplit.put("Zur", "Zu,der");
  }

  public static void splitHyphenatedToken(Tree tree) {
    List<Tree> childrenList = tree.getChildrenAsList();
    for (int i = 0 ; i < childrenList.size() ; i++) {
      if (childrenList.get(i).isPreTerminal()) {
        String potentialHyphenatedWord = childrenList.get(i).getLeaves().get(0).value();
        Matcher m = hyphenatedWordPattern.matcher(potentialHyphenatedWord);
        if (m.find()) {
          tree.removeChild(i);
          String wordTag = childrenList.get(i).value();
          String[] splitUpWord = potentialHyphenatedWord.split("-");
          for (int j = 0 ; j < splitUpWord.length-1 ; j++) {
            Tree newNode = createTagAndWordNode(wordTag, splitUpWord[splitUpWord.length-1-j]);
            tree.addChild(i, newNode);
            Tree hyphenNode = createTagAndWordNode("$[", "-");
            tree.addChild(i, hyphenNode);
          }
          Tree newNode = createTagAndWordNode(wordTag, splitUpWord[0]);
          tree.addChild(i, newNode);
        }
      }
    }
  }

  public static Tree createTagAndWordNode(String tag, String word) {
    Tree wordNode = factory.newLeaf(word);
    wordNode.setValue(word);
    Tree tagNode = factory.newTreeNode(tag, Arrays.asList(wordNode));
    tagNode.setValue(tag);
    return tagNode;
  }

  public static void main(String[] args) throws IOException {
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, factory);
    TreebankTagUpdater tagUpdater = new TreebankTagUpdater(taggerPath);
    /** iterate through trees **/
    Tree fullTree = tr.readTree();
    while (fullTree != null) {
      TregexPattern pattern;
      TregexMatcher matcher;
      // split hyphenated token
      pattern = TregexPattern.compile(String.format("/.*/ < (/.*/ < /%s/)", hyphenatedWordPatternString));
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        splitHyphenatedToken(matchTree);
      }
      // split MWT
      pattern = TregexPattern.compile(
          "/.*/ < (APPRART-AC < " +
              "/^((A|a)m|(A|a)ns|(A|a)ufs|(B|b)eim|(I|i)m|(I|i)ns|(Ü|ü)bers|(U|u)ms|(V|v)om|(Z|z)um|(Z|z)ur)$/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        List<Tree> childrenList = matchTree.getChildrenAsList();
        for (int i = 0 ; i < childrenList.size() ; i++) {
          if (childrenList.get(i).value().equals("APPRART-AC")) {
            String mwtWord = childrenList.get(i).getLeaves().get(0).value();
            if (wordToSplit.keySet().contains(mwtWord)) {
              matchTree.removeChild(i);
              Tree artNKNode = createTagAndWordNode("ART-NK", wordToSplit.get(mwtWord).split(",")[1]);
              matchTree.addChild(i,artNKNode);
              Tree apprACNode = createTagAndWordNode("APPR-AC", wordToSplit.get(mwtWord).split(",")[0]);
              matchTree.addChild(i,apprACNode);
            }
          }
        }
      }

      // print updated tree
      tagUpdater.tagTree(fullTree);
      System.out.println(fullTree);

      // update to next tree
      fullTree = tr.readTree();
    }
  }

}
