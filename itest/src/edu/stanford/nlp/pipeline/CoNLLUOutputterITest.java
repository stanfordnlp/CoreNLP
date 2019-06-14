package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Properties;

/**
 * A very basic test for {@link edu.stanford.nlp.pipeline.CoNLLUOutputter}.
 *
 * @author Sebastian Schuster
 * @author Gabor Angeli
 */
public class CoNLLUOutputterITest extends TestCase {

    static StanfordCoreNLP pipeline =
            new StanfordCoreNLP(new Properties() {{
                setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, udfeats");
                setProperty("parse.keepPunct", "true");
            }});

    /** Make sure that an invalid dependency type barfs. */
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

    public void testSimpleSentence() throws IOException {
        Annotation ann = new Annotation("CoNLL-U is neat. Better than XML.");
        pipeline.annotate(ann);
        String actual = new CoNLLUOutputter("enhanced").print(ann);
        String expected = "1\tCoNLL-U\tconll-u\tNOUN\tNN\tNumber=Sing\t3\tnsubj\t3:nsubj\t_\n" +
                "2\tis\tbe\tVERB\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t3\tcop\t3:cop\t_\n" +
                "3\tneat\tneat\tADJ\tJJ\tDegree=Pos\t0\troot\t0:root\t_\n" +
                "4\t.\t.\tPUNCT\t.\t_\t3\tpunct\t3:punct\t_\n" +
                "\n" +
                "1\tBetter\tbetter\tADV\tRBR\tDegree=Cmp\t0\troot\t0:root\t_\n" +
                "2\tthan\tthan\tADP\tIN\t_\t3\tcase\t3:case\t_\n" +
                "3\tXML\txml\tNOUN\tNN\tNumber=Sing\t1\tobl\t1:obl:than\t_\n" +
                "4\t.\t.\tPUNCT\t.\t_\t1\tpunct\t1:punct\t_\n\n";
        assertEquals(expected, actual);
    }

}
