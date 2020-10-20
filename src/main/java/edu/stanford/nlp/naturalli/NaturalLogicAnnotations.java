package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotation;

import java.util.Collection;

/**
 * A collection of {@link edu.stanford.nlp.ling.CoreAnnotation}s for various Natural Logic data.
 *
 * @author Gabor Angeli
 */
public class NaturalLogicAnnotations {

  /**
   * An annotation which attaches to a CoreLabel to denote that this is an operator in natural logic,
   * to describe which operator it is, and to give the scope of its argument(s).
   * This only attaches to tokens which are operators (i.e., the head words of operators).
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
   * An annotation, similar to {@link PolarityAnnotation}, which just measures whether
   * the polarity of a token is upwards, downwards, or flat.
   * This annotation always has values either "up", "down", or "flat".
   */
  public static final class PolarityDirectionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The set of sentences which are entailed by the original sentence, according to Natural Logic semantics.
   */
  public static final class EntailedSentencesAnnotation implements CoreAnnotation<Collection<SentenceFragment>> {
    @SuppressWarnings("unchecked")
    @Override
    public Class<Collection<SentenceFragment>> getType() {
      return (Class<Collection<SentenceFragment>>) ((Object) Collection.class);
    }
  }

  /**
   * A set of clauses contained in and entailed by this sentence.
   */
  public static final class EntailedClausesAnnotation implements CoreAnnotation<Collection<SentenceFragment>> {
    @SuppressWarnings("unchecked")
    @Override
    public Class<Collection<SentenceFragment>> getType() {
      return (Class<Collection<SentenceFragment>>) ((Object) Collection.class);
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
