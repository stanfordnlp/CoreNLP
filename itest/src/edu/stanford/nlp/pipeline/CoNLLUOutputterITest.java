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

    public void testSimpleSentence() throws IOException {
        Annotation ann = new Annotation("CoNLL-U is neat. Better than XML.");
        pipeline.annotate(ann);
        String actual = new CoNLLUOutputter().print(ann);
        String expected = "1\tCoNLL-U\tconll-u\tNOUN\tNN\tNumber=Sing\t3\tnsubj\t_\t_\n" +
                "2\tis\tbe\tVERB\tVBZ\tMood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin\t3\tcop\t_\t_\n" +
                "3\tneat\tneat\tADJ\tJJ\tDegree=Pos\t0\troot\t_\t_\n" +
                "4\t.\t.\tPUNCT\t.\t_\t3\tpunct\t_\t_\n" +
                "\n" +
                "1\tBetter\tbetter\tADV\tRBR\tDegree=Cmp\t0\troot\t_\t_\n" +
                "2\tthan\tthan\tADP\tIN\t_\t3\tcase\t_\t_\n" +
                "3\tXML\txml\tNOUN\tNN\tNumber=Sing\t1\tnmod\t_\t_\n" +
                "4\t.\t.\tPUNCT\t.\t_\t1\tpunct\t_\t_\n\n";
        assertEquals(expected, actual);
    }

}
