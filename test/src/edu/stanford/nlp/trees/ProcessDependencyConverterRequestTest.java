package edu.stanford.nlp.trees;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.Tree;

public class ProcessDependencyConverterRequestTest {

  static CoreNLPProtos.DependencyConverterRequest buildRequest(String ... trees) {
    CoreNLPProtos.DependencyConverterRequest.Builder builder = CoreNLPProtos.DependencyConverterRequest.newBuilder();

    for (String tree : trees) {
      Tree t = Tree.valueOf(tree);
      builder.addTrees(ProtobufAnnotationSerializer.toFlattenedTree(t));      
    }

    return builder.build();
  }

  static void checkResults(CoreNLPProtos.DependencyConverterResponse response, String ... expectedResults) {
    Assert.assertEquals(expectedResults.length, response.getConversionsList().size());
    for (int i = 0; i < expectedResults.length; ++i) {
      CoreNLPProtos.DependencyGraph responseGraph = response.getConversionsList().get(i).getGraph();
      CoreNLPProtos.FlattenedParseTree responseTree = response.getConversionsList().get(i).getTree();
      Tree tree = ProtobufAnnotationSerializer.fromProto(responseTree);
      List<CoreLabel> sentence = tree.taggedLabeledYield(false, 1);

      SemanticGraph expected = SemanticGraph.valueOf(expectedResults[i], i);
      SemanticGraph graph = ProtobufAnnotationSerializer.fromProto(responseGraph, sentence, null);
      //for (IndexedWord word : expected.vertexSet()) {
      //  System.out.println(word + " " + word.index() + " " + word.sentIndex() + " " + word.docID());
      //}
      //for (IndexedWord word : graph.vertexSet()) {
      //  System.out.println(word + " " + word.index() + " " + word.sentIndex() + " " + word.docID());
      //}
      //System.out.println(expected.toCompactString());
      //System.out.println(graph.toCompactString());
      Assert.assertEquals(expected, graph);
    }
  }

  /** Test a single Tree turning into Dependencies */
  @Test
  public void testOneTree() {
    CoreNLPProtos.DependencyConverterRequest request = buildRequest("(ROOT (S (NP (NNP Jennifer)) (VP (VBZ has) (NP (JJ nice) (NNS antennae)))))");
    CoreNLPProtos.DependencyConverterResponse response = ProcessDependencyConverterRequest.processRequest(request);
    checkResults(response, "[has/VBZ-2 nsubj>Jennifer/NNP-1 obj>[antennae/NNS-4 amod>nice/JJ-3]]");
  }

  /** Test two trees turning into Dependencies */
  @Test
  public void testTwoTrees() {
    CoreNLPProtos.DependencyConverterRequest request = buildRequest("(ROOT (S (NP (NNP Jennifer)) (VP (VBZ has) (NP (JJ nice) (NNS antennae)))))",
                                                                    "(ROOT (S (NP (PRP She)) (VP (VBZ is) (ADJP (RB hella) (JJ basic)) (ADVP (RB though)))))");
    CoreNLPProtos.DependencyConverterResponse response = ProcessDependencyConverterRequest.processRequest(request);
    checkResults(response,
                 "[has/VBZ-2 nsubj>Jennifer/NNP-1 obj>[antennae/NNS-4 amod>nice/JJ-3]]",
                 "[basic/JJ-4 nsubj>She/PRP-1 cop>is/VBZ-2 advmod>hella/RB-3 advmod>though/RB-5]");
  }

}


