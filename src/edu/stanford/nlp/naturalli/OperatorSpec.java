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

  /**
   * If true, this is an explcit quantifier, such as "all" or "some."
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OperatorSpec)) return false;
    OperatorSpec that = (OperatorSpec) o;
    return objectBegin == that.objectBegin && objectEnd == that.objectEnd && subjectBegin == that.subjectBegin && subjectEnd == that.subjectEnd;

  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = subjectBegin;
    result = 31 * result + subjectEnd;
    result = 31 * result + objectBegin;
    result = 31 * result + objectEnd;
    return result;
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
}
