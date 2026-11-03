package edu.stanford.nlp.trees.ud;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.stanford.nlp.io.IOUtils;

/**
 * What UniversalDependenciesFeatureAnnotator writes for a few sentences.
 *<br>
 * These record the behavior rather than argue for it: the expected values
 * are what the annotator produced when the test was written.
 *<br>
 * The annotator needs the constituency tree of each sentence as well as the
 * CoNLL-U, though all it takes from the tree is its S-IMP tags and, with
 * addUPOS, the UPOS tags.  Imperatives are also found from the dependency
 * graph, so they do not need the tree.
 *
 * @author John Bauer
 */
public class UniversalDependenciesFeatureAnnotatorITest {

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

  /** The sentence whose verb is imperative, marked S-IMP in its tree */
  static final int IMPERATIVE = 2;

  /** The sentence with no XPOS, which is reported rather than featurized */
  static final int NO_XPOS = 4;

  /** The sentence with an empty word in its enhanced graph */
  static final int EMPTY_NODE = 6;

  /** The sentence where one word has no enhanced dependencies */
  static final int PARTIAL_DEPS = 7;

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

    // an empty word, which is only in the enhanced graph, so whether its
    // line is written depends on which graph the rows are taken from.  the
    // tree has no leaf for it, since it is not a word of the text
    new Sentence("empty-node",
        "(ROOT (S (S (NP (NNP John)) (VP (VBD bought) (NP (NNS apples)))) (CC and) (S (NP (NNP Mary)) (VP (NP (NNS pears)))) (. .)))",
        "# sent_id = empty-node",
        "# text = John bought apples and Mary pears.",
        "1\tJohn\tJohn\tPROPN\tNNP\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tbought\tbuy\tVERB\tVBD\t_\t0\troot\t0:root\t_",
        "3\tapples\tapple\tNOUN\tNNS\t_\t2\tobj\t2:obj\t_",
        "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
        "5\tMary\tMary\tPROPN\tNNP\t_\t2\tconj\t5.1:nsubj\t_",
        "5.1\tbought\tbuy\tVERB\tVBD\t_\t_\t_\t2:conj\t_",
        "6\tpears\tpear\tNOUN\tNNS\t_\t5\torphan\t5.1:obj\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),

    // one word has DEPS and one does not, so the enhanced graph is missing
    // a word of the sentence
    new Sentence("partial-deps",
        "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (PRP her))) (. .)))",
        "# sent_id = partial-deps",
        "# text = I saw her.",
        "1\tI\tI\tPRON\tPRP\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tsaw\tsee\tVERB\tVBD\t_\t0\troot\t0:root\t_",
        "3\ther\tshe\tPRON\tPRP\t_\t2\tobj\t_\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
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

  /** Run the annotator over a sentence with -noTrees rather than a tree file */
  static String annotateWithoutTrees(Sentence sentence, String... properties) throws Exception {
    File conlluFile = IOUtils.writeStringToTempFile(sentence.conllu, "annotatortest", "UTF-8");
    conlluFile.deleteOnExit();
    try {
      Properties props = new Properties();
      props.setProperty("conlluFile", conlluFile.getPath());
      props.setProperty("noTrees", "true");
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
    }
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
        "2\tsaw\tsee\tVERB\tVBD\tMood=Ind|Number=Sing|Person=1|Tense=Past|VerbForm=Fin\t0\troot\t0:root\t_",
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
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t2:obl:in\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t2:obl:in|4:conj:and\tSpaceAfter=No",
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
        "3\tsite\t_\tX\tGW\t_\t2\tgoeswith\t2:goeswith\tSpaceAfter=No"),
        annotate(SENTENCES[3]));
  }

