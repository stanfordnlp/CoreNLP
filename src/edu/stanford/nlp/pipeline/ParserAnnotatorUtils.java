package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.util.CoreMap;

/** @author David McClosky */
public class ParserAnnotatorUtils {

  private ParserAnnotatorUtils() {} // static methods

  /** Put the tree in the CoreMap for the sentence, also add any
   *  dependency graphs to the sentence, and fill in missing tag annotations.
   *
   *  Thread safety note: nothing special is done to ensure the thread
   *  safety of the GrammaticalStructureFactory.  However, both the
   *  EnglishGrammaticalStructureFactory and the
   *  ChineseGrammaticalStructureFactory are thread safe.
   */
  public static void fillInParseAnnotations(boolean verbose, boolean buildGraphs,
                                            GrammaticalStructureFactory gsf, CoreMap sentence, Tree tree,
                                            GrammaticalStructure.Extras extras) {
    // make sure all tree nodes are CoreLabels
    // TODO: why isn't this always true? something fishy is going on
    Trees.convertToCoreLabels(tree);

    // index nodes, i.e., add start and end token positions to all nodes
    // this is needed by other annotators down stream, e.g., the NFLAnnotator
    tree.indexSpans(0);

    sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
    if (verbose) {
      System.err.println("Tree is:");
      tree.pennPrint(System.err);
    }

    if (buildGraphs) {
      // generate the dependency graph
      // unfortunately, it is necessary to make the
      // GrammaticalStructure three times, as the dependency
      // conversion changes the given data structure
      SemanticGraph deps = SemanticGraphFactory.generateCollapsedDependencies(gsf.newGrammaticalStructure(tree), extras);
      SemanticGraph uncollapsedDeps = SemanticGraphFactory.generateUncollapsedDependencies(gsf.newGrammaticalStructure(tree), extras);
      SemanticGraph ccDeps = SemanticGraphFactory.generateCCProcessedDependencies(gsf.newGrammaticalStructure(tree), extras);
      if (verbose) {
        System.err.println("SDs:");
        System.err.println(deps.toString(SemanticGraph.OutputFormat.LIST));
      }
      sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, deps);
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);
    }

    setMissingTags(sentence, tree);
  }

  /**
   * Set the tags of the original tokens and the leaves if they
   * aren't already set.
   */
  public static void setMissingTags(CoreMap sentence, Tree tree) {
    List<TaggedWord> taggedWords = null;
    List<Label> leaves = null;
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0, size = tokens.size(); i < size; ++i) {
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

}
