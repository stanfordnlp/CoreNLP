package edu.stanford.nlp.international.french.scripts;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;

import java.io.*;
import java.util.*;

public class FrenchTreebankUDUpdater {


  public static LabeledScoredTreeFactory factory = new LabeledScoredTreeFactory();

  public static HashMap<String,String> wordToSplit = new HashMap<>();

  static {
    wordToSplit.put("au", "à,le");
    wordToSplit.put("aux", "à,les");
    wordToSplit.put("des", "de,les");
    wordToSplit.put("du", "de,le");
    wordToSplit.put("Au", "À,le");
    wordToSplit.put("Aux", "À,les");
    wordToSplit.put("Des", "De,les");
    wordToSplit.put("Du", "De,le");
  }

  /** Given a tree with an MWN child that contains a (PUNC -), merge and potentially remove MWN **/
  public static void fixNPWithHyphen(Tree parentTree) {
    System.err.println("---");
    System.err.println(parentTree);
    List<Tree> postMergeSubTrees = new ArrayList<Tree>();
    List<Tree> npNodes = parentTree.getChildrenAsList();
    for (int i = 0 ; i < npNodes.size() ; i++) {
      if (npNodes.get(i).label().value().equals("PUNC") &&
          npNodes.get(i).getLeaves().get(0).label().value().equals("-")) {
        Tree leftTree = postMergeSubTrees.size() > 0 ? postMergeSubTrees.remove(postMergeSubTrees.size()-1) : null;
        Tree rightTree = i+1 < npNodes.size() ? npNodes.get(i+1) : null;
        if (leftTree != null && rightTree != null && leftTree.value().equals(rightTree.value()) &&
            (leftTree.value().equals("N") || leftTree.value().equals("NC") || leftTree.value().equals("NPP")
            || leftTree.value().equals("DET") || leftTree.value().equals("ADJ"))) {
          Tree mergedHyphenTree = factory.newLeaf(leftTree.getLeaves().get(0).label().value() + "-" +
              rightTree.getLeaves().get(0).label().value());
          Tree tagTree = factory.newTreeNode(leftTree.value(), Arrays.asList(mergedHyphenTree));
          postMergeSubTrees.add(tagTree);
          System.err.println("XXXX "+tagTree);
          i++;
        } else {
          postMergeSubTrees.add(npNodes.get(i));
        }
      } else {
        postMergeSubTrees.add(npNodes.get(i));
      }
    }
    parentTree.setChildren(postMergeSubTrees);
    System.err.println(parentTree);
  }

  /** Given a tree with an MWN child that contains a (PUNC -), merge and potentially remove MWN **/
  public static void fixMWNWithHyphen(Tree parentTree, Tree mwnChildTree, int mwnIndex) {
    System.err.println("---");
    System.err.println(mwnChildTree);
    List<Tree> postMergeSubTrees = new ArrayList<Tree>();
    List<Tree> mwnNodes = mwnChildTree.getChildrenAsList();
    for (int i = 0 ; i < mwnNodes.size() ; i++) {
      if (mwnNodes.get(i).label().value().equals("PUNC") &&
          mwnNodes.get(i).getLeaves().get(0).label().value().equals("-")) {
        if (postMergeSubTrees.size() > 0 && i < mwnNodes.size() - 1) {
          Tree leftTree = postMergeSubTrees.remove(postMergeSubTrees.size() - 1);
          Tree rightTree = mwnNodes.get(i + 1);
          Tree mergedHyphenTree = factory.newLeaf(leftTree.getLeaves().get(0).label().value() + "-" +
              rightTree.getLeaves().get(0).label().value());
          Tree tagTree = factory.newTreeNode("N", Arrays.asList(mergedHyphenTree));
          postMergeSubTrees.add(tagTree);
          System.err.println(mergedHyphenTree);
          i += 1;
        }
      } else {
        postMergeSubTrees.add(mwnNodes.get(i));
      }
    }
    System.err.println(parentTree);
    if (postMergeSubTrees.size() == 1) {
      parentTree.removeChild(mwnIndex);
      parentTree.addChild(mwnIndex, postMergeSubTrees.get(0));
    } else {
      Tree newMWNNode = factory.newTreeNode("MWN", postMergeSubTrees);
      parentTree.removeChild(mwnIndex);
      parentTree.addChild(mwnIndex, newMWNNode);
    }
    System.err.println(parentTree);
  }

