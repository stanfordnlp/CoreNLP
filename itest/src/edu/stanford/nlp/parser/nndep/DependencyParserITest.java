package edu.stanford.nlp.parser.nndep;

import junit.framework.TestCase;


/** Test that the NN dependency parser performance doesn't change.
 *
 *  @author Christopher Manning
 */
public class DependencyParserITest extends TestCase {

  private static final double EnglishSdLas = 89.58544553340093;

  public void testDependencyParserEnglishSD() {
    NNParser parser = new NNParser();
    double las = parser.test("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_Stanford/dev.conll",
            "/u/nlp/data/depparser/nn/PTB_Stanford_params.txt", null);
    assertEquals(String.format("English SD LAS should be %.2f but was %.2f",
            EnglishSdLas, las), EnglishSdLas, las, 1e-4);
  }

}
