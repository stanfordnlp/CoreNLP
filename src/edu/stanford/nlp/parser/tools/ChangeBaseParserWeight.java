package edu.stanford.nlp.parser.tools;

import java.util.List;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.util.Generics;

/**
 * A simple tool to change the default baseParserWeight flag embedded
 * in a LexicalizedParser model.
 * <br>
 * Expected arguments: <br>
 * <code> -input model </code> <br>
 * <code> -output model </code> <br>
 * <code> -baseParserWeight weight </code> <br>
 *
 * TODO: could be generalized to allow updating of many different options
 *
 * @author John Bauer
 */
public class ChangeBaseParserWeight {
  public static void main(String[] args) {
    String input = null;
    String output = null;

    List<String> extraArgs = Generics.newArrayList();

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-input")) {
        input = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        output = args[argIndex + 1];
        argIndex += 2;
      } else {
        extraArgs.add(args[argIndex++]);
      }
    }

    LexicalizedParser parser = LexicalizedParser.loadModel(input, extraArgs);
    parser.saveParserToSerialized(output);
  }
}
