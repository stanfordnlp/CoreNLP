package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public class Util {

  public static String guessNER(List<CoreLabel> tokens, Span span) {
    Counter<String> nerGuesses = new ClassicCounter<>();
    for (int i : span) {
      nerGuesses.incrementCount(tokens.get(i).ner());
    }
    nerGuesses.remove("O");
    nerGuesses.remove(null);
    if (nerGuesses.size() > 0) {
      return Counters.argmax(nerGuesses);
    } else {
      return "O";
    }
  }

  public static String guessNER(List<CoreLabel> tokens) {
    return guessNER(tokens, new Span(0, tokens.size()));
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param tokens
   * @param seed
   * @return
   */
  public static Span extractNER(List<CoreLabel> tokens, Span seed) {
    if (seed == null) {
      return new Span(0, 1);
    }
    if (tokens.get(seed.start()).ner() == null) {
      return seed;
    }
    int begin = seed.start();
    while (begin > 0 && tokens.get(begin - 1).ner().equals(tokens.get(seed.start()).ner())) {
      begin -= 1;
    }
    int end = seed.end() - 1;
    while (end < tokens.size() - 1 && tokens.get(end + 1).ner().equals(tokens.get(seed.end() - 1).ner())) {
      end += 1;
    }
    return Span.fromValues(begin, end + 1);
  }


  public static void annotate(CoreMap sentence, AnnotationPipeline pipeline) {
    Annotation ann = new Annotation(StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class), " "));
    ann.set(CoreAnnotations.TokensAnnotation.class, sentence.get(CoreAnnotations.TokensAnnotation.class));
    ann.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
    pipeline.annotate(ann);
  }
}
