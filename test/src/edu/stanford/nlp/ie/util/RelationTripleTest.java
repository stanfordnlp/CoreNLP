package edu.stanford.nlp.ie.util;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A test of various functions in {@link RelationTriple}.
 *
 * @author Gabor Angeli
 */
public class RelationTripleTest extends TestCase {

  protected CoreLabel mkWord(String gloss, int index) {
    CoreLabel w = new CoreLabel();
    w.setWord(gloss);
    w.setValue(gloss);
    if (index >= 0) {
      w.setIndex(index);
    }
    return w;
  }

  /**
   * Create a relation from a CoNLL format like:
   * <pre>
   *   word_index  word  parent_index  incoming_relation
   * </pre>
   */
  protected Optional<RelationTriple> mkExtraction(String conll) {
    List<CoreLabel> sentence = new ArrayList<>();
    SemanticGraph tree = new SemanticGraph();
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      int index = Integer.parseInt(fields[0]);
      String word = fields[1];
      CoreLabel label = mkWord(word, index);
      sentence.add(label);
      if (fields[2].equals("0")) {
        tree.addRoot(new IndexedWord(label));
      } else {
        tree.addVertex(new IndexedWord(label));
      }
    }
    int i = 0;
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      int parent = Integer.parseInt(fields[2]);
      String reln = fields[3];
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, reln),
            1.0, false
        );
      }
      i += 1;
    }
    return RelationTriple.segment(tree);
  }

  protected RelationTriple blueCatsPlayWithYarnNoIndices() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(mkWord("blue", -1));
    sentence.add(mkWord("cats", -1));
    sentence.add(mkWord("play", -1));
    sentence.add(mkWord("with", -1));
    sentence.add(mkWord("yarn", -1));
    return new RelationTriple(sentence.subList(0, 2), sentence.subList(2, 4), sentence.subList(4, 5));
  }

  protected RelationTriple blueCatsPlayWithYarn() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(mkWord("blue", 0));
    sentence.add(mkWord("cats", 1));
    sentence.add(mkWord("play", 2));
    sentence.add(mkWord("with", 3));
    sentence.add(mkWord("yarn", 4));
    return new RelationTriple(sentence.subList(0, 2), sentence.subList(2, 4), sentence.subList(4, 5));
  }

  protected RelationTriple yarnBlueCatsPlayWith() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(mkWord("yarn", 0));
    sentence.add(mkWord("blue", 1));
    sentence.add(mkWord("cats", 2));
    sentence.add(mkWord("play", 3));
    sentence.add(mkWord("with", 4));
    return new RelationTriple(sentence.subList(1, 3), sentence.subList(3, 5), sentence.subList(0, 1));
  }

  public void testToSentenceNoIndices() {
    assertEquals(new ArrayList<CoreLabel>(){{
      add(mkWord("blue", -1));
      add(mkWord("cats", -1));
      add(mkWord("play", -1));
      add(mkWord("with", -1));
      add(mkWord("yarn", -1));
    }}, blueCatsPlayWithYarnNoIndices().asSentence());
  }

  public void testToSentenceInOrder() {
    assertEquals(new ArrayList<CoreLabel>(){{
      add(mkWord("blue", 0));
      add(mkWord("cats", 1));
      add(mkWord("play", 2));
      add(mkWord("with", 3));
      add(mkWord("yarn", 4));
    }}, blueCatsPlayWithYarn().asSentence());
  }

  public void testToSentenceOutOfOrder() {
    assertEquals(new ArrayList<CoreLabel>(){{
      add(mkWord("yarn", 0));
      add(mkWord("blue", 1));
      add(mkWord("cats", 2));
      add(mkWord("play", 3));
      add(mkWord("with", 4));
    }}, yarnBlueCatsPlayWith().asSentence());
  }

  public void testSameSemanticsForDifferentWordOrder() {
    assertEquals(blueCatsPlayWithYarn().toString(), yarnBlueCatsPlayWith().toString());
    assertEquals("blue cats\tplay with\tyarn", blueCatsPlayWithYarn().toString());
    assertEquals("blue cats\tplay with\tyarn", yarnBlueCatsPlayWith().toString());
  }

  public void testGlosses() {
    assertEquals("blue cats", blueCatsPlayWithYarn().subjectGloss());
    assertEquals("play with", blueCatsPlayWithYarn().relationGloss());
    assertEquals("yarn", blueCatsPlayWithYarn().objectGloss());
  }

  public void testBlueCatsPlayWithYarn() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tblue\t2\tamod\n" +
        "2\tcats\t3\tnsubj\n" +
        "3\tplay\t0\troot\n" +
        "4\twith\t3\tprep\n" +
        "5\tyarn\t4\tpobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("blue cats\tplay with\tyarn", extraction.get().toString());
  }

  public void testBlueCatsPlayQuietlyWithYarn() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tblue\t2\tamod\n" +
            "2\tcats\t3\tnsubj\n" +
            "3\tplay\t0\troot\n" +
            "4\tquietly\t3\tadvmod\n" +
            "5\twith\t3\tprep\n" +
            "6\tyarn\t5\tpobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("blue cats\tplay quietly with\tyarn", extraction.get().toString());
  }

  public void testCatsHaveTails() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tcats\t2\tnsubj\n" +
            "2\thave\t0\troot\n" +
            "3\ttails\t2\tdobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("cats\thave\ttails", extraction.get().toString());
  }

  public void testFishLikeToSwim() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tfish\t2\tnsubj\n" +
            "2\tlike\t0\troot\n" +
            "3\tto\t4\taux\n" +
            "4\tswim\t2\txcomp\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("fish\tlike\tto swim", extraction.get().toString());
  }

  public void testCatsAreCute() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tcats\t3\tnsubj\n" +
            "2\tare\t3\tcop\n" +
            "3\tcute\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("cats\tare\tcute", extraction.get().toString());
  }

  public void testHeWasInaugurated() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t3\tnsubjpass\n" +
            "2\twas\t3\tauxpass\n" +
            "3\tinaugurated\t0\troot\n" +
            "5\tpresident\t3\tprep_as\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("he\twas inaugurated as\tpresident", extraction.get().toString());
  }

  public void testPPAttachment() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t2\tnsubj\n" +
            "2\tserved\t0\troot\n" +
            "3\tpresident\t2\tprep_as\n" +
            "4\tHarvard\t6\taux\n" +
            "5\tLaw\t6\taux\n" +
            "6\tReview\t3\tprep_of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("he\tserved as\tpresident of Harvard Law Review", extraction.get().toString());
  }

  public void testPPAttachmentTwo() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t4\tnsubj\n" +
            "2\twas\t4\tcop\n" +
            "3\tcommunity\t4\tnn\n" +
            "4\torganizer\t0\troot\n" +
            "6\tChicago\t4\tprep_in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("he\twas\tcommunity organizer in Chicago", extraction.get().toString());
  }

  public void testXComp() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t3\tnsubjpass\n" +
        "2\twas\t3\tauxpass\n" +
        "3\tnamed\t0\troot\n" +
        "4\t2009\t8\tnum\n" +
        "5\tNobel\t8\tnn\n" +
        "6\tPeace\t8\tnn\n" +
        "7\tPrize\t8\tnn\n" +
        "8\tLaureate\t3\txcomp\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("Obama\twas named\t2009 Nobel Peace Prize Laureate", extraction.get().toString());
  }
}
