package edu.stanford.nlp.wordseg;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/** @author KellenSunderland (public domain contribution) */
public class ChineseStringUtilsTest extends TestCase {

  private static final Integer SEGMENT_ATTEMPTS_PER_THREAD = 100;
  private static final Integer THREADS = 8;

  /**
   * A small test with stubbed data that is meant to expose multithreading initialization errors
   * in combineSegmentedSentence.
   *
   * In my testing this reliably reproduces the crash seen in the issue:
   * https://github.com/stanfordnlp/CoreNLP/issues/263
   *
   * @throws Exception Various exceptions including Interrupted, all of which should be handled by
   *                   failing the test.
   */
  public void testMultithreadedCombineSegmentedSentence() throws Exception {
    SeqClassifierFlags flags = createTestFlags();
    List<CoreLabel> labels = createTestTokens();
    List<Future<Boolean>> tasks = new ArrayList<>(THREADS);
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    for (int v = 0; v < THREADS; v++) {
      Future<Boolean> f = executor.submit(() -> {
        for (int i = 0; i < SEGMENT_ATTEMPTS_PER_THREAD; i++) {
          ChineseStringUtils.combineSegmentedSentence(labels, flags);
        }
        return true;
      });
      tasks.add(f);
    }

    for (Future<Boolean> task : tasks) {
      // This assert will fail by throwing a propagated exception, if exceptions due to
      // multithreading issues (generally NPEs) were thrown during the test.
      assert (task.get());
    }
  }

  // Arbitrary test input.  We just need to segment something on multiple threads to reproduce
  // the issue
  private static List<CoreLabel> createTestTokens() {
    CoreLabel token = new CoreLabel();
    token.setWord("你好，世界");
    token.setValue("你好，世界");
    token.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
    token.set(CoreAnnotations.AnswerAnnotation.class, "0");
    List<CoreLabel> labels = new ArrayList<>();
    labels.add(token);
    return labels;
  }

  // Somewhat arbitrary flags.  We're just picking flags that will execute the problematic code
  // path.
  private static SeqClassifierFlags createTestFlags() {
    SeqClassifierFlags flags = new SeqClassifierFlags();
    flags.sighanPostProcessing = true;
    flags.usePk = true;
    flags.keepEnglishWhitespaces = false;
    flags.keepAllWhitespaces = false;
    return flags;
  }

}
