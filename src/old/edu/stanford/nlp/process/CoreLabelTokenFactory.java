package old.edu.stanford.nlp.process;


import old.edu.stanford.nlp.ling.CoreLabel;
import old.edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import old.edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;

/**
 * Constructs {@link CoreLabel}s from Strings optionally with
 * beginning and ending (character after the end) offset positions in
 * an original text.  The makeToken method will put the token in the
 * WordAnnotation AND CurrentAnnotation, and optionally records
 * begin and position after offsets in BeginPositionAnnotation and
 * EndPositionAnnotation.
 *
 * @author Anna Rafferty
 */
public class CoreLabelTokenFactory implements LexedTokenFactory<CoreLabel> {

  final boolean addIndices;

  /**
   * Constructor for a new token factory which will add in the word, the
   * "current" annotation, and the begin/end position annotations.
   */
  public CoreLabelTokenFactory() {
    this(true);
  }

  /**
   * Constructor that allows one to choose if index annotation indicating begin/end position will be included in
   * the label
   * @param addIndices if true, begin and end position annotations will be included (this is the default)
   */
  public CoreLabelTokenFactory(boolean addIndices) {
    super();
    this.addIndices = addIndices;
  }

  /**
   * Constructs a CoreLabel as a String with a corresponding BEGIN and END position.
   * (Does not take substr).
   */
  public CoreLabel makeToken(String str, int begin, int length) {
    CoreLabel cl = new CoreLabel();
    cl.setWord(str);
    cl.setCurrent(str);
    if(addIndices) {
      cl.set(CharacterOffsetBeginAnnotation.class, begin);
      cl.set(CharacterOffsetEndAnnotation.class, begin+length);
    }
    return cl;
  }

}
