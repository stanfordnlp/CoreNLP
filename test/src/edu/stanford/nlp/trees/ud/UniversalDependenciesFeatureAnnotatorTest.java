package edu.stanford.nlp.trees.ud;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import edu.stanford.nlp.io.IOUtils;

/**
 * What UniversalDependenciesFeatureAnnotator writes for a few sentences.
 *<br>
 * These record the behavior rather than argue for it: the expected values
 * are what the annotator produced when the test was written, and some of
 * what they record is wrong.  The tests which pin something wrong say so,
 * so that fixing it shows up as that test changing and nothing else.
 *<br>
 * The annotator needs the constituency tree of each sentence as well as the
 * CoNLL-U, though all it takes from the tree is which verbs are imperative
 * and, with addUPOS, the UPOS tags.
 *
 * @author John Bauer
 */
public class UniversalDependenciesFeatureAnnotatorTest {

  /** One sentence as CoNLL-U, with the tree for the same words */
  static final class Sentence {
    final String name;
    final String conllu;
    final String tree;
    Sentence(String name, String tree, String... conllu) {
      this.name = name;
      this.tree = tree;
      this.conllu = String.join("\n", conllu) + "\n\n";
    }
  }

  static final Sentence[] SENTENCES = {
    // pronouns and a past tense verb, for Person, Case, Number, Tense
    new Sentence("pronouns",
        "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (PRP her))) (. .)))",
        "# sent_id = pronouns",
        "# text = I saw her.",
        "1\tI\tI\tPRON\tPRP\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tsaw\tsee\tVERB\tVBD\t_\t0\troot\t0:root\t_",
        "3\ther\tshe\tPRON\tPRP\t_\t2\tobj\t2:obj\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),

    // enhanced arcs which share their governor with the basic arc: the
    // shape where the DEPS column can lose its obl:in and conj:and
    new Sentence("shared-governor",
        "(ROOT (S (NP (PRP He)) (VP (VBZ works) (PP (IN in) (NP (NNP Paris) (CC and) (NNP London)))) (. .)))",
        "# sent_id = shared-governor",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tworks\twork\tVERB\tVBZ\t_\t0\troot\t0:root\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t4:case\t_",
        "4\tParis\tParis\tPROPN\tNNP\t_\t2\tobl\t2:obl:in\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\t_\t4\tconj\t2:obl:in|4:conj:and\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),

    // S-IMP is the only thing the annotator uses the tree for, apart from UPOS
    new Sentence("imperative",
        "(ROOT (S-IMP (VP (VB Go) (NP (NN home))) (. .)))",
        "# sent_id = imperative",
        "# text = Go home.",
        "1\tGo\tgo\tVERB\tVB\t_\t0\troot\t0:root\t_",
        "2\thome\thome\tNOUN\tNN\t_\t1\tobj\t1:obj\tSpaceAfter=No",
        "3\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"),

    // the second half of a split word has no lemma of its own in UD
    new Sentence("goeswith",
        "(ROOT (NP (DT the) (NN web) (GW site)))",
        "# sent_id = goeswith",
        "# text = the web site",
        "1\tthe\tthe\tDET\tDT\t_\t2\tdet\t2:det\t_",
        "2\tweb\tweb\tNOUN\tNN\t_\t0\troot\t0:root\t_",
        "3\tsite\t_\tX\tGW\t_\t2\tgoeswith\t2:goeswith\tSpaceAfter=No"),

    // no XPOS: an underscore which one reader keeps as a string and the
    // other treats as having no tag
    new Sentence("no-xpos",
        "(ROOT (S (INTJ (UH Hello)) (NP (NN world)) (. .)))",
        "# sent_id = no-xpos",
        "# text = Hello world.",
        "1\tHello\thello\tINTJ\t_\t_\t0\troot\t0:root\t_",
        "2\tworld\tworld\tNOUN\t_\t_\t1\tvocative\t1:vocative\tSpaceAfter=No",
        "3\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_"),

    // a multiword token with a misc of its own, and a bare infinitive
    new Sentence("mwt",
        "(ROOT (S (NP (PRP I)) (VP (VBP do) (RB n't) (VP (VB know))) (. .)))",
        "# sent_id = mwt",
        "# text = I don't know.",
        "1\tI\tI\tPRON\tPRP\t_\t4\tnsubj\t4:nsubj\t_",
        "2-3\tdon't\t_\t_\t_\t_\t_\t_\t_\tGloss=do+not",
        "2\tdo\tdo\tAUX\tVBP\t_\t4\taux\t4:aux\t_",
        "3\tn't\tnot\tPART\tRB\t_\t4\tadvmod\t4:advmod\t_",
        "4\tknow\tknow\tVERB\tVB\t_\t0\troot\t0:root\tSpaceAfter=No",
        "5\t.\t.\tPUNCT\t.\t_\t4\tpunct\t4:punct\t_"),
  };


  /** Run the annotator over the given CoNLL-U and trees, with any extra properties as name, value pairs */
  static String annotate(String conllu, String trees, String... properties) throws Exception {
    File conlluFile = IOUtils.writeStringToTempFile(conllu, "annotatortest", "UTF-8");
    File treeFile = IOUtils.writeStringToTempFile(trees, "annotatortesttrees", "UTF-8");
    conlluFile.deleteOnExit();
    treeFile.deleteOnExit();
    try {
      Properties props = new Properties();
      props.setProperty("conlluFile", conlluFile.getPath());
      props.setProperty("treeFile", treeFile.getPath());
      for (int i = 0; i < properties.length; i += 2) {
        props.setProperty(properties[i], properties[i + 1]);
      }
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (PrintStream out = new PrintStream(bytes, true, "UTF-8")) {
        UniversalDependenciesFeatureAnnotator.annotate(props, out);
      }
      return bytes.toString("UTF-8");
    } finally {
      conlluFile.delete();
      treeFile.delete();
    }
  }

  static String annotate(Sentence sentence, String... properties) throws Exception {
    return annotate(sentence.conllu, sentence.tree, properties);
  }

  /** The lines of one written sentence, ending with the blank line after it */
  static String written(String... lines) {
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append(System.lineSeparator());
    }
    text.append(System.lineSeparator());
    return text.toString();
  }

  /** The column of the word with this form, in the first line it turns up in */
  private static String column(String written, String word, int column) {
    for (String line : written.split("\\R")) {
      String[] fields = line.split("\t");
      if (fields.length == 10 && fields[1].equals(word)) {
        return fields[column];
      }
    }
    return null;
  }

  // ------------------------------------------------------------------
  // what each sentence comes out as
  // ------------------------------------------------------------------

  @Test
  public void testPronouns() throws Exception {
    assertEquals(written(
        "# sent_id = pronouns",
        "# text = I saw her.",
        "1\tI\tI\tPRON\tPRP\tCase=Nom|Number=Sing|Person=1|PronType=Prs\t2\tnsubj\t2:nsubj\t_",
        "2\tsaw\tsee\tVERB\tVBD\tMood=Ind|Tense=Past|VerbForm=Fin\t0\troot\t0:root\t_",
        "3\ther\tshe\tPRON\tPRP\tCase=Acc|Gender=Fem|Number=Sing|Person=3|PronType=Prs\t2\tobj\t2:obj\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
        annotate(SENTENCES[0]));
  }

  @Test
  public void testSharedGovernor() throws Exception {
    assertEquals(written(
        "# sent_id = shared-governor",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\tCase=Nom|Gender=Masc|Number=Sing|Person=3|PronType=Prs\t2\tnsubj\t2:nsubj\t_",
        "2\tworks\twork\tVERB\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t0\troot\t0:root\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t4:case\t_",
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t2:obl\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t2:obl:in|4:conj\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
        annotate(SENTENCES[1]));
  }

  @Test
  public void testImperative() throws Exception {
    assertEquals(written(
        "# sent_id = imperative",
        "# text = Go home.",
        "1\tGo\tgo\tVERB\tVB\tMood=Imp|VerbForm=Fin\t0\troot\t0:root\t_",
        "2\thome\thome\tNOUN\tNN\tNumber=Sing\t1\tobj\t1:obj\tSpaceAfter=No",
        "3\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"),
        annotate(SENTENCES[2]));
  }

  @Test
  public void testGoeswith() throws Exception {
    assertEquals(written(
        "# sent_id = goeswith",
        "# text = the web site",
        "1\tthe\tthe\tDET\tDT\tDefinite=Def|PronType=Art\t2\tdet\t2:det\t_",
        "2\tweb\tweb\tNOUN\tNN\tNumber=Sing\t0\troot\t0:root\t_",
        "3\tsite\tsite\tX\tGW\t_\t2\tgoeswith\t2:goeswith\tSpaceAfter=No"),
        annotate(SENTENCES[3]));
  }

  @Test
  public void testNoXpos() throws Exception {
    assertEquals(written(
        "# sent_id = no-xpos",
        "# text = Hello world.",
        "1\tHello\thello\tINTJ\t_\t_\t0\troot\t0:root\t_",
        "2\tworld\tworld\tNOUN\t_\t_\t1\tvocative\t1:vocative\tSpaceAfter=No",
        "3\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_"),
        annotate(SENTENCES[4]));
  }

  @Test
  public void testMultiWordToken() throws Exception {
    assertEquals(written(
        "# sent_id = mwt",
        "# text = I don't know.",
        "1\tI\tI\tPRON\tPRP\tCase=Nom|Number=Sing|Person=1|PronType=Prs\t4\tnsubj\t4:nsubj\t_",
        "2-3\tdon't\t_\t_\t_\t_\t_\t_\t_\t_",
        "2\tdo\tdo\tAUX\tVBP\tMood=Ind|Tense=Pres|VerbForm=Fin\t4\taux\t4:aux\t_",
        "3\tn't\tnot\tPART\tRB\t_\t4\tadvmod\t4:advmod\t_",
        "4\tknow\tknow\tVERB\tVB\tVerbForm=Inf\t0\troot\t0:root\tSpaceAfter=No",
        "5\t.\t.\tPUNCT\t.\t_\t4\tpunct\t4:punct\t_"),
        annotate(SENTENCES[5]));
  }

  @Test
  public void testAddUPOSChangesNothingHere() throws Exception {
    // the UPOS in these sentences is already what the trees would give, so
    // taking it from the trees changes nothing.  a sentence whose UPOS
    // disagreed with its tree would be needed to test the flag
    for (Sentence sentence : SENTENCES) {
      assertEquals(sentence.name, annotate(sentence), annotate(sentence, "addUPOS", "true"));
    }
  }

  @Test
  public void testSentencesTogether() throws Exception {
    // the trees and the sentences are walked in step, so a file of all of
    // them comes out as each of them would on its own
    StringBuilder conllu = new StringBuilder();
    StringBuilder trees = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (Sentence sentence : SENTENCES) {
      conllu.append(sentence.conllu);
      trees.append(sentence.tree).append('\n');
      expected.append(annotate(sentence));
    }
    assertEquals(expected.toString(), annotate(conllu.toString(), trees.toString()));
  }

  // ------------------------------------------------------------------
  // things these record which are wrong
  // ------------------------------------------------------------------

  @Test
  public void testSharedGovernorLosesTheSpecific() throws Exception {
    // wrong.  the enhanced dependencies are written from what the reader
    // left on each word, and the basic arc is then put over the top of
    // whichever enhanced arc has the same governor, so obl:in becomes obl
    // and conj:and becomes conj.  London's obl:in survives only because
    // its governor is not London's basic governor
    String written = annotate(SENTENCES[1]);
    assertEquals("2:obl", column(written, "Paris", 8));
    assertEquals("2:obl:in|4:conj", column(written, "London", 8));
  }

  @Test
  public void testGoeswithGetsALemma() throws Exception {
    // wrong.  the second half of a split word has no lemma of its own in
    // UD, but an empty lemma is filled in like any other
    assertEquals("site", column(annotate(SENTENCES[3]), "site", 2));
  }

  @Test
  public void testMultiWordTokenMiscIsDropped() throws Exception {
    // wrong.  the misc of an MWT range line does not come through the
    // reader, so the Gloss is gone
    assertEquals("_", column(annotate(SENTENCES[5]), "don't", 9));
  }

  @Test
  public void testNoXposMeansNoFeatures() throws Exception {
    // the features come from the PTB tag, so a word with no XPOS gets none,
    // not even the Number of a noun whose UPOS says it is one
    assertEquals("_", column(annotate(SENTENCES[4]), "world", 5));
  }
}

