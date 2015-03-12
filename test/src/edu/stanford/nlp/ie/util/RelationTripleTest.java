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
      if (fields.length > 4) {
        label.setTag(fields[4]);
      }
      if (fields.length > 5) {
        label.setNER(fields[5]);
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
            new GrammaticalRelation(GrammaticalRelation.Language.English, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }
    return RelationTriple.segment(tree, Optional.empty());
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
        "4\twith\t3\tprep\n" +
        "5\tyarn\t4\tpobj\n"
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
            "5\twith\t3\tprep\n" +
            "6\tyarn\t5\tpobj\n"
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
            "5\tpresident\t3\tprep_as\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\the\twas inaugurated as\tpresident", extraction.get().toString());
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
    assertEquals("1.0\the\tserved as\tpresident of Harvard Law Review", extraction.get().toString());
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
    assertEquals("1.0\the\twas\tcommunity organizer in Chicago", extraction.get().toString());
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
    assertEquals("1.0\tObama\twas named\t2009 Nobel Peace Prize Laureate", extraction.get().toString());
  }

  public void testPassiveNSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tHRE\t3\tnsubjpass\n" +
        "2\twas\t3\tauxpass\n" +
        "3\tfounded\t0\troot\n" +
        "5\t1991\t3\tprep_in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\twas founded in\t1991", extraction.get().toString());

    extraction = mkExtraction(
        "1\tfounded\t0\troot\n" +
        "2\tHRE\t1\tnsubjpass\n" +
        "3\t2003\t1\tprep_in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHRE\tfounded in\t2003", extraction.get().toString());
  }

  public void testPossessive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tUnicredit\t4\tposs\tNNP\tORGANIZATION\n" +
        "2\tBank\t4\tnn\tNNP\tORGANIZATION\n" +
        "3\tAustria\t4\tnn\tNNP\tORGANIZATION\n" +
        "4\tCreditanstalt\t0\troot\tNNP\tORGANIZATION\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tUnicredit\t's\tBank Austria Creditanstalt", extraction.get().toString());
  }

  public void testPossessiveWithObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTim\t2\tposs\n" +
        "2\tfather\t0\troot\n" +
        "3\tTom\t2\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tTim\t's father\tTom", extraction.get().toString());
  }

  public void testApposInObject() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tNewspaper\t2\tnsubj\n" +
        "2\tpublished\t0\troot\n" +
        "3\tTucson\t2\tprep_in\n" +
        "4\tArizona\t3\tappos\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tNewspaper\tpublished in\tArizona", extraction.get().toString());
  }

  public void testApposAsSubj() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tDurin\t0\troot\n" +
        "2\tson\t1\tappos\n" +
        "3\tThorin\t2\tprep_of\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tDurin\tson of\tThorin", extraction.get().toString());
  }

  public void testPPExtraction() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tObama\t0\troot\tNNP\tPERSON\n" +
        "2\tTucson\t1\tprep_in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tObama\tin\tTucson", extraction.get().toString());

    extraction = mkExtraction(
        "1\tPietro\t2\tnn\tNNP\tPERSON\n" +
        "2\tBadoglio\t0\troot\tNNP\tPERSON\n" +
        "3\tsouthern\t4\tamod\n" +
        "4\tItaly\t2\tprep_in\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tPietro Badoglio\tin\tsouthern Italy", extraction.get().toString());
  }

  public void testPassiveReflexive() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\tTom\t4\tnsubjpass\n" +
        "2\tJerry\t1\tconj_and\n" +
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
        "3\tits\t6\tposs\n" +
        "4\tfirst\t6\tamod\n" +
        "5\tproduction\t6\tnn\n" +
        "6\tplant\t2\tdobj\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tScania-Vabis\testablished\tits first production plant", extraction.get().toString());
  }

  public void testOfWhich() {
    Optional<RelationTriple> extraction = mkExtraction(
        "1\twhich\t4\tprep_of\n" +
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
        "3\tproduction\t4\tnn\tNN\tO\n" +
        "4\tplant\t2\tdobj\tNN\tO\n" +
        "5\tSödertälje\t2\tprep_outside\tNN\tO\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tScania-Vabis\testablished production plant outside\tSödertälje", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tnn\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tdobj\tNN\tO\n" +
        "5\tShinawatra\t3\tprep_with\tNNP\tPERSON\n"
    );
    assertTrue("No extraction for sentence!", extraction.isPresent());
    assertEquals("1.0\tHun Sen\tplayed golf with\tShinawatra", extraction.get().toString());

    extraction = mkExtraction(
        "1\tHun\t2\tnn\tNNP\tPERSON\n" +
        "2\tSen\t3\tnsubj\tNNP\tPERSON\n" +
        "3\tplayed\t0\troot\tVBD\tO\n" +
        "4\tgolf\t3\tdobj\tNN\tO\n" +
        "5\tShinawatra\t3\tprep_with\tNNP\tPERSON\n" +
        "6\tCambodia\t3\tdobj\tNNP\tLOCATION\n"
    );
    assertFalse("No extraction for sentence!", extraction.isPresent());
  }

}
