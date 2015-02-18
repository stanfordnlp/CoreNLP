package edu.stanford.nlp.parser.tools;

import java.util.Collections;
import java.util.List;
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
    LexicalizedParser parser = LexicalizedParser.loadModel(args[0]);
    Set<String> tags = Generics.newHashSet();
    for (String tag : parser.tagIndex) {
      String[] pieces = tag.split("\\^");
      pieces[0] = pieces[0].replaceAll("-[A-Z]+$", "");
      tags.add(pieces[0]);
    }
    List<String> sortedTags = Generics.newArrayList(tags);
    Collections.sort(sortedTags);
    System.err.println(sortedTags);
  }
}
