/**
 * A module with a command line program for the processing of semgrex requests.
 *<br>
 * This will compile a given semgrex expression, build SemanticGraph objects,
 * and return the results of those objects
 */

package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ProcessProtobufRequest;
import edu.stanford.nlp.util.XMLUtils;

/**
 * Processes Ssurgeon requests sent as protobufs.  A request holds
 * Ssurgeon operations and dependency graphs, and the response holds
 * each graph after all of the operations have been applied to it.
 */
public class ProcessSsurgeonRequest extends ProcessProtobufRequest {
  /** Creates a processor; there is no state to initialize. */
  public ProcessSsurgeonRequest() { }

  /**
   * Read each operation, then read each graph.  For each graph, apply each operation
   * and append it to the output.
   *
   * @param request the Ssurgeon operations and the graphs to apply them to
   * @return one result per input graph, in order, with the edited graph and whether any operation changed it
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
    return responseBuilder.build();
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
