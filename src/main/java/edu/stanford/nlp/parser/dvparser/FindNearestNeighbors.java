package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.FileFilter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.RerankingParserQuery;
import edu.stanford.nlp.trees.DeepTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;

/**
 * A tool which takes all of the n-grams of a certain length and looks
 * for other n-grams which are close using distance between word vectors.
 * Useful for coming up with interesting analysis of how the word vectors
 * help the parsing task.
 *
 * @author John Bauer
 */
public class FindNearestNeighbors  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FindNearestNeighbors.class);
  // TODO: parameter?
  static final int numNeighbors = 5;
  static final int maxLength = 8;

  public static class ParseRecord {
    final List<Word> sentence;
    final Tree goldTree;
    final Tree parse;
    final SimpleMatrix rootVector;
    final IdentityHashMap<Tree, SimpleMatrix> nodeVectors;

    public ParseRecord(List<Word> sentence, Tree goldTree, Tree parse, SimpleMatrix rootVector, IdentityHashMap<Tree, SimpleMatrix> nodeVectors) {
      this.sentence = sentence;
      this.goldTree = goldTree;
      this.parse = parse;
      this.rootVector = rootVector;
      this.nodeVectors = nodeVectors;
    }
  }

  public static void main(String[] args) throws Exception {
    String modelPath = null;
    String outputPath = null;

    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;

    List<String> unusedArgs = new ArrayList<>();

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testTreebankPath = treebankDescription.first();
        testTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        outputPath = args[argIndex + 1];
        argIndex += 2;
      } else {
        unusedArgs.add(args[argIndex++]);
      }
    }

    if (modelPath == null) {
      throw new IllegalArgumentException("Need to specify -model");
    }
    if (testTreebankPath == null) {
      throw new IllegalArgumentException("Need to specify -testTreebank");
    }
    if (outputPath == null) {
      throw new IllegalArgumentException("Need to specify -output");
    }

    String[] newArgs = unusedArgs.toArray(new String[unusedArgs.size()]);

    LexicalizedParser lexparser = LexicalizedParser.loadModel(modelPath, newArgs);

    Treebank testTreebank = null;
    if (testTreebankPath != null) {
      log.info("Reading in trees from " + testTreebankPath);
      if (testTreebankFilter != null) {
        log.info("Filtering on " + testTreebankFilter);
      }
      testTreebank = lexparser.getOp().tlpParams.memoryTreebank();;
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
      log.info("Read in " + testTreebank.size() + " trees for testing");
    }

    FileWriter out = new FileWriter(outputPath);
    BufferedWriter bout = new BufferedWriter(out);

    log.info("Parsing " + testTreebank.size() + " trees");
    int count = 0;
    List<ParseRecord> records = Generics.newArrayList();
    for (Tree goldTree : testTreebank) {
      List<Word> tokens = goldTree.yieldWords();
      ParserQuery parserQuery = lexparser.parserQuery();
      if (!parserQuery.parse(tokens)) {
        throw new AssertionError("Could not parse: " + tokens);
      }
      if (!(parserQuery instanceof RerankingParserQuery)) {
        throw new IllegalArgumentException("Expected a LexicalizedParser with a Reranker attached");
      }
      RerankingParserQuery rpq = (RerankingParserQuery) parserQuery;
      if (!(rpq.rerankerQuery() instanceof DVModelReranker.Query)) {
        throw new IllegalArgumentException("Expected a LexicalizedParser with a DVModel attached");
      }
      DeepTree tree = ((DVModelReranker.Query) rpq.rerankerQuery()).getDeepTrees().get(0);

      SimpleMatrix rootVector = null;
      for (Map.Entry<Tree, SimpleMatrix> entry : tree.getVectors().entrySet()) {
        if (entry.getKey().label().value().equals("ROOT")) {
          rootVector = entry.getValue();
          break;
        }
      }
      if (rootVector == null) {
        throw new AssertionError("Could not find root nodevector");
      }
      out.write(tokens + "\n");
      out.write(tree.getTree() + "\n");
      for (int i = 0; i < rootVector.getNumElements(); ++i) {
        out.write("  " + rootVector.get(i));
      }
      out.write("\n\n\n");
      count++;
      if (count % 10 == 0) {
        log.info("  " + count);
      }

      records.add(new ParseRecord(tokens, goldTree, tree.getTree(), rootVector, tree.getVectors()));
    }
    log.info("  done parsing");

    List<Pair<Tree, SimpleMatrix>> subtrees = Generics.newArrayList();
    for (ParseRecord record : records) {
      for (Map.Entry<Tree, SimpleMatrix> entry : record.nodeVectors.entrySet()) {
        if (entry.getKey().getLeaves().size() <= maxLength) {
          subtrees.add(Pair.makePair(entry.getKey(), entry.getValue()));
        }
      }
    }

    log.info("There are " + subtrees.size() + " subtrees in the set of trees");

    PriorityQueue<ScoredObject<Pair<Tree, Tree>>> bestmatches = new PriorityQueue<>(101, ScoredComparator.DESCENDING_COMPARATOR);

    for (int i = 0; i < subtrees.size(); ++i) {
      log.info(subtrees.get(i).first().yieldWords());
      log.info(subtrees.get(i).first());

      for (int j = 0; j < subtrees.size(); ++j) {
        if (i == j) {
          continue;
        }

        // TODO: look at basic category?
        double normF = subtrees.get(i).second().minus(subtrees.get(j).second()).normF();

        bestmatches.add(new ScoredObject<>(Pair.makePair(subtrees.get(i).first(), subtrees.get(j).first()), normF));
        if (bestmatches.size() > 100) {
          bestmatches.poll();
        }
      }
      List<ScoredObject<Pair<Tree, Tree>>> ordered = Generics.newArrayList();
      while (bestmatches.size() > 0) {
        ordered.add(bestmatches.poll());
      }
      Collections.reverse(ordered);
      for (ScoredObject<Pair<Tree, Tree>> pair : ordered) {
        log.info(" MATCHED " + pair.object().second.yieldWords() + " ... " + pair.object().second() + " with a score of " + pair.score());
      }
      log.info();
      log.info();
      bestmatches.clear();
    }

    /*
    for (int i = 0; i < records.size(); ++i) {
      if (i % 10 == 0) {
        log.info("  " + i);
      }
      List<ScoredObject<ParseRecord>> scored = Generics.newArrayList();
      for (int j = 0; j < records.size(); ++j) {
        if (i == j) continue;

        double score = 0.0;
        int matches = 0;
        for (Map.Entry<Tree, SimpleMatrix> first : records.get(i).nodeVectors.entrySet()) {
          for (Map.Entry<Tree, SimpleMatrix> second : records.get(j).nodeVectors.entrySet()) {
            String firstBasic = dvparser.dvModel.basicCategory(first.getKey().label().value());
            String secondBasic = dvparser.dvModel.basicCategory(second.getKey().label().value());
            if (firstBasic.equals(secondBasic)) {
              ++matches;
              double normF = first.getValue().minus(second.getValue()).normF();
              score += normF * normF;
            }
          }
        }
        if (matches == 0) {
          score = Double.POSITIVE_INFINITY;
        } else {
          score = score / matches;
        }
        //double score = records.get(i).vector.minus(records.get(j).vector).normF();
        scored.add(new ScoredObject<ParseRecord>(records.get(j), score));
      }
      Collections.sort(scored, ScoredComparator.ASCENDING_COMPARATOR);

      out.write(records.get(i).sentence.toString() + "\n");
      for (int j = 0; j < numNeighbors; ++j) {
        out.write("   " + scored.get(j).score() + ": " + scored.get(j).object().sentence + "\n");
      }
      out.write("\n\n");
    }
    log.info();
    */

    bout.flush();
    out.flush();
    out.close();
  }
}
