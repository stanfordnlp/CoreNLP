package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.IETestUtils;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A test of various functions in {@link edu.stanford.nlp.ie.util.RelationTriple}.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("AssertEqualsBetweenInconvertibleTypes")
public class RelationTripleSegmenterTest extends TestCase {

  private static Optional<RelationTriple> mkExtraction(String conll) {
    return mkExtraction(conll, 0, false);
  }

  private static Optional<RelationTriple> mkExtraction(String conll, boolean allNominals) {
    return mkExtraction(conll, 0, allNominals);
  }

  private static Optional<RelationTriple> mkExtraction(String conll, int listIndex) {
    return mkExtraction(conll, listIndex, false);
  }

  /**
   * Parse a CoNLL formatted tree into a SemanticGraph object (along with a list of tokens).
   *
   * @param conll The CoNLL formatted tree.
   *
   * @return A pair of a SemanticGraph and a token list, corresponding to the parse of the sentence
   *         and to tokens in the sentence.
   */
  private static Pair<SemanticGraph, List<CoreLabel>> mkTree(String conll) {
    List<CoreLabel> sentence = new ArrayList<>();
    SemanticGraph tree = new SemanticGraph();
    for (String line : conll.split("\n")) {
      if (line.trim().isEmpty()) { continue; }
      String[] fields = line.trim().split("\\s+");
      int index = Integer.parseInt(fields[0]);
      String word = fields[1];
      CoreLabel label = IETestUtils.mkWord(word, index);
      sentence.add(label);
      if (fields[2].equals("0")) {
        tree.addRoot(new IndexedWord(label));
      } else {
        tree.addVertex(new IndexedWord(label));
      }
      if (fields.length > 4) {
        label.setTag(fields[4]);
      }
      if (fields.length > 5) {
        label.setNER(fields[5]);
      }
      if (fields.length > 6) {
        label.setLemma(fields[6]);
      }
    }
    int i = 0;
    for (String line : conll.split("\n")) {
      if (line.trim().isEmpty()) { continue; }
      String[] fields = line.trim().split("\\s+");
      int parent = Integer.parseInt(fields[2]);
      String reln = fields[3];
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            new GrammaticalRelation(Language.UniversalEnglish, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }

    return Pair.makePair(tree, sentence);
  }

  /**
   * Create a relation from a CoNLL format like:
   * <pre>
   *   word_index  word  parent_index  incoming_relation
   * </pre>
   */
  private static Optional<RelationTriple> mkExtraction(String conll, int listIndex, boolean allNominals) {
    Pair<SemanticGraph, List<CoreLabel>> info = mkTree(conll);
    SemanticGraph tree = info.first;
    List<CoreLabel> sentence = info.second;
    // Run extractor
    Optional<RelationTriple> segmented = new RelationTripleSegmenter(allNominals).segment(tree, Optional.empty());
    if (segmented.isPresent() && listIndex == 0) {
      return segmented;
    }
    List<RelationTriple> extracted = new RelationTripleSegmenter(allNominals).extract(tree, sentence);
    if (extracted.size() > listIndex) {
      return Optional.of(extracted.get(listIndex - (segmented.isPresent() ? 1 : 0)));
    }
    return Optional.empty();
  }

  private static RelationTriple blueCatsPlayWithYarnNoIndices() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(IETestUtils.mkWord("blue", -1));
    sentence.add(IETestUtils.mkWord("cats", -1));
    sentence.add(IETestUtils.mkWord("play", -1));
    sentence.add(IETestUtils.mkWord("with", -1));
    sentence.add(IETestUtils.mkWord("yarn", -1));
    return new RelationTriple(sentence.subList(0, 2), sentence.subList(2, 4), sentence.subList(4, 5));
  }

