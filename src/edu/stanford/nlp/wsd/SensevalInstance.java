package edu.stanford.nlp.wsd;

import java.util.ArrayList;

/**
 * A class for storing senseval instance info.
 *
 * @author Galen Andrew (pupochik@cs.stanford.edu)
 */

public class SensevalInstance {

  private String lexElt;
  private String nakedLexElt;
  private String instId;
  private String docSource;
  private ArrayList<String> senseIDs;
  private ArrayList<String> context;
  private int targetWordPos;

  public SensevalInstance(String lexElt, String instId, String docSource, ArrayList<String> senseIDs, ArrayList<String> context, int targetWordPos) {
    this.lexElt = lexElt;
    this.instId = instId;
    this.docSource = docSource;
    this.senseIDs = senseIDs;
    this.context = context;
    this.targetWordPos = targetWordPos;
    nakedLexElt = lexElt.substring(0, lexElt.indexOf('.'));
  }

  public String getLexElt() {
    return lexElt;
  }

  public String getNakedLexElt() {
    return nakedLexElt;
  }

  public String getInstId() {
    return instId;
  }

  public String getDocSource() {
    return docSource;
  }

  public ArrayList<String> getSenseIDs() {
    return senseIDs;
  }

  public ArrayList<String> getContext() {
    return context;
  }

  public int getTargetWordPos() {
    return targetWordPos;
  }
}
