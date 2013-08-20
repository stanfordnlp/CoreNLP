package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Timing;

/**
 * This class adds NER information to an annotation using the RegexNERSequenceClassifier.
 * It assumes that the Annotation has already been split into sentences, then tokenized
 * into Lists of CoreLabels. Adds NER information to each CoreLabel as a NamedEntityTagAnnotation.
 *
 * @author jtibs
 *
 */

public class RegexNERAnnotator implements Annotator {

  private final RegexNERSequenceClassifier classifier;
  private final Timing timer;
  private final boolean verbose;

  public RegexNERAnnotator(String mapping) {
    this(mapping, false, true, RegexNERSequenceClassifier.DEFAULT_VALID_POS, false);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase) {
    this(mapping, ignoreCase, RegexNERSequenceClassifier.DEFAULT_VALID_POS);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase, String validPosPattern) {
    this(mapping, ignoreCase, true, validPosPattern, false);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase, boolean overwriteMyLabels, String validPosPattern, boolean verbose) {
    classifier = new RegexNERSequenceClassifier(mapping, ignoreCase, overwriteMyLabels, validPosPattern);
    timer = new Timing();
    this.verbose = verbose;
  }

  public void annotate(Annotation annotation) {
    if (verbose) {
      timer.start();
      System.err.print("Adding RegexNER annotation...");
    }

    if (! annotation.containsKey(SentencesAnnotation.class))
      throw new RuntimeException("Unable to find sentences in " + annotation);

    List<CoreMap> sentences = annotation.get(SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
      classifier.classify(tokens);

      for (CoreLabel token : tokens) {
        if (token.get(NamedEntityTagAnnotation.class) == null)
          token.set(NamedEntityTagAnnotation.class, classifier.flags.backgroundSymbol);
      }

      for (int start = 0; start < tokens.size(); start++) {
        CoreLabel token = tokens.get(start);
        String answerType = token.get(AnswerAnnotation.class);
        String NERType = token.get(NamedEntityTagAnnotation.class);
        if (answerType == null) continue;

        int answerEnd = findEndOfAnswerAnnotation(tokens, start);
        int NERStart = findStartOfNERAnnotation(tokens, start);
        int NEREnd = findEndOfNERAnnotation(tokens, start);

        // check that the spans are the same, specially handling the case of
        // tokens with background named entity tags ("other")
        if ((NERStart == start || NERType.equals(classifier.flags.backgroundSymbol)) &&
            (answerEnd == NEREnd || (NERType.equals(classifier.flags.backgroundSymbol) && NEREnd >= answerEnd))) {

          // annotate each token in the span
          for (int i = start; i < answerEnd; i ++)
            tokens.get(i).set(NamedEntityTagAnnotation.class, answerType);
        }
        start = answerEnd - 1;
      }
    }

    if (verbose)
      timer.stop("done.");
  }

  private static int findEndOfAnswerAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(AnswerAnnotation.class);
    while (start < tokens.size() && type.equals(tokens.get(start).get(AnswerAnnotation.class)))
      start++;
    return start;
  }

  private static int findStartOfNERAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(NamedEntityTagAnnotation.class);
    while (start >= 0 && type.equals(tokens.get(start).get(NamedEntityTagAnnotation.class)))
      start--;
    return start + 1;
  }

  private static int findEndOfNERAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(NamedEntityTagAnnotation.class);
    while (start < tokens.size() && type.equals(tokens.get(start).get(NamedEntityTagAnnotation.class)))
      start++;
    return start;
  }
}
