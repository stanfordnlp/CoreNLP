package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/** @author Christopher Manning */
public class TreebankLanguagePackTest extends TestCase {

  public void testBasicCategory() {
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    assertEquals("NP", tlp.basicCategory("NP-SBJ-R"));
    assertEquals("-", tlp.basicCategory("-"));
    assertEquals("-LRB-", tlp.basicCategory("-LRB-"));
    assertEquals("-", tlp.basicCategory("--PU"));
    assertEquals("-", tlp.basicCategory("--PU-U"));
    assertEquals("-LRB-", tlp.basicCategory("-LRB--PU"));
    assertEquals("-LRB-", tlp.basicCategory("-LRB--PU-U"));
  }

}
