package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;

/**
 * A collection of {@link edu.stanford.nlp.ling.CoreAnnotation}s for various Natural Logic data.
 *
 * @author Gabor Angeli
 */
public class NaturalLogicAnnotations {

  /**
   * An annotation which attaches to a CoreLabel to denote that this is an operator in natural logic,
   * to describe which operator it is, and to give the scope of its argument(s).
   */
  public static final class OperatorAnnotation implements CoreAnnotation<OperatorSpec> {
    @Override
    public Class<OperatorSpec> getType() {
      return OperatorSpec.class;
    }
  }

  /**
   * An annotation which attaches to a CoreLabel to denote that this is an operator in natural logic,
   * to describe which operator it is, and to give the scope of its argument(s).
   */
  public static final class PolarityAnnotation implements CoreAnnotation<Polarity> {
    @Override
    public Class<Polarity> getType() {
      return Polarity.class;
    }
  }
}
