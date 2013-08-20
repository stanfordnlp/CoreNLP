package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.WordTag;

/**
 * Provides a class that implements Function which
 * maps any Label to another Label where the Tag is indicative of its
 * "word shape" -- e.g.,
 * whether capitalized, numeric, etc.  Different implementations may
 * implement quite different, normally language specific ideas of what
 * word shapes are useful.  See
 * {@link edu.stanford.nlp.process.WordShapeClassifier} for details of the provided word shape
 * functions.
 *
 * @author Christopher Manning
 */
public class WordShapeFunction implements Function<Label,WordTag> {

  private int classifierToUse;

  /**
   * Makes a <code>WordShapeClassifier.WORDSHAPEDAN2</code> function.
   */
  public WordShapeFunction() {
    this(WordShapeClassifier.WORDSHAPEDAN2);
  }

  /**
   * Make a WordShapeFunction.
   *
   * @param which An integer constant for a word shape function,
   *              as defined in {@link edu.stanford.nlp.process.WordShapeClassifier}.
   */
  public WordShapeFunction(int which) {
    classifierToUse = which;
  }


  /**
   * Map a Label to a WordTag which has its shape as the tag.
   *
   * @param in A Label
   * @return A WordTag with the input value() and shape as tag()
   */
  public WordTag apply(Label in) {
    String inStr = in.value();
    return new WordTag(inStr, edu.stanford.nlp.process.WordShapeClassifier.wordShape(inStr, classifierToUse));
  }

}
