package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import edu.stanford.nlp.io.IOUtils;

public class ThreadedStanfordCoreNLPSlowITest extends TestCase {

  static List<String> convertAnnotations(List<Annotation> annotations,
                                         StanfordCoreNLP pipeline)
      throws IOException
  {
    List<String> converted = new ArrayList<>();
    for (Annotation annotation : annotations) {
      StringWriter out = new StringWriter();
      pipeline.xmlPrint(annotation, out);
      converted.add(out.toString());
    }
    return converted;
  }

  static List<String> getAnnotations(List<File> files,
                                     StanfordCoreNLP pipeline)
      throws IOException
  {
    List<Annotation> annotations = new ArrayList<>();

    for (File file : files) {
      String text = IOUtils.slurpFile(file);
      Annotation annotation = pipeline.process(text);
      annotations.add(annotation);
      System.out.println("Processed " + annotations.size());
    }

    return convertAnnotations(annotations, pipeline);
  }

  static class CoreNLPThread extends Thread {
    List<String> annotations;
    private final List<File> files;
    private final StanfordCoreNLP pipeline;

    CoreNLPThread(List<File> files, StanfordCoreNLP pipeline) {
      this.files = files;
      this.pipeline = pipeline;
    }

    @Override
    public void run() {
      try {
        annotations = getAnnotations(files, pipeline);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }


  private static final int numThreads = 2;
  private static final int numDocs = 10;

  public void testTwoThreads() throws Exception {
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("maxAdditionalKnownLCWords", "0"));
    List<File> files = StanfordCoreNLPSlowITest.getFileList();
    files = files.subList(0, numDocs);

    List<String> baseline = getAnnotations(files, pipeline);

    CoreNLPThread[] threads = new CoreNLPThread[numThreads];
    for (int i = 0; i < numThreads; ++i) {
      threads[i] = new CoreNLPThread(files, pipeline);
      threads[i].start();
    }
    for (int i = 0; i < numThreads; ++i) {
      threads[i].join();
      assertEquals("Thread " + i + " did not produce " +
                   baseline.size() + " results",
                   baseline.size(), threads[i].annotations.size());
    }
    for (int i = 0; i < baseline.size(); ++i) {
      //System.out.println("Baseline " + i + ":");
      //System.out.println(baseline.get(i));
      for (int j = 0; j < numThreads; ++j) {
        //System.out.println("Thread " + j + " annotation " + i + ":");
        //System.out.println(threads[j].annotations.get(i));
        assertEquals("Annotating document " + i + ": thread " + j + " produced annotation:\n" +
                        threads[j].annotations.get(i) + "versus the baseline:\n" + baseline.get(i),
                     baseline.get(i), threads[j].annotations.get(i));
      }
    }
  }

}

