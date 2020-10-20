package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.tagger.maxent.*;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.stream.*;

/** Class for automatically applying tags to a treebank **/

public class TreebankTagUpdater {

  public MaxentTagger maxentTagger;

  public TreebankTagUpdater(String taggerPath) {
    maxentTagger = new MaxentTagger(taggerPath);
  }

  /** run the supplied tagger on the leaves of this tree and update **/
  public void tagTree(Tree tree) {
    List<Tree> tagsAndWords = getTaggedLeaves(tree);
    assert(tagsAndWords.size() == tree.getLeaves().size());
    List<CoreLabel> sentence =
        tagsAndWords.stream().map(w -> w.getLeaves().get(0).value()).map(
            w -> CoreLabel.wordFromString(w)).collect(Collectors.toList());
    maxentTagger.tagCoreLabels(sentence);
    assert(tree.getLeaves().size() == sentence.size());
    for (int i = 0 ; i < sentence.size() ; i++) {
      tagsAndWords.get(i).setValue(sentence.get(i).tag());
    }
  }

  public List<Tree> getTaggedLeaves(Tree tree) {
    if (tree.isPreTerminal())
      return Arrays.asList(tree);
    else {
      List<Tree> returnList = new ArrayList<Tree>();
      for (Tree c : tree.getChildrenAsList()) {
        returnList.addAll(getTaggedLeaves(c));
      }
      return returnList;
    }
  }

  /** Read in a tree file, print out trees with updated tags **/
  public static void main(String[] args) throws IOException {
    // load properties
    Properties props = StringUtils.argsToProperties(args);
    String taggerPath = props.getProperty("tagger");
    String treeFilePath = props.getProperty("treeFile");
    // build tag updater
    TreebankTagUpdater updater = new TreebankTagUpdater(taggerPath);
    // read in trees, update, and print out updated tree
    // set up tree reader
    TreeFactory tf = new LabeledScoredTreeFactory();
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(treeFilePath), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, tf);
    Tree t = tr.readTree();
    while (t != null) {
      // update tree
      updater.tagTree(t);
      // print new updated tree
      System.out.println(t);
      // move on to next tree
      t = tr.readTree();
    }
  }

}
