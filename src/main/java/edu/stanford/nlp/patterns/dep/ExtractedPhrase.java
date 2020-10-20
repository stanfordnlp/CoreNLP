package edu.stanford.nlp.patterns.dep;

import java.io.Serializable;

import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.IntPair;

public class ExtractedPhrase implements Serializable{
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  
  int startIndex;
  int endIndex;
  SemgrexPattern pattern;
  String value;
  double confidence = 1;
  String articleId = null;
  Integer sentId = null;
  Counter<String> features;

  public ExtractedPhrase(int startIndex, int endIndex, SemgrexPattern pattern, String value) {
    this(startIndex, endIndex, pattern, value, 1.0, null, null, null);
  }

  public ExtractedPhrase(int startIndex, int endIndex, SemgrexPattern pattern, String value, Counter<String> features) {
    this(startIndex, endIndex, pattern, value, 1.0, null, null, features);
  }

  public ExtractedPhrase(int startIndex, int endIndex, SemgrexPattern pattern, String value, double weight,
                         String articleId, Integer sentId){
    this(startIndex, endIndex, pattern, value, weight, articleId, sentId, null);
  }

  public ExtractedPhrase(int startIndex, int endIndex, SemgrexPattern pattern, String value, double weight,
      String articleId, Integer sentId, Counter<String> features) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.pattern = pattern;
    this.value = value;
    this.confidence = weight;
    this.articleId = articleId;
    this.sentId = sentId;
    this.features = features;
  }

  // public ExtractedPhrase(int startIndex, int endIndex) {
  // this(startIndex, endIndex, null, null);
  // }

  // public ExtractedPhrase(int startIndex, int endIndex, SemgrexPattern
  // pattern) {
  // this(startIndex, endIndex, pattern, null);
  // }

  public ExtractedPhrase(int startIndex, int endIndex, String value) {
    this(startIndex, endIndex, null, value);
  }

  int getStartIndex() {
    return this.startIndex;
  }

  int getEndIndex() {
    return this.endIndex;
  }

  public IntPair getIndices() {
    return new IntPair(startIndex, endIndex);
  }

  public String getValue() {
    return this.value;
  }

  public SemgrexPattern getPattern() {
    return this.pattern;
  }

  void setPattern(SemgrexPattern pattern) {
    this.pattern = pattern;
  }

  void setConfidence(double weight) {
    this.confidence = weight;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ExtractedPhrase)) return false;
    ExtractedPhrase p = (ExtractedPhrase) o;

    if (p.startIndex == this.startIndex && p.endIndex == this.endIndex && (this.value.equals(p.value)))
      return true;
    else
      return false;
  }

  @Override
  public int hashCode() {
    return this.startIndex * 31 + this.endIndex + this.value.hashCode();
  }

  public Counter<String> getFeatures(){
    return this.features;
  }

  @Override
  public String toString(){
    return this.value + "("+startIndex+","+endIndex+"," + features+")";
  }
}
