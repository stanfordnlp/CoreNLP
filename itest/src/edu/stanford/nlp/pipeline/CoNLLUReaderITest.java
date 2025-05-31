package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

/**
 * Test reading in a CoNLL-U document.  Compare the Annotation created from file to a gold one.
 */
public class CoNLLUReaderITest {

  public String examplePath = String.format("edu/stanford/nlp/pipeline/es-example.conllu");

  static final String[] EXPECTED_SENTENCE_TEXT = {
    "Pero la  existencia de dos recién nacidos en la misma caja sólo podía deberse a un descuido de fábrica.",
    "De allí las rebajas."
  };
  static final String EXPECTED_TEXT = String.join(" ", EXPECTED_SENTENCE_TEXT) + " ";

  static final String[][] EXPECTED_WORD_TEXT = {
    {"Pero", "la", "existencia", "de", "dos", "recién", "nacidos", "en", "la", "misma", "caja", "sólo", "podía", "deber", "se", "a", "un", "descuido", "de", "fábrica", "."},
    {"De", "allí", "las", "rebajas", "."},
  };

  static final String[][] EXPECTED_LEMMA_TEXT = {
    {"pero", "el", "existencia", "de", "dos", "recién", "nacido", "en", "el", "mismo", "caja", "sólo", "poder", "deber", "él", "a", "uno", "descuido", "de", "fábrica", "."},
    {"de", "allí", "el", "rebaja", "."},
  };

  static final String[][] EXPECTED_UPOS = {
    {"CCONJ", "DET", "NOUN", "ADP", "NUM", "ADV", "ADJ", "ADP", "DET", "DET", "NOUN", "ADV", "AUX", "VERB", "PRON", "ADP", "DET", "NOUN", "ADP", "NOUN", "PUNCT"},
    {"ADP", "ADV", "DET", "NOUN", "PUNCT"},
  };
  static final String[][] EXPECTED_XPOS = {
    {"cc", "da0fs0", "ncfs000", "sps00", "dn0cp0", "rg", "aq0mpp", "sps00", "da0fs0", "di0fs0", "ncfs000", "rg", "vmii3s0", "vmn0000", null, "sps00", "di0ms0", "ncms000", "sps00", "ncfs000", "fp"},
    {"sps00", "rg", "da0fp0", "ncfp000", "fp"},
  };

  static final String[][] EXPECTED_MISC = {
    {null, null, "ArgTem=arg1:tem", null, null, "ArgTem=argM:tmp", "ArgTem=arg1:tem", null, null, null, "ArgTem=argM:loc", "ArgTem=argM:adv", null, null, null, null, null, "ArgTem=arg2:atr", null, "ArgTem=arg0:agt", null},
    {null, null, null, null, null},
  };

  static final String[][] EXPECTED_FEATS = {
    {
      null,
      "Definite=Def|Gender=Fem|Number=Sing|PronType=Art",
      "Gender=Fem|Number=Sing",
      null,
      "Number=Plur|NumForm=Word|NumType=Card",
      null,
      "Gender=Masc|Number=Plur|VerbForm=Part",
      null,
      "Definite=Def|Gender=Fem|Number=Sing|PronType=Art",
      "Gender=Fem|Number=Sing|PronType=Dem",
      "Gender=Fem|Number=Sing",
      null,
      "Mood=Ind|Number=Sing|Person=3|Tense=Imp|VerbForm=Fin",
      "VerbForm=Inf",
      "Case=Acc|Person=3|PrepCase=Npr|PronType=Prs|Reflex=Yes",
      null,
      "Definite=Ind|Gender=Masc|Number=Sing|PronType=Art",
      "Gender=Masc|Number=Sing",
      null,
      "Gender=Fem|Number=Sing",
      "PunctType=Peri",
    },
    {
      null,
      null,
      "Definite=Def|Gender=Fem|Number=Plur|PronType=Art",
      "Gender=Fem|Number=Plur",
      "PunctType=Peri",
    }
  };

  static final String[][] EXPECTED_RELNS = {
    { "advmod", "det", "nsubj", "case", "nummod", "advmod", "amod", "case", "det", "det", "nmod", "advmod", "aux", "root",
      "expl:pv", "case", "det", "obl:arg", "case", "nmod", "punct" },
    { "case", "advmod", "det", "root", "punct" },
  };
  static final int[][] EXPECTED_HEADS = {
    { 14, 3, 14, 7, 7, 7, 3, 11, 11, 9, 3, 14, 14, 0, 14, 18, 18, 14, 20, 18, 14 },
    { 2, 4, 4, 0, 4 },
  };

