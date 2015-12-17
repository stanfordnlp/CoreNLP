package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;

/**
 * Stores the offsets for a span of text
 * Offsets may indicate either token or byte positions
 * Start is inclusive, end is exclusive
 * @author Mihai 
 */
public class Span implements Serializable, Iterable<Integer> {
  private static final long serialVersionUID = -3861451490217976693L;

  private int start;
  private int end;

  /** For Kryo serializer */
  @SuppressWarnings("UnusedDeclaration")
  private Span() { }

  /**
   * This assumes that s &lt;= e.  Use fromValues if you can't guarantee this.
   */
  public Span(int s, int e) {
    start = s;
    end = e;
  }
  
  /**
   * Creates a span that encloses all spans in the argument list.  Behavior is undefined if given no arguments.
   */
  public Span(Span... spans) {
    this(Integer.MAX_VALUE, Integer.MIN_VALUE);

    for (Span span : spans) {
      expandToInclude(span);
    }
  }
  
  /**
   * Safe way to construct Spans if you're not sure which value is higher.
   */
  @SuppressWarnings("UnusedDeclaration")
  public static Span fromValues(int val1, int val2) {
    if (val1 <= val2) {
      return new Span(val1, val2);
    } else {
      return new Span(val2, val1);
    }
  }

  public static Span fromValues(Object... values) {
    if (values.length == 1) {
      return fromValues(values[0], values[0] instanceof Number ? ((Number) values[0]).intValue() + 1 : Integer.parseInt(values[0].toString()) + 1);
    }
    if (values.length != 2) { throw new IllegalArgumentException("fromValues() must take an array with 2 elements"); }
    int val1;
    if (values[0] instanceof Number) { val1 = ((Number) values[0]).intValue(); }
    else if (values[0] instanceof String) { val1 = Integer.parseInt((String) values[0]); }
    else { throw new IllegalArgumentException("Unknown value for span: " + values[0]); }
    int val2;
    if (values[1] instanceof Number) { val2 = ((Number) values[1]).intValue(); }
    else if (values[0] instanceof String) { val2 = Integer.parseInt((String) values[1]); }
    else { throw new IllegalArgumentException("Unknown value for span: " + values[1]); }
    return fromValues(val1, val2);
  }

  public int start() { return start; }
  public int end() { return end; }
  
  public void setStart(int s) { start = s; }
  public void setEnd(int e) { end = e; }
  
  @Override
  public boolean equals(Object other) {
    if(! (other instanceof Span)) return false;
    Span otherSpan = (Span) other;
    return start == otherSpan.start && end == otherSpan.end;
  }
  
  @Override
  public int hashCode() {
    return (new Pair<>(start, end)).hashCode();
  }
  
  @Override
  public String toString() {
    return "[" + start + "," + end + ")";
  }
  
  public void expandToInclude(Span otherSpan) {
    if (otherSpan.start() < start) {
      setStart(otherSpan.start());
    }
    if (otherSpan.end() > end) {
      setEnd(otherSpan.end());
    }
  }

  /**
   * Returns true if this span contains otherSpan.  Endpoints on spans may match.
   */
  public boolean contains(Span otherSpan) {
    return this.start <= otherSpan.start && otherSpan.end <= this.end;
  }
  
  /**
   * Returns true if i is inside this span.  Note that the start is inclusive and the end is exclusive.
   */
  public boolean contains(int i) {
    return this.start <= i && i < this.end;
  }

  /**
   * Returns true if this span ends before the otherSpan starts.
   * 
   * @throws IllegalArgumentException if either span contains the other span
   */
  public boolean isBefore(Span otherSpan) {
    if (this.contains(otherSpan) || otherSpan.contains(this)) {
      throw new IllegalArgumentException("Span " + toString() + " contains otherSpan " + otherSpan + " (or vice versa)");
    }
    return this.end <= otherSpan.start;
  }

