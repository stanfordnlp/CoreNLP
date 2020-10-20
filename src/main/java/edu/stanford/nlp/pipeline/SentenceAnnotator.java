package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.concurrent.InterruptibleMulticoreWrapper;
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

    final Annotation annotation;

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

  private InterruptibleMulticoreWrapper<CoreMap, CoreMap> buildWrapper(Annotation annotation) {
    InterruptibleMulticoreWrapper<CoreMap, CoreMap> wrapper = new InterruptibleMulticoreWrapper<>(nThreads(), new AnnotatorProcessor(annotation), true, maxTime());
    return wrapper;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      if (nThreads() != 1 || maxTime() > 0) {
        InterruptibleMulticoreWrapper<CoreMap, CoreMap> wrapper = buildWrapper(annotation);
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          boolean success = false;
          // We iterate twice for each sentence so that if we fail for
          // a sentence once, we start a new queue and try again.
          // If the sentence fails a second time we give up.
          for (int attempt = 0; attempt < 2; ++attempt) {
            try {
              wrapper.put(sentence);
              success = true;
              break;
            } catch (RejectedExecutionException e) {
              // If we time out, for now, we just throw away all jobs which were running at the time.
              // Note that in order for this to be useful, the underlying job needs to handle Thread.interrupted()
              List<CoreMap> failedSentences = wrapper.joinWithTimeout();
              if (failedSentences != null) {
                for (CoreMap failed : failedSentences) {
                  doOneFailedSentence(annotation, failed);
                }
              }
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
          if (!success) {
            doOneFailedSentence(annotation, sentence);
          }
          while (wrapper.peek()) {
            wrapper.poll();
          }
        }
        List<CoreMap> failedSentences = wrapper.joinWithTimeout();
        while (wrapper.peek()) {
          wrapper.poll();
        }
        if (failedSentences != null) {
          for (CoreMap failed : failedSentences) {
            doOneFailedSentence(annotation, failed);
          }
        }
      } else {
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          if (Thread.interrupted()) {
            throw new RuntimeInterruptedException();
          }
          doOneSentence(annotation, sentence);
        }
      }
    } else {
      throw new IllegalArgumentException("unable to find sentences in: " + annotation);
    }
  }

  protected abstract int nThreads();

  /**
   * The maximum time to run this annotator for, in milliseconds.
   */
  protected abstract long maxTime();

  /** annotation is included in case there is global information we care about */
  protected abstract void doOneSentence(Annotation annotation, CoreMap sentence);

  /**
   * Fills in empty annotations for trees, tags, etc if the annotator
   * failed or timed out.  Not supposed to do major processing.
   *
   * @param annotation The whole Annotation object, in case it is needed for context.
   * @param sentence The particular sentence to process
   */
  protected abstract void doOneFailedSentence(Annotation annotation, CoreMap sentence);

}

