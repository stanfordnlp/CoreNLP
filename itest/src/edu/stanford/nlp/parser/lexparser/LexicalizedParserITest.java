// LexicalizedParserITest
// Copyright (c) 2002-2010 Leland Stanford Junior University

//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//Support/Questions: java-nlp-user@lists.stanford.edu
//Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml

package edu.stanford.nlp.parser.lexparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * The purpose of this itest is a simple test to make sure the
 * standard LexicalizedParser parses things in an expected way.  Since
 * simple sentences should be parsed in the same way regardless of
 * updated data files, this mostly tests the interface and that the
 * data file in the default location hasn't drastically changed.
 *
 * @author John Bauer
 */
public class LexicalizedParserITest {

  private static LexicalizedParser englishParser = null;
  private static TreePrint tagPrint = null;
  private static TreePrint pennPrint = null;
  private static TreePrint typDepPrint = null;
  private static TreePrint typDepColPrint = null;

  private static LexicalizedParser chineseParser = null;
  private static TreePrint chinesePennPrint = null;
  private static TreePrint chineseTypDepPrint = null;

  // TODO: add more tests

  @Before
  public void setUp() throws Exception {
    synchronized(LexicalizedParserITest.class) {
      if (englishParser == null) {
        // sharing a bunch of code here with the webapp in
        // parser/webapp/index.jsp...  perhaps we could reuse that code
        englishParser = LexicalizedParser.loadModel();
        TreebankLanguagePack tLP =
          englishParser.getOp().tlpParams.treebankLanguagePack();
        tagPrint = new TreePrint("wordsAndTags", tLP);
        pennPrint = new TreePrint("penn", tLP);
        typDepPrint = new TreePrint("typedDependencies", "basicDependencies", tLP);
        typDepColPrint = new TreePrint("typedDependencies", tLP);  // default is now CCprocessed

        File englishPath = new File(LexicalizedParser.DEFAULT_PARSER_LOC);
        String chinesePath = (englishPath.getParent() + File.separator +
                              "chineseFactored.ser.gz");
        chineseParser = LexicalizedParser.loadModel(chinesePath);
        tLP = chineseParser.getOp().tlpParams.treebankLanguagePack();
        chineseParser.getTLPParams().setGenerateOriginalDependencies(true); // test was made with Chinese SD not UD

        chinesePennPrint = new TreePrint("penn", tLP);
        chineseTypDepPrint = new TreePrint("typedDependencies", "basicDependencies", tLP);
      }
    }
  }

  /**
   * Compares one view of the result tree to the expected results.
   *
   * Setting outputResults to true makes it print out the results.
   * This is useful because assertEquals sometimes abbreviates the
   * strings on failure, which makes it hard to diagnose.
   */
  private static void compareSingleOutput(Tree results, boolean outputResults,
                                          TreePrint printer,
                                          String expectedOutput) {
    StringWriter sw = new StringWriter();
    printer.printTree(results, (new PrintWriter(sw)));
    if (expectedOutput != null) {
      expectedOutput = expectedOutput.replaceAll("\\s+", " ").trim();
    }
    String actualOutput = sw.toString().replaceAll("\\s+", " ").trim();
    if (outputResults) {
      if (expectedOutput != null) {
        System.out.println(expectedOutput);
      }
      System.out.println(actualOutput);
    }
    if (expectedOutput != null) {
      assertEquals(expectedOutput, actualOutput);
    }
  }

  /**
   * Given a tree and a bunch of expected strings, this method takes
   * that tree and compares its components to the expected output by
   * printing the tree in a few different ways.  There are probably
   * better ways of testing the trees, ie by comparing the tree
   * directly instead of printing it out, but printing it also makes
   * the output very easy to inspect visually.
   *
   * Setting outputResults to true makes it print out the results.
   */
  private static void compareOutput(Tree results, boolean outputResults,
                                    String expectedTags,
                                    String expectedPenn,
                                    String expectedDep,
                                    String expectedDepCol) {
    compareSingleOutput(results, outputResults, tagPrint, expectedTags);
    compareSingleOutput(results, outputResults, pennPrint, expectedPenn);
    compareSingleOutput(results, outputResults, typDepPrint, expectedDep);
    compareSingleOutput(results, outputResults, typDepColPrint, expectedDepCol);
  }

