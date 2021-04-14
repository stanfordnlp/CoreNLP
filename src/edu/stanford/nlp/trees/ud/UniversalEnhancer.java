package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Reads in a treebank in any language annotated according to UD v2
 * and adds enhancements according to a rule-based system.
 *
 * @author Sebastian Schuster
 */

public class UniversalEnhancer {

  private static boolean isEmptyNode(IndexedWord iw) {
    return (iw.pseudoPosition() * 10) % 10 > 0;
  }

  public static void copyEmptyNodes(SemanticGraph source, SemanticGraph target) {
    for (IndexedWord node : source.vertexSet()) {
      if (isEmptyNode(node)) {
        target.addVertex(node);
        System.err.println("added vertex" + node);
      }
    }

    //remove all orphan dependencies
    for (SemanticGraphEdge edge: target.edgeListSorted()) {
      if (edge.getRelation().getShortName().equals("orphan")) {
        target.removeEdge(edge);
        System.err.println("removed edge" + edge);

      }
    }
    for (SemanticGraphEdge edge : source.edgeIterable()) {
      if (edge.getRelation().getShortName().equals("orphan") || isEmptyNode(edge.getDependent()) || isEmptyNode(edge.getGovernor())) {
        target.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
        System.err.println("added edge" + edge);
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

  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);

    String conlluFileName = props.getProperty("conlluFile");

    String relativePronounsPatternStr = props.getProperty("relativePronouns");

    String embeddingsFilename = props.getProperty("embeddings");

    boolean keepEmptyNodes = PropertiesUtils.getBool(props, "keepEmpty", false);

    Pattern relativePronounsPattern = Pattern.compile(relativePronounsPatternStr);

    Iterator<Pair<SemanticGraph, SemanticGraph>> sgIterator; // = null;

    CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
    try {
      sgIterator = reader.getIterator(IOUtils.readerFromString(conlluFileName));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Embedding embeddings = null;
    if (embeddingsFilename != null) {
      embeddings = new Embedding(embeddingsFilename);
    }

    while (sgIterator.hasNext()) {
      Pair<SemanticGraph, SemanticGraph> sgs = sgIterator.next();
      SemanticGraph basic = sgs.first();
      SemanticGraph originalEnhanced = sgs.second();

      SemanticGraph enhanced = enhanceGraph(basic, originalEnhanced, keepEmptyNodes, embeddings, relativePronounsPattern);
      System.out.print(writer.printSemanticGraph(basic, enhanced));
    }

  } // end main()

}
