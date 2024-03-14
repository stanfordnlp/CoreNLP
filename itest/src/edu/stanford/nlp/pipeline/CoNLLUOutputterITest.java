package edu.stanford.nlp.pipeline;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Properties;

import org.junit.Test;

/**
 * A very basic test for {@link edu.stanford.nlp.pipeline.CoNLLUOutputter}.
 *
 * @author Sebastian Schuster
 * @author Gabor Angeli
 */
public class CoNLLUOutputterITest {

    static StanfordCoreNLP pipeline =
            new StanfordCoreNLP(new Properties() {{
                setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, udfeats");
                setProperty("parse.keepPunct", "true");
            }});

    /** Make sure that an invalid dependency type barfs. */
    @Test
    public void testInvalidOutputter() throws IOException {
        try {
            Annotation ann = new Annotation("CoNLL-U is neat. Better than XML.");
            pipeline.annotate(ann);
            String actual = new CoNLLUOutputter("this should fail").print(ann);
            throw new AssertionError("This should have failed");
        } catch (IllegalArgumentException e) {
            // yay
        }
    }

    @Test
    public void testSimpleSentence() throws IOException {
        Annotation ann = new Annotation("CoNLL-U is neat. Better than XML.");
        pipeline.annotate(ann);
        String actual = new CoNLLUOutputter("enhanced").print(ann);
        String expected = "1\tCoNLL\tconll\tNOUN\tNN\tNumber=Sing\t3\tcompound\t3:compound\tSpaceAfter=No\n" +
            "2\t-\t-\tPUNCT\tHYPH\t_\t3\tpunct\t3:punct\tSpaceAfter=No\n" +
            "3\tU\tu\tNOUN\tNN\tNumber=Sing\t5\tnsubj\t5:nsubj\t_\n" +
            "4\tis\tbe\tAUX\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t5\tcop\t5:cop\t_\n" +
            "5\tneat\tneat\tADJ\tJJ\tDegree=Pos\t0\troot\t0:root\tSpaceAfter=No\n" +
            "6\t.\t.\tPUNCT\t.\t_\t5\tpunct\t5:punct\t_\n" +
            "\n" +
            "1\tBetter\tgood\tADJ\tJJR\tDegree=Cmp\t0\troot\t0:root\t_\n" +
            "2\tthan\tthan\tADP\tIN\t_\t3\tcase\t3:case\t_\n" +
            "3\tXML\txml\tNOUN\tNN\tNumber=Sing\t1\tobl\t1:obl:than\tSpaceAfter=No\n" +
            "4\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\tSpaceAfter=No\n\n";
        assertEquals(expected, actual);
    }

}

