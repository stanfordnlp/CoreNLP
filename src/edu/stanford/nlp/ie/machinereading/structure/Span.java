package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

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
    return (new Pair<Integer,Integer>(start,end)).hashCode();
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

  public static boolean overlaps(Span spanA, Collection<Span> spanB) {
    for (Span candidate : spanB) {
      if (overlaps(spanA, candidate)) { return true; }
    }
    return false;
  }
}