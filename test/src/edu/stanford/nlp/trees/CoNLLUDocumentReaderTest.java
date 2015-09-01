package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import junit.framework.TestCase;

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

/**
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentReaderITest extends TestCase {

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
            "1     They       They       PRON    PRP    _    2   nsubj   4:nsubj         _\n" +
            "2     buy        buy        VERB    VBP    _    0   root    _               _\n" +
            "3     and        and        CONJ    CC     _    2   cc      _               _\n" +
            "4     sell       sell       VERB    VBP    _    5   conj    _               _\n" +
            "5     books      book       NOUN    NNS    _    2   dobj    4:dobj          _\n" +
            "6     ,          ,          PUNCT   ,      _    5   punct   _               _\n" +
            "7     newspapers newspaper  NOUN    NNS    _    5   conj    2:dobj|4:dobj   _\n" +
            "8     and        and        CONJ    CC     _    5   cc      _               _\n" +
            "9     magazines  magazine   NOUN    NNS    _    5   conj    2:dobj|4:dobj   _\n" +
            "10    .          .          PUNCT   .      _    2   punct   _               _\n\n";


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
    }

    public void testComment() {
        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        Reader stringReader = new StringReader(COMMENT_TEST_INPUT);
        Iterator<SemanticGraph> it = reader.getIterator(stringReader);

        SemanticGraph sg = it.next();
        assertNotNull(sg);
        assertFalse("The input only contains one dependency tree.", it.hasNext());
        assertEquals("[have/VBP nsubj>I/PRP neg>not/RB dobj>[clue/NN det>a/DT] punct>./.]", sg.toCompactString(true));
    }

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
}
