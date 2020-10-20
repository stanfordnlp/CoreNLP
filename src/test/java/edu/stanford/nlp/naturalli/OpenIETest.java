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
import java.util.Set;

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