  /** Given a tree with (PREF .*-), merge hypen into next token **/
  public static void fixPREFEndingWithHyphen(Tree parentTree) {
    System.err.println("---");
    System.err.println(parentTree);
    List<Tree> newChildren = new ArrayList<Tree>();
    for (int i = 0 ; i < parentTree.getChildrenAsList().size() ; i++) {
      if (parentTree.getChildrenAsList().get(i).label().value().equals("PREF") &&
          parentTree.getChildrenAsList().get(i).getLeaves().get(0).label().value().endsWith("-") &&
          i < parentTree.getChildrenAsList().size()-1 &&
          !parentTree.getChildrenAsList().get(i+1).value().equals("PUNC")) {
        if (parentTree.getChildrenAsList().get(i+1).isPreTerminal()) {
          parentTree.getChildrenAsList().get(i+1).getLeaves().get(0).setValue(
              parentTree.getChildrenAsList().get(i).getLeaves().get(0).label().value() +
              parentTree.getChildrenAsList().get(i+1).getLeaves().get(0).label().value());
        } else if (i < parentTree.getChildrenAsList().size()-1 &&
            parentTree.getChildrenAsList().get(i+1).label().value().equals("MWN")){
          Tree mwnNodeToUpdate = parentTree.getChildrenAsList().get(i+1).getChildrenAsList().get(0).getLeaves().get(0);
          String mwnNodeToUpdateOriginalText = mwnNodeToUpdate.getLeaves().get(0).label().value();
          mwnNodeToUpdate.setValue(parentTree.getChildrenAsList().get(i).getLeaves().get(0).label().value() +
              mwnNodeToUpdateOriginalText);
        } else {
          newChildren.add(parentTree.getChildrenAsList().get(i));
        }
      } else {
        newChildren.add(parentTree.getChildrenAsList().get(i));
      }
    }
    if (newChildren.size() != parentTree.getChildrenAsList().size()) {
      parentTree.setChildren(newChildren);
      System.err.println(parentTree);
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
    TreeFactory tf = new LabeledScoredTreeFactory();
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, tf);
    /** iterate through trees **/
    Tree fullTree = tr.readTree();
    while (fullTree != null) {
      TregexPattern pattern;
      TregexMatcher matcher;
      // handle merging PREF .*-
      pattern = TregexPattern.compile("/.*/ < (PREF < /.*-/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        fixPREFEndingWithHyphen(matchTree);
      }
      // handle merging hyphens in MWN
      pattern = TregexPattern.compile("/.*/ < (MWN < (PUNC < /-/))");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        for (int i = 0 ; i < matchTree.getChildrenAsList().size() ; i++) {
          Tree mwnTree = matchTree.getChildrenAsList().get(i);
          if (mwnTree.label().value().equals("MWN"))
            fixMWNWithHyphen(matchTree, mwnTree, i);
        }
      }
      // handle merging hyphens in NP
      pattern = TregexPattern.compile("/AP|NP|MWP|PP|MWP|MWA|MWADV|MWPRO|MWD|SENT/ < (PUNC < /-/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        fixNPWithHyphen(matchTree);
      }
      // handle P < cases
      pattern = TregexPattern.compile("PP < (P < /(A|a)u|(D|d)es|(D|d)u/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        List<Tree> children = matchTree.getChildrenAsList();
        String firstWord = children.get(0).getLeaves().get(0).label().value();
        if (wordToSplit.containsKey(firstWord) && children.size() > 1 &&
            children.get(1).label().value().equals("NP")) {
          //System.out.println("---");
          //System.out.println(matchTree);
          // add DET to NP
          Tree newNode = createTagAndWordNode("DET", wordToSplit.get(firstWord).split(",")[1]);
          children.get(1).addChild(0, newNode);
          // remove original word
          matchTree.removeChild(0);
          // add P
          newNode = createTagAndWordNode("P", wordToSplit.get(firstWord).split(",")[0]);
          matchTree.addChild(0,newNode);
          //System.out.println(matchTree);
        }
      }
      // handle MWP < cases, where the new words stay in the MWP
      pattern = TregexPattern.compile("MWP < (P < /(A|a)u|(D|d)es|(D|d)u/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        List<Tree> children = matchTree.getChildrenAsList();
        String firstWord = children.get(0).getLeaves().get(0).label().value();
        if (wordToSplit.containsKey(firstWord)) {
          //System.out.println("---");
          //System.out.println(matchTree);
          // remove original word
          matchTree.removeChild(0);
          // add DET
          Tree newNode = createTagAndWordNode("DET", wordToSplit.get(firstWord).split(",")[1]);
          matchTree.addChild(0, newNode);
          // add P
          newNode = createTagAndWordNode("P", wordToSplit.get(firstWord).split(",")[0]);
          matchTree.addChild(0,newNode);
          //System.out.println(matchTree);
        }
      }
      // handle .* < MWP $ NP cases, where the DET moves into the adjoining NP

      // print updated tree
      System.out.println(fullTree);

      // update to next tree
      fullTree = tr.readTree();
    }
  }
}