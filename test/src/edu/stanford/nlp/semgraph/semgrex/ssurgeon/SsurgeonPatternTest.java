package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/** Tests the pieces of the {@link SsurgeonPattern} command line which can be tested without the command line */
public class SsurgeonPatternTest {

  private static final String MWT_SENTENCE = String.join("\n",
    "# text = Vamos al mercado, ¿no?",
    "1\tVamos\tir\tVERB\t_\t_\t0\troot\t_\t_",
    "2-3\tal\t_\t_\t_\t_\t_\t_\t_\t_",
    "2\ta\ta\tADP\t_\t_\t4\tcase\t_\t_",
    "3\tel\tel\tDET\t_\t_\t4\tdet\t_\t_",
    "4\tmercado\tmercado\tNOUN\t_\t_\t1\tobl\t_\tSpaceAfter=No",
    "5\t,\t,\tPUNCT\t_\t_\t7\tpunct\t_\t_",
    "6\t¿\t¿\tPUNCT\t_\t_\t7\tpunct\t_\tSpaceAfter=No",
    "7\tno\tno\tADV\t_\t_\t1\tadvmod\t_\tSpaceAfter=No",
    "8\t?\t?\tPUNCT\t_\t_\t1\tpunct\t_\t_",
    "",
    "");

  private static CoreMap readSentence(String conllu) throws IOException {
    File file = File.createTempFile("ssurgeon", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    List<Annotation> docs = new CoNLLUReader().readCoNLLUFile(file.getPath());
    return docs.get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
  }

  private static SemanticGraph readGraph(String conllu) throws IOException {
    return readSentence(conllu).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
  }

  @Test
  public void sentenceText() throws IOException {
    SemanticGraph graph = readGraph(MWT_SENTENCE);
    assertEquals("Vamos al mercado, ¿no?", SsurgeonPattern.sentenceText(graph));
  }

  @Test
  public void sentenceTextAfterEdit() throws IOException {
    SemanticGraph graph = readGraph(MWT_SENTENCE);
    List<SsurgeonPattern> patterns = Ssurgeon.readFromEditText("{word:mercado}=m\nEditNode -node m -word supermercado", "UniversalEnglish");
    SemanticGraph edited = patterns.get(0).iterate(graph).first();
    assertEquals("Vamos al supermercado, ¿no?", SsurgeonPattern.sentenceText(edited));
  }

  /** "I" is the subject of "go" only in the enhanced graph */
  private static final String ENHANCED_SENTENCE = String.join("\n",
    "# text = I want to go : now : ok",
    "1\tI\tI\tPRON\t_\t_\t2\tnsubj\t2:nsubj|4:nsubj:xsubj\t_",
    "2\twant\twant\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tto\tto\tPART\t_\t_\t4\tmark\t4:mark\t_",
    "4\tgo\tgo\tVERB\t_\t_\t2\txcomp\t2:xcomp\t_",
    "5\t:\t:\tPUNCT\t_\t_\t2\tpunct\t2:punct\t_",
    "6\tnow\tnow\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "7\t:\t:\tPUNCT\t_\t_\t2\tpunct\t2:punct\t_",
    "8\tok\tok\tINTJ\t_\t_\t2\tdiscourse\t2:discourse\t_",
    "",
    "");

  @Test
  public void iterateSearchesEnhanced() throws IOException {
    CoreMap sentence = readSentence(ENHANCED_SENTENCE);
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >/nsubj:xsubj/@enhanced {}\nEditNode -node gov -lemma GO", "UniversalEnglish").get(0);
    assertTrue(pattern.searchesBeyondBasic());

    Pair<SemanticGraph, Boolean> result = pattern.iterate(basic, enhanced);
    assertTrue(result.second());
    assertEquals("GO", result.first().getNodeByIndex(4).lemma());
    // the words are shared, so the enhanced graph sees the edit as well
    assertEquals("GO", enhanced.getNodeByIndex(4).lemma());
    // the edit only touched a word, so the structure of the basic graph is unchanged
    assertEquals(basic, result.first());
  }

  @Test
  public void iterateWithoutEnhanced() throws IOException {
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >/nsubj:xsubj/@enhanced {}\nEditNode -node gov -lemma GO", "UniversalEnglish").get(0);
    assertThrows(IllegalStateException.class, () -> pattern.iterate(basic));
    assertThrows(IllegalStateException.class, () -> pattern.iterate(basic, null));

    SsurgeonPattern basicOnly = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    assertFalse(basicOnly.searchesBeyondBasic());
    assertTrue(basicOnly.iterate(basic).second());
  }

  @Test
  public void iterateStopsWhenEnhancedNoLongerFits() throws IOException {
    CoreMap sentence = readSentence(ENHANCED_SENTENCE);
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

    // deleting a word means the enhanced graph no longer describes the
    // words of the basic graph, so a pattern which searches the enhanced
    // graph stops after the first deletion
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:/:/}=p <punct@enhanced {}\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    Pair<SemanticGraph, Boolean> result = pattern.iterate(basic, enhanced);
    assertTrue(result.second());
    assertEquals(7, result.first().size());

    // a pattern on the basic graph alone deletes both, even with the enhanced graph given.
    // renumbering the words changes their shared labels, so this needs a fresh sentence
    sentence = readSentence(ENHANCED_SENTENCE);
    basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    SsurgeonPattern basicOnly = Ssurgeon.readFromEditText("{word:/:/}=p <punct {}\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    result = basicOnly.iterate(basic, enhanced);
    assertTrue(result.second());
    assertEquals(6, result.first().size());
  }
}
