package edu.stanford.nlp.ie.ner;

import java.util.ArrayList;
import java.util.List;

/**
 * This class takes as input a BioCreative output file and find results
 * where a gene was returned and another gene entirely contianing the
 * original gene is also returned.  It then proints out the original
 * output file but with the longer genes removed.
 * <p/>
 * usage: <br>
 * &gt; java edu.stanford.nlp.ie.ner.SmallerOverlapResolver inputfile &gt; outputfile
 */
public class SmallerOverlapResolver extends OverlapResolver {

  public static void main(String[] args) {
    SmallerOverlapResolver ior = new SmallerOverlapResolver(args[0]);
  }

  public SmallerOverlapResolver(String f) {
    super(f);
  }

  @Override
  public List<Answer> resolveContains(Answer a, Answer b) {

    List<Answer> l = new ArrayList<Answer>();

    if (a.ans.length > b.ans.length) {
      l.add(b);
    } else {
      l.add(a);
    }
    return l;
  }

  @Override
  public List<Answer> resolveOverlaps(Answer a, Answer b) {

    List<Answer> l = new ArrayList<Answer>(2);
    l.add(a);
    l.add(b);
    return l;

  }

  public Answer merge(Answer a, Answer b) {
    Answer c = new Answer();
    c.id = a.id;
    c.start = a.start;
    c.end = b.end;

    int length = c.end - c.start + 1;

    c.ans = new String[length];

    if (length <= a.ans.length) {
      System.arraycopy(a.ans, 0, c.ans, 0, length);
    } else {
      int length1 = length - a.ans.length;
      System.arraycopy(a.ans, 0, c.ans, 0, a.ans.length);
      System.arraycopy(b.ans, b.ans.length - length1, c.ans, a.ans.length, length1);
    }
    return c;
  }

}
