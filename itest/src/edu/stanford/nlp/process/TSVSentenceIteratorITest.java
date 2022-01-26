package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.process.TSVSentenceIterator.SentenceField;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
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
  // @Test
  public void testOnlyGloss() {
    List<List<String>> entries = Collections.singletonList(
            Arrays.asList("124", "docid1", "1", "This is a test document."));

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(),
            Arrays.asList(SentenceField.ID, SentenceField.DOC_ID, SentenceField.SENTENCE_INDEX, SentenceField.GLOSS));
    Sentence sentence = it.next();
    Assert.assertEquals(1, sentence.sentenceIndex());
    Assert.assertEquals("This is a test document."  , sentence.text());
    Assert.assertEquals("docid1", sentence.asCoreMap().get(CoreAnnotations.DocIDAnnotation.class));
    Assert.assertEquals("124", sentence.asCoreMap().get(CoreAnnotations.SentenceIDAnnotation.class));
  }

  @Test
  public void testFullTokens() {
    List<List<String>> entries = Collections.singletonList(
            Arrays.asList(
                    "3424",
                    "d2-s1-a1",
                    "0",
                    "{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,-LRB-,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,),.}",
                    "{chess,be,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,be,shooting,and,curling,-lrb-,which,\"\",\"\",in,fact,\"\",\"\",have,be,nickname,``,chess,on,ice,'',5,),.}",
                    "{NN,VBZ,RB,DT,RB,JJ,NN,\"\",\"\",RB,DT,VBP,JJ,CC,NN,-LRB-,WDT,\"\",\"\",IN,NN,\"\",\"\",VBZ,VBN,VBN,``,NN,IN,NN,'',LS,-RRB-,.}",
                    "{O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,NUMBER,O,O}",
                    "{0,6,9,13,15,29,38,43,45,49,57,61,70,74,82,83,88,90,93,97,99,103,108,118,119,125,128,131,132,133,134}",
                    "{5,8,12,14,28,37,43,44,48,56,60,69,73,81,83,88,89,92,97,98,102,107,117,119,124,127,131,132,133,134,135}"	,
                    //"[{\"\"dependent\"\": 7, \"\"dep\"\": \"\"ROOT\"\", \"\"governorGloss\"\": \"\"ROOT\"\", \"\"governor\"\": 0, \"\"dependentGloss\"\": \"\"sport\"\"}, {\"\"dependent\"\": 1, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"Chess\"\"}, {\"\"dependent\"\": 2, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"is\"\"}, {\"\"dependent\"\": 3, \"\"dep\"\": \"\"neg\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"not\"\"}, {\"\"dependent\"\": 4, \"\"dep\"\": \"\"det\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"a\"\"}, {\"\"dependent\"\": 5, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"physical\"\", \"\"governor\"\": 6, \"\"dependentGloss\"\": \"\"predominantly\"\"}, {\"\"dependent\"\": 6, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"physical\"\"}, {\"\"dependent\"\": 9, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"yet\"\"}, {\"\"dependent\"\": 10, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"neither\"\"}, {\"\"dependent\"\": 11, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"are\"\"}, {\"\"dependent\"\": 12, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"shooting\"\"}, {\"\"dependent\"\": 13, \"\"dep\"\": \"\"cc\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"and\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"conj:and\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 16, \"\"dep\"\": \"\"nsubjpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"which\"\"}, {\"\"dependent\"\": 18, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"fact\"\", \"\"governor\"\": 19, \"\"dependentGloss\"\": \"\"in\"\"}, {\"\"dependent\"\": 19, \"\"dep\"\": \"\"nmod:in\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"fact\"\"}, {\"\"dependent\"\": 21, \"\"dep\"\": \"\"aux\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"has\"\"}, {\"\"dependent\"\": 22, \"\"dep\"\": \"\"auxpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"been\"\"}, {\"\"dependent\"\": 23, \"\"dep\"\": \"\"dep\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"nicknamed\"\"}, {\"\"dependent\"\": 25, \"\"dep\"\": \"\"dobj\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"chess\"\"}, {\"\"dependent\"\": 26, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"ice\"\", \"\"governor\"\": 27, \"\"dependentGloss\"\": \"\"on\"\"}, {\"\"dependent\"\": 27, \"\"dep\"\": \"\"nmod:on\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"ice\"\"}, {\"\"dependent\"\": 29, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"5\"\"}]",
                    "Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5)."
            ));

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(), Arrays.asList(
            SentenceField.ID,
            SentenceField.DOC_ID,
            SentenceField.SENTENCE_INDEX,
            SentenceField.WORDS,
            SentenceField.LEMMAS,
            SentenceField.POS_TAGS,
            SentenceField.NER_TAGS,
            SentenceField.DOC_CHAR_BEGIN,
            SentenceField.DOC_CHAR_END,
            SentenceField.GLOSS
    ));

    Sentence sentence = it.next();
    Assert.assertEquals("3424", sentence.sentenceid().orElse("-1"));
    Assert.assertEquals("d2-s1-a1", sentence.document.docid().orElse("???"));
    Assert.assertEquals(0, sentence.sentenceIndex());
    Assert.assertEquals("Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5)." , sentence.text());
    Assert.assertArrayEquals(new String[]{
            "Chess","is","not","a","predominantly","physical","sport",",","yet","neither","are","shooting","and","curling","-LRB-","which",",","in","fact",",","has","been","nicknamed","``","chess","on","ice","''","5",")","."
    }, sentence.words().toArray());
    Assert.assertArrayEquals(new String[]{
            "chess","be","not","a","predominantly","physical","sport",",","yet","neither","be","shooting","and","curling","-lrb-","which",",","in","fact",",","have","be","nickname","``","chess","on","ice","''","5",")","."
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

    // System.err.println(in);
    // System.err.println(Arrays.asList(out));
    // System.err.println(Arrays.asList(TSVUtils.parseArray(in).toArray()));
    Assert.assertArrayEquals(out, TSVUtils.parseArray(in).toArray());

    String in2 = "{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,(,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,),.}";
    String[] out2 = {"Chess","is","not","a","predominantly","physical","sport",",","yet","neither","are","shooting","and","curling","(","which",",","in","fact",",","has","been","nicknamed","``","chess","on","ice","''","5",")","."};

    Assert.assertArrayEquals(out2, TSVUtils.parseArray(in2).toArray());
  }

  @Test
  public void testParseTrees() {
    // NOTE: this is not a legal basic dependency tree, as "curling" is the dependent of two relations
    List<List<String>> entries = Collections.singletonList(
            Arrays.asList(
                    "3424",
                    "d2-s1-a1",
                    "0",
                    "{Chess,is,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,are,shooting,and,curling,-LRB-,which,\"\",\"\",in,fact,\"\",\"\",has,been,nicknamed,``,chess,on,ice,'',5,-RRB-,.}",
                    "{chess,be,not,a,predominantly,physical,sport,\"\",\"\",yet,neither,be,shooting,and,curling,-lrb-,which,\"\",\"\",in,fact,\"\",\"\",have,be,nickname,``,chess,on,ice,'',5,-rrb-,.}",
                    "{NN,VBZ,RB,DT,RB,JJ,NN,\"\",\"\",RB,DT,VBP,JJ,CC,NN,-LRB-,WDT,\"\",\"\",IN,NN,\"\",\"\",VBZ,VBN,VBN,``,NN,IN,NN,'',LS,-RRB-,.}",
                    "{O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,O,NUMBER,O,O}",
                    "{0,6,9,13,15,29,38,43,45,49,57,61,70,74,82,83,88,90,93,97,99,103,108,118,119,125,128,131,132,133,134}",
                    "{5,8,12,14,28,37,43,44,48,56,60,69,73,81,83,88,89,92,97,98,102,107,117,119,124,127,131,132,133,134,135}"	,
                    "[{\"\"dependent\"\": 7, \"\"dep\"\": \"\"ROOT\"\", \"\"governorGloss\"\": \"\"ROOT\"\", \"\"governor\"\": 0, \"\"dependentGloss\"\": \"\"sport\"\"}, {\"\"dependent\"\": 1, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"Chess\"\"}, {\"\"dependent\"\": 2, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"is\"\"}, {\"\"dependent\"\": 3, \"\"dep\"\": \"\"neg\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"not\"\"}, {\"\"dependent\"\": 4, \"\"dep\"\": \"\"det\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"a\"\"}, {\"\"dependent\"\": 5, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"physical\"\", \"\"governor\"\": 6, \"\"dependentGloss\"\": \"\"predominantly\"\"}, {\"\"dependent\"\": 6, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"physical\"\"}, {\"\"dependent\"\": 9, \"\"dep\"\": \"\"advmod\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"yet\"\"}, {\"\"dependent\"\": 10, \"\"dep\"\": \"\"nsubj\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"neither\"\"}, {\"\"dependent\"\": 11, \"\"dep\"\": \"\"cop\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"are\"\"}, {\"\"dependent\"\": 12, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"shooting\"\"}, {\"\"dependent\"\": 13, \"\"dep\"\": \"\"cc\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"and\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"parataxis\"\", \"\"governorGloss\"\": \"\"sport\"\", \"\"governor\"\": 7, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 14, \"\"dep\"\": \"\"conj:and\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"curling\"\"}, {\"\"dependent\"\": 16, \"\"dep\"\": \"\"nsubjpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"which\"\"}, {\"\"dependent\"\": 18, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"fact\"\", \"\"governor\"\": 19, \"\"dependentGloss\"\": \"\"in\"\"}, {\"\"dependent\"\": 19, \"\"dep\"\": \"\"nmod:in\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"fact\"\"}, {\"\"dependent\"\": 21, \"\"dep\"\": \"\"aux\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"has\"\"}, {\"\"dependent\"\": 22, \"\"dep\"\": \"\"auxpass\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"been\"\"}, {\"\"dependent\"\": 23, \"\"dep\"\": \"\"dep\"\", \"\"governorGloss\"\": \"\"shooting\"\", \"\"governor\"\": 12, \"\"dependentGloss\"\": \"\"nicknamed\"\"}, {\"\"dependent\"\": 25, \"\"dep\"\": \"\"dobj\"\", \"\"governorGloss\"\": \"\"nicknamed\"\", \"\"governor\"\": 23, \"\"dependentGloss\"\": \"\"chess\"\"}, {\"\"dependent\"\": 26, \"\"dep\"\": \"\"case\"\", \"\"governorGloss\"\": \"\"ice\"\", \"\"governor\"\": 27, \"\"dependentGloss\"\": \"\"on\"\"}, {\"\"dependent\"\": 27, \"\"dep\"\": \"\"nmod:on\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"ice\"\"}, {\"\"dependent\"\": 29, \"\"dep\"\": \"\"amod\"\", \"\"governorGloss\"\": \"\"chess\"\", \"\"governor\"\": 25, \"\"dependentGloss\"\": \"\"5\"\"}]",
                    "Chess is not a predominantly physical sport, yet neither are shooting and curling (which, in fact, has been nicknamed “chess on ice”5).")
    );

    TSVSentenceIterator it = new TSVSentenceIterator(entries.iterator(), Arrays.asList(
            SentenceField.ID,
            SentenceField.DOC_ID,
            SentenceField.SENTENCE_INDEX,
            SentenceField.WORDS,
            SentenceField.LEMMAS,
            SentenceField.POS_TAGS,
            SentenceField.NER_TAGS,
            SentenceField.DOC_CHAR_BEGIN,
            SentenceField.DOC_CHAR_END,
            SentenceField.DEPENDENCIES_BASIC,
            SentenceField.GLOSS
    ));
    Sentence sentence = it.next();
    sentence.dependencyGraph();
    sentence.openieTriples();
  }

}
