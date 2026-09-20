package edu.stanford.nlp.trees.ud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author John Bauer
 */
public class UniversalDependenciesConverterITest {
  @Test
  public void testAddFeatures() throws Exception {
    assertEquals(UniversalDependenciesConverterTest.written(
        "# sent_id = 0",
        "# sent_id = relcl",
        "# text = the man who I saw left",
        "1\tthe\tthe\tDET\tDT\tDefinite=Def|PronType=Art\t2\tdet\t2:det\t_",
        "2\tman\tman\tNOUN\tNN\tNumber=Sing\t6\tnsubj\t5:obj|6:nsubj\t_",
        "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t2:ref\t_",
        "4\tI\tI\tPRON\tPRP\tCase=Nom|Number=Sing|Person=1|PronType=Prs\t5\tnsubj\t5:nsubj\t_",
        "5\tsaw\tsee\tVERB\tVBD\tMood=Ind|Tense=Past|VerbForm=Fin\t2\tacl:relcl\t2:acl:relcl\t_",
        "6\tleft\tleave\tVERB\tVBD\tMood=Ind|Tense=Past|VerbForm=Fin\t0\troot\t0:root\t_",
        "",
        "# sent_id = 1",
        "# sent_id = coord",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\tCase=Nom|Gender=Masc|Number=Sing|Person=3|PronType=Prs\t2\tnsubj\t2:nsubj\t_",
        "2\tworks\twork\tVERB\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t0\troot\t0:root\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t4:case\t_",
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t2:obl:in\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t2:obl:in|4:conj:and\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_",
        "",
        "# sent_id = 2",
        "# sent_id = mwt",
        "# text = it's fine",
        "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\tGloss=it+is",
        "1\tit\tit\tPRON\tPRP\tCase=Nom|Gender=Neut|Number=Sing|Person=3|PronType=Prs\t3\tnsubj\t3:nsubj\t_",
        "2\t's\tbe\tAUX\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t3\tcop\t3:cop\t_",
        "3\tfine\tfine\tADJ\tJJ\tDegree=Pos\t0\troot\t0:root\tSpaceAfter=No"),
        UniversalDependenciesConverterTest.convert("outputRepresentation", "enhanced", "addFeatures", "true"));
  }
}
