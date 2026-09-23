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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
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
   * The proto version of a graph name.
   *<br>
   * Each graph name has to be listed here and in the proto; a name
   * missing from this switch is an error, not a quiet BASIC.
   */
  static CoreNLPProtos.SemgrexResponse.GraphName toProto(SemgrexGraphName name) {
    switch (name) {
    case BASIC:
      return CoreNLPProtos.SemgrexResponse.GraphName.BASIC;
    case ENHANCED:
      return CoreNLPProtos.SemgrexResponse.GraphName.ENHANCED;
    default:
      throw new IllegalArgumentException("No proto GraphName for " + name);
    }
  }

  /**
   * Which graph of the sentence an edge was matched in, or null if it
   * is in none of them.
   *<br>
   * The edges a match names are the graph's own edge objects, so this
   * checks for that very object.  An edge which is in both the basic
   * and the enhanced graph is still reported as the one it was
   * matched in.
   */
  static SemgrexGraphName graphOfEdge(CoreMap sentence, SemanticGraphEdge edge) {
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = sentence.get(name.annotation);
      if (graph == null || !graph.containsVertex(edge.getSource())) {
        continue;
      }
      for (SemanticGraphEdge candidate : graph.getAllEdges(edge.getSource(), edge.getTarget())) {
        if (candidate == edge) {
          return name;
        }
      }
    }
    return null;
  }

  /**
   * Builds the PatternResult for one SemgrexPattern and one sentence
   *<br>
   * The sentence is used to tell which of its graphs each named edge came from.
   */
  public static CoreNLPProtos.SemgrexResponse.PatternResult matchSentence(SemgrexPattern pattern, CoreMap sentence, List<SemgrexMatch> matches, int patternIdx, int sentenceIdx) {
    CoreNLPProtos.SemgrexResponse.PatternResult.Builder patternResultBuilder = CoreNLPProtos.SemgrexResponse.PatternResult.newBuilder();
    patternResultBuilder.setSemgrexIndex(patternIdx);
    for (SemgrexMatch matcher : matches) {
      CoreNLPProtos.SemgrexResponse.Match.Builder matchBuilder = CoreNLPProtos.SemgrexResponse.Match.newBuilder();
      // the root of the match can be a copy or empty node if the
      // pattern starts in the enhanced graph
      IndexedWord root = matcher.getMatch();
      matchBuilder.setMatchIndex(root.index());
      if (root.copyCount() != 0) {
        matchBuilder.setMatchCopy(root.copyCount());
      }
      if (root.getEmptyIndex() != 0) {
        matchBuilder.setMatchEmptyIndex(root.getEmptyIndex());
      }
      matchBuilder.setSemgrexIndex(patternIdx);
      matchBuilder.setSentenceIndex(sentenceIdx);

      // add descriptions of the named nodes
      for (String nodeName : matcher.getNodeNames()) {
        CoreNLPProtos.SemgrexResponse.NamedNode.Builder nodeBuilder = CoreNLPProtos.SemgrexResponse.NamedNode.newBuilder();
        IndexedWord node = matcher.getNode(nodeName);
        nodeBuilder.setName(nodeName);
        nodeBuilder.setMatchIndex(node.index());
        if (node.copyCount() != 0) {
          nodeBuilder.setCopy(node.copyCount());
        }
        if (node.getEmptyIndex() != 0) {
          nodeBuilder.setEmptyIndex(node.getEmptyIndex());
        }
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
        if (edge.getSource().getEmptyIndex() != 0) {
          edgeBuilder.setSourceEmpty(edge.getSource().getEmptyIndex());
        }
        if (edge.getTarget().getEmptyIndex() != 0) {
          edgeBuilder.setTargetEmpty(edge.getTarget().getEmptyIndex());
        }
        SemgrexGraphName graphName = graphOfEdge(sentence, edge);
        if (graphName != null) {
          edgeBuilder.setGraph(toProto(graphName));
        }
        matchBuilder.addEdge(edgeBuilder.build());
      }

      // add descriptions of the variable strings
      for (String var : matcher.getVariableNames()) {
        CoreNLPProtos.SemgrexResponse.VariableString.Builder varBuilder = CoreNLPProtos.SemgrexResponse.VariableString.newBuilder();
        varBuilder.setName(var);
        varBuilder.setValue(matcher.getVariableString(var));
        matchBuilder.addVarstring(varBuilder.build());
      }

      patternResultBuilder.addMatch(matchBuilder.build());
    }
    return patternResultBuilder.build();
  }

  public static CoreNLPProtos.SemgrexResponse processRequest(List<CoreMap> sentences, List<SemgrexPattern> patterns) {
    Map<CoreMap, Integer> sentenceIndices = new IdentityHashMap<>();
    for (CoreMap sentence : sentences) {
      sentenceIndices.put(sentence, sentenceIndices.size());
    }

    Map<SemgrexPattern, Integer> semgrexIndices = new IdentityHashMap<>();
    for (SemgrexPattern pattern : patterns) {
      semgrexIndices.put(pattern, semgrexIndices.size());
    }

    CoreNLPProtos.SemgrexResponse.Builder responseBuilder = CoreNLPProtos.SemgrexResponse.newBuilder();
    Map<Integer, List<Pair<SemgrexPattern, List<SemgrexMatch>>>> allMatches = new LinkedHashMap<>();

    boolean isSorted = false;
    for (SemgrexPattern pattern : patterns) {
      if (pattern.isSorted()) {
        isSorted = true;
        break;
      }
    }

    for (SemgrexPattern pattern : patterns) {
      List<Pair<CoreMap, List<SemgrexMatch>>> patternMatches;
      if (isSorted) {
        patternMatches = pattern.matchSentences(sentences, false);
      } else {
        patternMatches = pattern.matchSentences(sentences, true);
      }

      for (int i = 0; i < patternMatches.size(); ++i) {
        Pair<CoreMap, List<SemgrexMatch>> sentenceMatches = patternMatches.get(i);
        int sentenceIdx = sentenceIndices.get(sentenceMatches.first());
        if (!allMatches.containsKey(sentenceIdx)) {
          allMatches.put(sentenceIdx, new ArrayList<>());
        }
        allMatches.get(sentenceIdx).add(new Pair<>(pattern, sentenceMatches.second()));
      }
    }

    Iterable<Integer> sentenceIterable;
    if (isSorted) {
      sentenceIterable = allMatches.keySet();
    } else {
      IntStream range = IntStream.range(0, sentences.size());
      sentenceIterable = () -> range.iterator();
    }

    for (int sentenceIdx : sentenceIterable) {
      CoreNLPProtos.SemgrexResponse.SentenceResult.Builder sentenceResultBuilder = CoreNLPProtos.SemgrexResponse.SentenceResult.newBuilder();
      sentenceResultBuilder.setSentenceIndex(sentenceIdx);

      CoreMap sentence = sentences.get(sentenceIdx);
      if (allMatches.containsKey(sentenceIdx)) {
        List<Pair<SemgrexPattern, List<SemgrexMatch>>> sentenceMatches = allMatches.get(sentenceIdx);
        for (Pair<SemgrexPattern, List<SemgrexMatch>> patternMatches : sentenceMatches) {
          SemgrexPattern pattern = patternMatches.first();
          int patternIdx = semgrexIndices.get(pattern);
          sentenceResultBuilder.addPattern(matchSentence(pattern, sentence, patternMatches.second(), patternIdx, sentenceIdx));
        }
      }

      responseBuilder.addSentence(sentenceResultBuilder.build());
    }
    return responseBuilder.build();
  }

  /**
   * For a single request, iterate through the sentences it includes,
   * and add the results of each Semgrex operation included in the
   * request.
   *<br>
   * Each sentence has a basic graph and optionally an enhanced graph.
   * Both are built over the same list of tokens, so a node reached in
   * one graph is the same node when a relation moves to the other.
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
      if (sentence.hasEnhancedGraph()) {
        SemanticGraph enhanced = ProtobufAnnotationSerializer.fromProto(sentence.getEnhancedGraph(), tokens, "semgrex");
        coremap.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, enhanced);
      }
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
