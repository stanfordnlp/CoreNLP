/**
 * A module with a command line program for the processing of semgrex requests.
 *<br>
 * This will compile a given semgrex expression, build SemanticGraph objects,
 * and return the results of those objects
 */

package edu.stanford.nlp.semgraph.semgrex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;

public class ProcessSemgrexRequest {
  /**
   * Builds a single inner SemgrexResult structure from the pair of a SemgrexPattern and a SemanticGraph
   */
  public static CoreNLPProtos.SemgrexResponse.SemgrexResult matchSentence(SemgrexPattern pattern, SemanticGraph graph) {
    CoreNLPProtos.SemgrexResponse.SemgrexResult.Builder semgrexResultBuilder = CoreNLPProtos.SemgrexResponse.SemgrexResult.newBuilder();
    SemgrexMatcher matcher = pattern.matcher(graph);
    while (matcher.find()) {
      CoreNLPProtos.SemgrexResponse.Match.Builder matchBuilder = CoreNLPProtos.SemgrexResponse.Match.newBuilder();
      matchBuilder.setMatchIndex(matcher.getMatch().index());

      for (String nodeName : matcher.getNodeNames()) {
        CoreNLPProtos.SemgrexResponse.NamedNode.Builder nodeBuilder = CoreNLPProtos.SemgrexResponse.NamedNode.newBuilder();
        nodeBuilder.setName(nodeName);
        nodeBuilder.setMatchIndex(matcher.getNode(nodeName).index());
        matchBuilder.addNode(nodeBuilder.build());
      }

      for (String relnName : matcher.getRelationNames()) {
        CoreNLPProtos.SemgrexResponse.NamedRelation.Builder relnBuilder = CoreNLPProtos.SemgrexResponse.NamedRelation.newBuilder();
        relnBuilder.setName(relnName);
        relnBuilder.setReln(matcher.getRelnString(relnName));
        matchBuilder.addReln(relnBuilder.build());
      }

      semgrexResultBuilder.addMatch(matchBuilder.build());
    }
    return semgrexResultBuilder.build();
  }

  public static CoreNLPProtos.SemgrexResponse processRequest(CoreNLPProtos.SemgrexRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.SemgrexResponse.Builder responseBuilder = CoreNLPProtos.SemgrexResponse.newBuilder();

    List<SemgrexPattern> patterns = request.getSemgrexList().stream().map(SemgrexPattern::compile).collect(Collectors.toList());
    for (CoreNLPProtos.SemgrexRequest.Dependencies sentence : request.getQueryList()) {
      CoreNLPProtos.SemgrexResponse.GraphResult.Builder graphResultBuilder = CoreNLPProtos.SemgrexResponse.GraphResult.newBuilder();

      List<CoreLabel> tokens = sentence.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
      SemanticGraph graph = ProtobufAnnotationSerializer.fromProto(sentence.getGraph(), tokens, "semgrex");
      for (SemgrexPattern pattern : patterns) {
        graphResultBuilder.addResult(matchSentence(pattern, graph));
      }

      responseBuilder.addResult(graphResultBuilder.build());
    }
    return responseBuilder.build();
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  public static void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.SemgrexRequest request = CoreNLPProtos.SemgrexRequest.parseFrom(in);
    CoreNLPProtos.SemgrexResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Processes multiple requests from the same stream.
   *<br>
   * As per the google suggestion for streaming multiple messages,
   * this reads the length of the buffer, then reads exactly that many
   * bytes and decodes it.  It repeats until either 0 is read for the
   * length or until EOF.
   *<br>
   * https://developers.google.com/protocol-buffers/docs/techniques#streamimg
   */
  public static void processMultipleInputs(InputStream in, OutputStream out) throws IOException {
    DataInputStream din = new DataInputStream(in);
    DataOutputStream dout = new DataOutputStream(out);
    int size = 0;
    do {
      try {
        size = din.readInt();
      } catch (EOFException e) {
        // If the stream ends without a closing 0, we consider that okay too
        size = 0;
      }

      // stream is done if there's a closing 0 or if the stream ends
      if (size == 0) {
        dout.writeInt(0);
        break;
      }

      byte[] inputArray = new byte[size];
      din.read(inputArray, 0, size);
      ByteArrayInputStream bin = new ByteArrayInputStream(inputArray);
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      processInputStream(bin, result);
      byte[] outputArray = result.toByteArray();
      dout.writeInt(outputArray.length);
      dout.write(outputArray);
    } while (size > 0);
  }

  /**
   * Command line tool for processing a semgrex request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   */
  public static void main(String[] args) throws IOException {
    if (args.length > 0 &&
        (args[0].equalsIgnoreCase("-multiple") || args[0].equalsIgnoreCase("--multiple"))) {
      processMultipleInputs(System.in, System.out);
    } else {
      processInputStream(System.in, System.out);
    }
  }
}
