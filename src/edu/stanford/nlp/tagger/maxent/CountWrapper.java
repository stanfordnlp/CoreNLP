/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */
package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.OutDataStreamFile;

import java.io.IOException;
import java.io.DataInputStream;


/** A simple data structure for some tag counts.
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class CountWrapper {

  private String word;
  private int countPart;
  private int countThat;
  private int countIn;
  private int countRB;
  //private Dictionary dictLocal = new Dictionary();
  //private static final String rpTag = "RP";
  //private static final String inTag = "IN";
  //private static final String rbTag = "RB";

  public CountWrapper() {
  }

  protected CountWrapper(String word, int countPart, int countThat, int countIn, int countRB) {
    assert(word != null);
    this.word = word;
    this.countPart = countPart;
    this.countThat = countThat;
    this.countIn = countIn;
    this.countRB = countRB;

  }

  protected void incThat() {
    this.countThat++;
  }

  public int getCountPart() {
    return countPart;
  }

  public int getCountThat() {
    return countThat;
  }


  public int getCountIn() {
    return countIn;
  }

  public int getCountRB() {
    return countRB;
  }


  public String getWord() {
    return word;
  }

  @Override
  public int hashCode() {
    return word.hashCode();
  }

  /** Equality is tested only on the word, and not the various counts
   *  that are maintained.
   *
   *  @param obj Item tested for equality
   *  @return Whether equal
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if ( ! (obj instanceof CountWrapper)) {
      return false;
    }
    CountWrapper cw = (CountWrapper) obj;
    return word.equals(cw.word);
  }

  protected void save(OutDataStreamFile rf) {
    try {
      rf.writeInt(word.length());
      rf.write(word.getBytes());
      rf.writeInt(countPart);
      rf.writeInt(countThat);
      rf.writeInt(countIn);
      rf.writeInt(countRB);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  protected void read(DataInputStream rf) {
    try {
      int len = rf.readInt();
      byte[] buff = new byte[len];
      if (rf.read(buff) != len) { System.err.println("Error: rewrite CountWrapper.read"); }
      word = new String(buff);
      assert(word != null);
      countPart = rf.readInt();
      countThat = rf.readInt();
      countIn = rf.readInt();
      countRB = rf.readInt();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


}


