package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * A parent class for annotators which might want to analyze one
 * sentence at a time, possibly in a multithreaded manner.
 *
 * TODO: also factor out the POS
 *
 * @author John Bauer
 */
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

  private MulticoreWrapper<CoreMap, CoreMap> buildWrapper(Annotation annotation) {
    MulticoreWrapper<CoreMap, CoreMap> wrapper = new MulticoreWrapper<CoreMap, CoreMap>(nThreads(), new AnnotatorProcessor(annotation));
    if (maxTime() > 0) {
      wrapper.setMaxBlockTime(maxTime());
    }
    return wrapper;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      if (nThreads() != 1 || maxTime() > 0) {
        MulticoreWrapper<CoreMap, CoreMap> wrapper = buildWrapper(annotation);
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          for (int attempt = 0; attempt < 2; ++attempt) {
            try {
              wrapper.put(sentence);
              break;
            } catch (RejectedExecutionException e) {
              // If we time out, for now, we just throw away all jobs which were running at the time.
              // Note that in order for this to be useful, the underlying job needs to handle Thread.interrupted()
              wrapper.shutdownNow();
              // We don't wait for termination here, and perhaps this
              // is a mistake.  If the processor used does not respect
              // interruption, we could easily create many threads
              // which are all doing useless work.  However, there is
              // no clean way to interrupt the thread and then
              // guarantee it finishes without running the risk of
              // waiting forever for the thread to finish, which is
              // exactly what we don't want with the timeout.
              wrapper = buildWrapper(annotation);
            }
          }
          while (wrapper.peek()) {
            wrapper.poll();
          }
        }
        wrapper.join();
        while (wrapper.peek()) {
          wrapper.poll();
        }
      } else {
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

