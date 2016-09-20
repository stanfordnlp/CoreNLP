package edu.stanford.nlp.international.spanish.process;

import java.io.StringReader;
import java.util.List;

import junit.framework.TestCase;

import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;

/**
 * SpanishTokenizer regression tests.
 *
 * @author Jon Gauthier
 */
public class SpanishTokenizerTest extends TestCase {

    private void testOffset(String input, int[] beginOffsets, int[] endOffsets) {
        TokenizerFactory<CoreLabel> tf = SpanishTokenizer.ancoraFactory();
        Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(input));
        List<CoreLabel> tokens = tokenizer.tokenize();

        assertEquals("Number of tokens doesn't match reference '" + input + "'", beginOffsets.length, tokens.size());
        for (int i = 0; i < beginOffsets.length; i++) {
            assertEquals("Char begin offset of word " + i + " deviates from reference '" + input + "'",
                    beginOffsets[i], tokens.get(i).beginPosition());
            assertEquals("Char end offset of word " + i + " deviates from reference '" + input + "'",
                    endOffsets[i], tokens.get(i).endPosition());
        }
    }

    public void testCliticPronounOffset() {
        // will be tokenized into "tengo que decir te algo"
        testOffset("tengo que decirte algo", new int[] {0, 6, 10, 15, 18}, new int[] {5, 9, 15, 17, 22});
    }

    public void testIr() {
        // "ir" is a special case -- it is a verb ending without a stem!
        testOffset("tengo que irme ahora", new int[] {0, 6, 10, 12, 15}, new int[] {5, 9, 12, 14, 20});
    }

    public void testContractionOffsets() {
        // y de el y
        testOffset("y del y", new int[] {0, 2, 3, 6}, new int[] {1, 3, 5, 7});

        // y a el y
        testOffset("y al y", new int[] {0, 2, 3, 5}, new int[] {1, 3, 4, 6});

        // y con mí y
        testOffset("y conmigo y", new int[] {0, 2, 5, 10}, new int[] {1, 5, 9, 11});
    }

    public void testCompoundOffset() {
        testOffset("y abc-def y", new int[] {0, 2, 5, 6, 10}, new int[] {1, 5, 6, 9, 11});
        testOffset("y abc - def y", new int[] {0, 2, 6, 8, 12}, new int[] {1, 5, 7, 11, 13});
    }

}