  @Test
  public void testReadingInCoNLLUFile() throws ClassNotFoundException, IOException {
    Annotation readInDocument = readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(examplePath).get(0);

    assertTrue(readInDocument.containsKey(CoreAnnotations.TextAnnotation.class));
    assertTrue(readInDocument.containsKey(CoreAnnotations.TokensAnnotation.class));
    assertTrue(readInDocument.containsKey(CoreAnnotations.SentencesAnnotation.class));
    assertEquals(3, readInDocument.keySet().size());

    // Compare text of the document and its sentences
    assertEquals(EXPECTED_TEXT, readInDocument.get(CoreAnnotations.TextAnnotation.class));
    List<CoreMap> sentences = readInDocument.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(EXPECTED_SENTENCE_TEXT.length, sentences.size());
    for (int i = 0; i < EXPECTED_SENTENCE_TEXT.length; ++i) {
      assertEquals(EXPECTED_SENTENCE_TEXT[i], sentences.get(i).get(CoreAnnotations.TextAnnotation.class));
    }

    // Compare sentence ids
    // Check that the enhanced dependencies exist
    //   (these sentences should all have them, but we will check it
    //    more thoroughly for the Empty test sentence)
    // Check number of keys on each sentence
    for (int i = 0; i < sentences.size(); ++i) {
      assertEquals(Integer.valueOf(i), sentences.get(i).get(CoreAnnotations.SentenceIndexAnnotation.class));
      assertTrue(sentences.get(i).containsKey(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
      assertTrue(sentences.get(i).containsKey(CoreAnnotations.CommentsAnnotation.class));
      assertEquals(6, sentences.get(i).keySet().size());
    }

    // Check the document tokens and the sentence tokens lists are the same
    // The composite list on the document level should just be the sentence tokens gathered into one list
    List<CoreMap> allTokens = new ArrayList<>();
    for (int i = 0; i < sentences.size(); ++i) {
      allTokens.addAll(sentences.get(i).get(CoreAnnotations.TokensAnnotation.class));
    }
    assertEquals(readInDocument.get(CoreAnnotations.TokensAnnotation.class), allTokens);

    // Check the text on each of the words
    // Check the lemmas
    // Check indices and a couple other annotations we expect to be here
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      assertEquals(EXPECTED_WORD_TEXT[i].length, tokens.size());
      assertEquals(EXPECTED_LEMMA_TEXT[i].length, tokens.size());
      assertEquals(EXPECTED_UPOS[i].length, tokens.size());
      assertEquals(EXPECTED_XPOS[i].length, tokens.size());
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);
        assertEquals(EXPECTED_WORD_TEXT[i][j], token.value());
        assertEquals(EXPECTED_WORD_TEXT[i][j], token.word());
        assertEquals(EXPECTED_WORD_TEXT[i][j], token.get(CoreAnnotations.OriginalTextAnnotation.class));

        assertEquals(EXPECTED_LEMMA_TEXT[i][j], token.lemma());
        assertEquals(EXPECTED_UPOS[i][j], token.get(CoreAnnotations.CoarseTagAnnotation.class));
        assertEquals(EXPECTED_XPOS[i][j], token.tag());

        assertEquals(Integer.valueOf(i), token.get(CoreAnnotations.SentenceIndexAnnotation.class));
        assertEquals(Integer.valueOf(j+1), token.get(CoreAnnotations.IndexAnnotation.class));

        // all tokens should have a False isNewline
        assertFalse(token.get(CoreAnnotations.IsNewlineAnnotation.class));
      }
    }

