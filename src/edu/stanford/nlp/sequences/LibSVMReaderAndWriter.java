package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import java.util.function.Function;


/**
 * DocumentReader for column format
 *
 * @author Jenny Finkel
 */
//TODO: repair this so it works with the feature label/coreLabel change
public class LibSVMReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  /**
   * 
   */
  private static final long serialVersionUID = -7997837004847909059L;
  private SeqClassifierFlags flags = null;
  private IteratorFromReaderFactory factory;
  
  public void init(SeqClassifierFlags flags) { 
    this.flags = flags;
    factory = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new ColumnDocParser());
  }
  
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  int num = 0;
  private class ColumnDocParser implements Function<String,List<CoreLabel>> {
    public List<CoreLabel> apply(String doc) {

      if (num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;
      
      List<CoreLabel> words = new ArrayList<CoreLabel>();
      
      String[] lines = doc.split("\n");
      
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].trim().length() < 1) {
          continue;
        }
        CoreLabel wi = new CoreLabel();
        String[] info = lines[i].split("\\s+");              
        wi.set(CoreAnnotations.AnswerAnnotation.class, info[0]);
        wi.set(CoreAnnotations.GoldAnswerAnnotation.class, info[0]);
        for (int j = 1; j < info.length; j++) {
          String[] bits = info[j].split(":");
          //wi.set(bits[0], bits[1]);
        }
//        System.err.println(wi);
        words.add(wi);
      }
      return words;
    }
  }
  
  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String goldAnswer = wi.get(CoreAnnotations.GoldAnswerAnnotation.class);
      out.println(goldAnswer + "\t" + answer);
    }
    out.println();
  }

}
