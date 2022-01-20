/**
 * A module with a command line program for the processing of tregex requests.
 *<br>
 * This will compile a given list of tregex queries, build trees out of the
 * input proto, query the N trees with the M queries, and return NxM results
 * <br>
 * TODO: could add headfinder and basic category options
 */

package edu.stanford.nlp.trees.tregex;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;

import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ProcessProtobufRequest;

public class ProcessTregexRequest extends ProcessProtobufRequest {
  /**
   * Extract a list of queries from a TregexRequest proto
   */
  private static List<TregexPattern> parseQueries(CoreNLPProtos.TregexRequest request) {
    // TODO: could add headfinder and basic category options
    TregexPatternCompiler compiler = new TregexPatternCompiler();
    List<TregexPattern> queries = request.getTregexList().stream().map(compiler::compile).collect(Collectors.toList());
    return queries;
  }

  /**
   * If the subtree is found in the current tree, return the node position in a preorder traversal.
   * This will match the order the nodes are added to a FlattenedParseTree in the serialization.
   */
  public static int findIndex(Tree top, Tree subtree) {
    int offset = 0;
    Stack<Tree> stack = new Stack<>();
    stack.push(top);
    while (stack.size() > 0) {
      Tree current = stack.pop();
      if (current == subtree) {
        return offset;
      }
      ++offset;
      Tree[] children = current.children();
      for (int i = children.length - 1; i >= 0; --i) {
        stack.push(children[i]);
      }
    }
    return -1;
  }

  /**
   * Convert a single match on a tree to a TregexResponse.Match object
   *<br>
   * Adds the position of the match, all of its named nodes, and all of its variable groups
   */
  public static CoreNLPProtos.TregexResponse.Match convertMatch(Tree tree, TregexMatcher matcher) {
    CoreNLPProtos.TregexResponse.Match.Builder matchBuilder = CoreNLPProtos.TregexResponse.Match.newBuilder();

    Tree match = matcher.getMatch();
    matchBuilder.setPosition(findIndex(tree, match));

    // Add a entry to the match for each named node from the pattern
    Set<String> nodeNames = new TreeSet<>(matcher.getNodeNames());
    for (String nodeName : nodeNames) {
      Tree node = matcher.getNode(nodeName);
      if (node == null) {
        continue;
      }
      CoreNLPProtos.TregexResponse.MatchNode.Builder nodeBuilder = CoreNLPProtos.TregexResponse.MatchNode.newBuilder();
      nodeBuilder.setName(nodeName);
      nodeBuilder.setPosition(findIndex(tree, node));
      matchBuilder.addNodes(nodeBuilder.build());
    }

    // Add entries for variable names as well
    // sort them before adding
    Set<String> varNames = new TreeSet<>(matcher.getVariableNames());
    for (String varName : varNames) {
      String value = matcher.getVariableString(varName);
      if (value == null) {
        continue;
      }
      CoreNLPProtos.TregexResponse.VarString.Builder varBuilder = CoreNLPProtos.TregexResponse.VarString.newBuilder();
      varBuilder.setName(varName);
      varBuilder.setValue(value);
      matchBuilder.addVariables(varBuilder.build());
    }

    return matchBuilder.build();
  }

  /**
   * For a single request, iterate through the Trees it includes,
   * perform each Tsurgeon operation on each tree, and return
   * a result with one tree per input tree.
   */
  public static CoreNLPProtos.TregexResponse processRequest(CoreNLPProtos.TregexRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.TregexResponse.Builder responseBuilder = CoreNLPProtos.TregexResponse.newBuilder();

    List<TregexPattern> queries = parseQueries(request);
    List<Tree> trees = request.getTreesList().stream().map(ProtobufAnnotationSerializer::fromProto).collect(Collectors.toList());

    // The result will have one list per tree
    //   of one list per tregex
    //     with one item per match
    for (Tree tree : trees) {
      CoreNLPProtos.TregexResponse.TreeTregexMatches.Builder treeBuilder = CoreNLPProtos.TregexResponse.TreeTregexMatches.newBuilder();
      for (TregexPattern pattern : queries) {
        CoreNLPProtos.TregexResponse.TreeMatches.Builder tregexBuilder = CoreNLPProtos.TregexResponse.TreeMatches.newBuilder();
        TregexMatcher matcher = pattern.matcher(tree);
        // TODO: options we could add: findNextMatchingNode, findAt
        while (matcher.find()) {
          tregexBuilder.addMatches(convertMatch(tree, matcher));
        }
        treeBuilder.addMatches(tregexBuilder.build());
      }
      responseBuilder.addMatches(treeBuilder.build());
    }
    return responseBuilder.build();
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.TregexRequest request = CoreNLPProtos.TregexRequest.parseFrom(in);
    CoreNLPProtos.TregexResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a semgrex request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessTregexRequest(), args);
  }
}
