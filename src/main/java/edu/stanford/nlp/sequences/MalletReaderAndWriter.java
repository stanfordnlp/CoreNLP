package edu.stanford.nlp.sequences; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import java.util.function.Function;
import edu.stanford.nlp.util.StringUtils;

/**
 * DocumentReaderAndWriter for SimpleTagger of Mallet. 
 * Each line represents one instance, and contains any number of features followed by 
 * the class label. Empty lines are treated as sequence delimiters.
 * See http://mallet.cs.umass.edu/index.php/SimpleTagger_example for more information.
 * 
 * @author Michel Galley
 */
public class MalletReaderAndWriter implements DocumentReaderAndWriter<CoreLabel>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MalletReaderAndWriter.class);

  private static final long serialVersionUID = 3806263423691913704L;

  private SeqClassifierFlags flags = null;
  private String[] map = null;
  private IteratorFromReaderFactory factory;
  
  @Override
  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    this.map = StringUtils.mapStringToArray(flags.map);
    factory = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new MalletDocParser());
  }
  
  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  int num = 0;
  private class MalletDocParser implements Serializable, Function<String,List<CoreLabel>> {
    private static final long serialVersionUID = -6211332661459630572L;
    @Override
    public List<CoreLabel> apply(String doc) {

      if (num % 1000 == 0) { log.info("["+num+"]"); }
      num++;
      
      List<CoreLabel> words = new ArrayList<>();
      
      String[] lines = doc.split("\n");

      for (String line : lines) {
        if (line.trim().length() < 1)
          continue;
        int idx = line.lastIndexOf(" ");
        if (idx < 0)
          throw new RuntimeException("Bad line: " + line);
        CoreLabel wi = new CoreLabel();
        wi.setWord(line.substring(0, idx));
        wi.set(CoreAnnotations.AnswerAnnotation.class, line.substring(idx + 1));
        wi.set(CoreAnnotations.GoldAnswerAnnotation.class, line.substring(idx + 1));
        words.add(wi);
      }
      return words;
    }
  }
  
  @Override
  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String goldAnswer = wi.get(CoreAnnotations.GoldAnswerAnnotation.class);
      out.println(wi.word() + "\t" + goldAnswer + "\t" + answer);
    }
    out.println();
  }

}
