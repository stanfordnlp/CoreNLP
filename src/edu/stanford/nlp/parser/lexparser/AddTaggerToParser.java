package edu.stanford.nlp.parser.lexparser;

import java.io.IOException;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * A simple tool to add a tagger to the parser for reranking purposes.
 *
 * @author John Bauer
 */
public class AddTaggerToParser {
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    String taggerFile = null;
    String inputFile = null;
    String outputFile = null;

    double weight = 1.0;
    
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-tagger")) {
        taggerFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-input")) {
        inputFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        outputFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-weight")) {
        weight = Double.valueOf(args[argIndex + 1]);
        argIndex += 2;
      } else {
        throw new IllegalArgumentException("Unknown argument: " + args[argIndex]);
      }
    }

    LexicalizedParser parser = LexicalizedParser.loadModel(inputFile);
    MaxentTagger tagger = new MaxentTagger(taggerFile);
    parser.reranker = new TaggerReranker(tagger, parser.getOp());
    parser.saveParserToSerialized(outputFile);
  }
}