    // Check the MWT features
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);
        // words 14-15 (indexed one lower here) are the only MWT in this document
        // otherwise, all fields should be false
        if (i == 0 && j == 13) {
          assertTrue(token.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
          assertTrue(token.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
          assertEquals("deberse", token.get(CoreAnnotations.MWTTokenTextAnnotation.class));
        } else if (i == 0 && j == 14) {
          assertTrue(token.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
          assertFalse(token.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
          assertEquals("deberse", token.get(CoreAnnotations.MWTTokenTextAnnotation.class));
        } else {
          assertFalse(token.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
          assertFalse(token.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
          assertFalse(token.containsKey(CoreAnnotations.MWTTokenTextAnnotation.class));
        }
      }
    }

    // Check the Before & After features
    // TODO: May need to reconsider the end of sentence treatment
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);
        if (i == 0 && j == 1) {
          assertEquals("  ", token.after());
        } else if (j == tokens.size() - 2) {
          assertEquals("", token.after());
        } else if (i == 0 && j == 13) {
          assertEquals("", token.after());
        } else {
          assertEquals(" ", token.after());
        }

        if (i == 0 && j == 2) {
          assertEquals("  ", token.before());
        } else if (i == 0 && j == 0) {
          assertEquals("", token.before());
        } else if (j == tokens.size() - 1) {
          assertEquals("", token.before());
        } else if (i == 0 && j == 14) {
          assertEquals("", token.before());
        } else {
          assertEquals(" ", token.before());
        }
      }
    }

    // Check that these fields are set
    // Perhaps not checking the values of the offsets, though
    int tokenCount = 0;
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);
        assertTrue(token.containsKey(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
        assertTrue(token.containsKey(CoreAnnotations.CharacterOffsetEndAnnotation.class));
        assertEquals(Integer.valueOf(tokenCount), token.get(CoreAnnotations.TokenBeginAnnotation.class));
        assertEquals(Integer.valueOf(tokenCount+1), token.get(CoreAnnotations.TokenEndAnnotation.class));
        ++tokenCount;
      }
    }

    // check the features, the misc columns, and that there are no fields currently unaccounted for
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      assertEquals(EXPECTED_FEATS[i].length, tokens.size());
      assertEquals(EXPECTED_MISC[i].length, tokens.size());
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);

        String expected = EXPECTED_FEATS[i][j];
        int expectedKeys = 16;

        if (expected == null) {
          assertFalse(token.containsKey(CoreAnnotations.CoNLLUFeats.class));
        } else {
          expectedKeys += 1;
          String feats = token.get(CoreAnnotations.CoNLLUFeats.class).toString();
          assertEquals(expected, feats);
        }

        String expectedMisc = EXPECTED_MISC[i][j];
        if (expectedMisc == null) {
          assertFalse(token.containsKey(CoreAnnotations.CoNLLUMisc.class));
        } else {
          expectedKeys += 1;
          String misc = token.get(CoreAnnotations.CoNLLUMisc.class).toString();
          assertEquals(expectedMisc, misc);
        }

        // Some of the AnCora sentences don't have XPOS
        if (token.containsKey(CoreAnnotations.PartOfSpeechAnnotation.class)) {
          expectedKeys += 1;
        }

        // the MWT token specifically gets one more field, the MWT text
        if (i == 0 && (j == 13 || j == 14)) {
          expectedKeys += 1;
        }
        if (i == 0 && j == 0) {
          // The very first key won't have a Before unless the document specifically has one
          expectedKeys -= 1;
        }
        assertEquals("Error at sentence " + i + " word " + j, expectedKeys, token.keySet().size());

        // The known fields should be the ones checked above:
        //    CoreAnnotations.TextAnnotation
        //    CoreAnnotations.ValueAnnotation
        //    CoreAnnotations.OriginalTextAnnotation
        //    CoreAnnotations.IsNewlineAnnotation
        //    CoreAnnotations.LemmaAnnotation
        //    CoreAnnotations.PartOfSpeechAnnotation
        //    CoreAnnotations.CoarseTagAnnotation
        //    CoreAnnotations.IndexAnnotation
        //    CoreAnnotations.AfterAnnotation
        //    CoreAnnotations.BeforeAnnotation
        //    CoreAnnotations.IsMultiWordTokenAnnotation
        //    CoreAnnotations.IsFirstWordOfMWTAnnotation
        //    CoreAnnotations.CharacterOffsetBeginAnnotation
        //    CoreAnnotations.CharacterOffsetEndAnnotation
        //    CoreAnnotations.TokenBeginAnnotation
        //    CoreAnnotations.TokenEndAnnotation
        //    CoreAnnotations.SentenceIndexAnnotation
        // and sometimes
        //    CoreAnnotations.CoNLLUFeats
        //    CoreAnnotations.MWTTokenTextAnnotation
        //
        // TODO: make it always add a Feats, even if it's not present?
      }
    }

    // compare the SemanticGraph
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      assertNotNull(graph);

      List<IndexedWord> vertices = graph.vertexListSorted();
      assertEquals(EXPECTED_WORD_TEXT[i].length, vertices.size());
      assertEquals(EXPECTED_RELNS[i].length, vertices.size());
      assertEquals(EXPECTED_HEADS[i].length, vertices.size());
      for (int j = 0; j < vertices.size(); ++j) {
        IndexedWord vertex = vertices.get(j);
        assertEquals(EXPECTED_WORD_TEXT[i][j], vertex.value());

        // each word should be properly indexed with the sentIndex and position in the sentence
        assertEquals(i, vertex.sentIndex());
        // j+1 because the arrows are laid out with 0 as root, words with a 1-based index
        assertEquals(j+1, vertex.index());

        if (EXPECTED_HEADS[i][j] == 0) {
          assertTrue(graph.isRoot(vertex));
          continue;
        }

        // If not a root, then the word should have exactly one parent
        // The HEAD and RELNS arrays specify the expected parent and relation of the edge
        List<SemanticGraphEdge> edges = graph.getIncomingEdgesSorted(vertex);
        assertEquals(1, edges.size());
        assertEquals(EXPECTED_HEADS[i][j], edges.get(0).getGovernor().index());
        assertEquals(EXPECTED_RELNS[i][j], edges.get(0).getRelation().toString());
      }
    }
  }

  public static final String emptiesPath = String.format("edu/stanford/nlp/pipeline/en-example.conllu");

  static final String[] EXPECTED_ENGLISH_WORDS = {
    "Over", "300", "Iraqis", "are", "reported", "dead", "and", "500", "wounded", "in", "Fallujah", "alone", "."
  };

  static final String[][] EXPECTED_ENHANCED_EDGES = {
    {"1", "2", "advmod"},
    {"2", "3", "nummod"},
    {"3", "5", "nsubj:pass"},
    {"3", "6", "nsubj:xsubj"},
    {"3", "8", "nsubj:pass"},
    {"4", "5", "aux:pass"},
    {"6", "5", "xcomp"},
    {"7", "8", "cc"},
    {"7", "8.1", "cc"},
    {"8", "5", "conj:and"},
    {"8", "8.1", "nsubj:pass"},
    {"8", "9", "nsubj:xsubj"},
    {"8.1", "5", "conj:and"},
    {"9", "8.1", "xcomp"},
    {"10", "11", "case"},
    {"11", "5", "obl:in"},
    {"12", "11", "advmod"},
    {"13", "5", "punct"},
  };
  static final SemanticGraph EXPECTED_ENHANCED = buildEnhancedTest();
  static SemanticGraph buildEnhancedTest() {
    Map<String, IndexedWord> graphNodes = new HashMap<>();
    for (int i = 0; i < EXPECTED_ENGLISH_WORDS.length; ++i) {
      String index = Integer.toString(i+1);
      CoreLabel cl = new CoreLabel();
      cl.setValue(EXPECTED_ENGLISH_WORDS[i]);
      cl.setIndex(i+1);
      cl.setSentIndex(0);
      graphNodes.put(index, new IndexedWord(cl));
    }
    {
      String index = "8.1";
      CoreLabel cl = new CoreLabel();
      cl.setValue("reported");
      cl.setIndex(8);
      cl.set(CoreAnnotations.EmptyIndexAnnotation.class, 1);
      cl.setSentIndex(0);
      graphNodes.put(index, new IndexedWord(cl));
    }
    List<SemanticGraphEdge> edges = new ArrayList<>();
    for (String[] edge : EXPECTED_ENHANCED_EDGES) {
      IndexedWord dep = graphNodes.get(edge[0]);
      IndexedWord gov = graphNodes.get(edge[1]);
      GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, edge[2]);
      edges.add(new SemanticGraphEdge(gov, dep, reln, 1.0, false));
    }
    List<IndexedWord> roots = new ArrayList<>();
    roots.add(graphNodes.get("5"));
    SemanticGraph enhancedParse = SemanticGraphFactory.makeFromEdges(edges);
    enhancedParse.setRoots(roots);
    return enhancedParse;
  }

  @Test
  /**
   * Here we run fewer tests.  Just make sure the EmptyToken is properly handled,
   * and make sure there isn't some weird line skipping going on with the rest of the tokens
   */
  public void testReadingInEmpties() throws ClassNotFoundException, IOException {
    Annotation readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(emptiesPath).get(0);

    // this document only has one sentence
    List<CoreMap> sentences = readInDocument.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    CoreMap sentence = sentences.get(0);

    // cursory check of the tokens
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(13, tokens.size());
    assertEquals(13, EXPECTED_ENGLISH_WORDS.length);
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(i+1, tokens.get(i).index());
      assertEquals(EXPECTED_ENGLISH_WORDS[i], tokens.get(i).value());
      assertEquals(0, tokens.get(i).sentIndex());
    }

    List<CoreLabel> emptyTokens = sentence.get(CoreAnnotations.EmptyTokensAnnotation.class);
    assertEquals(1, emptyTokens.size());
    CoreLabel empty = emptyTokens.get(0);
    assertEquals(8, empty.index());
    assertEquals(Integer.valueOf(1), empty.get(CoreAnnotations.EmptyIndexAnnotation.class));
    assertEquals(0, empty.sentIndex());
    assertEquals("reported", empty.value());

    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertEquals(EXPECTED_ENHANCED, enhanced);
    for (IndexedWord node : enhanced.vertexSet()) {
      assertEquals(0, node.sentIndex());
    }

    assertEquals("  ", tokens.get(0).before());
  }


  public static final String noEnhancedPath = String.format("edu/stanford/nlp/pipeline/en-example-noenhanced.conllu");

  @Test
  /**
   * Here we run fewer tests.  Just make sure reading a sentence with no enhanced graph still works
   */
  public void testReadingNoEnhanced() throws ClassNotFoundException, IOException {
    Annotation readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(noEnhancedPath).get(0);

    // this document only has one sentence
    List<CoreMap> sentences = readInDocument.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    CoreMap sentence = sentences.get(0);

    // cursory check of the tokens
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(13, tokens.size());
    assertEquals(13, EXPECTED_ENGLISH_WORDS.length);
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(i+1, tokens.get(i).index());
      assertEquals(EXPECTED_ENGLISH_WORDS[i], tokens.get(i).value());
    }

    assertFalse(sentence.containsKey(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
  }

  public static final String oneWordPath = String.format("edu/stanford/nlp/pipeline/en-example-oneword.conllu");

  @Test
  /**
   * This test checks that the graphs are successfully constructed
   * when they are only one word big.
   * (Previously, such a case would throw an error.)
   */
  public void testReadingOneWord() throws ClassNotFoundException, IOException {
    Annotation readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(oneWordPath).get(0);

    // this document only has one sentence
    List<CoreMap> sentences = readInDocument.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    CoreMap sentence = sentences.get(0);

    // cursory check of the tokens
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(1, tokens.size());
    assertEquals("...", tokens.get(0).value());

    assertTrue(sentence.containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    assertEquals(0, graph.edgeListSorted().size());
    assertEquals(1, graph.getRoots().size());

    assertTrue(sentence.containsKey(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
    graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertEquals(0, graph.edgeListSorted().size());
    assertEquals(1, graph.getRoots().size());
  }


  public static final String mwtSpaceAfterPath = String.format("edu/stanford/nlp/pipeline/en-example-misc-spaceafter.conllu");

  @Test
  /**
   * This test checks that the SpaceAfter on a Misc is respected.
   */
  public void testReadingMiscSpaceAfter() throws ClassNotFoundException, IOException {
    Annotation readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(mwtSpaceAfterPath).get(0);

    // this document only has one sentence
    List<CoreMap> sentences = readInDocument.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    CoreMap sentence = sentences.get(0);

    // cursory check of the tokens
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(8, tokens.size());

    // check that the non-last words of the MWT have no SpaceAfter
    assertEquals("", tokens.get(0).after());
    // check the SpaceAfter of the second word, which is where the MWT SpaceAfter should go
    assertEquals("  ", tokens.get(1).after());

    // the MWT SpacesBefore should go on the first word
    assertEquals("  ", tokens.get(0).before());
    assertEquals("", tokens.get(1).before());

    assertEquals("Foo=Bar", tokens.get(0).get(CoreAnnotations.MWTTokenMiscAnnotation.class));
    assertEquals(null, tokens.get(1).get(CoreAnnotations.MWTTokenMiscAnnotation.class));

    assertTrue(sentence.containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    assertTrue(sentence.containsKey(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
  }

}
