package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * This class will add the lemmas of all the words to the Annotation.
 * It assumes that the Annotation already contains the tokenized words as
 * a {@code List<CoreLabel>} for a list of sentences under the
 * {@code SentencesAnnotation.class} key.
 * The Annotator adds lemma information to each CoreLabel,
 * in the LemmaAnnotation.class.
 *
 * @author Jenny Finkel
 */
public class MorphaAnnotator implements Annotator {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MorphaAnnotator.class);

  private boolean VERBOSE = false;


  private static final String[] prep = {"abroad", "across", "after", "ahead", "along", "aside", "away", "around", "back", "down", "forward", "in", "off", "on", "over", "out", "round", "together", "through", "up"};
  private static final List<String> particles = Arrays.asList(prep);

  public MorphaAnnotator() {
    this(true);
  }

  public MorphaAnnotator(boolean verbose) {
    VERBOSE = verbose;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Finding lemmas ...");
    }
    Morphology morphology = new Morphology();
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        //log.info("Lemmatizing sentence: " + tokens);
        for (CoreLabel token : tokens) {
          String text = token.get(CoreAnnotations.TextAnnotation.class);
          String posTag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
          addLemma(morphology, CoreAnnotations.LemmaAnnotation.class, token, text, posTag);
        }
      }
    } else {
      throw new RuntimeException("Unable to find words/tokens in: " +
                                 annotation);
    }
  }


  private static void addLemma(Morphology morpha,
                        Class<? extends CoreAnnotation<String>> ann,
                        CoreMap map, String word, String tag) {
    if ( ! tag.isEmpty()) {
      String phrasalVerb = phrasalVerb(morpha, word, tag);
      if (phrasalVerb == null) {
        map.set(ann, morpha.lemma(word, tag));
      } else {
        map.set(ann, phrasalVerb);
      }
    } else {
      map.set(ann, morpha.stem(word));
    }
  }


  /** If a token is a phrasal verb with an underscore between a verb and a
   *  particle, return the phrasal verb lemmatized. If not, return null
   */
  private static String phrasalVerb(Morphology morpha, String word, String tag) {

    // must be a verb and contain an underscore
    assert(word != null);
    assert(tag != null);
    if(!tag.startsWith("VB")  || !word.contains("_")) return null;

    // check whether the last part is a particle
    String[] verb = word.split("_");
    if(verb.length != 2) return null;
    String particle = verb[1];
    if(particles.contains(particle)) {
      String base = verb[0];
      String lemma = morpha.lemma(base, tag);
      return lemma + '_' + particle;
    }

    return null;
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.LemmaAnnotation.class);
  }

}
