package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.util.CoreMap;

/** @author David McClosky */
public class ParserAnnotatorUtils {

  private ParserAnnotatorUtils() {} // static methods

  /**
   * Thread safety note: nothing special is done to ensure the thread
   * safety of the GrammaticalStructureFactory.  However, both the
   * EnglishGrammaticalStructureFactory and the
   * ChineseGrammaticalStructureFactory are thread safe.
   */
  public static void fillInParseAnnotations(boolean verbose, boolean buildGraphs, GrammaticalStructureFactory gsf, CoreMap sentence, Tree tree) {
    // make sure all tree nodes are CoreLabels
    // TODO: why isn't this always true? something fishy is going on
    ParserAnnotatorUtils.convertToCoreLabels(tree);

    // index nodes, i.e., add start and end token positions to all nodes
    // this is needed by other annotators down stream, e.g., the NFLAnnotator
    tree.indexSpans(0);

    sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
    if (verbose) {
      System.err.println("Tree is:");
      tree.pennPrint(System.err);
    }

    if (buildGraphs) {
      String docID = sentence.get(CoreAnnotations.DocIDAnnotation.class);
      if (docID == null) {
        docID = "";
      }

      Integer sentenceIndex = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
      int index = (sentenceIndex == null) ? 0 : sentenceIndex;

      // generate the dependency graph
      SemanticGraph deps = SemanticGraphFactory.generateCollapsedDependencies(gsf.newGrammaticalStructure(tree), docID, index);
      SemanticGraph uncollapsedDeps = SemanticGraphFactory.generateUncollapsedDependencies(gsf.newGrammaticalStructure(tree), docID, index);
      SemanticGraph ccDeps = SemanticGraphFactory.generateCCProcessedDependencies(gsf.newGrammaticalStructure(tree), docID, index);
      if (verbose) {
        System.err.println("SDs:");
        System.err.println(deps.toString("plain"));
      }
      sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, deps);
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);
    }

    setMissingTags(sentence, tree);
  }

  /**
   * Set the tags of the original tokens and the leaves if they
   * aren't already set
   */
  public static void setMissingTags(CoreMap sentence, Tree tree) {
    List<TaggedWord> taggedWords = null;
    List<Label> leaves = null;
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);
      if (token.tag() == null) {
        if (taggedWords == null) {
          taggedWords = tree.taggedYield();
        }
        if (leaves == null) {
          leaves = tree.yield();
        }
        token.setTag(taggedWords.get(i).tag());
        Label leaf = leaves.get(i);
        if (leaf instanceof HasTag) {
          ((HasTag) leaf).setTag(taggedWords.get(i).tag());
        }
      }
    }
  }

  /**
   * Converts the tree labels to CoreLabels.
   * We need this because we store additional info in the CoreLabel, like token span.
   * @param tree
   */
  public static void convertToCoreLabels(Tree tree) {
    Label l = tree.label();
    if (!(l instanceof CoreLabel)) {
      CoreLabel cl = new CoreLabel();
      cl.setValue(l.value());
      tree.setLabel(cl);
    }

    for (Tree kid : tree.children()) {
      convertToCoreLabels(kid);
    }
  }

  /**
   * Construct a fall through tree in case we can't parse this sentence
   * @param words
   * @return a tree with X for all the internal nodes
   */
  public static Tree xTree(List<? extends HasWord> words) {
    TreeFactory lstf = new LabeledScoredTreeFactory();
    List<Tree> lst2 = new ArrayList<Tree>();
    for (HasWord obj : words) {
      String s = obj.word();
      Tree t = lstf.newLeaf(s);
      Tree t2 = lstf.newTreeNode("X", Collections.singletonList(t));
      lst2.add(t2);
    }
    return lstf.newTreeNode("X", lst2);
  }

}