  /**
   * Returns true if this span starts after the otherSpan's end.
   * 
   * @throws IllegalArgumentException if either span contains the other span
   */
  @SuppressWarnings("UnusedDeclaration")
  public boolean isAfter(Span otherSpan) {
    if (this.contains(otherSpan) || otherSpan.contains(this)) {
      throw new IllegalArgumentException("Span " + toString() + " contains otherSpan " + otherSpan + " (or vice versa)");
    }
    return this.start >= otherSpan.end;
  }

  /**
   * Move a span by the given amount. Useful for, e.g., switching between 0- and 1-indexed spans.
   * @param diff The difference to ADD to both the beginning and end of the span. So, -1 moves the span left by one.
   * @return A new span, offset by the given difference.
   */
  public Span translate(int diff) {
    return new Span(start + diff, end + diff);
  }

  /**
   * Convert an end-exclusive span to an end-inclusive span.
   */
  public Span toInclusive() {
    assert end > start;
    return new Span(start, end - 1);
  }

  /**
   * Convert an end-inclusive span to an end-exclusive span.
   */
  public Span toExclusive() {
    return new Span(start, end + 1);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new Iterator<Integer>() {
      int nextIndex = start;
      @Override
      public boolean hasNext() {
        return nextIndex < end;
      }
      @Override
      public Integer next() {
        if (!hasNext()) { throw new NoSuchElementException(); }
        nextIndex += 1;
        return nextIndex - 1;
      }
      @Override
      public void remove() { throw new UnsupportedOperationException(); }
    };
  }

  public int size() {
    return end - start;
  }

  public static boolean overlaps(Span spanA, Span spanB) {
    return spanA.contains(spanB) ||
            spanB.contains(spanA) ||
            (spanA.end > spanB.end && spanA.start < spanB.end) ||
            (spanB.end > spanA.end && spanB.start < spanA.end) ||
            spanA.equals(spanB);
  }

  public static int overlap(Span spanA, Span spanB) {
    if (spanA.contains(spanB)) {
      return Math.min(spanA.end - spanA.start, spanB.end - spanB.start);
    } else if (spanA.equals(spanB)) {
      return spanA.end - spanA.start;
    } else if ( (spanA.end > spanB.end && spanA.start < spanB.end) ||
                (spanB.end > spanA.end && spanB.start < spanA.end) ) {
      return Math.min(spanA.end, spanB.end) - Math.max(spanA.start, spanB.start) ;
    } else {
      return 0;
    }
  }

  public static boolean overlaps(Span spanA, Collection<Span> spanB) {
    for (Span candidate : spanB) {
      if (overlaps(spanA, candidate)) { return true; }
    }
    return false;
  }

  /**
   * Returns the smallest distance between two spans.
   */
  public static int distance(Span a, Span b) {
    if (Span.overlaps(a, b)) {
      return 0;
    } else if (a.contains(b) || b.contains(a)) {
      return 0;
    } else if (a.isBefore(b)) {
      return b.start - a.end;
    } else if (b.isBefore(a)) {
      return a.start - b.end;
    } else {
      throw new IllegalStateException("This should be impossible...");
    }
  }

  /**
   * A silly translation between a pair and a span.
   */
  public static Span fromPair(Pair<Integer, Integer> span) {
    return fromValues(span.first, span.second);
  }

  /**
   * Another silly translation between a pair and a span.
   */
  public static Span fromPair(IntPair span) {
    return fromValues(span.getSource(), span.getTarget());
  }

  /**
   * A silly translation between a pair and a span.
   */
  public static Span fromPairOneIndexed(Pair<Integer, Integer> span) {
    return fromValues(span.first - 1, span.second - 1);
  }

  /**
   * The union of two spans. That is, the minimal span that contains both.
   */
  public static Span union(Span a, Span b) {
    return Span.fromValues(Math.min(a.start, b.start), Math.max(a.end, b.end));
  }
}