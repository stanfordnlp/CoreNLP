package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;

/**
 * Constructs {@link CyclicCoreLabel}s as Strings with a corresponding BEGIN and END position.
 *
 * @author Anna Rafferty
 */
public class CyclicCoreLabelTokenFactory implements LexedTokenFactory<CyclicCoreLabel> {
  boolean addIndices = true;

  /**
   * Constructor for a new token factory which will add in the word, the "current" annotation, and the begin/end position annotations.
   */
  public CyclicCoreLabelTokenFactory() {
    super();
  }

  /**
   * Constructor that allows one to choose if index annotation indicating begin/end position will be included in
   * the label
   * @param addIndices if true, begin and end position annotations will be included (this is the default)
   */
  public CyclicCoreLabelTokenFactory(boolean addIndices) {
    super();
    this.addIndices = addIndices;
  }

  /**
   * Constructs a CyclicCoreLabel as a String with a corresponding BEGIN and END position.
   * (Does not take substr).
   */
  public CyclicCoreLabel makeToken(String str, int begin, int length) {
    CyclicCoreLabel fl = new CyclicCoreLabel();
    fl.setWord(str);
    fl.setOriginalText(str);
    if(addIndices) {
      fl.set(CharacterOffsetBeginAnnotation.class, begin);
      fl.set(CharacterOffsetEndAnnotation.class, begin+length);
    }
    return fl;
  }
}