  @Test
  public void testNoXpos() throws Exception {
    // the features are worked out from the PTB tag, so a sentence without
    // one is reported, naming the word, rather than coming out unfeaturized
    try {
      annotate(SENTENCES[NO_XPOS]);
      fail("Expected a word with no XPOS to be reported");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("no XPOS"));
      assertTrue(e.getMessage(), e.getMessage().contains("Hello"));
    }
  }

  @Test
  public void testMultiWordToken() throws Exception {
    assertEquals(written(
        "# sent_id = mwt",
        "# text = I don't know.",
        "1\tI\tI\tPRON\tPRP\tCase=Nom|Number=Sing|Person=1|PronType=Prs\t4\tnsubj\t4:nsubj\t_",
        "2-3\tdon't\t_\t_\t_\t_\t_\t_\t_\tGloss=do+not",
        "2\tdo\tdo\tAUX\tVBP\tMood=Ind|Number=Sing|Person=1|Tense=Pres|VerbForm=Fin\t4\taux\t4:aux\t_",
        "3\tn't\tnot\tPART\tRB\tPolarity=Neg\t4\tadvmod\t4:advmod\t_",
        "4\tknow\tknow\tVERB\tVB\tVerbForm=Inf\t0\troot\t0:root\tSpaceAfter=No",
        "5\t.\t.\tPUNCT\t.\t_\t4\tpunct\t4:punct\t_"),
        annotate(SENTENCES[5]));
  }

  @Test
  public void testEmptyNode() throws Exception {
    assertEquals(written(
        "# sent_id = empty-node",
        "# text = John bought apples and Mary pears.",
        "1\tJohn\tJohn\tPROPN\tNNP\tNumber=Sing\t2\tnsubj\t2:nsubj\t_",
        "2\tbought\tbuy\tVERB\tVBD\tMood=Ind|Number=Sing|Person=3|Tense=Past|VerbForm=Fin\t0\troot\t0:root\t_",
        "3\tapples\tapple\tNOUN\tNNS\tNumber=Plur\t2\tobj\t2:obj\t_",
        "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
        "5\tMary\tMary\tPROPN\tNNP\tNumber=Sing\t2\tconj\t5.1:nsubj\t_",
        "5.1\tbought\tbuy\tVERB\tVBD\t_\t_\t_\t2:conj\t_",
        "6\tpears\tpear\tNOUN\tNNS\tNumber=Plur\t5\torphan\t5.1:obj\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
        annotate(SENTENCES[EMPTY_NODE]));
  }

  @Test
  public void testAddUPOSChangesNothingHere() throws Exception {
    // the UPOS in these sentences is already what the trees would give, so
    // taking it from the trees changes nothing.  a sentence whose UPOS
    // disagreed with its tree would be needed to test the flag
    for (int i = 0; i < SENTENCES.length; i++) {
      if (i == NO_XPOS) {
        continue;
      }
      Sentence sentence = SENTENCES[i];
      assertEquals(sentence.name, annotate(sentence), annotate(sentence, "addUPOS", "true"));
    }
  }

  @Test
  public void testGoeswithHasNoLemma() throws Exception {
    // the second half of a split word has no lemma of its own in UD, so an
    // empty lemma there is left empty rather than filled in
    assertEquals("_", column(annotate(SENTENCES[3]), "site", 2));
  }

  @Test
  public void testSentencesTogether() throws Exception {
    // the trees and the sentences are walked in step, so a file of all of
    // them comes out as each of them would on its own
    StringBuilder conllu = new StringBuilder();
    StringBuilder trees = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < SENTENCES.length; i++) {
      if (i == NO_XPOS) {
        continue;
      }
      Sentence sentence = SENTENCES[i];
      conllu.append(sentence.conllu);
      trees.append(sentence.tree).append('\n');
      expected.append(annotate(sentence));
    }
    assertEquals(expected.toString(), annotate(conllu.toString(), trees.toString()));
  }

  // ------------------------------------------------------------------
  // without trees
  // ------------------------------------------------------------------

  @Test
  public void testNoTreesGivesTheSameFeatures() throws Exception {
    // the dependency graph finds the same imperatives as the S-IMP of these
    // trees, so every sentence comes out the same whether or not the trees
    // are there
    for (int i = 0; i < SENTENCES.length; i++) {
      if (i == NO_XPOS) {
        continue;
      }
      Sentence sentence = SENTENCES[i];
      assertEquals(sentence.name, annotate(sentence), annotateWithoutTrees(sentence));
    }
  }

  @Test
  public void testNoTreesFindsImperativesFromTheGraph() throws Exception {
    // the PTB tags an imperative VB the same as an infinitive.  without the
    // S-IMP of the tree, Go is still found to be imperative, since it is the
    // head of the sentence and has no subject
    assertEquals("Mood=Imp|VerbForm=Fin", column(annotateWithoutTrees(SENTENCES[IMPERATIVE]), "Go", 5));
  }

  // ------------------------------------------------------------------
  // imperatives and "was" from the dependency graph alone
  // ------------------------------------------------------------------

  /** The features the annotator gives a word, from the CoNLL-U alone */
  static String featsWithoutTrees(String word, String... conllu) throws Exception {
    Sentence sentence = new Sentence(word, null, conllu);
    String feats = column(annotateWithoutTrees(sentence), word, 5);
    assertNotNull(word + " should have a line of its own", feats);
    return feats;
  }

  @Test
  public void testDoSupportImperative() throws Exception {
    // only do is imperative in a do imperative, as in UD_English-EWT#623
    String[] conllu = {
        "# sent_id = do-support",
        "# text = Don't sleep.",
        "1-2\tDon't\t_\t_\t_\t_\t_\t_\t_\t_",
        "1\tDo\tdo\tAUX\tVB\t_\t3\taux\t3:aux\t_",
        "2\tn't\tnot\tPART\tRB\t_\t3\tadvmod\t3:advmod\t_",
        "3\tsleep\tsleep\tVERB\tVB\t_\t0\troot\t0:root\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_"};
    assertEquals("Mood=Imp|VerbForm=Fin", featsWithoutTrees("Do", conllu));
    assertEquals("VerbForm=Inf", featsWithoutTrees("sleep", conllu));
  }

  @Test
  public void testConjoinedImperatives() throws Exception {
    // each conjunct of an imperative is an imperative of its own
    String[] conllu = {
        "# sent_id = conjoined",
        "# text = Copy and paste.",
        "1\tCopy\tcopy\tVERB\tVB\t_\t0\troot\t0:root\t_",
        "2\tand\tand\tCCONJ\tCC\t_\t3\tcc\t3:cc\t_",
        "3\tpaste\tpaste\tVERB\tVB\t_\t1\tconj\t1:conj:and\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"};
    assertEquals("Mood=Imp|VerbForm=Fin", featsWithoutTrees("Copy", conllu));
    assertEquals("Mood=Imp|VerbForm=Fin", featsWithoutTrees("paste", conllu));
  }

  @Test
  public void testWhyNotIsInfinitive() throws Exception {
    // "why not" with a bare verb is an infinitive question, not an order
    assertEquals("VerbForm=Inf", featsWithoutTrees("go",
        "# sent_id = why-not",
        "# text = Why not go?",
        "1\tWhy\twhy\tADV\tWRB\t_\t3\tadvmod\t3:advmod\t_",
        "2\tnot\tnot\tPART\tRB\t_\t3\tadvmod\t3:advmod\t_",
        "3\tgo\tgo\tVERB\tVB\t_\t0\troot\t0:root\tSpaceAfter=No",
        "4\t?\t?\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_"));
  }

  @Test
  public void testSubjectlessQuestionIsInfinitive() throws Exception {
    // a subjectless question is usually an elided "do you" question
    assertEquals("VerbForm=Inf", featsWithoutTrees("Remember",
        "# sent_id = question",
        "# text = Remember those days?",
        "1\tRemember\tremember\tVERB\tVB\t_\t0\troot\t0:root\t_",
        "2\tthose\tthat\tDET\tDT\t_\t3\tdet\t3:det\t_",
        "3\tdays\tday\tNOUN\tNNS\t_\t1\tobj\t1:obj\tSpaceAfter=No",
        "4\t?\t?\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"));
  }

  @Test
  public void testPleaseQuestionIsImperative() throws Exception {
    // with please, a question is a request
    assertEquals("Mood=Imp|VerbForm=Fin", featsWithoutTrees("clarify",
        "# sent_id = please-question",
        "# text = Please clarify?",
        "1\tPlease\tplease\tINTJ\tUH\t_\t2\tdiscourse\t2:discourse\t_",
        "2\tclarify\tclarify\tVERB\tVB\t_\t0\troot\t0:root\tSpaceAfter=No",
        "3\t?\t?\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"));
  }

  @Test
  public void testPolarity() throws Exception {
    // negation and yes have a Polarity, as in EWT and GUM, but neither as a
    // determiner does not
    String[] conllu = {
        "# sent_id = polarity",
        "# text = No, yes, not neither nor neither one.",
        "1\tNo\tno\tINTJ\tUH\t_\t5\tdiscourse\t5:discourse\tSpaceAfter=No",
        "2\t,\t,\tPUNCT\t,\t_\t1\tpunct\t1:punct\t_",
        "3\tyes\tyes\tINTJ\tUH\t_\t5\tdiscourse\t5:discourse\tSpaceAfter=No",
        "4\tnot\tnot\tPART\tRB\t_\t5\tadvmod\t5:advmod\t_",
        "5\tneither\tneither\tCCONJ\tCC\t_\t0\troot\t0:root\t_",
        "6\tnor\tnor\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
        "7\tneither\tneither\tDET\tDT\t_\t8\tdet\t8:det\t_",
        "8\tone\tone\tNUM\tCD\t_\t5\tconj\t5:conj\tSpaceAfter=No",
        "9\t.\t.\tPUNCT\t.\t_\t5\tpunct\t5:punct\t_"};
    String written = annotateWithoutTrees(new Sentence("polarity", null, conllu));
    assertEquals("Polarity=Neg", column(written, "No", 5));
    assertEquals("Polarity=Pos", column(written, "yes", 5));
    assertEquals("Polarity=Neg", column(written, "not", 5));
    assertEquals("Polarity=Neg", column(written, "nor", 5));
    for (String line : written.split("\\R")) {
      String[] fields = line.split("\t");
      if (fields.length == 10 && fields[1].equals("neither")) {
        assertEquals(fields[0] + " " + fields[4], fields[4].equals("CC") ? "Polarity=Neg" : "_", fields[5]);
      }
    }
  }

  @Test
  public void testNumForm() throws Exception {
    // every word with a NumType has a NumForm, which depends on its form and,
    // for Roman letters, on whether a number comes right before it
    String[] conllu = {
        "# sent_id = numform",
        "# text = twice the first two of 3 , 7:30 , +1 , '72 , 1990s , XIV , iv , 5 M , MM , 10 mm",
        "1\ttwice\ttwice\tADV\tRB\t_\t4\tadvmod\t4:advmod\t_",
        "2\tthe\tthe\tDET\tDT\t_\t4\tdet\t4:det\t_",
        "3\tfirst\tfirst\tADJ\tJJ\t_\t4\tamod\t4:amod\t_",
        "4\ttwo\ttwo\tNUM\tCD\t_\t0\troot\t0:root\t_",
        "5\tof\tof\tADP\tIN\t_\t6\tcase\t6:case\t_",
        "6\t3\t3\tNUM\tCD\t_\t4\tnmod\t4:nmod:of\t_",
        "7\t,\t,\tPUNCT\t,\t_\t8\tpunct\t8:punct\t_",
        "8\t7:30\t7:30\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "9\t,\t,\tPUNCT\t,\t_\t10\tpunct\t10:punct\t_",
        "10\t+1\t+1\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "11\t,\t,\tPUNCT\t,\t_\t12\tpunct\t12:punct\t_",
        "12\t'72\t'72\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "13\t,\t,\tPUNCT\t,\t_\t14\tpunct\t14:punct\t_",
        "14\t1990s\t1990\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "15\t,\t,\tPUNCT\t,\t_\t16\tpunct\t16:punct\t_",
        "16\tXIV\tXIV\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "17\t,\t,\tPUNCT\t,\t_\t18\tpunct\t18:punct\t_",
        "18\tiv\tiv\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "19\t,\t,\tPUNCT\t,\t_\t21\tpunct\t21:punct\t_",
        "20\t5\t5\tNUM\tCD\t_\t21\tnummod\t21:nummod\t_",
        "21\tM\tM\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "22\t,\t,\tPUNCT\t,\t_\t23\tpunct\t23:punct\t_",
        "23\tMM\tMM\tNUM\tCD\t_\t4\tappos\t4:appos\t_",
        "24\t,\t,\tPUNCT\t,\t_\t26\tpunct\t26:punct\t_",
        "25\t10\t10\tNUM\tCD\t_\t26\tnummod\t26:nummod\t_",
        "26\tmm\tmm\tNUM\tCD\t_\t4\tappos\t4:appos\t_"};
    String written = annotateWithoutTrees(new Sentence("numform", null, conllu));
    String[][] expected = {
        {"twice", "NumForm=Word|NumType=Mult"},
        {"first", "Degree=Pos|NumForm=Word|NumType=Ord"},
        {"two", "NumForm=Word|NumType=Card"},
        {"3", "NumForm=Digit|NumType=Card"},
        {"7:30", "NumForm=Digit|NumType=Card"},
        {"+1", "NumForm=Digit|NumType=Card"},
        {"'72", "NumForm=Digit|NumType=Card"},
        {"1990s", "NumForm=Combi|NumType=Card"},
        {"XIV", "NumForm=Roman|NumType=Card"},
        {"iv", "NumForm=Roman|NumType=Card"},
        {"M", "NumForm=Word|NumType=Card"},
        {"MM", "NumForm=Roman|NumType=Card"},
        {"mm", "NumForm=Word|NumType=Card"},
    };
    for (String[] word : expected) {
      assertEquals(word[0], word[1], column(written, word[0], 5));
    }
    // a word without a NumType has no NumForm
    assertEquals("Definite=Def|PronType=Art", column(written, "the", 5));
  }

  @Test
  public void testRomanNumeral() {
    // Roman letters are a numeral unless a number written with digits comes
    // right before them, in which case they are a multiplier such as M
    assertTrue(UniversalDependenciesFeatureAnnotator.isRomanNumeral("IV", "Title", "NNP"));
    assertTrue(UniversalDependenciesFeatureAnnotator.isRomanNumeral("iv", ",", ","));
    assertTrue(UniversalDependenciesFeatureAnnotator.isRomanNumeral("XIII", null, null));
    assertTrue(UniversalDependenciesFeatureAnnotator.isRomanNumeral("M", "two", "CD"));
    assertTrue(!UniversalDependenciesFeatureAnnotator.isRomanNumeral("M", "5", "CD"));
    assertTrue(!UniversalDependenciesFeatureAnnotator.isRomanNumeral("mm", "10", "CD"));
    assertTrue(!UniversalDependenciesFeatureAnnotator.isRomanNumeral("IVY", "Title", "NNP"));
  }

  @Test
  public void testOrdinals() throws Exception {
    // ordinal adjectives and adverbs are NumType=Ord, and so are ordinal
    // nouns written with digits, but not a noun such as "second"
    String[] conllu = {
        "# sent_id = ordinals",
        "# text = First , the eighth and twenty-first second of Dec. 10th",
        "1\tFirst\tfirst\tADV\tRB\t_\t7\tadvmod\t7:advmod\t_",
        "2\t,\t,\tPUNCT\t,\t_\t1\tpunct\t1:punct\t_",
        "3\tthe\tthe\tDET\tDT\t_\t7\tdet\t7:det\t_",
        "4\teighth\teighth\tADJ\tJJ\t_\t7\tamod\t7:amod\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\ttwenty-first\ttwenty-first\tADJ\tJJ\t_\t4\tconj\t4:conj:and\t_",
        "7\tsecond\tsecond\tNOUN\tNN\t_\t0\troot\t0:root\t_",
        "8\tof\tof\tADP\tIN\t_\t10\tcase\t10:case\t_",
        "9\tDec.\tDec.\tPROPN\tNNP\t_\t10\tcompound\t10:compound\t_",
        "10\t10th\t10th\tNOUN\tNN\t_\t7\tnmod\t7:nmod:of\t_"};
    String written = annotateWithoutTrees(new Sentence("ordinals", null, conllu));
    assertEquals("NumForm=Word|NumType=Ord", column(written, "First", 5));
    assertEquals("Degree=Pos|NumForm=Word|NumType=Ord", column(written, "eighth", 5));
    assertEquals("Degree=Pos|NumForm=Word|NumType=Ord", column(written, "twenty-first", 5));
    assertEquals("Number=Sing", column(written, "second", 5));
    assertEquals("Number=Sing|NumForm=Combi|NumType=Ord", column(written, "10th", 5));
  }

  @Test
  public void testProperNounOrdinals() throws Exception {
    // an ordinal in a name is NumType=Ord as well, written as a word or
    // with digits
    String[] conllu = {
        "# sent_id = proper-ordinals",
        "# text = First Union is on 17th Street",
        "1\tFirst\tFirst\tPROPN\tNNP\t_\t2\tcompound\t2:compound\t_",
        "2\tUnion\tUnion\tPROPN\tNNP\t_\t6\tnsubj\t6:nsubj\t_",
        "3\tis\tbe\tAUX\tVBZ\t_\t6\tcop\t6:cop\t_",
        "4\ton\ton\tADP\tIN\t_\t6\tcase\t6:case\t_",
        "5\t17th\t17th\tPROPN\tNNP\t_\t6\tcompound\t6:compound\t_",
        "6\tStreet\tStreet\tPROPN\tNNP\t_\t0\troot\t0:root\t_"};
    String written = annotateWithoutTrees(new Sentence("proper-ordinals", null, conllu));
    assertEquals("Number=Sing|NumForm=Word|NumType=Ord", column(written, "First", 5));
    assertEquals("Number=Sing|NumForm=Combi|NumType=Ord", column(written, "17th", 5));
    assertEquals("Number=Sing", column(written, "Union", 5));
  }

  @Test
  public void testPassiveParticiples() throws Exception {
    // a VBN with no auxiliary is passive, as is one with a passive
    // auxiliary, but a perfect is not, and neither is a conjunct which
    // shares the perfect's auxiliary, or "been"
    String[] conllu = {
        "# sent_id = passive",
        "# text = Born in Paris , the suspected thief has been arrested , and he has eaten and drunk .",
        "1\tBorn\tbear\tVERB\tVBN\t_\t10\tadvcl\t10:advcl\t_",
        "2\tin\tin\tADP\tIN\t_\t3\tcase\t3:case\t_",
        "3\tParis\tParis\tPROPN\tNNP\t_\t1\tobl\t1:obl:in\t_",
        "4\t,\t,\tPUNCT\t,\t_\t1\tpunct\t1:punct\t_",
        "5\tthe\tthe\tDET\tDT\t_\t7\tdet\t7:det\t_",
        "6\tsuspected\tsuspect\tVERB\tVBN\t_\t7\tamod\t7:amod\t_",
        "7\tthief\tthief\tNOUN\tNN\t_\t10\tnsubj:pass\t10:nsubj:pass\t_",
        "8\thas\thave\tAUX\tVBZ\t_\t10\taux\t10:aux\t_",
        "9\tbeen\tbe\tAUX\tVBN\t_\t10\taux:pass\t10:aux:pass\t_",
        "10\tarrested\tarrest\tVERB\tVBN\t_\t0\troot\t0:root\t_",
        "11\t,\t,\tPUNCT\t,\t_\t15\tpunct\t15:punct\t_",
        "12\tand\tand\tCCONJ\tCC\t_\t15\tcc\t15:cc\t_",
        "13\the\the\tPRON\tPRP\t_\t15\tnsubj\t15:nsubj\t_",
        "14\thas\thave\tAUX\tVBZ\t_\t15\taux\t15:aux\t_",
        "15\teaten\teat\tVERB\tVBN\t_\t10\tconj\t10:conj:and\t_",
        "16\tand\tand\tCCONJ\tCC\t_\t17\tcc\t17:cc\t_",
        "17\tdrunk\tdrink\tVERB\tVBN\t_\t15\tconj\t15:conj:and\t_",
        "18\t.\t.\tPUNCT\t.\t_\t10\tpunct\t10:punct\t_"};
    String written = annotateWithoutTrees(new Sentence("passive", null, conllu));
    assertEquals("Tense=Past|VerbForm=Part|Voice=Pass", column(written, "Born", 5));
    assertEquals("Tense=Past|VerbForm=Part|Voice=Pass", column(written, "suspected", 5));
    assertEquals("Tense=Past|VerbForm=Part|Voice=Pass", column(written, "arrested", 5));
    assertEquals("Tense=Past|VerbForm=Part", column(written, "been", 5));
    assertEquals("Tense=Past|VerbForm=Part", column(written, "eaten", 5));
    assertEquals("Tense=Past|VerbForm=Part", column(written, "drunk", 5));
  }

  @Test
  public void testContractedHadIsNotPassive() throws Exception {
    // "would" takes a bare verb, so 'd with a participle is "had", even
    // when it is tagged MD
    assertEquals("Tense=Past|VerbForm=Part", featsWithoutTrees("cooked",
        "# sent_id = contracted-had",
        "# text = He'd cooked.",
        "1-2\tHe'd\t_\t_\t_\t_\t_\t_\t_\t_",
        "1\tHe\the\tPRON\tPRP\t_\t3\tnsubj\t3:nsubj\t_",
        "2\t'd\twould\tAUX\tMD\t_\t3\taux\t3:aux\t_",
        "3\tcooked\tcook\tVERB\tVBN\t_\t0\troot\t0:root\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_"));
  }

  @Test
  public void testBareGotIsNotPassive() throws Exception {
    // "got" without an auxiliary is "have got" with the "have" left out
    assertEquals("Tense=Past|VerbForm=Part", featsWithoutTrees("Got",
        "# sent_id = got",
        "# text = Got any ideas?",
        "1\tGot\tget\tVERB\tVBN\t_\t0\troot\t0:root\t_",
        "2\tany\tany\tDET\tDT\t_\t3\tdet\t3:det\t_",
        "3\tideas\tidea\tNOUN\tNNS\t_\t1\tobj\t1:obj\tSpaceAfter=No",
        "4\t?\t?\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"));
  }

  /** The Person and Number of a word, written as "1 Sing" */
  static String agreement(String written, String word) {
    String feats = column(written, word, 5);
    assertNotNull(word + " should have a line of its own", feats);
    String person = "-";
    String number = "-";
    for (String feature : feats.split("\\|")) {
      if (feature.startsWith("Person=")) {
        person = feature.substring(7);
      } else if (feature.startsWith("Number=")) {
        number = feature.substring(7);
      }
    }
    return person + " " + number;
  }

  @Test
  public void testVerbAgreement() throws Exception {
    // past tense and non-3rd-person present verbs agree with their subject
    String written = annotateWithoutTrees(new Sentence("agreement", null,
        "# sent_id = agreement",
        "# text = We were here, the police are here, John and I left, and all who volunteered stayed.",
        "1\tWe\twe\tPRON\tPRP\t_\t3\tnsubj\t3:nsubj\t_",
        "2\twere\tbe\tAUX\tVBD\t_\t3\tcop\t3:cop\t_",
        "3\there\there\tADV\tRB\t_\t0\troot\t0:root\tSpaceAfter=No",
        "4\t,\t,\tPUNCT\t,\t_\t8\tpunct\t8:punct\t_",
        "5\tthe\tthe\tDET\tDT\t_\t6\tdet\t6:det\t_",
        "6\tpolice\tpolice\tNOUN\tNN\t_\t8\tnsubj\t8:nsubj\t_",
        "7\tare\tbe\tAUX\tVBP\t_\t8\tcop\t8:cop\t_",
        "8\there\there\tADV\tRB\t_\t3\tconj\t3:conj\tSpaceAfter=No",
        "9\t,\t,\tPUNCT\t,\t_\t13\tpunct\t13:punct\t_",
        "10\tJohn\tJohn\tPROPN\tNNP\t_\t13\tnsubj\t13:nsubj\t_",
        "11\tand\tand\tCCONJ\tCC\t_\t12\tcc\t12:cc\t_",
        "12\tI\tI\tPRON\tPRP\t_\t10\tconj\t10:conj:and\t_",
        "13\tleft\tleave\tVERB\tVBD\t_\t3\tconj\t3:conj\tSpaceAfter=No",
        "14\t,\t,\tPUNCT\t,\t_\t19\tpunct\t19:punct\t_",
        "15\tand\tand\tCCONJ\tCC\t_\t19\tcc\t19:cc\t_",
        "16\tall\tall\tDET\tDT\t_\t19\tnsubj\t19:nsubj\t_",
        "17\twho\twho\tPRON\tWP\t_\t18\tnsubj\t18:nsubj\t_",
        "18\tvolunteered\tvolunteer\tVERB\tVBD\t_\t16\tacl:relcl\t16:acl:relcl\t_",
        "19\tstayed\tstay\tVERB\tVBD\t_\t3\tconj\t3:conj:and\tSpaceAfter=No",
        "20\t.\t.\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_"));
    assertEquals("1 Plur", agreement(written, "were"));
    // a VBP cannot be 3rd person singular, so "the police" is plural
    assertEquals("3 Plur", agreement(written, "are"));
    // the lowest person of the conjuncts, and plural
    assertEquals("1 Plur", agreement(written, "left"));
    // a relative pronoun agrees with its antecedent
    assertEquals("3 Plur", agreement(written, "volunteered"));
    assertEquals("3 Plur", agreement(written, "stayed"));
  }

  @Test
  public void testVerbAgreementWithoutSubject() throws Exception {
    // a VBP with no subject is first person, as in a diary, and "was" is
    // third person
    String written = annotateWithoutTrees(new Sentence("no-subject", null,
        "# sent_id = no-subject",
        "# text = Love it, was great.",
        "1\tLove\tlove\tVERB\tVBP\t_\t0\troot\t0:root\t_",
        "2\tit\tit\tPRON\tPRP\t_\t1\tobj\t1:obj\tSpaceAfter=No",
        "3\t,\t,\tPUNCT\t,\t_\t5\tpunct\t5:punct\t_",
        "4\twas\tbe\tAUX\tVBD\t_\t5\tcop\t5:cop\t_",
        "5\tgreat\tgreat\tADJ\tJJ\t_\t1\tparataxis\t1:parataxis\tSpaceAfter=No",
        "6\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_"));
    assertEquals("1 Sing", agreement(written, "Love"));
    assertEquals("3 Sing", agreement(written, "was"));
  }

  @Test
  public void testCopularWasWithI() throws Exception {
    // the subject of a copula is on its predicate
    assertEquals("Mood=Ind|Number=Sing|Person=1|Tense=Past|VerbForm=Fin", featsWithoutTrees("was",
        "# sent_id = copular-was",
        "# text = I was lax.",
        "1\tI\tI\tPRON\tPRP\t_\t3\tnsubj\t3:nsubj\t_",
        "2\twas\tbe\tAUX\tVBD\t_\t3\tcop\t3:cop\t_",
        "3\tlax\tlax\tADJ\tJJ\t_\t0\troot\t0:root\tSpaceAfter=No",
        "4\t.\t.\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_"));
  }

  @Test
  public void testWasWithAClausalSubjectInAConjunct() throws Exception {
    // the conjunct has a subject of its own, so the I of the first clause
    // does not make this was first person
    assertEquals("Mood=Ind|Number=Sing|Person=3|Tense=Past|VerbForm=Fin", featsWithoutTrees("was",
        "# sent_id = clausal-subject",
        "# text = I left, but what he did was criticized.",
        "1\tI\tI\tPRON\tPRP\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tleft\tleave\tVERB\tVBD\t_\t0\troot\t0:root\tSpaceAfter=No",
        "3\t,\t,\tPUNCT\t,\t_\t9\tpunct\t9:punct\t_",
        "4\tbut\tbut\tCCONJ\tCC\t_\t9\tcc\t9:cc\t_",
        "5\twhat\twhat\tPRON\tWP\t_\t7\tobj\t7:obj\t_",
        "6\the\the\tPRON\tPRP\t_\t7\tnsubj\t7:nsubj\t_",
        "7\tdid\tdo\tVERB\tVBD\t_\t9\tcsubj:pass\t9:csubj:pass\t_",
        "8\twas\tbe\tAUX\tVBD\t_\t9\taux:pass\t9:aux:pass\t_",
        "9\tcriticized\tcriticize\tVERB\tVBN\t_\t2\tconj\t2:conj:but\tSpaceAfter=No",
        "10\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"));
  }

  @Test
  public void testNoTreesWithATreeFile() throws Exception {
    try {
      annotate(SENTENCES[0], "noTrees", "true");
      fail("Expected -treeFile with -noTrees to be reported");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("-noTrees"));
    }
  }

  @Test
  public void testNoTreesWithAddUPOS() throws Exception {
    // addUPOS takes its tags from the trees, so there is nothing for it to use
    try {
      annotateWithoutTrees(SENTENCES[0], "addUPOS", "true");
      fail("Expected -addUPOS with -noTrees to be reported");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("-addUPOS"));
    }
  }

  @Test
  public void testLeavingOutTheTreesIsNotNoTrees() throws Exception {
    // a missing tree file is not taken as asking to annotate without trees:
    // that has to be asked for, so nothing is written
    File conlluFile = IOUtils.writeStringToTempFile(SENTENCES[0].conllu, "annotatortest", "UTF-8");
    conlluFile.deleteOnExit();
    try {
      Properties props = new Properties();
      props.setProperty("conlluFile", conlluFile.getPath());
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (PrintStream out = new PrintStream(bytes, true, "UTF-8")) {
        UniversalDependenciesFeatureAnnotator.annotate(props, out);
      }
      assertEquals("", bytes.toString("UTF-8"));
    } finally {
      conlluFile.delete();
    }
  }

  // ------------------------------------------------------------------
  // the enhanced dependencies go back out as they came in
  // ------------------------------------------------------------------

  @Test
  public void testSharedGovernorKeepsTheSpecific() throws Exception {
    // an enhanced arc which shares its governor with the basic arc keeps
    // its own relation, not the basic one
    String written = annotate(SENTENCES[1]);
    assertEquals("2:obl:in", column(written, "Paris", 8));
    assertEquals("2:obl:in|4:conj:and", column(written, "London", 8));
  }

  @Test
  public void testEmptyNodeIsWritten() throws Exception {
    // the empty word has a line of its own, and the words whose enhanced
    // arcs point at it keep those arcs and no others
    String written = annotate(SENTENCES[EMPTY_NODE]);
    boolean found = false;
    for (String line : written.split("\\R")) {
      if (line.startsWith("5.1\t")) {
        found = true;
      }
    }
    assertTrue(written, found);
    assertEquals("5.1:nsubj", column(written, "Mary", 8));
    assertEquals("5.1:obj", column(written, "pears", 8));
  }

  @Test
  public void testMultiWordTokenMiscIsKept() throws Exception {
    assertEquals("Gloss=do+not", column(annotate(SENTENCES[5]), "don't", 9));
  }

  @Test
  public void testPartialDepsKeepsEveryWord() throws Exception {
    // her has no enhanced dependencies of its own, so it is not in the
    // enhanced graph.  the rows are written from the basic graph instead,
    // which has every word, rather than her being left out of the file
    String written = annotate(SENTENCES[PARTIAL_DEPS]);
    for (String word : new String[] {"I", "saw", "her", "."}) {
      assertNotNull(word + " should have a line of its own", column(written, word, 1));
    }
  }
}
