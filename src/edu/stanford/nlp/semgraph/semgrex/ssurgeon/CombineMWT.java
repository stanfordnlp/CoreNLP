package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.util.StringUtils;


/**
 * Combines two or more words into one MWT
 *<br>
 * For the most part, the nodes themselves are unchanged, but the
 * nodes now have the CoreAnnotations representing MWTness added
 *<br>
 * This is a convenience operation which exists because the basic
 * version of it using EditNode is a bit of a pain
 *
 * @author John Bauer
 */
public class CombineMWT extends SsurgeonEdit {
  public static final String LABEL = "combineMWT";
  final List<String> names;
  final String word;

  public CombineMWT(List<String> names, String word) {
    this.names = new ArrayList<>(names);
    this.word = word;
    if (names.size() < 2) {
      throw new SsurgeonParseException("Cannot combine MWT out of only " + names.size() + " words");
    }
  }

  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    for (String name : names) {
      buf.write("\t");
      buf.write(name);
    }
    if (word != null && !word.equals("")) {
      buf.write("\t");
      buf.write(word);
    }
    return buf.toString();
  }

  /**
   * Combine the nodes named in the expression, as long as they are all in a row
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    List<IndexedWord> nodes = new ArrayList<>();
    for (String name : names) {
      IndexedWord node = sm.getNode(name);
      if (node == null) {
        return false;
      }
      nodes.add(node);
    }
    Collections.sort(nodes);
    for (int i = 0; i < nodes.size() - 1; ++i) {
      // if the nodes aren't consecutive, then punt
      if (nodes.get(i).index() != nodes.get(i+1).index() - 1) {
        return false;
      }
    }

    String newWord = this.word;
    if (newWord == null || newWord.equals("")) {
      StringBuilder newWordBuilder = new StringBuilder();
      for (IndexedWord node : nodes) {
        newWordBuilder.append(node.word());
      }
      newWord = newWordBuilder.toString();
    }

    boolean changed = false;
    int startIndex = nodes.get(0).index();
    int endIndex = nodes.get(nodes.size() - 1).index();
    for (IndexedWord vertex : sg.vertexSet()) {
      if (vertex.index() >= startIndex && vertex.index() <= endIndex) {
        if (!vertex.containsKey(CoreAnnotations.IsMultiWordTokenAnnotation.class) ||
            !vertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class)) {
          changed = true;
          vertex.set(CoreAnnotations.IsMultiWordTokenAnnotation.class, true);
        }
        if (!vertex.containsKey(CoreAnnotations.MWTTokenTextAnnotation.class) ||
            !vertex.get(CoreAnnotations.MWTTokenTextAnnotation.class).equals(newWord)) {
          changed = true;
          vertex.set(CoreAnnotations.MWTTokenTextAnnotation.class, newWord);
        }
        if (vertex.index() == startIndex) {
          if (!vertex.containsKey(CoreAnnotations.IsFirstWordOfMWTAnnotation.class) ||
              !vertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class)) {
            changed = true;
            vertex.set(CoreAnnotations.IsFirstWordOfMWTAnnotation.class, true);
          }
        } else if (!vertex.containsKey(CoreAnnotations.IsFirstWordOfMWTAnnotation.class) ||
                   vertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class)) {
          changed = true;
          vertex.set(CoreAnnotations.IsFirstWordOfMWTAnnotation.class, false);
        }
      }
    }

    return false;
  }
}
