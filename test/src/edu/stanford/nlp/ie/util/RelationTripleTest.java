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

  protected Optional<RelationTriple> mkExtraction(String conll) {
    return mkExtraction(conll, 0);
  }

  /**
   * Create a relation from a CoNLL format like:
   * <pre>
   *   word_index  word  parent_index  incoming_relation
   * </pre>
   */
  protected Optional<RelationTriple> mkExtraction(String conll, int listIndex) {
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
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      int parent = Integer.parseInt(fields[2]);
      String reln = fields[3];
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            new GrammaticalRelation(GrammaticalRelation.Language.UniversalEnglish, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }
    // Run extractor
    Optional<RelationTriple> segmented = RelationTriple.segment(tree, Optional.empty());
    if (segmented.isPresent() && listIndex == 0) {
      return segmented;
    }
    List<RelationTriple> extracted = RelationTriple.extract(tree, sentence);
    if (extracted.size() > listIndex) {
      return Optional.of(extracted.get(listIndex - (segmented.isPresent() ? 1 : 0)));
    }
    return Optional.empty();
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
        "5\tyarn\t3\tnmod:with\n"
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
            "6\tyarn\t3\tnmod:with\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tblue cats\tplay quietly with\tyarn", extraction.get().toString());
  }

  public void testCatsHaveTails() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tcats\t2\tnsubj\n" +
            "2\thave\t0\troot\n" +
            "3\ttails\t2\tdobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tcats\thave\ttails", extraction.get().toString());
  }

  public void testFishLikeToSwim() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\tfish\t2\tnsubj\n" +
            "2\tlike\t0\troot\n" +
            "3\tto\t4\taux\n" +
            "4\tswim\t2\txcomp\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfish\tlike\tto swim", extraction.get().toString());
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

  public void testHeWasInaugurated() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t3\tnsubjpass\n" +
            "2\twas\t3\tauxpass\n" +
            "3\tinaugurated\t0\troot\n" +
            "5\tpresident\t3\tnmod:as\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\twas inaugurated as\tpresident", extraction.get().toString());
  }

  public void testPPAttachment() {
    Optional<RelationTriple> extraction = mkExtraction(
            "1\the\t2\tnsubj\n" +
            "2\tserved\t0\troot\n" +
            "3\tpresident\t2\tnmod:as\n" +
            "4\tHarvard\t6\tcompound\n" +
            "5\tLaw\t6\tcompound\n" +
            "6\tReview\t3\tnmod:of\n"
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
            "6\tChicago\t4\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\twas\tcommunity organizer in Chicago", extraction.get().toString());
  }

  public void testXComp() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t3\tnsubjpass\n" +
        "2\twas\t3\tauxpass\n" +
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
        "1\tHRE\t3\tnsubjpass\n" +
        "2\twas\t3\tauxpass\n" +
        "3\tfounded\t0\troot\n" +
        "5\t1991\t3\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\twas founded in\t1991", extraction.get().toString());

    extraction = mkExtraction(
        "1\tfounded\t0\troot\n" +
        "2\tHRE\t1\tnsubjpass\n" +
        "3\t2003\t1\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\tfounded in\t2003", extraction.get().toString());
  }

  public void testPossessive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnicredit\t0\troot\tNNP\tORGANIZATION\n" +
        "2\t's\t4\tcase\tPOS\tO\n" +
        "3\tBank\t5\tcompound\tNNP\tORGANIZATION\n" +
        "4\tAustria\t5\tcompound\tNNP\tORGANIZATION\n" +
        "5\tCreditanstalt\t1\tnmod:poss\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tUnicredit\t's\tBank Austria Creditanstalt", extraction.get().toString());
  }

  public void testPossessiveWithObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTim\t2\tnmod:poss\n" +
        "2\tfather\t0\troot\n" +
        "3\tTom\t2\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tTim\t's father is\tTom", extraction.get().toString());
  }

  public void testApposInObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tNewspaper\t2\tnsubj\n" +
        "2\tpublished\t0\troot\n" +
        "3\tTucson\t2\tnmod:in\n" +
        "4\tArizona\t3\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tNewspaper\tpublished in\tArizona", extraction.get().toString());
  }

  public void testApposAsSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tDurin\t0\troot\n" +
        "2\tson\t1\tappos\n" +
        "3\tThorin\t2\tnmod:of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tDurin\tson of\tThorin", extraction.get().toString());
  }

  public void testPassiveReflexive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTom\t4\tnsubjpass\n" +
        "2\tJerry\t1\tconj:and\n" +
        "3\twere\t4\tauxpass\n" +
        "4\tfighting\t0\troot\n"
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
        "1\twhich\t4\tnmod:of\n" +
        "2\tBono\t4\tnsubj\n" +
        "3\tis\t4\tcop\n" +
        "4\tco-founder\t0\troot\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tBono\tis co-founder of\twhich", extraction.get().toString());
  }

  public void testObjInRelation() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tScania-Vabis\t2\tnsubj\tNNP\tORGANIZATION\n" +
        "2\testablished\t0\troot\tVB\tO\n" +
        "3\tproduction\t4\tcompound\tNN\tO\n" +
        "4\tplant\t2\tdobj\tNN\tO\n" +
        "5\tSödertälje\t2\tnmod:outside\tNN\tO\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tScania-Vabis\testablished production plant outside\tSödertälje", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tcompound\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tdobj\tNN\tO\n" +
        "5\tShinawatra\t3\tnmod:with\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHun Sen\tplayed golf with\tShinawatra", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tcompound\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tdobj\tNN\tO\n" +
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
        "4\thorse\t3\tnmod:next_to\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tfoal\tbe standing next to\thorse", extraction.get().toString());
  }

  public void testThereAre() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tthere\t2\texpl\n" +
        "2\tare\t0\troot\tVBG\tO\tbe\n" +
        "3\tdogs\t2\tnsubj\n" +
        "4\theaven\t3\tnmod:in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tdogs\tare in\theaven", extraction.get().toString());
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
        "1\tthings\t3\tnsubjpass\n" +
        "2\tare\t3\tauxpass\n" +
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
        "3\tHonolulu\t2\tnmod:in\n" +
        "4\tHawaii\t3\tcompound\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tBorn in\tHonolulu Hawaii", extraction.get().toString());
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
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnited\t2\tcompound\tNNP\tLOCATION\n" +
        "2\tStates\t4\tnmod:poss\tNNP\tLOCATION\n" +
        "3\t's\t2\tcase\tPOS\tO\n" +
        "4\tpresident\t0\troot\tNN\tO\n" +
        "5\tObama\t2\tappos\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis president of\tUnited States", extraction.get().toString());
  }

  public void testUSsAllyBritain() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnited\t2\tcompound\tNNP\tLOCATION\n" +
        "2\tStates\t4\tnmod:poss\tNNP\tLOCATION\n" +
        "3\t's\t2\tcase\tPOS\tO\n" +
        "4\tally\t0\troot\tNN\tO\n" +
        "5\tBritain\t2\tappos\tNNP\tPERSON\n"
    );
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
        "3\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testChrisManningOfStanfordLong() {
    String conll =
        "1\tChris\t2\tcompound\tNNP\tPERSON\n" +
        "2\tManning\t4\tnsubj\tNNP\tPERSON\n" +
        "3\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n" +
        "4\tvisited\t0\troot\tVBD\tO\n" +
        "5\tChina\t4\tdobj\tNNP\tLOCATION\n";
    Optional<RelationTriple> extraction = mkExtraction(conll);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testChrisIsOfStanford() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tChris\t2\tcompound\tNNP\tPERSON\n" +
        "2\tManning\t0\troot\tNNP\tPERSON\n" +
        "3\tStanford\t2\tnmod:of\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tChris Manning\tis of\tStanford", extraction.get().toString());
  }

  public void testPPExtraction() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t0\troot\tNNP\tPERSON\n" +
        "2\tTucson\t1\tnmod:in\tNNP\tLOCATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tis in\tTucson", extraction.get().toString());

    extraction = mkExtraction(
        "1\tPietro\t2\tcompound\tNNP\tPERSON\n" +
        "2\tBadoglio\t0\troot\tNNP\tPERSON\n" +
        "3\tsouthern\t4\tamod\tJJ\tO\n" +
        "4\tItaly\t2\tnmod:in\tNN\tLOCATION\n"
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
        "1\tIBM\t0\troot\tNNP\tORGANIZATION\n" +
        "2\t's\t1\tcase\tPOS\tO\n" +
        "3\tCEO\t4\tcompound\tNNP\tTITLE\n" +
        "4\tRometty\t1\tnmod:poss\tNNP\tORGANIZATION\n";
    Optional<RelationTriple> extraction = mkExtraction(conll, 0);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tRometty\tis\tCEO", extraction.get().toString());
    extraction = mkExtraction(conll, 1);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tIBM\t's\tRometty", extraction.get().toString());
    extraction = mkExtraction(conll, 2);
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tRometty\tis CEO of\tIBM", extraction.get().toString());
  }
}
