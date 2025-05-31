/**
 * A module with a command line program for the processing of semgrex requests.
 *<br>
 * This will compile a given semgrex expression, build SemanticGraph objects,
 * and return the results of those objects
 */

package edu.stanford.nlp.semgraph.semgrex;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ProcessProtobufRequest;

public class ProcessSemgrexRequest extends ProcessProtobufRequest {
  /**
   * Builds a single inner SemgrexResult structure from the pair of a SemgrexPattern and a SemanticGraph
   */
  public static CoreNLPProtos.SemgrexResponse.SemgrexResult matchSentence(SemgrexPattern pattern, SemanticGraph graph, List<SemgrexMatch> matches, int patternIdx, int graphIdx) {
    CoreNLPProtos.SemgrexResponse.SemgrexResult.Builder semgrexResultBuilder = CoreNLPProtos.SemgrexResponse.SemgrexResult.newBuilder();
    for (SemgrexMatch matcher : matches) {
      CoreNLPProtos.SemgrexResponse.Match.Builder matchBuilder = CoreNLPProtos.SemgrexResponse.Match.newBuilder();
      matchBuilder.setMatchIndex(matcher.getMatch().index());
      matchBuilder.setSemgrexIndex(patternIdx);
      matchBuilder.setGraphIndex(graphIdx);

      // add descriptions of the named nodes
      for (String nodeName : matcher.getNodeNames()) {
        CoreNLPProtos.SemgrexResponse.NamedNode.Builder nodeBuilder = CoreNLPProtos.SemgrexResponse.NamedNode.newBuilder();
        nodeBuilder.setName(nodeName);
        nodeBuilder.setMatchIndex(matcher.getNode(nodeName).index());
        matchBuilder.addNode(nodeBuilder.build());
      }

      // add descriptions of the named relations
      for (String relnName : matcher.getRelationNames()) {
        CoreNLPProtos.SemgrexResponse.NamedRelation.Builder relnBuilder = CoreNLPProtos.SemgrexResponse.NamedRelation.newBuilder();
        relnBuilder.setName(relnName);
        relnBuilder.setReln(matcher.getRelnString(relnName));
        matchBuilder.addReln(relnBuilder.build());
      }

      // add descriptions of the named edges
      for (String edgeName : matcher.getEdgeNames()) {
        CoreNLPProtos.SemgrexResponse.NamedEdge.Builder edgeBuilder = CoreNLPProtos.SemgrexResponse.NamedEdge.newBuilder();
        edgeBuilder.setName(edgeName);
        SemanticGraphEdge edge = matcher.getEdge(edgeName);
        edgeBuilder.setSource(edge.getSource().index());
        edgeBuilder.setTarget(edge.getTarget().index());
        edgeBuilder.setReln(edge.getRelation().toString());
        edgeBuilder.setIsExtra(edge.isExtra());
        if (edge.getSource().copyCount() != 0) {
          edgeBuilder.setSourceCopy(edge.getSource().copyCount());
        }
        if (edge.getTarget().copyCount() != 0) {
          edgeBuilder.setTargetCopy(edge.getTarget().copyCount());
        }
        matchBuilder.addEdge(edgeBuilder.build());
      }

      semgrexResultBuilder.addMatch(matchBuilder.build());
    }
    return semgrexResultBuilder.build();
  }

  public static CoreNLPProtos.SemgrexResponse processRequest(List<CoreMap> sentences, List<SemgrexPattern> patterns) {
    CoreNLPProtos.SemgrexResponse.Builder responseBuilder = CoreNLPProtos.SemgrexResponse.newBuilder();
    List<Pair<CoreMap, List<Pair<SemgrexPattern, List<SemgrexMatch>>>>> allMatches = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      allMatches.add(new Pair<>(sentence, new ArrayList<>()));
    }
    for (SemgrexPattern pattern : patterns) {
      List<Pair<CoreMap, List<SemgrexMatch>>> patternMatches = pattern.matchSentences(sentences, true);
      for (int i = 0; i < sentences.size(); ++i) {
        Pair<CoreMap, List<SemgrexMatch>> sentenceMatches = patternMatches.get(i);
        allMatches.get(i).second().add(new Pair<>(pattern, sentenceMatches.second()));
      }
    }

    int graphIdx = 0;
    for (Pair<CoreMap, List<Pair<SemgrexPattern, List<SemgrexMatch>>>> sentenceMatches : allMatches) {
      CoreNLPProtos.SemgrexResponse.GraphResult.Builder graphResultBuilder = CoreNLPProtos.SemgrexResponse.GraphResult.newBuilder();

      int patternIdx = 0;
      SemanticGraph graph = sentenceMatches.first().get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      for (Pair<SemgrexPattern, List<SemgrexMatch>> patternMatches : sentenceMatches.second()) {
        SemgrexPattern pattern = patternMatches.first();
        graphResultBuilder.addResult(matchSentence(pattern, graph, patternMatches.second(), patternIdx, graphIdx));
        ++patternIdx;
      }

      responseBuilder.addResult(graphResultBuilder.build());
      ++graphIdx;
    }
    return responseBuilder.build();
  }

  /**
   * For a single request, iterate through the SemanticGraphs it
   * includes, and add the results of each Semgrex operation included
   * in the request.
   */
  public static CoreNLPProtos.SemgrexResponse processRequest(CoreNLPProtos.SemgrexRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();

    List<CoreMap> sentences = new ArrayList<>();
    for (CoreNLPProtos.SemgrexRequest.Dependencies sentence : request.getQueryList()) {
      final List<CoreLabel> tokens;
      if (sentence.getGraph().getTokenList().size() > 0) {
        tokens = sentence.getGraph().getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
      } else {
        tokens = sentence.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
      }
      SemanticGraph graph = ProtobufAnnotationSerializer.fromProto(sentence.getGraph(), tokens, "semgrex");
      CoreMap coremap = new ArrayCoreMap();
      coremap.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
      coremap.set(CoreAnnotations.TokensAnnotation.class, tokens);
      sentences.add(coremap);
    }

    List<SemgrexPattern> patterns = request.getSemgrexList().stream().map(SemgrexPattern::compile).collect(Collectors.toList());
    return processRequest(sentences, patterns);
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.SemgrexRequest request = CoreNLPProtos.SemgrexRequest.parseFrom(in);
    CoreNLPProtos.SemgrexResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a semgrex request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessSemgrexRequest(), args);
  }
}
