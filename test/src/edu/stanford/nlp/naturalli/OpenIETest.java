package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.GrammaticalRelation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
 * A test of the hard-coded clause splitting rules.
 *
 * @author Gabor Angeli
 */
public class OpenIETest {


  protected CoreLabel mkWord(String gloss, int index) {
    CoreLabel w = new CoreLabel();
    w.setWord(gloss);
    w.setValue(gloss);
    if (index >= 0) {
      w.setIndex(index);
    }
    return w;
  }

  protected Set<String> clauses(String conll) {
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
            new GrammaticalRelation(Language.English, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }
    // Run extractor
    ClauseSplitterSearchProblem problem = new ClauseSplitterSearchProblem(tree, true);
    Set<String> clauses = new HashSet<>();
    problem.search(
        triple -> {
          clauses.add(triple.third.get().toString());
          return true;
        },
        new LinearClassifier<>(new ClassicCounter<>()),
        ClauseSplitterSearchProblem.HARD_SPLITS,
        triple -> new ClassicCounter<String>(){{setCount("__undocumented_junit_no_classifier", 1.0);}},
        100000);
    return clauses;
  }

  /**
   * Build the dependency tree of a conll fragment, as clauses() does, but
   * keep the tokens so that they can carry annotations.
   */
  protected SemanticGraph parseTree(String conll, List<CoreLabel> sentence) {
    SemanticGraph tree = new SemanticGraph();
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) { continue; }
      String[] fields = line.trim().split("\\s+");
      CoreLabel label = mkWord(fields[1], Integer.parseInt(fields[0]));
      if (fields.length > 4) { label.setTag(fields[4]); }
      // entailmentsFromClause reads the polarity of the adjective and the
      // copula, so every token needs one or it throws
      label.set(NaturalLogicAnnotations.PolarityAnnotation.class, Polarity.DEFAULT);
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
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            new GrammaticalRelation(Language.English, fields[3], null, null),
            1.0, false
        );
      }
      i += 1;
    }
    return tree;
  }

  /**
   * The entailments of a single clause, as strings.
   *<br>
   * The OpenIE system is built with the models turned off and with no
   * forward entailments, so that only the hard coded adjective rule in
   * entailmentsFromClause runs.  That keeps the test to the one thing it
   * is about and means it needs nothing from the classpath.
   */
  protected Set<String> adjectiveEntailments(String conll) {
    Properties props = new Properties();
    props.setProperty("splitter.disable", "true");
    props.setProperty("ignore_affinity", "true");
    props.setProperty("max_entailments_per_clause", "0");
    OpenIE openie = new OpenIE(props);

    List<CoreLabel> sentence = new ArrayList<>();
    SentenceFragment clause = new SentenceFragment(parseTree(conll, sentence), true, false);
    Set<String> entailments = new TreeSet<>();
    for (SentenceFragment fragment : openie.entailmentsFromClause(clause)) {
      entailments.add(fragment.toString());
    }
    return entailments;
  }

  /** "Bill is a happy man" */
  private static final String NO_PP =
      "1\tBill\t5\tnsubj\tNNP\n" +
      "2\tis\t5\tcop\tVBZ\n" +
      "3\ta\t5\tdet\tDT\n" +
      "4\thappy\t5\tamod\tJJ\n" +
      "5\tman\t0\troot\tNN\n";

  /** "Bill is a happy man in the park" */
  private static final String ONE_PP = NO_PP +
      "6\tin\t7\tcase\tIN\n" +
      "7\tpark\t5\tnmod:in\tNN\n";

  /** "Bill is a happy man in the park with a hat" */
  private static final String TWO_PPS = ONE_PP +
      "8\twith\t9\tcase\tIN\n" +
      "9\that\t5\tnmod:with\tNN\n";

  /**
   * The adjective rule turns "Bill is a happy man" into "Bill is happy"
   */
  @Test
  public void testAdjectiveEntailmentNoPP() {
    assertEquals(new TreeSet<String>() {{
      add("Bill is a happy man");
      add("Bill is happy");
    }}, adjectiveEntailments(NO_PP));
  }

  /**
   * A prepositional phrase on the noun is carried over onto the adjective
   */
  @Test
  public void testAdjectiveEntailmentOnePP() {
    assertEquals(new TreeSet<String>() {{
      add("Bill is a happy man in park");
      add("Bill is happy park");
    }}, adjectiveEntailments(ONE_PP));
  }

  /**
   * With two prepositional phrases, only one of them is carried over
   *<br>
   * The rule's pattern ends in {@code ?>/(nmod|acl).*&#47;=prep {}=pobj},
   * and an optional relation currently stops after its first satisfying
   * edge, so only one PP ever reaches the entailment.  Which one is
   * decided by the order the edges were added to the graph rather than by
   * anything about the sentence, so "with a hat" is dropped here and
   * would be kept if the parse had been built the other way round.
   *<br>
   * If optional relations are changed to yield every satisfying edge,
   * this test should start failing with a third entailment,
   * "Bill is happy hat", and that third entailment is the correct
   * behaviour -- both PPs are real.  Update the expectation rather than
   * the code.
   */
  @Test
  public void testAdjectiveEntailmentTwoPPs() {
    assertEquals(new TreeSet<String>() {{
      add("Bill is a happy man in park with hat");
      add("Bill is happy park");
    }}, adjectiveEntailments(TWO_PPS));
  }

  @Test
  public void testNoClauses() {
    assertEquals(new HashSet<String>() {{
      add("cats have tails");
    }}, clauses(
        "1\tcats\t2\tnsubj\tNN\n" +
        "2\thave\t0\troot\tVB\n" +
        "3\ttails\t2\tobj\tNN\n"
    ));
  }

  @Test
  public void testXCompObj() {
    assertEquals(new HashSet<String>() {{
      add("I persuaded Fred to leave the room");
      add("Fred leave the room");
    }}, clauses(
        "1\tI\t2\tnsubj\tPR\n" +
        "2\tpersuaded\t0\troot\tVBD\n" +
        "3\tFred\t2\tobj\tNNP\n" +
        "4\tto\t5\taux\tTO\n" +
        "5\tleave\t2\txcomp\tVB\n" +
        "6\tthe\t7\tdet\tDT\n" +
        "7\troom\t5\tobj\tNN\n"
    ));
  }

  @Test
  public void testXCompSubj() {
    assertEquals(new HashSet<String>() {{
      add("I was persuaded to leave the room");
      add("I leave the room");
    }}, clauses(
        "1\tI\t3\tnsubjpass\tPR\n" +
        "2\twas\t3\tauxpass\tVB\n" +
        "3\tpersuaded\t0\troot\tVBD\n" +
        "4\tto\t5\taux\tTO\n" +
        "5\tleave\t3\txcomp\tVB\n" +
        "6\tthe\t7\tdet\tDT\n" +
        "7\troom\t5\tobj\tNN\n"
    ));
  }

  @Test
  public void testCComp() {
    assertEquals(new HashSet<String>() {{
      add("I suggested that he leave the room");
      add("he leave the room");
    }}, clauses(
        "1\tI\t2\tnsubj\tPR\n" +
        "2\tsuggested\t0\troot\tVBD\n" +
        "3\tthat\t5\tmark\tIN\n" +
        "4\the\t5\tnsubj\tPR\n" +
        "5\tleave\t2\tccomp\tVB\n" +
        "6\tthe\t7\tdet\tDT\n" +
        "7\troom\t5\tobj\tNN\n"
    ));
  }

}
