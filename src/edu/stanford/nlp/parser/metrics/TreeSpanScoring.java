package edu.stanford.nlp.parser.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.LabeledConstituent;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

/**
 * Provides a method for deciding how similar two trees are.
 *
 * @author John Bauer
 */
public class TreeSpanScoring {
  private TreeSpanScoring() {} // static only

  /**
   * Counts how many spans are present in goldTree, including
   * preterminals, but not present in guessTree, along with how many
   * spans are present in guessTree and not goldTree.  Each one counts
   * as an error, meaning that something like a mislabeled span or
   * preterminal counts as two errors.
   * <br>
   * Span labels are compared using the basicCategory() function
   * from the passed in TreebankLanguagePack.
   */
  public static int countSpanErrors(TreebankLanguagePack tlp, Tree goldTree, Tree guessTree) {
    Set<Constituent> goldConstituents = goldTree.constituents(LabeledConstituent.factory());
    Set<Constituent> guessConstituents = guessTree.constituents(LabeledConstituent.factory());

    Set<Constituent> simpleGoldConstituents = simplifyConstituents(tlp, goldConstituents);
    Set<Constituent> simpleGuessConstituents = simplifyConstituents(tlp, guessConstituents);

    //System.out.println(simpleGoldConstituents);
    //System.out.println(simpleGuessConstituents);

    int errors = 0;
    for (Constituent gold : simpleGoldConstituents) {
      if (!simpleGuessConstituents.contains(gold)) {
        ++errors;
      }
    }
    for (Constituent guess : simpleGuessConstituents) {
      if (!simpleGoldConstituents.contains(guess)) {
        ++errors;
      }
    }

    // The spans returned by constituents() doesn't include the
    // preterminals, so we need to count those ourselves now
    List<TaggedWord> goldWords = goldTree.taggedYield();
    List<TaggedWord> guessWords = guessTree.taggedYield();
    int len = Math.min(goldWords.size(), guessWords.size());
    for (int i = 0; i < len; ++i) {
      String goldTag = tlp.basicCategory(goldWords.get(i).tag());
      String guessTag = tlp.basicCategory(guessWords.get(i).tag());
      if (!goldTag.equals(guessTag)) {
        // we count one error for each span that is present in the
        // gold and not in the guess, and one error for each span that
        // is present in the guess and not the gold, so this counts as
        // two errors
        errors += 2;
      }
    }

    return errors;
  }

  public static Set<Constituent> simplifyConstituents(TreebankLanguagePack tlp, Set<Constituent> constituents) {
    Set<Constituent> newConstituents = new HashSet<>();
    for (Constituent con : constituents) {
      if (!(con instanceof LabeledConstituent)) {
        throw new AssertionError("Unexpected constituent type " + con.getClass());
      }
      LabeledConstituent labeled = (LabeledConstituent) con;
      newConstituents.add(new LabeledConstituent(labeled.start(), labeled.end(), tlp.basicCategory(labeled.value())));
    }
    return newConstituents;
  }
}
