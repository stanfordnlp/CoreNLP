package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.SentenceUtils;
import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.FileSystem;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.RerankerQuery;
import edu.stanford.nlp.parser.lexparser.RerankingParserQuery;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.DeepTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;



public class ParseAndPrintMatrices  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ParseAndPrintMatrices.class);

  public static void outputMatrix(BufferedWriter bout, SimpleMatrix matrix) throws IOException {
    for (int i = 0; i < matrix.getNumElements(); ++i) {
      bout.write("  " + matrix.get(i));
    }
    bout.newLine();
  }

  public static void outputTreeMatrices(BufferedWriter bout, Tree tree, IdentityHashMap<Tree, SimpleMatrix> vectors) throws IOException {
    if (tree.isPreTerminal() || tree.isLeaf()) {
      return;
    }
    for (int i = tree.children().length - 1; i >= 0; i--) {
      outputTreeMatrices(bout, tree.children()[i], vectors);
    }
    outputMatrix(bout, vectors.get(tree));
  }

  public static Tree findRootTree(IdentityHashMap<Tree, SimpleMatrix> vectors) {
    for (Tree tree : vectors.keySet()) {
      if (tree.label().value().equals("ROOT")) {
        return tree;
      }
    }
    throw new RuntimeException("Could not find root");
  }


  public static void main(String[] args) throws IOException {
    String modelPath = null;
    String outputPath = null;
    String inputPath = null;

    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;


    List<String> unusedArgs = Generics.newArrayList();
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        outputPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-input")) {
        inputPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testTreebankPath = treebankDescription.first();
        testTreebankFilter = treebankDescription.second();
      } else {
        unusedArgs.add(args[argIndex++]);
      }
    }

    String[] newArgs = unusedArgs.toArray(new String[unusedArgs.size()]);
    LexicalizedParser parser = LexicalizedParser.loadModel(modelPath, newArgs);
    DVModel model = DVParser.getModelFromLexicalizedParser(parser);

    File outputFile = new File(outputPath);
    FileSystem.checkNotExistsOrFail(outputFile);
    FileSystem.mkdirOrFail(outputFile);

    int count = 0;
    if (inputPath != null) {
      Reader input = new BufferedReader(new FileReader(inputPath));
      DocumentPreprocessor processor = new DocumentPreprocessor(input);
      for (List<HasWord> sentence : processor) {
        count++; // index from 1
        ParserQuery pq = parser.parserQuery();
        if (!(pq instanceof RerankingParserQuery)) {
          throw new IllegalArgumentException("Expected a RerankingParserQuery");
        }
        RerankingParserQuery rpq = (RerankingParserQuery) pq;
        if (!rpq.parse(sentence)) {
          throw new RuntimeException("Unparsable sentence: " + sentence);
        }
        RerankerQuery reranker = rpq.rerankerQuery();
        if (!(reranker instanceof DVModelReranker.Query)) {
          throw new IllegalArgumentException("Expected a DVModelReranker");
        }
        DeepTree deepTree = ((DVModelReranker.Query) reranker).getDeepTrees().get(0);
        IdentityHashMap<Tree, SimpleMatrix> vectors = deepTree.getVectors();

        for (Map.Entry<Tree, SimpleMatrix> entry : vectors.entrySet()) {
          log.info(entry.getKey() + "   " +  entry.getValue());
        }

        FileWriter fout = new FileWriter(outputPath + File.separator + "sentence" + count + ".txt");
        BufferedWriter bout = new BufferedWriter(fout);

        bout.write(SentenceUtils.listToString(sentence));
        bout.newLine();
        bout.write(deepTree.getTree().toString());
        bout.newLine();

        for (HasWord word : sentence) {
          outputMatrix(bout, model.getWordVector(word.word()));
        }

        Tree rootTree = findRootTree(vectors);
        outputTreeMatrices(bout, rootTree, vectors);

        bout.flush();
        fout.close();
      }
    }
  }  
}

