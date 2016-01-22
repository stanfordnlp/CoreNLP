package edu.stanford.nlp.parser.dvparser;

import java.io.PrintWriter;
import java.util.List;
import java.util.TreeSet;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.trees.Tree;

/**
 * Prints out words which are unknown to the DVParser.
 * <br>
 * This does not have to be specific to the DVParser.  We could easily
 * add an interface which lets it call something to ask if the word is
 * known or not, and if not, keeps track of those words.
 *
 * @author John Bauer
 */
public class UnknownWordPrinter implements Eval {

  final DVModel model;
  final SimpleMatrix unk;

  final TreeSet<String> unkWords = new TreeSet<>();

  public UnknownWordPrinter(DVModel model) {
    this.model = model;
    this.unk = model.getUnknownWordVector();
  }

  @Override
  public void evaluate(Tree guess, Tree gold) {
    evaluate(guess, gold, new PrintWriter(System.out, true));
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    evaluate(guess, gold, pw, 1.0);
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw, double weight) {
    List<Label> words = guess.yield();
    int pos = 0;
    for (Label word : words) {
      ++pos;
      SimpleMatrix wv = model.getWordVector(word.value());
      // would be faster but more implementation-specific if we
      // removed wv.equals
      if (wv == unk || wv.equals(unk)) {
        pw.printf("  Unknown word in position %d: %s%n", pos, word.value());
        unkWords.add(word.value());
      }
    }
  }

  @Override
  public void display(boolean verbose) {
    display(verbose, new PrintWriter(System.out, true));
  }

  @Override
  public void display(boolean verbose, PrintWriter pw) {
    if (unkWords.isEmpty()) {
      pw.printf("UnknownWordPrinter: all words known by DVModel%n");
    } else {
      pw.printf("UnknownWordPrinter: the following words are unknown%n");
      for (String word : unkWords) {
        pw.printf("  %s%n", word);
      }
    }
  }

}
