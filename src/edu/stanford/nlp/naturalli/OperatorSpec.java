package edu.stanford.nlp.naturalli;

/**
 * A silly little class to denote a quantifier scope.
 *
 * @author Gabor Angeli
 */
public class OperatorSpec {
  public final Operator instance;
  public final int quantifierBegin;
  public final int quantifierEnd;
  public final int quantifierHead;
  public final int subjectBegin;
  public final int subjectEnd;
  public final int objectBegin;
  public final int objectEnd;

  public OperatorSpec(
      Operator instance,
      int quantifierBegin, int quantifierEnd,
      int subjectBegin, int subjectEnd,
      int objectBegin, int objectEnd) {
    this.instance = instance;
    this.quantifierBegin = quantifierBegin;
    this.quantifierEnd = quantifierEnd;
    this.quantifierHead = quantifierEnd - 1;
    this.subjectBegin = subjectBegin;
    this.subjectEnd = subjectEnd;
    this.objectBegin = objectBegin;
    this.objectEnd = objectEnd;
  }

  protected OperatorSpec(
      Operator instance,
      int quantifierBegin, int quantifierEnd,
      int subjectBegin, int subjectEnd,
      int objectBegin, int objectEnd,
      int sentenceLength) {
    this(instance,
        Math.max(0, Math.min(sentenceLength - 1, quantifierBegin)),
        Math.max(0, Math.min(sentenceLength, quantifierEnd)),
        Math.max(0, Math.min(sentenceLength - 1, subjectBegin)),
        Math.max(0, Math.min(sentenceLength, subjectEnd)),
        Math.max(0, objectBegin == sentenceLength ? sentenceLength : Math.min(sentenceLength - 1, objectBegin)),
        Math.max(0, Math.min(sentenceLength, objectEnd)));
  }

  /**
   * If true, this is an explicit quantifier, such as "all" or "some."
   * The other option is for this to be an implicit quantification, for instance with proper names:
   *
   * <code>
   * "Felix is a cat" -> \forall x, Felix(x) \rightarrow cat(x).
   * </code>
   */
  public boolean isExplicit() {
    return instance != Operator.IMPLICIT_NAMED_ENTITY;
  }

  public boolean isBinary() {
    return objectEnd > objectBegin;
  }

  public int quantifierLength() {
    return quantifierEnd - quantifierBegin;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "QuantifierScope{" +
        "subjectBegin=" + subjectBegin +
        ", subjectEnd=" + subjectEnd +
        ", objectBegin=" + objectBegin +
        ", objectEnd=" + objectEnd +
        '}';
  }

  public static OperatorSpec merge(OperatorSpec x, OperatorSpec y) {
    assert (x.quantifierBegin == y.quantifierBegin);
    assert (x.quantifierEnd == y.quantifierEnd);
    assert (x.instance == y.instance);
    return new OperatorSpec(
        x.instance,
        Math.min(x.quantifierBegin, y.quantifierBegin),
        Math.min(x.quantifierEnd, y.quantifierEnd),
        Math.min(x.subjectBegin, y.subjectBegin),
        Math.max(x.subjectEnd, y.subjectEnd),
        Math.min(x.objectBegin, y.objectBegin),
        Math.max(x.objectEnd, y.objectEnd));
  }

  @SuppressWarnings("RedundantIfStatement")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OperatorSpec)) return false;

    OperatorSpec that = (OperatorSpec) o;

    if (objectBegin != that.objectBegin) return false;
    if (objectEnd != that.objectEnd) return false;
    if (quantifierBegin != that.quantifierBegin) return false;
    if (quantifierEnd != that.quantifierEnd) return false;
    if (quantifierHead != that.quantifierHead) return false;
    if (subjectBegin != that.subjectBegin) return false;
    if (subjectEnd != that.subjectEnd) return false;
    if (instance != that.instance) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = instance != null ? instance.hashCode() : 0;
    result = 31 * result + quantifierBegin;
    result = 31 * result + quantifierEnd;
    result = 31 * result + quantifierHead;
    result = 31 * result + subjectBegin;
    result = 31 * result + subjectEnd;
    result = 31 * result + objectBegin;
    result = 31 * result + objectEnd;
    return result;
  }
}