  private static RelationTriple blueCatsPlayWithYarn() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(IETestUtils.mkWord("blue", 0));
    sentence.add(IETestUtils.mkWord("cats", 1));
    sentence.add(IETestUtils.mkWord("play", 2));
    sentence.add(IETestUtils.mkWord("with", 3));
    sentence.add(IETestUtils.mkWord("yarn", 4));
    return new RelationTriple(sentence.subList(0, 2), sentence.subList(2, 4), sentence.subList(4, 5));
  }

  private static RelationTriple yarnBlueCatsPlayWith() {
    List<CoreLabel> sentence = new ArrayList<>();
    sentence.add(IETestUtils.mkWord("yarn", 0));
    sentence.add(IETestUtils.mkWord("blue", 1));
    sentence.add(IETestUtils.mkWord("cats", 2));
    sentence.add(IETestUtils.mkWord("play", 3));
    sentence.add(IETestUtils.mkWord("with", 4));
    return new RelationTriple(sentence.subList(1, 3), sentence.subList(3, 5), sentence.subList(0, 1));
  }



  public void testToSentenceNoIndices() {
    assertEquals(new ArrayList<CoreLabel>() {{
      add(IETestUtils.mkWord("blue", -1));
      add(IETestUtils.mkWord("cats", -1));
      add(IETestUtils.mkWord("play", -1));
      add(IETestUtils.mkWord("with", -1));
      add(IETestUtils.mkWord("yarn", -1));
    }}, blueCatsPlayWithYarnNoIndices().asSentence());
  }

  public void testToSentenceInOrder() {
    assertEquals(new ArrayList<CoreLabel>(){{
      add(IETestUtils.mkWord("blue", 0));
      add(IETestUtils.mkWord("cats", 1));
      add(IETestUtils.mkWord("play", 2));
      add(IETestUtils.mkWord("with", 3));
      add(IETestUtils.mkWord("yarn", 4));
    }}, blueCatsPlayWithYarn().asSentence());
  }

  public void testToSentenceOutOfOrder() {
    assertEquals(new ArrayList<CoreLabel>(){{
      add(IETestUtils.mkWord("yarn", 0));
      add(IETestUtils.mkWord("blue", 1));
      add(IETestUtils.mkWord("cats", 2));
      add(IETestUtils.mkWord("play", 3));
      add(IETestUtils.mkWord("with", 4));
    }}, yarnBlueCatsPlayWith().asSentence());
  }

  public void testSameSemanticsForDifferentWordOrder() {
    assertEquals(blueCatsPlayWithYarn().toString(), yarnBlueCatsPlayWith().toString());
    assertEquals("1.0\tblue cats\tplay with\tyarn", blueCatsPlayWithYarn().toString());
    assertEquals("1.0\tblue cats\tplay with\tyarn", yarnBlueCatsPlayWith().toString());
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
        "4\twith\t5\tcase\n" +
        "5\tyarn\t3\tobl:with\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tblue cats\tplay with\tyarn", extraction.get().toString());
  }

  public void testBlueCatsPlayQuietlyWithYarn() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tblue\t2\tamod\n" +
            "2\tcats\t3\tnsubj\n" +
            "3\tplay\t0\troot\n" +
            "4\tquietly\t3\tadvmod\n" +
            "5\twith\t6\tcase\n" +
            "6\tyarn\t3\tobl:with\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tblue cats\tplay quietly with\tyarn", extraction.get().toString());
  }

  public void testCatsHaveTails() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tcats\t2\tnsubj\n" +
            "2\thave\t0\troot\n" +
            "3\ttails\t2\tobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tcats\thave\ttails", extraction.get().toString());
  }

  public void testrabbitsEatVegetables() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\trabbits\t2\tnsubj\n" +
        "2\teat\t0\troot\n" +
        "3\tvegetables\t2\tobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\trabbits\teat\tvegetables", extraction.get().toString());
  }

  public void testFishLikeToSwim() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tfish\t2\tnsubj\n" +
            "2\tlike\t0\troot\n" +
            "3\tto\t4\tmark\n" +
            "4\tswim\t2\txcomp\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfish\tlike\tto swim", extraction.get().toString());
  }

  public void testFishLikeToSwimAlternateParse() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tfish\t2\tnsubj\n" +
        "2\tlike\t0\troot\n" +
        "3\tto\t4\tcase\n" +
        "4\tswim\t2\tnmod:to\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfish\tlike to\tswim", extraction.get().toString());
  }

  public void testMyCatsPlayWithYarn() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tmy\t2\tnmod:poss\n" +
        "2\tcats\t3\tnsubj\n" +
        "3\tplay\t0\troot\n" +
        "4\twith\t5\tcase\n" +
        "5\tyarn\t3\tobl:with\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tmy cats\tplay with\tyarn", extraction.get().toString());
  }

  public void testCatsAreCute() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tcats\t3\tnsubj\n" +
            "2\tare\t3\tcop\n" +
            "3\tcute\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tcats\tare\tcute", extraction.get().toString());
  }

  public void testIAmInFlorida() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tI\t4\tnsubj\n" +
        "2\tam\t4\tcop\n" +
        "3\tin\t4\tcase\n" +
        "4\tFlorida\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tI\tam in\tFlorida", extraction.get().toString());  // not (I; am; Florida)
  }

  public void testWh() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\twhat\t3\tnsubj\tWP\n" +
        "2\tis\t3\tcop\tVBZ\n" +
        "3\tlove\t0\troot\tNN\n"
    );
    assertFalse("Extracted on WH word!", extraction.isPresent());
  }

  public void testPropagateCSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\ttruffles\t2\tnsubj\n" +
        "2\tpicked\t4\tcsubj\n" +
        "3\tare\t4\tcop\n" +
        "4\ttasty\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\ttruffles picked\tare\ttasty", extraction.get().toString());
  }

  public void testHeWasInaugurated() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t3\tnsubj:pass\n" +
            "2\twas\t3\taux:pass\n" +
            "3\tinaugurated\t0\troot\n" +
            "4\tas\t5\tcase\n" +
            "5\tpresident\t3\tobl:as\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\twas inaugurated as\tpresident", extraction.get().toString());
  }

  public void testPPAttachment() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t2\tnsubj\n" +
            "2\tserved\t0\troot\n" +
            "3\tas\t4\tcase\n" +
            "4\tpresident\t2\tobl:as\n" +
            "5\tof\t8\tcase\n" +
            "6\tHarvard\t8\tcompound\n" +
            "7\tLaw\t8\tcompound\n" +
            "8\tReview\t4\tnmod:of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\tserved as\tpresident of Harvard Law Review", extraction.get().toString());
  }

  public void testPPAttachmentTwo() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t4\tnsubj\n" +
            "2\twas\t4\tcop\n" +
            "3\tcommunity\t4\tcompound\n" +
            "4\torganizer\t0\troot\n" +
            "5\tin\t6\tcase\n" +
            "6\tChicago\t4\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\twas\tcommunity organizer in Chicago", extraction.get().toString());
  }

  public void testXComp() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t3\tnsubj:pass\n" +
        "2\twas\t3\taux:pass\n" +
        "3\tnamed\t0\troot\n" +
        "4\t2009\t8\tnummod\n" +
        "5\tNobel\t8\tcompound\n" +
        "6\tPeace\t8\tcompound\n" +
        "7\tPrize\t8\tcompound\n" +
        "8\tLaureate\t3\txcomp\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\twas named\t2009 Nobel Peace Prize Laureate", extraction.get().toString());
  }

  public void testPassiveNSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tHRE\t3\tnsubj:pass\n" +
        "2\twas\t3\taux:pass\n" +
        "3\tfounded\t0\troot\n" +
        "4\tin\t5\tcase\n" +
        "5\t1991\t3\tobl:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\twas founded in\t1991", extraction.get().toString());

    extraction = mkExtraction(
        "1\tfounded\t0\troot\n" +
        "2\tHRE\t1\tnsubj:pass\n" +
        "3\tin\t4\tcase\n" +
        "4\t2003\t1\tobl:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\tfounded in\t2003", extraction.get().toString());
  }

  public void testPossessive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnicredit\t5\tnmod:poss\tNNP\tORGANIZATION\n" +
        "2\t's\t1\tcase\tPOS\tO\n" +
        "3\tBank\t5\tcompound\tNNP\tORGANIZATION\n" +
        "4\tAustria\t5\tcompound\tNNP\tORGANIZATION\n" +
        "5\tCreditanstalt\t0\troot\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tUnicredit\thas\tBank Austria Creditanstalt", extraction.get().toString());
  }

  public void testPossessiveNoNER() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tIBM\t4\tnmod:poss\tNNP\tORGANIZATION\n" +
        "2\t's\t1\tcase\tPOS\tO\n" +
        "3\tresearch\t4\tcompound\tNN\tO\n" +
        "4\tgroup\t0\troot\tNN\tO\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tIBM\thas\tresearch group", extraction.get().toString());
  }

  public void testPossessiveWithObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTim\t3\tnmod:poss\n" +
        "2\t's\t1\tcase\n" +
        "3\tfather\t0\troot\n" +
        "4\tTom\t3\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tTim\t's father is\tTom", extraction.get().toString());
  }

  public void testApposInObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tNewspaper\t2\tnsubj\n" +
        "2\tpublished\t0\troot\n" +
        "3\tin\t4\tcase\n" +
        "4\tTucson\t2\tobl:in\n" +
        "5\tArizona\t4\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tNewspaper\tpublished in\tArizona", extraction.get().toString());
  }

  public void testApposAsSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tDurin\t0\troot\tNNP\n" +
        "2\tson\t1\tappos\tNN\n" +
        "3\tof\t4\tcase\tIN\n" +
        "4\tThorin\t2\tnmod:of\tNNP\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tDurin\tson of\tThorin", extraction.get().toString());
  }

  public void testReflexive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTom\t5\tnsubj\n" +
        "2\tand\t3\tcc\n" +
        "3\tJerry\t1\tconj:and\n" +
        "4\twere\t5\taux\n" +
        "5\tfighting\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tTom\tfighting\tJerry", extraction.get().toString());
  }

  public void testPassiveReflexive() {
    // same test as testReflexive, but use passive voice
    // (even if it's not exactly appropriate for this parse)
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTom\t5\tnsubj:pass\n" +
        "2\tand\t3\tcc\n" +
        "3\tJerry\t1\tconj:and\n" +
        "4\twere\t5\taux:pass\n" +
        "5\tfighting\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tTom\tfighting\tJerry", extraction.get().toString());
  }

  public void testPossessiveInEntity() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tScania-Vabis\t2\tnsubj\n" +
        "2\testablished\t0\troot\n" +
        "3\tits\t6\tnmod:poss\n" +
        "4\tfirst\t6\tamod\n" +
        "5\tproduction\t6\tcompound\n" +
        "6\tplant\t2\tdobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tScania-Vabis\testablished\tits first production plant", extraction.get().toString());
  }

  public void testOfWhich() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tof\t2\tcase\n" +
        "2\twhich\t5\tnmod:of\n" +
        "3\tBono\t5\tnsubj\n" +
        "4\tis\t5\tcop\n" +
        "5\tco-founder\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBono\tis co-founder of\twhich", extraction.get().toString());
  }

  public void testObjInRelation() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tScania-Vabis\t2\tnsubj\tNNP\tORGANIZATION\n" +
        "2\testablished\t0\troot\tVB\tO\n" +
        "3\tproduction\t4\tcompound\tNN\tO\n" +
        "4\tplant\t2\tobj\tNN\tO\n" +
        "5\toutside\t6\tcase\tIN\tO\n" +
        "6\tSödertälje\t2\tnmod:outside\tNN\tO\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tScania-Vabis\testablished production plant outside\tSödertälje", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tcompound\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tobj\tNN\tO\n" +
        "5\twith\t6\tcase\tIN\tO\n" +
        "6\tShinawatra\t3\tnmod:with\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHun Sen\tplayed golf with\tShinawatra", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tcompound\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tobj\tNN\tO\n" +
        "5\tShinawatra\t3\tnmod:with\tNNP\tPERSON\n" +
        "6\tCambodia\t3\tdobj\tNNP\tLOCATION\n"
    );
    assertFalse("Should not have found extraction for sentence! Incorrectly found: " + extraction.orElse(null), extraction.isPresent());
  }

  public void testVBG() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tfoal\t3\tnsubj\n" +
        "2\tbe\t3\taux\n" +
        "3\tstanding\t0\troot\n" +
        "4\tnext\t3\tadvmod\t\n" +
        "5\tto\t6\tcase\n" +
        "6\thorse\t3\tnmod:to\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfoal\tbe standing next to\thorse", extraction.get().toString());
  }

  public void testVBGCollapsed() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tfoal\t3\tnsubj\n" +
        "2\tbe\t3\taux\n" +
        "3\tstanding\t0\troot\n" +
        "4\tnext\t6\tcase\t\n" +
        "5\tto\t4\tmwe\n" +
        "6\thorse\t3\tnmod:next_to\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfoal\tbe standing next to\thorse", extraction.get().toString());
  }

  public void testThereAreIn() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tthere\t2\texpl\n" +
        "2\tare\t0\troot\tVBP\tO\tbe\n" +
        "3\tdogs\t2\tnsubj\tNN\n" +
        "4\tin\t5\tcase\tNN\n" +
        "5\theaven\t3\tnmod:in\tNN\n",
    true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tdogs\tis in\theaven", extraction.get().toString());
  }

  public void testThereAreWith() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tthere\t2\texpl\tEX\n" +
        "2\tare\t0\troot\tVBP\tO\tbe\n" +
        "3\tcats\t2\tnsubj\tNN\n" +
        "4\twith\t5\tcase\tIN\n" +
        "5\ttails\t3\tnmod:with\tNN\n",
        true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tcats\tis with\ttails", extraction.get().toString());
  }

  public void testThereAreVBing() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tthere\t2\texpl\n" +
        "2\tare\t0\troot\tVBP\tO\tbe\n" +
        "3\tdogs\t2\tnsubj\n" +
        "4\tsitting\t3\tacl\n" +
        "5\tin\t6\tcase\tNN\n" +
        "6\theaven\t4\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tdogs\tsitting in\theaven", extraction.get().toString());
  }

  public void testDogsInheaven() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tdogs\t0\troot\tNN\n" +
        "2\tin\t3\tcase\tNN\n" +
        "3\theaven\t1\tnmod:in\tNN\n",
    true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tdogs\tis in\theaven", extraction.get().toString());

    extraction = mkExtraction(
        "1\tdogs\t0\troot\tNN\n" +
        "2\tin\t3\tcase\tNN\n" +
        "3\theaven\t1\tnmod:of\tNN\n",
    true);
    assertFalse(extraction.isPresent());
  }

  public void testAdvObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\thorses\t3\tnsubj\n" +
        "2\tare\t3\tcop\n" +
        "3\tgrazing\t0\troot\n" +
        "4\tpeacefully\t3\tadvmod\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\thorses\tare\tgrazing peacefully", extraction.get().toString());
  }


  public void testAdvObjectPassive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tthings\t3\tnsubj:pass\n" +
        "2\tare\t3\taux:pass\n" +
        "3\tarranged\t0\troot\n" +
        "4\tneatly\t3\tadvmod\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tthings\tare\tarranged neatly", extraction.get().toString());
  }

  public void testObamaBornInRegression() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t2\tnsubj\n" +
        "2\tBorn\t0\troot\n" +
        "3\tin\t4\tcase\n" +
        "4\tHonolulu\t2\tnmod:in\n" +
        "5\tHawaii\t4\tcompound\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tBorn in\tHonolulu Hawaii", extraction.get().toString());
  }

  public void testObamaPresidentOfRegression() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t3\tnsubj\n" +
        "2\tis\t3\tcop\n" +
        "3\tpresident\t0\troot\n" +
        "4\tof\t5\tcase\n" +
        "5\tUS\t3\tnmod:of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis president of\tUS", extraction.get().toString());
  }

  public void testObamaPresidentOfRegressionFull() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t6\tnsubj\n" +
        "2\tis\t6\tcop\n" +
        "3\t44th\t6\tamod\n" +
        "4\tand\t5\tcc\n" +
        "5\tcurrent\t3\tconj:and\n" +
        "6\tpresident\t0\troot\n" +
        "7\tof\t8\tcase\n" +
        "8\tUS\t6\tnmod:of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis 44th and current president of\tUS", extraction.get().toString());
  }

  public void testGeorgeBoydRegression() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tGeorge\t2\tcompound\n" +
        "2\tBoyd\t4\tnsubj\n" +
        "3\thas\t4\taux\n" +
        "4\tjoined\t0\troot\n" +
        "5\tNottingham\t6\tcompound\n" +
        "6\tForest\t4\tdobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tGeorge Boyd\thas joined\tNottingham Forest", extraction.get().toString());
  }

  public void testUSPresidentObama1() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnited\t5\tcompound\tNNP\tORGANIZATION\n" +
        "2\tStates\t5\tcompound\tNNP\tORGANIZATION\n" +
        "3\tpresident\t5\tcompound\tNNP\tO\n" +
        "4\tBarack\t5\tcompound\tNNP\tPERSON\n" +
        "5\tObama\t0\troot\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBarack Obama\tis president of\tUnited States", extraction.get().toString());
  }

  public void testUSPresidentObama2() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnited\t5\tcompound\tNNP\tORGANIZATION\n" +
        "2\tStates\t5\tcompound\tNNP\tORGANIZATION\n" +
        "3\tpresident\t5\tcompound\tNNP\tTITLE\n" +
        "4\tBarack\t5\tcompound\tNNP\tPERSON\n" +
        "5\tObama\t0\troot\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBarack Obama\tis president of\tUnited States", extraction.get().toString());
  }

  public void testUSAllyBritain() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnited\t4\tcompound\tNNP\tLOCATION\n" +
        "2\tStates\t4\tcompound\tNNP\tLOCATION\n" +
        "3\tally\t4\tcompound\tNN\tO\n" +
        "4\tBritain\t0\troot\tNNP\tLOCATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBritain\tis ally of\tUnited States", extraction.get().toString());
  }

  public void testUSPresidentObama() {
    String conll =
        "1\tUnited\t2\tcompound\tNNP\tLOCATION\n" +
        "2\tStates\t4\tnmod:poss\tNNP\tLOCATION\n" +
        "3\t's\t2\tcase\tPOS\tO\n" +
        "4\tpresident\t0\troot\tNN\tO\n" +
        "5\tObama\t2\tappos\tNNP\tPERSON\n";
    Optional<RelationTriple> extraction = mkExtraction(conll, 0);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tUnited States\thas\tpresident", extraction.get().toString());
    extraction = mkExtraction(conll, 1);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis president of\tUnited States", extraction.get().toString());
  }

  public void testUSsAllyBritain() {
    String conll =
        "1\tUnited\t2\tcompound\tNNP\tLOCATION\n" +
        "2\tStates\t4\tnmod:poss\tNNP\tLOCATION\n" +
        "3\t's\t2\tcase\tPOS\tO\n" +
        "4\tally\t0\troot\tNN\tO\n" +
        "5\tBritain\t2\tappos\tNNP\tPERSON\n";
    Optional<RelationTriple> extraction = mkExtraction(conll, 0);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tUnited States\thas\tally", extraction.get().toString());
     extraction = mkExtraction(conll, 1);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBritain\tis ally of\tUnited States", extraction.get().toString());
  }

  public void testPresidentObama() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tPresident\t2\tcompound\tPOS\tTITLE\n" +
        "2\tObama\t0\troot\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis\tPresident", extraction.get().toString());
  }

  public void testAmericanActorChrisPratt() {
    String conll =
        "1\tAmerican\t4\tamod\tNN\tLOCATION\n" +
        "2\tactor\t4\tcompound\tNN\tTITLE\n" +
        "3\tChris\t4\tcompound\tNNP\tPERSON\n" +
        "4\tPratt\t0\troot\tNNP\tPERSON\n";
    Optional<RelationTriple> extraction = mkExtraction(conll, 0);
    assertTrue("No first extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Pratt\tis actor of\tAmerican", extraction.get().toString());
    extraction = mkExtraction(conll, 1);
    assertTrue("No second extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Pratt\tis\tAmerican", extraction.get().toString());
    extraction = mkExtraction(conll, 2);
    assertTrue("No third extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Pratt\tis\tactor", extraction.get().toString());
  }

  public void testChrisManningOfStanford() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tChris\t2\tcompound\tNNP\tPERSON\n" +
        "2\tManning\t0\troot\tNNP\tPERSON\n" +
        "3\tof\t4\tcase\tIN\tO\n" +
        "4\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testChrisManningOfStanfordLong() {
    String conll =
        "1\tChris\t2\tcompound\tNNP\tPERSON\n" +
        "2\tManning\t5\tnsubj\tNNP\tPERSON\n" +
        "3\tof\t4\tcase\tIN\tO\n" +
        "4\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n" +
        "5\tvisited\t0\troot\tVBD\tO\n" +
        "6\tChina\t5\tdobj\tNNP\tLOCATION\n";
    Optional<RelationTriple> extraction = mkExtraction(conll);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testChrisIsOfStanford() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tChris\t2\tcompound\tNNP\tPERSON\n" +
        "2\tManning\t0\troot\tNNP\tPERSON\n" +
        "3\tof\t4\tcase\tIN\tO\n" +
        "4\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testPPExtraction() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t0\troot\tNNP\tPERSON\n" +
        "2\tin\t3\tcase\tIN\tO\n" +
        "3\tTucson\t1\tnmod:in\tNNP\tLOCATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis in\tTucson", extraction.get().toString());

    extraction = mkExtraction(
        "1\tPietro\t2\tcompound\tNNP\tPERSON\n" +
        "2\tBadoglio\t0\troot\tNNP\tPERSON\n" +
        "3\tin\t5\tcase\tIN\tO\n" +
        "4\tsouthern\t5\tamod\tJJ\tO\n" +
        "5\tItaly\t2\tnmod:in\tNN\tLOCATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tPietro Badoglio\tis in\tItaly", extraction.get().toString());
  }

  public void testCommaDoesntOvergenerate() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tHonolulu\t3\tcompound\tNNP\tLOCATION\n" +
        "2\t,\t1\tpunct\t.\tO\n" +
        "3\tHawaii\t0\troot\tNNP\tLOCATION\n"
    );
    assertFalse("Found extraction when we shouldn't have! Extraction: " + (extraction.isPresent() ? extraction.get() : ""), extraction.isPresent());

    extraction = mkExtraction(
        "1\tHonolulu\t3\tcompound\tNNP\tLOCATION\n" +
        "2\t,\t1\tpunct\t.\tO\n" +
        "3\tHawaii\t0\troot\tNNP\tLOCATION\n" +
        "4\t,\t3\tpunct\t.\tO\n"
    );
    assertFalse("Found extraction when we shouldn't have! Extraction: " + (extraction.isPresent() ? extraction.get() : ""), extraction.isPresent());
  }

  public void testCompoundPossessive() {
    String conll =
        "1\tIBM\t4\tnmod:poss\tNNP\tORGANIZATION\n" +
        "2\t's\t1\tcase\tPOS\tO\n" +
        "3\tCEO\t4\tcompound\tNNP\tTITLE\n" +
        "4\tRometty\t0\troot\tNNP\tORGANIZATION\n";
    Optional<RelationTriple> extraction = mkExtraction(conll, 1);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tRometty\tis\tCEO", extraction.get().toString());
    extraction = mkExtraction(conll, 0);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tIBM\thas\tRometty", extraction.get().toString());
    extraction = mkExtraction(conll, 2);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tRometty\tis CEO of\tIBM", extraction.get().toString());
  }

  public void testAllNominals() {
    String conll =
        "1\tfierce\t2\tamod\tJJ\n" +
        "2\tlions\t0\troot\tNN\n" +
        "3\tin\t4\tcase\tIN\n" +
        "4\tNarnia\t2\tnmod:in\tNNP\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, 0, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfierce lions\tis in\tNarnia", extraction.get().toString());
    extraction = mkExtraction(conll, 1, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tlions\tis\tfierce", extraction.get().toString());
    // Negative case
    extraction = mkExtraction(conll, 0, false);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfierce lions\tis in\tNarnia", extraction.get().toString());
    assertFalse(mkExtraction(conll, 1, false).isPresent());
  }

  public void testAcl() {
    String conll =
        "1\tman\t0\troot\tNN\n" +
        "2\tsitting\t1\tacl\tVBG\n" +
        "3\tin\t4\tcase\tIN\n" +
        "4\ttree\t2\tnmod:in\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tman\tsitting in\ttree", extraction.get().toString());
  }

  public void testAclWithAdverb() {
    String conll =
        "1\tman\t0\troot\tNN\n" +
        "2\tsitting\t1\tacl\tVBG\n" +
        "3\tvery\t2\tadvmod\tRB\n" +
        "4\tquietly\t2\tadvmod\tRB\n" +
        "5\tin\t6\tcase\tIN\n" +
        "6\ttree\t2\tnmod:in\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tman\tsitting very quietly in\ttree", extraction.get().toString());
  }

  public void testAclNoPP() {
    String conll =
        "1\tman\t0\troot\tNN\n" +
        "2\triding\t1\tacl\tVBG\n" +
        "3\thorse\t2\tdobj\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tman\triding\thorse", extraction.get().toString());
  }

  public void testAclWithPP() {
    String conll =
        "1\tweeds\t0\troot\tNN\n" +
        "2\tgrowing\t1\tacl\tVBG\n" +
        "3\taround\t4\tcase\tIN\n" +
        "4\tplant\t2\tnmod:around\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tweeds\tgrowing around\tplant", extraction.get().toString());
  }

  public void testNmodTmod() {
    String conll =
        "1\tFriday\t3\tnmod:tmod\tNN\n" +
        "2\tI\t3\tnsubj\tPR\n" +
        "3\tmake\t0\troot\tVB\n" +
        "4\ttea\t3\tobj\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tI\tmake tea at_time\tFriday", extraction.get().toString());
  }

  public void testVPOnlyReplacedWith() {
    String conll =
        "1\treplaced\t0\tconj:and\tVBD\n" +
        "2\twith\t5\tcase\tIN\n" +
        "3\ta\t5\tdet\tDT\n" +
        "4\tdifferent\t5\tamod\tJJ\n" +
        "5\ttype\t1\tnmod:with\tNN\n" +
        "6\tof\t7\tcase\tIN\n" +
        "7\tfilter\t5\tnmod:of\tNN\n";
    // Positive case
    boolean matches = false;
    SemanticGraph tree = mkTree(conll).first;
    for (SemgrexPattern candidate : new RelationTripleSegmenter().VP_PATTERNS) {
      if (candidate.matcher(tree).matches()) {
        matches = true;
      }
    }
    assertTrue(matches);
  }


  public void testThrowAway() {
    String conll =
        "1\tI\t2\tnsubj\tPRP\n" +
        "2\tthrow\t0\troot\tVB\n" +
        "3\taway\t2\tcompound:ptr\tRP\n" +
        "4\tmy\t5\tnmod:poss\tPRP$\n" +
        "5\tlaptop\t2\tdobj\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tI\tthrow away\tmy laptop", extraction.get().toString());
  }

  public void testMassOfIron() {
    String conll =
       "1\tmass\t5\tnsubj\tNN\n" +
       "2\tof\t3\tcase\tIN\n" +
       "3\tiron\t1\tnmod:of\tNN\n" +
       "4\tis\t5\tcop\tVBZ\n" +
       "5\t55amu\t0\troot\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tiron\tmass of is\t55amu", extraction.get().toString());
  }

  public void testStateOfTheUnion() {
    String conll =
        "1\tState\t5\tnsubj\tNNP\n" +
        "2\tof\t3\tcase\tIN\n" +
        "3\tUnion\t1\tnmod:of\tNNP\n" +
        "4\tis\t5\tcop\tVBZ\n" +
        "5\ttomorrow\t0\troot\tNN\n";
    // Positive case
    Optional<RelationTriple> extraction = mkExtraction(conll, true);
    assertFalse("Extraction found when we shouldn't have: " + extraction, extraction.isPresent());
  }

}
