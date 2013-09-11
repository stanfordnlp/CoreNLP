package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EntityTypeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;

import java.util.*;

/**
 * Recovers tokenized, column format date strings
 * from collapsed date strings.
 *
 * usage: ExpandDatesIntoColums <file.collapsedDates.col> <file.col>
 *
 * @author Chris Cox
 */


public class ExpandDatesIntoColumns {
  private String collapsedFilename;
  private String targetFilename;

  public ExpandDatesIntoColumns(String s1, String s2){
    collapsedFilename=s1;
    targetFilename=s2;
  }

  public static void main(String[] args) throws Exception{
    ExpandDatesIntoColumns e = new ExpandDatesIntoColumns(args[0],args[1]);
    e.expand();
  }

  public void expand() throws Exception{

//    int targetFileIndex = 0;
//    int collapsedFileIndex = 0;
    CRFClassifier crf = CRFClassifier.getClassifier("/u/nlp/data/iedata/Pascal2004/final/crf2.ser.gz");
    DocumentReaderAndWriter readerAndWriter = crf.makeReaderAndWriter();
    String origMap = crf.flags.map;
    String testMap1 = "word=0,tag=1,shape=2,entityType=3,isURL=4,normalized=5,entityRule=6,answer=7";
    crf.flags.map = testMap1;
    ObjectBank<List<CoreLabel>> l = 
      crf.makeObjectBankFromFile(targetFilename, readerAndWriter);
    List<CoreLabel> targetWordInfos = l.iterator().next();
    String testMap2 = "word=0,goldAnswer=1,answer=2";
    crf.flags.map = testMap2;
    l = crf.makeObjectBankFromFile(collapsedFilename, readerAndWriter);
    crf.flags.map = origMap;
    List<CoreLabel> collapsedWordInfos = l.iterator().next();
    Iterator<CoreLabel> transferIter = collapsedWordInfos.iterator();

    for(Iterator<CoreLabel> targetIter = targetWordInfos.iterator();targetIter.hasNext();){
      CoreLabel targetWord = targetIter.next();
      CoreLabel transferFrom = transferIter.next();
      if(targetWord.get(EntityTypeAnnotation.class).equals("Date")){
        while(targetWord.get(EntityTypeAnnotation.class).equals("Date")){
          assert(targetWord.get(NormalizedNamedEntityTagAnnotation.class).equals(transferFrom.get(TextAnnotation.class)));
          targetWord.set(AnswerAnnotation.class, transferFrom.get(AnswerAnnotation.class));
          System.out.println(targetWord.word()+ "\t" + targetWord.get(GoldAnswerAnnotation.class) +
                             "\t" + targetWord.get(AnswerAnnotation.class));
          targetWord = targetIter.next();
        }
        transferFrom=transferIter.next();
      }else{
        assert(targetWord.get(TextAnnotation.class).equals(transferFrom.get(TextAnnotation.class)));
        targetWord.set(AnswerAnnotation.class, transferFrom.get(AnswerAnnotation.class));
      }
      System.out.println(targetWord.word()+ "\t" + targetWord.get(GoldAnswerAnnotation.class) +
                         "\t" + targetWord.get(AnswerAnnotation.class));

    }

  }

}
