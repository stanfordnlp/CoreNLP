package edu.stanford.nlp.parser.tools;

import java.util.Set;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.util.Generics;


/**
 * Loads a LexicalizedParser and tries to get its tag list.
 *
 * @author John Bauer
 */
public class PrintTagList {
  public static void main(String[] args) {
    String parserFile = null;
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-model")) {
        parserFile = args[argIndex + 1];
        argIndex += 2;
      } else {
        String error = "Unknown argument " + args[argIndex];
        System.err.println(error);
        throw new RuntimeException(error);
      }
    }
    if (parserFile == null) {
      System.err.println("Must specify a model file with -model");
      System.exit(2);
    }

    LexicalizedParser parser = LexicalizedParser.loadModel(parserFile);

    Set<String> tags = Generics.newTreeSet();
    for (String tag : parser.tagIndex) {
      tags.add(parser.treebankLanguagePack().basicCategory(tag));
    }
    System.out.println("Basic tags: " + tags.size());
    for (String tag : tags) {
      System.out.print("  " + tag);
    }
    System.out.println();
    System.out.println("All tags size: " + parser.tagIndex.size());

    Set<String> states = Generics.newTreeSet();
    for (String state : parser.stateIndex) {
      states.add(parser.treebankLanguagePack().basicCategory(state));
    }
    System.out.println("Basic states: " + states.size());
    for (String tag : states) {
      System.out.print("  " + tag);
    }
    System.out.println();
    System.out.println("All states size: " + parser.stateIndex.size());

    System.out.println("Unary grammar size: " + parser.ug.numRules());
    System.out.println("Binary grammar size: " + parser.bg.numRules());
  }
}
