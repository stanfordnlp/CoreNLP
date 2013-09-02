package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.StemAnnotation;
import edu.stanford.nlp.process.Stemmer;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Timing;

/**
 * This class will add the stems of all the
 * words to the
 * Annotation.  It assumes that the Annotation
 * already contains the tokenized words as a 
 * List<? extends CoreLabel> or a List<List<? extends CoreLabel>> under Annotation.WORDS_KEY
 * and addes stem information to each CoreLabel,
 * in the CoreLabel.STEM_KEY field.
 *
 * @author Jenny Finkel
 */
public class StemmerAnnotator implements Annotator{

  private Timing timer = new Timing();
  private static long millisecondsAnnotating = 0;
  private boolean VERBOSE = true;
  private Stemmer stemmer = new Stemmer();
  
  public StemmerAnnotator() {
    this(true);
  }

  public StemmerAnnotator(boolean verbose) {
    VERBOSE = verbose;
  }
  
  public void annotate(Annotation annotation) {
    if (VERBOSE) {    
      timer.start();
      System.err.print("Stemming...");
    }
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
//    List<List<? extends CoreLabel>> sentences  = annotation.get(WordsPLAnnotation.class);
    if (sentences != null) {
        for (CoreMap sent : sentences) {
          List<? extends CoreLabel> words = sent.get(CoreAnnotations.TokensAnnotation.class);
          if(words == null){ throw new IllegalArgumentException("Annotation has no TokensAnnotation"); }
          stemOneSentence(words);
        }
    }
    if (VERBOSE) {    
      millisecondsAnnotating += timer.stop("done.");
      System.err.println("output: "+sentences+"\n"); 
    }    
  }

  private void stemOneSentence(List<? extends CoreLabel> words) {
    for (CoreLabel word : words) {
      word.set(StemAnnotation.class, stemmer.stem(word.word()));
    }
  }
}
