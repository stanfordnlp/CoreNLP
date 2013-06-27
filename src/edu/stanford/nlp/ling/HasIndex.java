package edu.stanford.nlp.ling;

/**
 * @author grenager
 */
public interface HasIndex {
  String docID();
  void setDocID(String docID);
  int sentIndex();
  void setSentIndex(int sentIndex);
  int index();
  void setIndex(int index);
}
