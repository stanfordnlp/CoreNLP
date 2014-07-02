package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

public abstract class SentenceAnnotator implements Annotator {
  protected class AnnotatorProcessor implements ThreadsafeProcessor<CoreMap, CoreMap> {
    Annotation annotation;

    AnnotatorProcessor(Annotation annotation) {
      this.annotation = annotation;
    }

    @Override
    public CoreMap process(CoreMap sentence) {
      doOneSentence(annotation, sentence);
      return sentence;
    }

    @Override
    public ThreadsafeProcessor<CoreMap, CoreMap> newInstance() {
      return this;
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      if (nThreads() != 1 || maxTime() > 0) {
        System.err.println(getClass() + " with time limit " + maxTime());
        MulticoreWrapper<CoreMap, CoreMap> wrapper = new MulticoreWrapper<CoreMap, CoreMap>(nThreads(), new AnnotatorProcessor(annotation));
        if (maxTime() > 0) {
          wrapper.setMaxBlockTime(maxTime());
        }
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
      } else {
        System.err.println(getClass() + " with no time limit");
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          doOneSentence(annotation, sentence);
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  protected abstract int nThreads();

  protected abstract long maxTime();

  /** annotation is included in case there is global information we care about */
  protected abstract void doOneSentence(Annotation annotation, CoreMap sentence);
}

