/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */


package old.edu.stanford.nlp.tagger.maxent;

import java.io.PrintStream;


/**
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class History {
  int start;  // this is the index of the first word of the sentence
  int end;    //this is the index of the last word in the sentence - the dot
  int current; // this is the index of the current word
  final PairsHolder pairs;

  History(PairsHolder pairs) {
    this.pairs = pairs;
  }

  History(int start, int end, int current, PairsHolder pairs) {
    this.pairs = pairs;
    init(start,end,current);
  }

  void init(int start, int end, int current) {
    this.start = start;
    this.end = end;
    this.current = current;
  }

  /*
  public void save(OutDataStreamFile rf) {
    try {
      rf.writeInt(start);
      rf.writeInt(end);
      rf.writeInt(current);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void read(InDataStreamFile rf) {
    try {
      start = rf.readInt();
      end = rf.readInt();
      current = rf.readInt();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */

  private String getX(int index) {
    // get the string by the index in x
    return GlobalHolder.extractors.get(index).extract(this);
  }

  public String[] getX() {
    String[] x = new String[GlobalHolder.extractors.getSize()];
    for (int i = 0; i < x.length; i++) {
      x[i] = getX(i);
    }
    return x;
  }


  void print(PrintStream ps) {
    String[] str = getX();
    for (String aStr : str) {
      ps.print(aStr);
      ps.print('\t');
    }
    ps.println();
  }

  public void printSent() {
    print(System.out);

    for (int i = this.start; i < this.end; i++) {
      System.out.print(pairs.getTag(i) + ' ' + pairs.getWord(i) + '\t');
    }
    System.out.println();
  }
  
  protected void setTag(int pos, String tag) {
    pairs.setTag(pos + start, tag);
  }


  protected void set(int start, int end, int current) {
    this.start = start;
    this.end = end;
    this.current = current;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String[] str = getX();
    for (String aStr : str) {
      sb.append(aStr).append('\t');
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < GlobalHolder.extractors.getSize(); i++) {
      sb.append(getX(i));
    }
    return sb.toString().hashCode();
  }


  @Override
  public boolean equals(Object h1) {
    return h1 instanceof History && GlobalHolder.extractors.equals(this, (History) h1);
  }

}
