package edu.stanford.nlp.international.french.scripts;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;

import java.io.*;
import java.util.*;

public class FrenchTreebankUDUpdater {


  public static LabeledScoredTreeFactory factory = new LabeledScoredTreeFactory();

  public static HashMap<String,String> wordToSplit = new HashMap<>();

  public static HashSet<String> mwtWords;

  public static HashSet<String> acceptableMWTPostSplitTags;

  public static HashSet<String> acceptableHyphenMergeTags;

  static {
    wordToSplit.put("au", "à,le");
    wordToSplit.put("aux", "à,les");
    wordToSplit.put("des", "de,les");
    wordToSplit.put("du", "de,le");
    wordToSplit.put("Au", "À,le");
    wordToSplit.put("Aux", "À,les");
    wordToSplit.put("Des", "De,les");
    wordToSplit.put("Du", "De,le");

    mwtWords = new HashSet<>(Arrays.asList("Au", "au", "Aux", "aux", "Des", "des", "Du", "du"));

    acceptableMWTPostSplitTags = new HashSet<>(Arrays.asList("N", "ADV", "ADJ", "DET", "V", "NC"));

    acceptableHyphenMergeTags = new HashSet<>(Arrays.asList("N", "NC", "NPP", "DET", "ADJ"));
  }

  /** Given a tree with an NP child that contains a (PUNC -), merge and potentially remove MWN **/
  public static void fixNPWithHyphen(Tree parentTree) {
    System.err.println("---");
    System.err.println(parentTree);
    List<Tree> postMergeSubTrees = new ArrayList<Tree>();
    List<Tree> npNodes = parentTree.getChildrenAsList();
    for (int i = 0 ; i < npNodes.size() ; i++) {
      if (npNodes.get(i).label().value().equals("PUNC") &&
          npNodes.get(i).getLeaves().get(0).label().value().equals("-") &&
          postMergeSubTrees.size() > 0 &&
          i+1 < npNodes.size() &&
          acceptableHyphenMergeTags.contains(npNodes.get(i+1).value()) &&
          npNodes.get(i+1).value().equals(postMergeSubTrees.get(postMergeSubTrees.size()-1).value())) {
        Tree leftTree = postMergeSubTrees.remove(postMergeSubTrees.size()-1);
        Tree rightTree = npNodes.get(i+1);
        Tree mergedHyphenTree = factory.newLeaf(leftTree.getLeaves().get(0).label().value() + "-" +
            rightTree.getLeaves().get(0).label().value());
        Tree tagTree = factory.newTreeNode(leftTree.value(), Arrays.asList(mergedHyphenTree));
        postMergeSubTrees.add(tagTree);
        System.err.println("NP HYPHEN MERGE!!!");
        System.err.println("XXXX "+tagTree);
          i++;
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
    System.err.println(parentTree);
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
          System.err.println("MWN HYPHEN MERGE FIX!");
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
      System.err.println("PREF FIX!");
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

      // mwt splits
      TregexPattern mwtWordPattern = TregexPattern.compile("(P < /(A|a)u|(A|a)ux|(D|d)es|(D|d)u/)");
      TregexMatcher mwtMatcher;

      pattern = TregexPattern.compile("/PP|MWP|MWADV|NP|MWN/ < (P < /(A|a)u|(A|a)ux|(D|d)es|(D|d)u/)");
      matcher = pattern.matcher(fullTree);
      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        List<Tree> childrenList = matchTree.getChildrenAsList();
        // check all non ending words
        for (int i = 0 ; i < childrenList.size()-1; i++) {
          // handle basic
          if (childrenList.get(i).isPreTerminal() &&
              childrenList.get(i).value().equals("P") &&
              mwtWords.contains(childrenList.get(i).getLeaves().get(0).value()) &&
              childrenList.get(i+1).isPreTerminal() &&
              acceptableMWTPostSplitTags.contains(childrenList.get(i+1).value())) {
            System.err.println("---");
            System.err.println("MWT split!!");
            System.err.println(matchTree);
            String mwtWord = childrenList.get(i).getLeaves().get(0).value();
            // remove ith child
            matchTree.removeChild(i);
            // add DET
            Tree newNode = createTagAndWordNode("DET", wordToSplit.get(mwtWord).split(",")[1]);
            matchTree.addChild(i, newNode);
            // add P
            newNode = createTagAndWordNode("P", wordToSplit.get(mwtWord).split(",")[0]);
            matchTree.addChild(i,newNode);
            System.err.println(matchTree);
          } else if (childrenList.get(i).isPreTerminal() &&
              childrenList.get(i).value().equals("P") &&
              mwtWords.contains(childrenList.get(i).getLeaves().get(0).value()) && childrenList.get(i+1).value().equals("NP")) {
            String mwtWord = childrenList.get(i).getLeaves().get(0).value();
            Tree npNode = childrenList.get(i+1);
            // add DET to NP
            Tree newNode = createTagAndWordNode("DET", wordToSplit.get(mwtWord).split(",")[1]);
            npNode.addChild(0, newNode);
            // remove original word
            matchTree.removeChild(i);
            // add P
            newNode = createTagAndWordNode("P", wordToSplit.get(mwtWord).split(",")[0]);
            matchTree.addChild(i,newNode);
          }
        }
      }

      // handle .* < MWP $ NP cases, where the DET moves into the adjoining NP
      pattern = TregexPattern.compile("/.*/ < (MWP $ NP)");
      matcher = pattern.matcher(fullTree);

      while (matcher.find()) {
        Tree matchTree = matcher.getMatch();
        List<Tree> childrenList = matchTree.getChildrenAsList();
        for (int i = 0 ; i < childrenList.size()-1 ; i++) {
          if (childrenList.get(i).value().equals("MWP") &&
              childrenList.get(i+1).value().equals("NP")) {
            Tree mwpNode = childrenList.get(i);
            List<Tree> mwpChildren = mwpNode.getChildrenAsList();
            Tree lastMWPWord = mwpChildren.get(mwpChildren.size()-1);
            if (mwpChildren.get(mwpChildren.size()-1).isPreTerminal() &&
                mwtWords.contains(lastMWPWord.getLeaves().get(0).value())) {
              String mwtWord = lastMWPWord.getLeaves().get(0).value();
              Tree npNode = childrenList.get(i+1);
              // add DET to NP
              Tree newNode = createTagAndWordNode("DET", wordToSplit.get(mwtWord).split(",")[1]);
              npNode.addChild(0, newNode);
              // replace P
              Tree newPNode = createTagAndWordNode("P", wordToSplit.get(mwtWord).split(",")[0]);
              mwpNode.removeChild(mwpChildren.size()-1);
              mwpNode.addChild(mwpChildren.size()-1, newPNode);
            }
          }
        }
      }

      // print updated tree
      System.out.println(fullTree);

      // update to next tree
      fullTree = tr.readTree();
    }
  }
}