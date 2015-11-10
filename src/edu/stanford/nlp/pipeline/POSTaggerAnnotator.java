package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Wrapper for the maxent part of speech tagger.
 *
 * @author Anna Rafferty
 */
public class POSTaggerAnnotator implements Annotator {

  private final MaxentTagger pos;

  private final int maxSentenceLength;

  private final int nThreads;

  private final boolean reuseTags;

  /** Create a tagger annotator using the default English tagger from the models jar
   *  (and non-verbose initialization).
   */
  public POSTaggerAnnotator() {
    this(false);
  }

  public POSTaggerAnnotator(boolean verbose) {
    this(System.getProperty("pos.model", MaxentTagger.DEFAULT_JAR_PATH), verbose);
  }

  public POSTaggerAnnotator(String posLoc, boolean verbose) {
    this(posLoc, verbose, Integer.MAX_VALUE, 1);
  }

  /** Create a POS tagger annotator.
   *
   *  @param posLoc Location of POS tagger model (may be file path, classpath resource, or URL
   *  @param verbose Whether to show verbose information on model loading
   *  @param maxSentenceLength Sentences longer than this length will be skipped in processing
   *  @param numThreads The number of threads for the POS tagger annotator to use
   */
  public POSTaggerAnnotator(String posLoc, boolean verbose, int maxSentenceLength, int numThreads) {
    this(loadModel(posLoc, verbose), maxSentenceLength, numThreads);
  }

  public POSTaggerAnnotator(MaxentTagger model) {
    this(model, Integer.MAX_VALUE, 1);
  }

  public POSTaggerAnnotator(MaxentTagger model, int maxSentenceLength, int numThreads) {
    this.pos = model;
    this.maxSentenceLength = maxSentenceLength;
    this.nThreads = numThreads;
    this.reuseTags = false;
  }

  public POSTaggerAnnotator(String annotatorName, Properties props) {
    String posLoc = props.getProperty(annotatorName + ".model");
    if (posLoc == null) {
      posLoc = DefaultPaths.DEFAULT_POS_MODEL;
    }
    boolean verbose = PropertiesUtils.getBool(props, annotatorName + ".verbose", false);
    this.pos = loadModel(posLoc, verbose);
    this.maxSentenceLength = PropertiesUtils.getInt(props, annotatorName + ".maxlen", Integer.MAX_VALUE);
    this.nThreads = PropertiesUtils.getInt(props, annotatorName + ".nthreads", PropertiesUtils.getInt(props, "nthreads", 1));
    this.reuseTags = PropertiesUtils.getBool(props, annotatorName + ".reuseTags", false);
  }

  public static String signature(Properties props) {
    return ("pos.maxlen:" + props.getProperty("pos.maxlen", "") +
            "pos.verbose:" + PropertiesUtils.getBool(props, "pos.verbose") + 
            "pos.reuseTags:" + PropertiesUtils.getBool(props, "pos.reuseTags") + 
            "pos.model:" + props.getProperty("pos.model", DefaultPaths.DEFAULT_POS_MODEL) +
            "pos.nthreads:" + props.getProperty("pos.nthreads", props.getProperty("nthreads", "")));
  }

  private static MaxentTagger loadModel(String loc, boolean verbose) {
    Timing timer = null;
    if (verbose) {
      timer = new Timing();
      timer.doing("Loading POS Model [" + loc + ']');
    }
    MaxentTagger tagger = new MaxentTagger(loc);
    if (verbose) {
      timer.done();
    }
    return tagger;
  }

  @Override
  public void annotate(Annotation annotation) {
    // turn the annotation into a sentence
    if (annotation.has(CoreAnnotations.SentencesAnnotation.class)) {
      if (nThreads == 1) {
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          doOneSentence(sentence);
        }
      } else {
        MulticoreWrapper<CoreMap, CoreMap> wrapper = new MulticoreWrapper<CoreMap, CoreMap>(nThreads, new POSTaggerProcessor());
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          wrapper.put(sentence);
          while (wrapper.peek()) {
            wrapper.poll();
          }
        }
        wrapper.join();
        while (wrapper.peek()) {
          wrapper.poll();
        }
      }
    } else {
      throw new RuntimeException("unable to find words/tokens in: " + annotation);
    }
  }

  private class POSTaggerProcessor implements ThreadsafeProcessor<CoreMap, CoreMap> {
    @Override
    public CoreMap process(CoreMap sentence) {
      return doOneSentence(sentence);
    }

    @Override
    public ThreadsafeProcessor<CoreMap, CoreMap> newInstance() {
      return this;
    }
  }

  private CoreMap doOneSentence(CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<TaggedWord> tagged = null;
    if (tokens.size() <= maxSentenceLength) {
      try {
        tagged = pos.tagSentence(tokens, this.reuseTags);
      } catch (OutOfMemoryError e) {
        System.err.println("WARNING: Tagging of sentence ran out of memory. " +
                           "Will ignore and continue: " +
                           Sentence.listToString(tokens));
      }
    }

    if (tagged != null) {
      for (int i = 0, sz = tokens.size(); i < sz; i++) {
        tokens.get(i).set(CoreAnnotations.PartOfSpeechAnnotation.class, tagged.get(i).tag());
      }
    } else {
      for (int i = 0, sz = tokens.size(); i < sz; i++) {
        tokens.get(i).set(CoreAnnotations.PartOfSpeechAnnotation.class, "X");
      }
    }
    return sentence;
  }

  @Override
  public Set<Requirement> requires() {
    return Annotator.REQUIREMENTS.get(STANFORD_POS);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(POS_REQUIREMENT);
  }

}
