package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * DocumentReader for Ben Taskar's OCR data
 *
 * @author Jenny Finkel
 */
//TODO: Repair this so it works with featurelabel/corelabel switch
public class OCRDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 8481207852016988480L;

  private String fold;
  private boolean train;

  public void init(SeqClassifierFlags flags) {
//     this.fold = ""+flags.fold;
//     this.train = flags.train;
  }

  public Iterator<List<CoreLabel>> getIterator (Reader reader) {
    try {
      BufferedReader r = new BufferedReader(reader);
      List<List<CoreLabel>> words = new ArrayList<List<CoreLabel>>();
      List<String[]> word = new ArrayList<String[]>();
      while (r.ready()) {
        String[] line = r.readLine().split("\\s+");
        // skip if training and this is not the train fold,
        // or testing and this is the train fold
        if (train != line[5].equals(fold)) { continue; }
        word.add(line);
        if (line[2].equals("-1")) {
          words.add(processWord(word));
          word = new ArrayList<String[]>();
        }
      }
      return words.iterator();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @param doc The documents: A list of CoreLabels
   */
  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    for (CoreLabel wi : doc) {
      pw.println(wi.get(GoldAnswerAnnotation.class)+"\t"+wi.get(AnswerAnnotation.class));
    }
    pw.println();
  }

  public static List<CoreLabel> processWord(List<String[]> words) {

    List<CoreLabel> letters = new ArrayList<CoreLabel>();

    for (String[] word : words) {
      CoreLabel wi = new CoreLabel();
      wi.set(AnswerAnnotation.class, word[1]);
      for (int i = 6; i < word.length; i++) {
        //wi.put("p_"+((i-6)/8)+"_"+((i-6)%8), word[i].intern());
      }
      letters.add(wi);
    }
    return letters;
  }

}

