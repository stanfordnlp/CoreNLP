package edu.stanford.nlp.parser.tools;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;

/**
 * A simple tool to change the default baseParserWeight flag embedded
 * in a LexicalizedParser model.
 * <br>
 * Expected arguments: <br>
 * <code> -input model </code> <br>
 * <code> -output model </code> <br>
 * <code> -baseParserWeight weight </code> <br>
 *
 * @author John Bauer
 */
public class ChangeBaseParserWeight {
  public static void main(String[] args) {
    String input = null;
    String output = null;
    double weight = -1.0;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-input")) {
        input = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        output = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-baseParserWeight")) {
        weight = Double.valueOf(args[argIndex + 1]);
        argIndex += 2;
      } else {
        throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
      }
    }

    if (weight < 0) {
      String error = "Must specify weight >= 0 with -baseParserWeight";
      System.err.println(error);
      throw new IllegalArgumentException(error);
    }
    LexicalizedParser parser = LexicalizedParser.loadModel(input);
    parser.getOp().baseParserWeight = weight;
    parser.saveParserToSerialized(output);
  }
}
