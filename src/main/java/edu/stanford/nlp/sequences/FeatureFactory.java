package edu.stanford.nlp.sequences;

import java.util.*;
import java.util.function.Consumer;
import java.io.Serializable;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.PaddedList;


/**
 * This is the abstract class that all feature factories must
 * subclass.  It also defines most of the basic {@link Clique}s
 * that you would want to make features over.  It contains a
 * convenient method, getCliques(maxLeft, maxRight) which will give
 * you all the cliques within the specified limits.
 *
 * @param <IN> The type of the items in the PaddedList from which features
 *     are extracted
 *
 * @author Jenny Finkel
 */
public abstract class FeatureFactory<IN> implements Serializable {

  private static final long serialVersionUID = 7249250071983091694L;

  protected SeqClassifierFlags flags;

  public FeatureFactory() {}

  public void init (SeqClassifierFlags flags) {
    this.flags = flags;
  }

  public static final Clique cliqueC = Clique.valueOf(new int[] {0});
  public static final Clique cliqueCpC = Clique.valueOf(new int[] {-1, 0});
  public static final Clique cliqueCp2C = Clique.valueOf(new int[] {-2, 0});
  public static final Clique cliqueCp3C = Clique.valueOf(new int[] {-3, 0});
  public static final Clique cliqueCp4C = Clique.valueOf(new int[] {-4, 0});
  public static final Clique cliqueCp5C = Clique.valueOf(new int[] {-5, 0});
  public static final Clique cliqueCpCp2C = Clique.valueOf(new int[] {-2, -1, 0});
  public static final Clique cliqueCpCp2Cp3C = Clique.valueOf(new int[] {-3, -2, -1, 0});
  public static final Clique cliqueCpCp2Cp3Cp4C = Clique.valueOf(new int[] {-4, -3, -2, -1, 0});
  public static final Clique cliqueCpCp2Cp3Cp4Cp5C = Clique.valueOf(new int[] {-5, -4, -3, -2, -1, 0});
  public static final Clique cliqueCnC = Clique.valueOf(new int[] {0, 1});
  public static final Clique cliqueCpCnC = Clique.valueOf(new int[] {-1, 0, 1});

  // Note: there is code that relies on no clique like {+1,+2}.
  // We use the -leftmost value to group features.
  public static final List<Clique> knownCliques = Arrays.asList(cliqueC, cliqueCpC, cliqueCp2C, cliqueCp3C, cliqueCp4C, cliqueCp5C, cliqueCpCp2C, cliqueCpCp2Cp3C, cliqueCpCp2Cp3Cp4C, cliqueCpCp2Cp3Cp4Cp5C, cliqueCnC, cliqueCpCnC);

  public List<Clique> getCliques() {
    return getCliques(flags.maxLeft, flags.maxRight);
  }

  /**
   * Process cliques requiring exactly "left" lookbehind and exactly "right" lookahead.
   *
   * @param left Left window size
   * @param right Right window size
   * @param consumer Clique consumer
   */
  public static void eachClique(int left, int right, Consumer<Clique> consumer) {
    for (Clique c : knownCliques) {
      if (-c.maxLeft() == left && c.maxRight() == right) {
        consumer.accept(c);
      }
    }
  }

  public static List<Clique> getCliques(int maxLeft, int maxRight) {
    List<Clique> cliques = new ArrayList<>();
    for (Clique c : knownCliques) {
      if (-c.maxLeft() <= maxLeft && c.maxRight() <= maxRight) {
        cliques.add(c);
      }
    }
    return cliques;
  }

  /**
   * This method returns a {@link Collection} of the features
   * calculated for the word at the specified position in info (the list of
   * words) for the specified {@link Clique}.
   * It should return the actual String features, <b>NOT</b> wrapped in any
   * other object, as the wrapping
   * will be done automatically.
   * Because it takes a {@link PaddedList} you don't
   * need to worry about indices which are outside of the list.
   *
   * @param info A PaddedList of the feature-value pairs
   * @param position The current position to extract features at
   * @param clique The particular clique for which to extract features. It
   *     should be a member of the knownCliques list.
   * @return A {@link Collection} of the features
   *     calculated for the word at the specified position in info.
   */
  public abstract Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique);


  /** Makes more complete feature names out of partial feature names, by
   *  adding a suffix to the String feature name, adding results to an
   *  accumulator
   *
   * @param accumulator The output features are added here
   * @param addend The base set of features
   * @param suffix The suffix added to each feature in the addend set
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  protected void addAllInterningAndSuffixing(Collection<String> accumulator, Collection<String> addend, String suffix) {
    boolean nonNullSuffix = suffix != null && ! suffix.isEmpty();
    if (nonNullSuffix) {
      suffix = '|' + suffix;
    }
    // boolean intern2 = flags.intern2;
    for (String feat : addend) {
      if (nonNullSuffix) {
        feat = feat.concat(suffix);
      }
      // if (intern2) {
      //   feat = feat.intern();
      // }
      accumulator.add(feat);
    }
  }

  /**
   * Convenience methods for subclasses which use CoreLabel.  Gets the
   * word after applying any wordFunction present in the
   * SeqClassifierFlags.
   *
   * @param label A CoreLabel
   * @return The TextAnnotation of the label, perhaps after passing it through
   *     a function (flags.wordFunction)
   */
  protected String getWord(CoreLabel label) {
    String word = label.getString(CoreAnnotations.TextAnnotation.class);
    if (flags.wordFunction != null) {
      word = flags.wordFunction.apply(word);
    }
    return word;
  }

}
