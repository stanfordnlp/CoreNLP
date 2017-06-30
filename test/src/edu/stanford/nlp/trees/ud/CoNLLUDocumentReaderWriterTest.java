package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import junit.framework.TestCase;

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

/**
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentReaderWriterTest extends TestCase {

    private static String MULTIWORD_TEST_INPUT =
            "1     I         I      PRON    PRP   Case=Nom|Number=Sing|Person=1     2   nsubj   _   _\n" +
            "2-3   haven't   _      _       _     _                                 _   _   _   _\n" +
            "2     have      have   VERB    VBP    Number=Sing|Person=1|Tense=Pres   0   root   _   _\n" +
            "3     not       not    PART    RB    Negative=Neg                      2   neg   _   _\n" +
            "4     a         a      DET     DT    Definite=Ind|PronType=Art         5   det   _   _\n" +
            "5     clue      clue   NOUN    NN    Number=Sing                       2   dobj   _   _\n" +
            "6     .         .      PUNCT   .     _                                 2   punct   _   _\n\n";

    private static String COMMENT_TEST_INPUT =
            "#comment line 1\n" +
            "#comment line 2\n" +
            "1     I         I      PRON    PRP   Case=Nom|Number=Sing|Person=1     2   nsubj   _   _\n" +
            "2     have      have   VERB    VBP    Number=Sing|Person=1|Tense=Pres   0   root   _   _\n" +
            "3     not       not    PART    RB    Negative=Neg                      2   neg   _   _\n" +
            "4     a         a      DET     DT    Definite=Ind|PronType=Art         5   det   _   _\n" +
            "5     clue      clue   NOUN    NN    Number=Sing                       2   dobj   _   _\n" +
            "6     .         .      PUNCT   .     _                                 2   punct   _   _\n\n";

    private static String EXTRA_DEPS_TEST_INPUT =
            "1     They       They       PRON    PRP    _    2   nsubj   2:nsubj|4:nsubj         _\n" +
            "2     buy        buy        VERB    VBP    _    0   root    0:root               _\n" +
            "3     and        and        CONJ    CC     _    2   cc      2:cc               _\n" +
            "4     sell       sell       VERB    VBP    _    5   conj    5:conj               _\n" +
            "5     books      book       NOUN    NNS    _    2   dobj    2:dobj|4:dobj          _\n" +
            "6     ,          ,          PUNCT   ,      _    5   punct   5:punct               _\n" +
            "7     newspapers newspaper  NOUN    NNS    _    5   conj    2:dobj|4:dobj|5:conj   _\n" +
            "8     and        and        CONJ    CC     _    5   cc      5:cc               _\n" +
            "9     magazines  magazine   NOUN    NNS    _    5   conj    2:dobj|4:dobj|5:conj   _\n" +
            "10    .          .          PUNCT   .      _    2   punct   2:punct               _\n\n";

    private static String EXTRA_DEPS_TEST_EMPTY_NODEINPUT =
            "1     They       They       PRON    PRP    _    2   nsubj   2:nsubj|2.1:nsubj|2.2:nsubj         _\n" +
            "2     buy        buy        VERB    VBP    _    0   root    0:root               _\n" +
            "2.1     buy        buy        VERB    VBP    _    _   _    2:conj:and               _\n" +
            "2.2     buy        buy        VERB    VBP    _    _   _    2:conj:and               _\n" +
            "3     books      book       NOUN    NNS    _    2   dobj    2:dobj          _\n" +
            "4     ,          ,          PUNCT   ,      _    3   punct   3:punct               _\n" +
            "5     newspapers newspaper  NOUN    NNS    _    3   conj    2.1:dobj|3:conj   _\n" +
            "6     and        and        CONJ    CC     _    3   cc      3:cc               _\n" +
            "7     magazines  magazine   NOUN    NNS    _    3   conj    2.2:dobj|3:conj   _\n" +
            "8    .          .          PUNCT   .      _    2   punct   2:punct               _\n\n";



    public void testMultiWords() {
        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        Reader stringReader = new StringReader(MULTIWORD_TEST_INPUT);
        Iterator<SemanticGraph> it = reader.getIterator(stringReader);

        SemanticGraph sg = it.next();
        assertNotNull(sg);
        assertFalse("The input only contains one dependency tree.", it.hasNext());
        assertEquals("[have/VBP nsubj>I/PRP neg>not/RB dobj>[clue/NN det>a/DT] punct>./.]", sg.toCompactString(true));

        for (IndexedWord iw : sg.vertexListSorted()) {
            if (iw.index() != 2 && iw.index() != 3) {
                assertEquals("", iw.originalText());
            } else {
                assertEquals("haven't", iw.originalText());
            }
        }
        assertEquals(Integer.valueOf(3), sg.getNodeByIndex(2).get(CoreAnnotations.LineNumberAnnotation.class));

    }

    public void testComment() {
        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        Reader stringReader = new StringReader(COMMENT_TEST_INPUT);
        Iterator<SemanticGraph> it = reader.getIterator(stringReader);

        SemanticGraph sg = it.next();
        assertNotNull(sg);
        assertFalse("The input only contains one dependency tree.", it.hasNext());
        assertEquals("[have/VBP nsubj>I/PRP neg>not/RB dobj>[clue/NN det>a/DT] punct>./.]", sg.toCompactString(true));
        assertEquals(Integer.valueOf(3), sg.getNodeByIndex(1).get(CoreAnnotations.LineNumberAnnotation.class));

        assertEquals(2, sg.getComments().size());
        assertEquals("#comment line 1", sg.getComments().get(0));

    }


    /**
     * Tests whether extra dependencies are correctly parsed.
     */
    public void testExtraDependencies() {
        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        Reader stringReader = new StringReader(EXTRA_DEPS_TEST_INPUT);
        Iterator<SemanticGraph> it = reader.getIterator(stringReader);

        SemanticGraph sg = it.next();
        assertNotNull(sg);
        assertFalse("The input only contains one dependency tree.", it.hasNext());
        assertTrue(sg.containsEdge(sg.getNodeByIndex(4), sg.getNodeByIndex(1)));
        assertTrue(sg.containsEdge(sg.getNodeByIndex(2), sg.getNodeByIndex(7)));
        assertTrue(sg.containsEdge(sg.getNodeByIndex(4), sg.getNodeByIndex(7)));


    }


    /**
     * Tests whether reading a Semantic Graph and printing it
     * is equal to the original input.
     */
    private void testSingleReadAndWrite(String input) {
        String clean = input.replaceAll("[\\t ]+", "\t");

        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();

        Reader stringReader = new StringReader(clean);
        Iterator<SemanticGraph> it = reader.getIterator(stringReader);

        SemanticGraph sg = it.next();

        String output = writer.printSemanticGraph(sg);

        assertEquals(clean, output);

    }


    public void testReadingAndWriting() {
        testSingleReadAndWrite(COMMENT_TEST_INPUT);
        testSingleReadAndWrite(EXTRA_DEPS_TEST_INPUT);
        testSingleReadAndWrite(MULTIWORD_TEST_INPUT);
        testSingleReadAndWrite(EXTRA_DEPS_TEST_EMPTY_NODEINPUT);
    }

}
