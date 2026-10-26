package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

public class CoNLLUDocumentWriterTest {

  /** A sentence from AnCora with an empty word right after a multiword token */
  private static final String MWT_THEN_EMPTY = String.join("\n",
    "# sent_id = CESS-CAST-P-19981101-134-s20",
    "# text = Para no dormirse empieza a tocar de pie.",
    "# orig_file_sentence 089#68",
    "1\tPara\tpara\tADP\tsps00\t_\t3\tmark\t3:mark\tEntity=(CESSCASTP19981101134c9--6",
    "2\tno\tno\tADV\trn\tPolarity=Neg\t3\tadvmod\t3:advmod\t_",
    "3-4\tdormirse\t_\t_\t_\t_\t_\t_\t_\t_",
    "3\tdormir\tdormir\tVERB\tvmn0000\tVerbForm=Inf\t7\tadvcl\t7:advcl\tArgTem=argM:fin",
    "4\tse\tél\tPRON\t_\tPerson=3|PrepCase=Npr|PronType=Prs|Reflex=Yes\t3\texpl:pv\t3:expl:pv\t_",
    "4.1\t_\t_\tPRON\tp\t_\t_\t_\t5:nsubj\tArgTem=arg0:agt|Entity=(CESSCASTP19981101134c1-person-1-CorefType:ident,gstype:spec)",
    "5\tempieza\tempezar\tVERB\tvmip3s0\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t0\troot\t0:root\t_",
    "6\ta\ta\tADP\tsps00\t_\t7\tmark\t7:mark\t_",
    "7\ttocar\ttocar\tVERB\tvmn0000\tVerbForm=Inf\t5\txcomp\t5:xcomp\t_",
    "8\tde\tde\tADP\trg\tExtPos=ADV\t9\tcase\t9:case\tArgTem=argM:mnr|MWE=de_pie|MWEPOS=ADV",
    "9\tpie\tpie\tNOUN\t_\t_\t5\tobl\t5:obl\tSpaceAfter=No",
    "10\t.\t.\tPUNCT\tfp\tPunctType=Peri\t5\tpunct\t5:punct\tEntity=CESSCASTP19981101134c9)",
    "", "");

  /** Reads the one sentence of some CoNLL-U */
  private static CoreMap readSentence(String conllu) throws IOException {
    File file = File.createTempFile("writer", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    List<Annotation> docs = new CoNLLUReader().readCoNLLUFile(file.getPath());
    return docs.get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
  }

  /** Writes a sentence the way the Ssurgeon command line does */
  private static String write(CoreMap sentence, SemanticGraph basic, SemanticGraph enhanced) {
    List<String> comments = new ArrayList<>(sentence.get(CoreAnnotations.CommentsAnnotation.class));
    return new CoNLLUDocumentWriter().printSemanticGraph(basic, enhanced, false, comments);
  }

  /**
   * The range line of a multiword token is written when an empty word
   * follows its last word, however the words were added to the graph.
   * A graph built or edited elsewhere, such as by Ssurgeon, may have the
   * empty word 4.1 before the word 4 it follows.
   */
  @Test
  public void testMWTThenEmptyWord() throws IOException {
    CoreMap sentence = readSentence(MWT_THEN_EMPTY);
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertEquals(MWT_THEN_EMPTY, write(sentence, basic, enhanced));

    // the same graph with the empty words added first
    SemanticGraph emptyFirst = new SemanticGraph();
    for (IndexedWord word : enhanced.vertexListSorted()) {
      if (word.getEmptyIndex() != 0) {
        emptyFirst.addVertex(word);
      }
    }
    for (IndexedWord word : enhanced.vertexListSorted()) {
      if (word.getEmptyIndex() == 0) {
        emptyFirst.addVertex(word);
      }
    }
    for (SemanticGraphEdge edge : enhanced.edgeIterable()) {
      emptyFirst.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    emptyFirst.setRoots(enhanced.getRoots());
    assertEquals(MWT_THEN_EMPTY, write(sentence, basic, emptyFirst));
  }
}
