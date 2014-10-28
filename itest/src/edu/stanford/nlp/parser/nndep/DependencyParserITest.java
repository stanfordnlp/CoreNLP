package edu.stanford.nlp.parser.nndep;

import java.util.Properties;

import junit.framework.TestCase;

import edu.stanford.nlp.util.StringUtils;


/** Test that the NN dependency parser performance doesn't change.
 *
 *  @author Christopher Manning
 */
public class DependencyParserITest extends TestCase {

  private static final double EnglishSdLas = 89.58544553340093;

  public void testDependencyParserEnglishSD() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_Stanford_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_Stanford/3.3.0/dev.conll", null);
    assertEquals(String.format("English SD LAS should be %.2f but was %.2f",
            EnglishSdLas, las), EnglishSdLas, las, 1e-4);
  }

  private static final double EnglishConll2008Las = 90.97206578058122;

  public void testDependencyParserEnglishCoNLL2008() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_CoNLL_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_CoNLL/dev.conll", null);
    assertEquals(String.format("English CoNLL2008 LAS should be %.2f but was %.2f",
            EnglishConll2008Las, las), EnglishConll2008Las, las, 1e-4);
  }

  private static final double ChineseConllxGoldTagsLas = 82.42855503270974;

  public void testDependencyParserChineseCoNLLX() {
    Properties props = StringUtils.stringToProperties("language=Chinese");
    DependencyParser parser = new DependencyParser(props);
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/CTB_CoNLL_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/CTB/dev.gold.conll", null);
    assertEquals(String.format("Chinese CoNLLX gold tags LAS should be %.2f but was %.2f",
            ChineseConllxGoldTagsLas, las), ChineseConllxGoldTagsLas, las, 1e-4);
  }

}
