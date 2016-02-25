package edu.stanford.nlp.util;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.TSVSentenceIterator.SentenceField;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test cases for TSV sentence iterator.
 *
 * @author chaganty
 * @version 11/20/15
 */
public class TSVSentenceIteratorITest {

  /**
   * TODO(chaganty): Support creation of sentences with TSV iterator without any tokens annotations.
   * Currently, Sentence does not like that.
   */
  //@Test
  public void testOnlyGloss() {
    List<List<String>> entries = new ArrayList<>();
    entries.add(new ArrayList<String>() {{
      add("124");
      add("docid1");
      add("1");
      add("This is a test document.");
    }});

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(), new ArrayList<SentenceField>() {
      {
        add(SentenceField.ID);
        add(SentenceField.DOC_ID);
        add(SentenceField.SENTENCE_INDEX);
        add(SentenceField.GLOSS);
      }
    });
    Sentence sentence = it.next();
    Assert.assertEquals(1, sentence.sentenceIndex());
    Assert.assertEquals("This is a test document."  , sentence.text());
    Assert.assertEquals("docid1", sentence.asCoreMap().get(CoreAnnotations.DocIDAnnotation.class));
    Assert.assertEquals("124", sentence.asCoreMap().get(CoreAnnotations.SentenceIDAnnotation.class));
  }

  @Test
  public void testFullTokens() {
    List<List<String>> entries = new ArrayList<>();
    entries.add(new ArrayList<String>() {{
      add("3424");
      add("d2-s1-a1");
      add("0");
      add("{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,-LRB-,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,-RRB-,.}");
      add("{chess,be,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,be,shooting,and,curling,-lrb-,which,\"\",\"\",in,fact,\"\",\"\",have,be,nickname,``,chess,on,ice,'',5,-rrb-,.}");
      add("{NN,VBZ,RB,DT,RB,JJ,NN,\"\",\"\",RB,DT,VBP,JJ,CC,NN,-LRB-,WDT,\"\",\"\",IN,NN,\"\",\"\",VBZ,VBN,VBN,``,NN,IN,NN,'',LS,-RRB-,.}");
      add("{O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,NUMBER,O,O}");
      add("{0,6,9,13,15,29,38,43,45,49,57,61,70,74,82,83,88,90,93,97,99,103,108,118,119,125,128,131,132,133,134}");
      add("{5,8,12,14,28,37,43,44,48,56,60,69,73,81,83,88,89,92,97,98,102,107,117,119,124,127,131,132,133,134,135}"	);
      //add("[{\"\"dependent\"\": 7, \"\"dep\"\": \"\"ROOT\"\", \"\"governorGloss\"\": \"\"ROOT\"\", \"\"governor\"\": 0, \"\"dependentGloss\"\": \"\"sport\"\"}, {\"\"dependent\"\": 1, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"Chess\"\"}, {\"\"dependent\"\": 2, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"is\"\"}, {\"\"dependent\"\": 3, \"\"dep\"\": \"\"neg\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"not\"\"}, {\"\"dependent\"\": 4, \"\"dep\"\": \"\"det\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"a\"\"}, {\"\"dependent\"\": 5, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"physical\"\", \"\"governor\"\": 6, \"\"dependentGloss\"\": \"\"predominantly\"\"}, {\"\"dependent\"\": 6, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"physical\"\"}, {\"\"dependent\"\": 9, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"yet\"\"}, {\"\"dependent\"\": 10, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"neither\"\"}, {\"\"dependent\"\": 11, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"are\"\"}, {\"\"dependent\"\": 12, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"shooting\"\"}, {\"\"dependent\"\": 13, \"\"dep\"\": \"\"cc\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"and\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"conj:and\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 16, \"\"dep\"\": \"\"nsubjpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"which\"\"}, {\"\"dependent\"\": 18, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"fact\"\", \"\"governor\"\": 19, \"\"dependentGloss\"\": \"\"in\"\"}, {\"\"dependent\"\": 19, \"\"dep\"\": \"\"nmod:in\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"fact\"\"}, {\"\"dependent\"\": 21, \"\"dep\"\": \"\"aux\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"has\"\"}, {\"\"dependent\"\": 22, \"\"dep\"\": \"\"auxpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"been\"\"}, {\"\"dependent\"\": 23, \"\"dep\"\": \"\"dep\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"nicknamed\"\"}, {\"\"dependent\"\": 25, \"\"dep\"\": \"\"dobj\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"chess\"\"}, {\"\"dependent\"\": 26, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"ice\"\", \"\"governor\"\": 27, \"\"dependentGloss\"\": \"\"on\"\"}, {\"\"dependent\"\": 27, \"\"dep\"\": \"\"nmod:on\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"ice\"\"}, {\"\"dependent\"\": 29, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"5\"\"}]");
      add("Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5).");
    }});

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(), new ArrayList<SentenceField>() {
      {
        add(SentenceField.ID);
        add(SentenceField.DOC_ID);
        add(SentenceField.SENTENCE_INDEX);
        add(SentenceField.WORDS);
        add(SentenceField.LEMMAS);
        add(SentenceField.POS_TAGS);
        add(SentenceField.NER_TAGS);
        add(SentenceField.DOC_CHAR_BEGIN);
        add(SentenceField.DOC_CHAR_END);
        add(SentenceField.GLOSS);
      }
    });
    Sentence sentence = it.next();
    Assert.assertEquals("3424", sentence.sentenceid().orElse("-1"));
    Assert.assertEquals("d2-s1-a1", sentence.document.docid().orElse("???"));
    Assert.assertEquals(0, sentence.sentenceIndex());
    Assert.assertEquals("Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5)." , sentence.text());
    Assert.assertArrayEquals(new String[]{
        "Chess","is","not","a","predominantly","physical","sport",",","yet","neither","are","shooting","and","curling","-LRB-","which",",","in","fact",",","has","been","nicknamed","``","chess","on","ice","''","5","-RRB-","."
        }, sentence.words().toArray());
    Assert.assertArrayEquals(new String[]{
        "chess","be","not","a","predominantly","physical","sport",",","yet","neither","be","shooting","and","curling","-lrb-","which",",","in","fact",",","have","be","nickname","``","chess","on","ice","''","5","-rrb-","."
        }, sentence.lemmas().toArray());
    Assert.assertArrayEquals(new String[]{
        "NN","VBZ","RB","DT","RB","JJ","NN",",","RB","DT","VBP","JJ","CC","NN","-LRB-","WDT",",","IN","NN",",","VBZ","VBN","VBN","``","NN","IN","NN","''","LS","-RRB-","."
        }, sentence.posTags().toArray());
    Assert.assertArrayEquals(new String[]{
        "O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","NUMBER","O","O"
        }, sentence.nerTags().toArray());
    Assert.assertArrayEquals(new Integer[]{
        0,6,9,13,15,29,38,43,45,49,57,61,70,74,82,83,88,90,93,97,99,103,108,118,119,125,128,131,132,133,134
        }, sentence.characterOffsetBegin().toArray());
    Assert.assertArrayEquals(new Integer[]{
        5,8,12,14,28,37,43,44,48,56,60,69,73,81,83,88,89,92,97,98,102,107,117,119,124,127,131,132,133,134,135
      }, sentence.characterOffsetEnd().toArray());
  }

  @Test
  public void testParseArray() {
    String in = "{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,-LRB-,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,-RRB-,.}";
    String[] out = {"Chess","is","not","a","predominantly","physical","sport",",","yet","neither","are","shooting","and","curling","-LRB-","which",",","in","fact",",","has","been","nicknamed","``","chess","on","ice","''","5","-RRB-","."};

    Assert.assertArrayEquals(out, TSVUtils.parseArray(in).toArray());
  }

  @Test
  public void testParseTrees() {
    List<List<String>> entries = new ArrayList<>();
    entries.add(new ArrayList<String>() {{
      add("3424");
      add("d2-s1-a1");
      add("0");
      add("{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,-LRB-,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,-RRB-,.}");
      add("{chess,be,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,be,shooting,and,curling,-lrb-,which,\"\",\"\",in,fact,\"\",\"\",have,be,nickname,``,chess,on,ice,'',5,-rrb-,.}");
      add("{NN,VBZ,RB,DT,RB,JJ,NN,\"\",\"\",RB,DT,VBP,JJ,CC,NN,-LRB-,WDT,\"\",\"\",IN,NN,\"\",\"\",VBZ,VBN,VBN,``,NN,IN,NN,'',LS,-RRB-,.}");
      add("{O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,NUMBER,O,O}");
      add("{0,6,9,13,15,29,38,43,45,49,57,61,70,74,82,83,88,90,93,97,99,103,108,118,119,125,128,131,132,133,134}");
      add("{5,8,12,14,28,37,43,44,48,56,60,69,73,81,83,88,89,92,97,98,102,107,117,119,124,127,131,132,133,134,135}"	);
      add("[{\"\"dependent\"\": 7, \"\"dep\"\": \"\"ROOT\"\", \"\"governorGloss\"\": \"\"ROOT\"\", \"\"governor\"\": 0, \"\"dependentGloss\"\": \"\"sport\"\"}, {\"\"dependent\"\": 1, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"Chess\"\"}, {\"\"dependent\"\": 2, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"is\"\"}, {\"\"dependent\"\": 3, \"\"dep\"\": \"\"neg\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"not\"\"}, {\"\"dependent\"\": 4, \"\"dep\"\": \"\"det\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"a\"\"}, {\"\"dependent\"\": 5, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"physical\"\", \"\"governor\"\": 6, \"\"dependentGloss\"\": \"\"predominantly\"\"}, {\"\"dependent\"\": 6, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"physical\"\"}, {\"\"dependent\"\": 9, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"yet\"\"}, {\"\"dependent\"\": 10, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"neither\"\"}, {\"\"dependent\"\": 11, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"are\"\"}, {\"\"dependent\"\": 12, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"shooting\"\"}, {\"\"dependent\"\": 13, \"\"dep\"\": \"\"cc\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"and\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"conj:and\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 16, \"\"dep\"\": \"\"nsubjpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"which\"\"}, {\"\"dependent\"\": 18, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"fact\"\", \"\"governor\"\": 19, \"\"dependentGloss\"\": \"\"in\"\"}, {\"\"dependent\"\": 19, \"\"dep\"\": \"\"nmod:in\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"fact\"\"}, {\"\"dependent\"\": 21, \"\"dep\"\": \"\"aux\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"has\"\"}, {\"\"dependent\"\": 22, \"\"dep\"\": \"\"auxpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"been\"\"}, {\"\"dependent\"\": 23, \"\"dep\"\": \"\"dep\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"nicknamed\"\"}, {\"\"dependent\"\": 25, \"\"dep\"\": \"\"dobj\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"chess\"\"}, {\"\"dependent\"\": 26, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"ice\"\", \"\"governor\"\": 27, \"\"dependentGloss\"\": \"\"on\"\"}, {\"\"dependent\"\": 27, \"\"dep\"\": \"\"nmod:on\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"ice\"\"}, {\"\"dependent\"\": 29, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"5\"\"}]");
      add("Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5).");
    }});

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(), new ArrayList<SentenceField>() {
      {
        add(SentenceField.ID);
        add(SentenceField.DOC_ID);
        add(SentenceField.SENTENCE_INDEX);
        add(SentenceField.WORDS);
        add(SentenceField.LEMMAS);
        add(SentenceField.POS_TAGS);
        add(SentenceField.NER_TAGS);
        add(SentenceField.DOC_CHAR_BEGIN);
        add(SentenceField.DOC_CHAR_END);
        add(SentenceField.DEPENDENCIES_BASIC);
        add(SentenceField.GLOSS);
      }
    });
    Sentence sentence = it.next();
    sentence.dependencyGraph();
    sentence.openieTriples();
  }

}
