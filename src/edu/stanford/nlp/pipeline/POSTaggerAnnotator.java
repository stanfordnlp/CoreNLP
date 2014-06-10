package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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

  private int maxSentenceLength;

  private int nThreads = 1;

  public POSTaggerAnnotator() {
    this(true);
  }

  public POSTaggerAnnotator(boolean verbose) {
    this(System.getProperty("pos.model", MaxentTagger.DEFAULT_JAR_PATH), verbose);
  }

  public POSTaggerAnnotator(String posLoc, boolean verbose) {
    this(posLoc, verbose, Integer.MAX_VALUE);
  }

  public POSTaggerAnnotator(String posLoc, boolean verbose, int maxSentenceLength) {
    this(loadModel(posLoc, verbose), maxSentenceLength);
  }

  public POSTaggerAnnotator(MaxentTagger model) {
    this(model, Integer.MAX_VALUE);
  }

  public POSTaggerAnnotator(MaxentTagger model, int maxSentenceLength) {
    this.pos = model;
    this.maxSentenceLength = maxSentenceLength;
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
  }

  public void setMaxSentenceLength(int maxLen) {
    this.maxSentenceLength = maxLen;
  }

  private static MaxentTagger loadModel(String loc, boolean verbose) {
    Timing timer = null;
    if (verbose) {
      timer = new Timing();
      timer.doing("Loading POS Model [" + loc + ']');
    }
    MaxentTagger tagger;
    tagger = new MaxentTagger(loc);
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
    List<TaggedWord> tagged = pos.apply(tokens);

    for (int i = 0; i < tokens.size(); ++i) {
      tokens.get(i).set(CoreAnnotations.PartOfSpeechAnnotation.class, tagged.get(i).tag());
    }
    return sentence;
  }

  @Override
  public Set<Requirement> requires() {
    return TOKENIZE_AND_SSPLIT;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(POS_REQUIREMENT);
  }

}
