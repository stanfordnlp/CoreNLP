package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ProcessProtobufRequest;

/**
 * A module with a command line program for the processing of tsurgeon requests.
 *<br>
 * This will compile a given list of tsurgeon operations, build trees out of the
 * input proto, use the operations on these trees, and return the results.
 *<br>
 * The input request is a list of operations to run and a list of trees.
 * The result will be the effect of running each of the operations in order
 * on each of the trees, one output tree per input tree.
 * <br>
 * TODO: could add headfinder and basic category options
 */
public class ProcessTsurgeonRequest extends ProcessProtobufRequest {
  /** Creates a processor for tsurgeon requests. */
  public ProcessTsurgeonRequest() { }

  /**
   * Extract a list of operations from a TsurgeonRequest proto
   */
  private static List<Pair<TregexPattern, TsurgeonPattern>> parseOperations(List<CoreNLPProtos.TsurgeonRequest.Operation> protoOperations) {
    List<Pair<TregexPattern, TsurgeonPattern>> operations = new ArrayList<>();
    // TODO: could add headfinder and basic category options
    TregexPatternCompiler compiler = new TregexPatternCompiler();
    for (CoreNLPProtos.TsurgeonRequest.Operation protoOp : protoOperations) {
      TregexPattern tregex = compiler.compile(protoOp.getTregex());
      List<TsurgeonPattern> surgeries = protoOp.getTsurgeonList().stream().map(Tsurgeon::parseOperation).collect(Collectors.toList());
      TsurgeonPattern tsurgeon = Tsurgeon.collectOperations(surgeries);
      operations.add(new Pair<>(tregex, tsurgeon));
    }
    return operations;
  }

  /**
   * For a single request, iterate through the Trees it includes,
   * perform each Tsurgeon operation on each tree, and return
   * a result with one tree per input tree.
   *
   * @param request the operations and trees to process
   * @return the resulting trees, in the same order as the input trees
   */
  public static CoreNLPProtos.TsurgeonResponse processRequest(CoreNLPProtos.TsurgeonRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.TsurgeonResponse.Builder responseBuilder = CoreNLPProtos.TsurgeonResponse.newBuilder();

    List<Pair<TregexPattern, TsurgeonPattern>> operations = parseOperations(request.getOperationsList());
    List<Tree> trees = request.getTreesList().stream().map(ProtobufAnnotationSerializer::fromProto).collect(Collectors.toList());
    for (Tree tree : trees) {
      tree = Tsurgeon.processPatternsOnTree(operations, tree);
      responseBuilder.addTrees(ProtobufAnnotationSerializer.toFlattenedTree(tree));
    }
    return responseBuilder.build();
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.TsurgeonRequest request = CoreNLPProtos.TsurgeonRequest.parseFrom(in);
    CoreNLPProtos.TsurgeonResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a tsurgeon request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   *
   * @param args the command line arguments; only -multiple is recognized
   * @throws IOException if reading from stdin or writing to stdout fails
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessTsurgeonRequest(), args);
  }
}
