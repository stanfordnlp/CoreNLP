package edu.stanford.nlp.ie.util;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factor out some commonly used code (e.g., make a tree from a CoNLL spec)
 *
 * @author Gabor Angeli
 */
public class IETestUtils {

  /**
   * Create a dummy word, just with a given word at a given index.
   * Mostly useful for making semantic graphs.
   */
  public static CoreLabel mkWord(String gloss, int index) {
    CoreLabel w = new CoreLabel();
    w.setWord(gloss);
    w.setValue(gloss);
    if (index >= 0) {
      w.setIndex(index);
    }
    return w;
  }

  /**
   * Parse a CoNLL formatted string into a SemanticGraph.
   * This is useful for tests so that you don't need to load the model (and are robust to
   * model changes).
   *
   * @param conll The CoNLL format for the tree.
   * @return A semantic graph, as well as the flat tokens of the sentence.
   */
  public static Pair<SemanticGraph,List<CoreLabel>> parseCoNLL(String conll) {
    List<CoreLabel> sentence = new ArrayList<>();
    SemanticGraph tree = new SemanticGraph();
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      int index = Integer.parseInt(fields[0]);
      String word = fields[1];
      CoreLabel label = mkWord(word, index);
      sentence.add(label);
      if (fields[2].equals("0")) {
        tree.addRoot(new IndexedWord(label));
      } else {
        tree.addVertex(new IndexedWord(label));
      }
      if (fields.length > 4) {
        label.setTag(fields[4]);
      }
      if (fields.length > 5) {
        label.setNER(fields[5]);
      }
      if (fields.length > 6) {
        label.setLemma(fields[6]);
      }
    }
    int i = 0;
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      int parent = Integer.parseInt(fields[2]);
      String reln = fields[3];
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            new GrammaticalRelation(Language.UniversalEnglish, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }

    return Pair.makePair(tree, sentence);
  }


  /**
   * Create a sentence (list of CoreLabels) from a given text.
   * The resulting labels will have a word, lemma (guessed poorly), and
   * a part of speech if one is specified on the input.
   *
   * @param text The text to parse.
   *
   * @return A sentence corresponding to the text.
   */
  public static List<CoreLabel> parseSentence(String text) {
    return Arrays.asList(text.split("\\s+")).stream().map(w -> {
      CoreLabel token = new CoreLabel();
      if (w.contains("/")) {
        String[] fields = w.split("/");
        token.setWord(fields[0]);
        token.setTag(fields[1]);
      } else {
        token.setWord(w);
      }
      token.setValue(token.word());
      token.setLemma(token.word());
      if (token.word().equals("is") || token.word().equals("was") || token.word().equals("are")) {
        token.setLemma("be");
      }
      if (token.word().equals("has")) {
        token.setLemma("have");
      }
      if (token.word().equals("did") | token.word().equals("will") || token.word().equals("does")) {
        token.setLemma("do");
      }
      if (token.word().endsWith("ed")) {
        token.setLemma(token.word().substring(0, token.word().length() - 1));
      }
      if (token.word().endsWith("ing")) {
        token.setLemma(token.word().substring(0, token.word().length() - 3));
      }
      return token;
    }).collect(Collectors.toList());
  }
}
