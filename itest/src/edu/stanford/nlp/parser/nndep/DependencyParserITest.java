package edu.stanford.nlp.parser.nndep;

import junit.framework.TestCase;


/** Test that the NN dependency parser performance doesn't change.
 *
 *  @author Christopher Manning
 */
public class DependencyParserITest extends TestCase {

  private static final double EnglishSdLas = 89.58544553340093;

  public void testDependencyParserEnglishSD() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib/PTB_Stanford_params.txt");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_Stanford/dev.conll", null);
    assertEquals(String.format("English SD LAS should be %.2f but was %.2f",
            EnglishSdLas, las), EnglishSdLas, las, 1e-4);
  }

}
