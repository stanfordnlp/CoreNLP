package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Reads in a treebank in any language annotated according to UD v2
 * and adds enhancements according to a rule-based system.
 *
 * @author Sebastian Schuster
 */

public class UniversalEnhancer {

  /** Logging for the empty nodes and edges copied into an enhanced graph */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalEnhancer.class);

  /** True if this is an empty word, such as the 5.1 of a gapped clause */
  private static boolean isEmptyNode(IndexedWord iw) {
    return iw.getEmptyIndex() > 0;
  }

  public static void copyEmptyNodes(SemanticGraph source, SemanticGraph target) {
    for (IndexedWord node : source.vertexSet()) {
      if (isEmptyNode(node)) {
        target.addVertex(node);
        log.debug("added vertex " + node);
      }
    }

    //remove all orphan dependencies
    for (SemanticGraphEdge edge: target.edgeListSorted()) {
      if (edge.getRelation().getShortName().equals("orphan")) {
        target.removeEdge(edge);
        log.debug("removed edge " + edge);
      }
    }
    for (SemanticGraphEdge edge : source.edgeIterable()) {
      if (edge.getRelation().getShortName().equals("orphan") || isEmptyNode(edge.getDependent()) || isEmptyNode(edge.getGovernor())) {
        target.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
        log.debug("added edge " + edge);
      }
    }
  }

  public static SemanticGraph enhanceGraph(SemanticGraph basic, SemanticGraph originalEnhanced,
                                           boolean keepEmptyNodes,
                                           Embedding embeddings,
                                           Pattern relativePronounsPattern) {

    SemanticGraph enhanced = new SemanticGraph(basic.typedDependencies());

    if (keepEmptyNodes && originalEnhanced != null) {
      copyEmptyNodes(originalEnhanced, enhanced);
    }

    if (embeddings != null) {
      UniversalGappingEnhancer.addEnhancements(enhanced, embeddings);
    }
    if (relativePronounsPattern != null) {
      UniversalGrammaticalStructure.addRef(enhanced, relativePronounsPattern);
    }
    UniversalGrammaticalStructure.collapseReferent(enhanced);
    UniversalGrammaticalStructure.propagateConjuncts(enhanced);
    UniversalGrammaticalStructure.addExtraNSubj(enhanced);
    UniversalGrammaticalStructure.addCaseMarkerInformation(enhanced);
    UniversalGrammaticalStructure.addCaseMarkerForConjunctions(enhanced);
    UniversalGrammaticalStructure.addConjInformation(enhanced);
    return enhanced;
  }

  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    String conlluFileName = props.getProperty("conlluFile");
    String relativePronounsPatternStr = props.getProperty("relativePronouns");
    String embeddingsFilename = props.getProperty("embeddings");
    boolean keepEmptyNodes = PropertiesUtils.getBool(props, "keepEmpty", false);

    if (conlluFileName == null) {
      throw new IllegalArgumentException("Expected a treebank to enhance: -conlluFile <file>");
    }

    // both of these are optional: enhanceGraph skips the referents when
    // there is no pattern to find them with, and skips the gapping when
    // there are no embeddings
    Pattern relativePronounsPattern =
        relativePronounsPatternStr == null ? null : Pattern.compile(relativePronounsPatternStr);
    Embedding embeddings =
        embeddingsFilename == null ? null : new Embedding(embeddingsFilename);

    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
    try (CoNLLUReader.GraphIterator sgIterator = new CoNLLUReader().graphIterator(conlluFileName)) {
      while (sgIterator.hasNext()) {
        Pair<SemanticGraph, SemanticGraph> sgs = sgIterator.next();
        SemanticGraph basic = sgs.first();
        SemanticGraph originalEnhanced = sgs.second();

        SemanticGraph enhanced = enhanceGraph(basic, originalEnhanced, keepEmptyNodes, embeddings, relativePronounsPattern);
        System.out.print(writer.printSemanticGraph(basic, enhanced));
      }
    }
  } // end main()

}
