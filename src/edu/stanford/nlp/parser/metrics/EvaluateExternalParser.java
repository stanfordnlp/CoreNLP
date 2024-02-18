/**
 * Evaluates an external parser one tree at a time.
 */

package edu.stanford.nlp.parser.metrics;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ProcessProtobufRequest;
import edu.stanford.nlp.util.ScoredObject;


public class EvaluateExternalParser extends ProcessProtobufRequest {
  final Options op;

  public EvaluateExternalParser(String ... args) {
    // likely the EnglishTreebankParserParams will be sufficient for most use cases
    // perhaps need to add a flag to set a different TLPP
    op = new Options();
    // this can be turned off if the user really wants with -noQuietEvaluation
    op.setOptions("-quietEvaluation");
    op.setOptions(args);
  }

  /**
   * Extract the gold trees from a list of requests
   */
  public static List<Tree> getGoldTrees(CoreNLPProtos.EvaluateParserRequest parses) {
    List<Tree> trees = new ArrayList<>();
    for (CoreNLPProtos.EvaluateParserRequest.ParseResult parse : parses.getTreebankList()) {
      CoreNLPProtos.FlattenedParseTree gold = parse.getGold();
      trees.add(ProtobufAnnotationSerializer.fromProto(gold));
    }
    return trees;
  }

  /**
   * Extract the predicted trees from a list of requests
   */
  public static List<List<Tree>> getResults(CoreNLPProtos.EvaluateParserRequest parses) {
    List<List<Tree>> results = new ArrayList<>();
    for (CoreNLPProtos.EvaluateParserRequest.ParseResult parse : parses.getTreebankList()) {
      List<Tree> trees = parse.getPredictedList().stream().map(ProtobufAnnotationSerializer::fromProto).collect(Collectors.toList());
      results.add(trees);
    }
    return results;
  }

  public List<Pair<ParserQuery, Tree>> convertDataset(List<Tree> goldTrees, List<List<Tree>> results) {
    List<Pair<ParserQuery, Tree>> dataset = new ArrayList<>();

    if (goldTrees.size() != results.size()) {
      throw new AssertionError("The lists should always be of the same length at this point");
    }

    for (int i = 0; i < goldTrees.size(); ++i) {
      Tree gold = goldTrees.get(i);
      List<CoreLabel> sentence = SentenceUtils.toCoreLabelList(gold.yieldWords());

      List<ScoredObject<Tree>> scoredResult = new ArrayList<>();
      for (Tree tree : results.get(i)) {
        double score = tree.score();
        scoredResult.add(new ScoredObject<>(tree, score));
      }

      ExternalParserQuery pq = new ExternalParserQuery(sentence, scoredResult);
      dataset.add(new Pair<>(pq, gold));
    }
    return dataset;
  }


  public CoreNLPProtos.EvaluateParserResponse buildResponse(double f1, Double kbestF1, List<Double> f1History)  {
    CoreNLPProtos.EvaluateParserResponse.Builder responseBuilder = CoreNLPProtos.EvaluateParserResponse.newBuilder();
    responseBuilder.setF1(f1);
    if (kbestF1 != null) {
      responseBuilder.setKbestF1(kbestF1);
    }
    if (f1History != null) {
      for (Double treeF1 : f1History) {
        responseBuilder.addTreeF1(treeF1);
      }
    }
    CoreNLPProtos.EvaluateParserResponse response = responseBuilder.build();
    return response;
  }

  /**
   * Puts the list of gold trees and a list of list of results into EvaluateTreebank
   */
  public CoreNLPProtos.EvaluateParserResponse scoreDataset(List<Tree> goldTrees, List<List<Tree>> results) {
    List<Pair<ParserQuery, Tree>> treebank = convertDataset(goldTrees, results);

    EvaluateTreebank evaluator = new EvaluateTreebank(op, null, null, null, null, null);
    double f1 = evaluator.testOnTreebank(treebank);
    Double kbestF1 = null;
    if (evaluator.hasPCFGTopKF1()) {
      kbestF1 = evaluator.getPCFGTopKF1();
    }
    List<Double> f1History = evaluator.getF1History();
    return buildResponse(f1, kbestF1, f1History);
  }

  public CoreNLPProtos.EvaluateParserResponse processRequest(CoreNLPProtos.EvaluateParserRequest parses) throws IOException {
    List<Tree> goldTrees = getGoldTrees(parses);
    List<List<Tree>> results = getResults(parses);
    return scoreDataset(goldTrees, results);
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.EvaluateParserRequest request = CoreNLPProtos.EvaluateParserRequest.parseFrom(in);
    CoreNLPProtos.EvaluateParserResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a parser evaluation request.
   */
  public static void main(String[] args) throws IOException {
    EvaluateExternalParser processor = new EvaluateExternalParser(ProcessProtobufRequest.leftoverArgs(args));
    ProcessProtobufRequest.process(processor, args);
  }
}
