package edu.stanford.nlp.pipeline;

import java.io.File;
import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;

/**
 * Entity extractor for the NFL domain
 * Deprecated! Use NFLAnnotator instead!
 * <br>
 * Annotator that tags NFL entity types
 * 
 * Uses three type of annotations 
 * word: WordAnnotatoin.class 
 * pos: PartOfSpeechAnnotation.class annotations by ner: NERAnnotator (this allows to
 * expand from head word) (optional)
 * 
 * @author Andrey Gusev
 * 
 */
public class NFLEntityAnnotator implements Annotator {

  @SuppressWarnings("unchecked")
  private final CRFClassifier classifier;

  private static final String NFL_ENTITY_CLASSIFIER = "/u/nlp/data/ner/goodClassifiers/nfl.entity.classifier.ser";

  private boolean verbose = true;

  public NFLEntityAnnotator(boolean verbose) throws ClassCastException,
      IOException, ClassNotFoundException {
    this(NFL_ENTITY_CLASSIFIER, verbose);
  }

  public NFLEntityAnnotator(String path, boolean verbose)
      throws ClassCastException, IOException, ClassNotFoundException {
    this.classifier = loadClassifierFromFile(path);
    this.verbose = verbose;
  }

  /**
   * 
   * @param path
   *          - the location of classifier that was saved to disk
   * @throws ClassCastException
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @SuppressWarnings("unchecked")
  private CRFClassifier loadClassifierFromFile(String path)
      throws ClassCastException, IOException, ClassNotFoundException {
    File file = new File(path);
    if (file.exists()) {
      return CRFClassifier.getClassifier(file);
    } else {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence
      for (CoreMap sentence : annotation
          .get(CoreAnnotations.SentencesAnnotation.class)) {
        // each token needs to have two annotations
        // word: TextAnnotation.class
        // pos: PartOfSpeechAnnotation.class
        List<CoreLabel> tokens = sentence
            .get(CoreAnnotations.TokensAnnotation.class);

        List<CoreLabel> annotatedTokens = this.classifier.classify(tokens);

        // now add answer labels to original tokens
        for (int i = 0; i < tokens.size(); ++i) {
          annotateAndExpand(tokens, i, annotatedTokens.get(i).get(
              AnswerAnnotation.class));
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  // annotates given position in tokens with answerAnnotation and expands that
  // annotation for all adjacent identical ner annotation
  private void annotateAndExpand(List<CoreLabel> tokens, int currentIndex,
      String answerAnnotation) {
    // first set current annotation
    tokens.get(currentIndex).set(AnswerAnnotation.class, answerAnnotation);
    
    // don't need to expand if answer annotation is background symbol
    if(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL.equals(answerAnnotation)){
      return;
    }

    String currentNerAnnotation = tokens.get(currentIndex).ner();

    // we don't have to expand annotation in these cases
    if (currentNerAnnotation == null
        || SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL
            .equals(currentNerAnnotation)) {
      return;
    }

    int low = currentIndex - 1;
    while (low >= 0) {
      // only set annotation if ner annotation didn't change and we haven't
      // already set the AnswerAnnotation already
      if (currentNerAnnotation.equals(tokens.get(low).ner())
          && tokens.get(low).get(AnswerAnnotation.class) == null) {
        printIfVerbose("Setting answer annotation on adjacent similar ner left token: "
            + answerAnnotation);
        tokens.get(low).set(AnswerAnnotation.class, answerAnnotation);
      } else {
        break;
      }
      low--;
    }

    int high = currentIndex + 1;
    while (high < tokens.size()) {
      if (currentNerAnnotation.equals(tokens.get(high).ner())) {
        printIfVerbose("Setting answer annotation on adjacent similar ner right token: "
            + answerAnnotation);
        tokens.get(high).set(AnswerAnnotation.class, answerAnnotation);
      } else {
        break;
      }
      high++;
    }
  }

  private void printIfVerbose(String text) {
    if (verbose) {
      System.out.println(text);
    }
  }
}
