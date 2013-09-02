package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.util.Weighted;

/**
 * A <code>WeightedFollowedTaggedWord</code> object contains a word and its
 * tag, but it also records what text follows the token.  This can be
 * used to reconstruct the original source text.  It also stores a
 * weight for the token, so that one can do token feature weighting.
 * The <code>value()</code> of a TaggedWord is the Word.  The tag
 * and other attributes are secondary.
 *
 * @author Christopher Manning
 */
public class WeightedFollowedTaggedWord extends TaggedWord implements HasFollow, Weighted {

  /**
   * 
   */
  private static final long serialVersionUID = -7600197568995481394L;
  private String follow;
  private double weight;

  public WeightedFollowedTaggedWord() {
    super();
  }

  public WeightedFollowedTaggedWord(String word) {
    super(word);
  }

  public WeightedFollowedTaggedWord(String word, String tag) {
    super(word, tag);
  }

  public WeightedFollowedTaggedWord(String word, String tag, String follow, double weight) {
    super(word, tag);
    this.follow = follow;
    this.weight = weight;
  }

  public WeightedFollowedTaggedWord(Label oldLabel) {
    super(oldLabel);
    if (oldLabel instanceof Weighted) {
      this.weight = ((Weighted) oldLabel).weight();
    }
    if (oldLabel instanceof HasFollow) {
      this.follow = ((HasFollow) oldLabel).follow();
    }
  }

  public WeightedFollowedTaggedWord(Label word, Label tag) {
    super(word, tag);
  }

  public String follow() {
    return follow;
  }

  public void setFollow(String follow) {
    this.follow = follow;
  }

  public double weight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }

  @Override
  public String toString() {
    return word() + "/" + tag() + "[" + follow + "]";
  }


  /**
   * A <code>LabeledConstituentLabelFactory</code> object makes a
   * <code>StringLabel</code> <code>LabeledScoredConstituent</code>.
   */
  private static class WeightedFollowedTaggedWordFactory implements LabelFactory {

    /**
     * Make a new <code>WeightedFollowedTaggedWord</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabel(final String labelStr) {
      return new WeightedFollowedTaggedWord(labelStr);
    }


    /**
     * Make a new <code>WeightedFollowedTaggedWord</code>.
     *
     * @param labelStr A string.
     * @param options  The options are ignored.
     * @return The created label
     */
    public Label newLabel(final String labelStr, final int options) {
      return newLabel(labelStr);
    }


    /**
     * Make a new <code>WeightedFollowedTaggedWord</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabelFromString(final String labelStr) {
      return newLabel(labelStr);
    }


    /**
     * Create a new <code>WeightedFollowedTaggedWord</code>.
     *
     * @param oldLabel A <code>Label</code>.
     * @return A new <code>WeightedFollowedTaggedWord</code>
     */
    public Label newLabel(final Label oldLabel) {
      return new WeightedFollowedTaggedWord(oldLabel);
    }

  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {

    private static final LabelFactory lf = new WeightedFollowedTaggedWordFactory();

  }


  /**
   * Returns a factory that makes WeightedFollowedTaggedWords
   *
   * @return the LabelFactory for WeightedFollowedTaggedWords
   */
  @Override
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label.
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return LabelFactoryHolder.lf;
  }

}
