package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.Reranker;
import edu.stanford.nlp.parser.metrics.EvaluateTreebank;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Pair;

/**
 * This class combines multiple DVParsers into one by adding their
 * scores.  If the models are somewhat different but have similar
 * accuracy, this gives a slight accuracy increase.
 *
 * @author John Bauer
 */
public class CombineDVModels  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CombineDVModels.class);

  public static void main(String[] args)
    throws IOException, ClassNotFoundException
  {
    String modelPath = null;

    List<String> baseModelPaths = null;

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
      } else if (args[argIndex].equalsIgnoreCase("-baseModels")) {
        argIndex++;
        baseModelPaths = new ArrayList<>();
        while (argIndex < args.length && args[argIndex].charAt(0) != '-') {
          baseModelPaths.add(args[argIndex++]);
        }
        if (baseModelPaths.size() == 0) {
          throw new IllegalArgumentException("Found an argument -baseModels with no actual models named");
        }
      } else {
        unusedArgs.add(args[argIndex++]);
      }
    }

    String[] newArgs = unusedArgs.toArray(new String[unusedArgs.size()]);
    LexicalizedParser underlyingParser = null;
    Options options = null;
    LexicalizedParser combinedParser = null;
    if (baseModelPaths != null) {
      List<DVModel> dvparsers = new ArrayList<>();
      for (String baseModelPath : baseModelPaths) {
        log.info("Loading serialized DVParser from " + baseModelPath);
        LexicalizedParser dvparser = LexicalizedParser.loadModel(baseModelPath);
        Reranker reranker = dvparser.reranker;
        if (!(reranker instanceof DVModelReranker)) {
          throw new IllegalArgumentException("Expected parsers with DVModel embedded");
        }
        dvparsers.add(((DVModelReranker) reranker).getModel());
        if (underlyingParser == null) {
          underlyingParser = dvparser;
          options = underlyingParser.getOp();
          // TODO: other parser's options?
          options.setOptions(newArgs);
        }
        log.info("... done");
      }
      combinedParser = LexicalizedParser.copyLexicalizedParser(underlyingParser);
      CombinedDVModelReranker reranker = new CombinedDVModelReranker(options, dvparsers);
      combinedParser.reranker = reranker;
      combinedParser.saveParserToSerialized(modelPath);
    } else {
      throw new IllegalArgumentException("Need to specify -model to load an already prepared CombinedParser");
    }

    Treebank testTreebank = null;
    if (testTreebankPath != null) {
      log.info("Reading in trees from " + testTreebankPath);
      if (testTreebankFilter != null) {
        log.info("Filtering on " + testTreebankFilter);
      }
      testTreebank = combinedParser.getOp().tlpParams.memoryTreebank();;
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
      log.info("Read in " + testTreebank.size() + " trees for testing");

      EvaluateTreebank evaluator = new EvaluateTreebank(combinedParser.getOp(), null, combinedParser);
      evaluator.testOnTreebank(testTreebank);
    }
  }
}
