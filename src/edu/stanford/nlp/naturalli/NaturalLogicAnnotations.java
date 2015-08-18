package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.Collection;
import java.util.List;

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

  /**
   * The set of sentences which are entailed by the original sentence, according to Natural Logic semantics.
   */
  public static final class ImpliedSentencesAnnotation implements CoreAnnotation<Collection<List<CoreLabel>>> {
    @SuppressWarnings("unchecked")
    @Override
    public Class<Collection<List<CoreLabel>>> getType() {
      return (Class<Collection<List<CoreLabel>>>) ((Object) Collection.class);
    }
  }

  /**
   * The set of relation triples extracted from this sentence.
   */
  public static final class RelationTriplesAnnotation implements CoreAnnotation<Collection<RelationTriple>> {
    @SuppressWarnings("unchecked")
    @Override
    public Class<Collection<RelationTriple>> getType() {
      return (Class<Collection<RelationTriple>>) ((Object) Collection.class);
    }
  }
}