  private static List<CoreLabel> sampleSausage() {
    String[] words = {"My", "dog", "also", "likes", "eating", "sausage", "."};
    return SentenceUtils.toCoreLabelList(words);
  }

  /**
   * This method tests a very basic string and a few different results
   * that parsing that string should come up with.
   */
  @Test
  public void testParseString() {
    Tree results = englishParser.parse("My dog likes to eat yoghurt.");
    compareOutput(results, false,
                  "My/PRP$ dog/NN likes/VBZ to/TO eat/VB yoghurt/NN ./.",
                  "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (VBZ likes) (S (VP (TO to) (VP (VB eat) (NP (NN yoghurt)))))) (. .)))",
                  "nmod:poss(dog-2, My-1) nsubj(likes-3, dog-2) root(ROOT-0, likes-3) mark(eat-5, to-4) xcomp(likes-3, eat-5) obj(eat-5, yoghurt-6)",
                  "nmod:poss(dog-2, My-1) nsubj(likes-3, dog-2) nsubj:xsubj(eat-5, dog-2) root(ROOT-0, likes-3) mark(eat-5, to-4) xcomp(likes-3, eat-5) obj(eat-5, yoghurt-6)");
  }

  /**
   * Test the query structure that you can use for better control of
   * the parse
   */
  @Test
  public void testParserQuery() {
    List<CoreLabel> sentence = sampleSausage();
    ParserQuery pq = englishParser.parserQuery();
    pq.parse(sentence);
    compareSingleOutput(pq.getBestParse(), false, pennPrint,
                        "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
  }

  @Test
  public void testParseMultiple() {
    List<List<CoreLabel>> sentences = new ArrayList<>();
    sentences.add(SentenceUtils.toCoreLabelList("The", "Flyers", "lost", "again", "last", "night", "."));
    sentences.add(SentenceUtils.toCoreLabelList("If", "this", "continues", ",", "they", "will", "miss", "the", "playoffs", "."));
    sentences.add(SentenceUtils.toCoreLabelList("Hopefully", "they", "can", "turn", "it", "around", "."));
    sentences.add(SentenceUtils.toCoreLabelList("Winning", "on", "Wednesday", "would", "be", "a", "good", "first", "step", "."));
    sentences.add(SentenceUtils.toCoreLabelList("Their", "next", "opponent", "is", "quite", "bad", "."));

    List<Tree> results1 = englishParser.parseMultiple(sentences);
    List<Tree> results2 = englishParser.parseMultiple(sentences, 3);

    assertEquals(results1, results2);
  }

  /**
   * Test what happens if you put a constraint on the parse
   */
  @Test
  public void testConstraints() {
    List<CoreLabel> sentence = sampleSausage();

    ParserQuery pq = englishParser.parserQuery();

    ParserConstraint constraint = new ParserConstraint(0, 2, "INTJ");
    List<ParserConstraint> constraints = new ArrayList<>();
    constraints.add(constraint);
    pq.setConstraints(constraints);

    pq.parse(sentence);

    StringWriter sw = new StringWriter();
    pennPrint.printTree(pq.getBestParse(), (new PrintWriter(sw)));
    String actualOutput = sw.toString().replaceAll("\\s+", " ").trim();

    String expectedOutput = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    expectedOutput = expectedOutput.replaceAll("\\s+", " ").trim();

    // Not exactly sure what should come back, but it shouldn't be the
    // original output any more
    assertFalse("Tree should not match the original tree any more",
                expectedOutput.equals(actualOutput));
    assertTrue("Tree should be forced to contain INTJ",
            actualOutput.contains("INTJ"));

    //System.out.println(pq.getBestParse());
  }

  private static final String chineseTest = "我 看 了 一 条 狗";
  private static final String expectedChineseTree = "(ROOT (IP (NP (PN 我)) (VP (VV 看) (AS 了) (NP (QP (CD 一) (CLP (M 条))) (NP (NN 狗))))))";
  private static final String expectedChineseDeps = "nsubj(看-2, 我-1) root(ROOT-0, 看-2) asp(看-2, 了-3) nummod(条-5, 一-4) clf(狗-6, 条-5) dobj(看-2, 狗-6)";

  @Test
  public void testChineseDependencies() {
    Tree tree = chineseParser.parse(chineseTest);
    compareSingleOutput(tree, false, chinesePennPrint, expectedChineseTree);
    compareSingleOutput(tree, false, chineseTypDepPrint, expectedChineseDeps);
  }

  private static final String chineseTest2 = "这里 是 新闻 之 夜 ．";
  private static final String expectedChineseTree2 = "(ROOT (IP (NP (PN 这里)) (VP (VC 是) (NP (DNP (NP (NN 新闻)) (DEG 之)) (NP (NN 夜)))) (PU ．)))";
  /** This is the right answer for Chinese UD. */
  private static final String expectedChineseDeps2 = "nsubj(夜-5, 这里-1) cop(夜-5, 是-2) assmod(夜-5, 新闻-3) case(新闻-3, 之-4) root(ROOT-0, 夜-5)";
  /** This is the right answer for old-style Chinese SD. */
  private static final String expectedChineseDeps2sd = "top(是-2, 这里-1) root(ROOT-0, 是-2) assmod(夜-5, 新闻-3) assm(新闻-3, 之-4) attr(是-2, 夜-5)";

  @Test
  public void testChineseDependenciesSemanticHead() {
    Tree tree = chineseParser.parse(chineseTest2);
    compareSingleOutput(tree, false, chinesePennPrint, expectedChineseTree2);
    compareSingleOutput(tree, false, chineseTypDepPrint, expectedChineseDeps2sd);
    TreePrint paramsTreePrint = new TreePrint("typedDependencies", "basicDependencies", chineseParser.treebankLanguagePack(), chineseParser.getTLPParams().headFinder(), chineseParser.getTLPParams().typedDependencyHeadFinder());
    compareSingleOutput(tree, false, paramsTreePrint, expectedChineseDeps2sd);
  }

  @Test
  public void testAlreadyTagged() {
    List<CoreLabel> words = SentenceUtils.toCoreLabelList("foo", "bar", "baz");
    words.get(1).setTag("JJ");
    Tree tree = englishParser.parse(words);
    assertEquals("JJ", tree.taggedYield().get(1).tag());

    words.get(1).setTag("NN");
    tree = englishParser.parse(words);
    assertEquals("NN", tree.taggedYield().get(1).tag());
  }

  @Test
  public void testTagRegex() {
    List<CoreLabel> words = SentenceUtils.toCoreLabelList("foo", "bar", "baz");
    words.get(1).set(ParserAnnotations.CandidatePartOfSpeechAnnotation.class, "JJ");
    Tree tree = englishParser.parse(words);
    assertEquals("JJ", tree.taggedYield().get(1).tag());

    words.get(1).set(ParserAnnotations.CandidatePartOfSpeechAnnotation.class, "NN|NNP");
    tree = englishParser.parse(words);
    assertTrue(tree.taggedYield().get(1).tag().equals("NN") ||
            tree.taggedYield().get(1).tag().equals("NNP"));
  }

  @Test
  public void testCharOffsets() {
    String text = "  You can  eat fruits   such as apples and   oranges.";
    String[] tokens = { "You", "can", "eat", "fruits", "such", "as", "apples", "and", "oranges", "." };
    int[] begins = { 2, 6, 11, 15, 24, 29, 32, 39, 45, 52 };
    int[] ends = { 5, 9, 14, 21, 28, 31, 38, 42, 52, 53 };

    Tree tree = englishParser.parse(text);

    List<CoreLabel> yield = tree.yield(new ArrayList<CoreLabel>());

    assertEquals("Wrong number of tokens in parser output", tokens.length, yield.size());

    int i = 0;
    for (CoreLabel cl : yield) {
      assertEquals("Wrong token", tokens[i], cl.word());
      assertEquals("Wrong char begin", begins[i], (int) cl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      assertEquals("Wrong char end", ends[i], (int) cl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      i++;
    }
  }
}

