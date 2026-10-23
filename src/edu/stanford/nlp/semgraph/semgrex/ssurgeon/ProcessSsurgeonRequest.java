/**
 * A module with a command line program for the processing of ssurgeon requests.
 *<br>
 * This will compile a given semgrex expression, build SemanticGraph objects,
 * and return the results of those objects
 */

package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.ProcessSemgrexRequest;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ProcessProtobufRequest;
import edu.stanford.nlp.util.XMLUtils;

/**
 * Processes Ssurgeon requests sent as protobufs.  A request holds
 * Ssurgeon operations and the sentences to apply them to, and the
 * response holds each sentence after all of the operations have been
 * applied to it.
 *<br>
 * A sentence is sent either as a basic dependency graph with its
 * tokens, in {@code graph}, or as a SemgrexRequest.Dependencies with
 * its tokens, its basic graph, and optionally its enhanced graph, in
 * {@code sentence}.  The edits apply to every graph a sentence has.
 */
public class ProcessSsurgeonRequest extends ProcessProtobufRequest {
  /** Creates a processor; there is no state to initialize. */
  public ProcessSsurgeonRequest() { }

  /**
   * Read each operation, then read each graph and each sentence.  For
   * each, apply each operation and append the result to the output.
   *
   * @param request the Ssurgeon operations and the graphs and sentences to apply them to
   * @return one result per input graph, in order, with the edited graph and whether any operation
   *   changed it, and likewise one sentence result per input sentence, with the edited sentence
   * @throws RuntimeException naming the pattern and the sentence, if an operation fails on a sentence
   */
  public static CoreNLPProtos.SsurgeonResponse processRequest(CoreNLPProtos.SsurgeonRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    Ssurgeon inst = Ssurgeon.inst();
    StringBuilder xml = new StringBuilder();
    xml.append("<ssurgeon-pattern-list>\n");
    for (CoreNLPProtos.SsurgeonRequest.Ssurgeon operation : request.getSsurgeonList()) {
      xml.append("  <ssurgeon-pattern>\n");
      if (operation.hasId()) {
        xml.append("  <uid>" + XMLUtils.escapeXML(operation.getId()) + "</uid>\n");
      }
      if (operation.hasNotes()) {
        xml.append("  <notes>" + XMLUtils.escapeXML(operation.getNotes()) + "</notes>\n");
      }
      if (operation.hasLanguage()) {
        xml.append("  <language>" + XMLUtils.escapeXML(operation.getLanguage()) + "</language>\n");
      }
      xml.append("  <semgrex>" + XMLUtils.escapeXML(operation.getSemgrex()) + "</semgrex>\n");
      for (String op : operation.getOperationList()) {
        xml.append("  <edit-list>" + XMLUtils.escapeXML(op) + "</edit-list>\n");
      }
      xml.append("  </ssurgeon-pattern>\n");
    }
    xml.append("</ssurgeon-pattern-list>\n");
    List<SsurgeonPattern> patterns = inst.readFromString(xml.toString());

    CoreNLPProtos.SsurgeonResponse.Builder responseBuilder = CoreNLPProtos.SsurgeonResponse.newBuilder();
    for (CoreNLPProtos.DependencyGraph inputGraph : request.getGraphList()) {
      List<CoreLabel> tokens = inputGraph.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
      SemanticGraph graph = ProtobufAnnotationSerializer.fromProto(inputGraph, tokens, "ssurgeon");

      SemanticGraph newGraph = graph;
      boolean isChanged = false;
      for (SsurgeonPattern pattern : patterns) {
        Pair<SemanticGraph, Boolean> result = pattern.iterate(newGraph);
        newGraph = result.first;
        isChanged = isChanged || result.second;
      }
      CoreNLPProtos.SsurgeonResponse.SsurgeonResult.Builder graphBuilder = CoreNLPProtos.SsurgeonResponse.SsurgeonResult.newBuilder();
      graphBuilder.setGraph(serializer.toProto(newGraph, true));
      graphBuilder.setChanged(isChanged);
      responseBuilder.addResult(graphBuilder.build());
    }

    for (int sentenceIndex = 0; sentenceIndex < request.getSentenceCount(); ++sentenceIndex) {
      CoreNLPProtos.SemgrexRequest.Dependencies sentence = request.getSentence(sentenceIndex);
      // the graphs share their words, as Ssurgeon needs
      CoreMap read = ProcessSemgrexRequest.readSentence(serializer, sentence, sentenceIndex, "ssurgeon");
      SemgrexGraphs graphs = SemgrexGraphs.of(read.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
                                              read.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));

      boolean isChanged = false;
      for (SsurgeonPattern pattern : patterns) {
        try {
          Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
          graphs = result.first;
          isChanged = isChanged || result.second;
        } catch (RuntimeException e) {
          throw new RuntimeException("Ssurgeon pattern " + pattern.getUID() + " failed on " + describe(sentence, sentenceIndex), e);
        }
      }
      CoreNLPProtos.SsurgeonResponse.SsurgeonResult.Builder sentenceBuilder = CoreNLPProtos.SsurgeonResponse.SsurgeonResult.newBuilder();
      sentenceBuilder.setSentence(toDependencies(serializer, graphs, sentence));
      sentenceBuilder.setChanged(isChanged);
      responseBuilder.addSentenceResult(sentenceBuilder.build());
    }
    return responseBuilder.build();
  }

  /**
   * The edited sentence: the tokens of the words of its graphs, empty
   * words included, in order, then its graphs, built over those tokens,
   * and the sentenceID of the sentence in the request.  A copy of a word
   * has no token of its own, as its node refers to the token of the word.
   */
  private static CoreNLPProtos.SemgrexRequest.Dependencies toDependencies(ProtobufAnnotationSerializer serializer, SemgrexGraphs graphs,
                                                                         CoreNLPProtos.SemgrexRequest.Dependencies request) {
    Map<List<Integer>, IndexedWord> words = new TreeMap<>(Comparator.<List<Integer>>comparingInt(x -> x.get(0)).thenComparingInt(x -> x.get(1)));
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = graphs.get(name);
      if (graph == null) {
        continue;
      }
      for (IndexedWord word : graph.vertexSet()) {
        if (word.copyCount() == 0) {
          words.putIfAbsent(Arrays.asList(word.index(), word.getEmptyIndex()), word);
        }
      }
    }
    CoreNLPProtos.SemgrexRequest.Dependencies.Builder builder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
    for (IndexedWord word : words.values()) {
      builder.addToken(serializer.toProto(word.backingLabel()));
    }
    builder.setGraph(serializer.toProto(graphs.getDefault(), false));
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    if (enhanced != null) {
      builder.setEnhancedGraph(serializer.toProto(enhanced, false));
    }
    if (request.hasSentenceID()) {
      builder.setSentenceID(request.getSentenceID());
    }
    return builder.build();
  }

  /** A sentence of the request as it would be named in a complaint about it */
  private static String describe(CoreNLPProtos.SemgrexRequest.Dependencies sentence, int sentenceIndex) {
    if (sentence.hasSentenceID()) {
      return "the sentence with sent_id |" + sentence.getSentenceID() + "|";
    }
    return "sentence " + sentenceIndex + " of the request, counting from 0";
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.SsurgeonRequest request = CoreNLPProtos.SsurgeonRequest.parseFrom(in);
    CoreNLPProtos.SsurgeonResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a ssurgeon request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   *
   * @param args command line arguments; only {@code -multiple} as the first argument is used
   * @throws IOException if reading the request from stdin or writing the response to stdout fails
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessSsurgeonRequest(), args);
  }
}
