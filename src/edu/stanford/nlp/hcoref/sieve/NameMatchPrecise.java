package edu.stanford.nlp.hcoref.sieve;

/**
 * Use name matcher - more precise match
 *
 * @author Angel Chang
 */
public class NameMatchPrecise extends NameMatch {


  public NameMatchPrecise() {
    super();
    ignoreGender = false;
    minTokens = 2;
  }



}
