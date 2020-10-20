package edu.stanford.nlp.ie.crf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Properties;

import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;

public class TestThreadedCRFClassifier {

  TestThreadedCRFClassifier(Properties props) {
    inputEncoding = props.getProperty("inputEncoding", "UTF-8");
  }

  // number of threads to run the first specified classifier under
  private static final int DEFAULT_SIM_THREADS = 3;

  private static final int DEFAULT_MULTIPLE_THREADS = 2;

  private final String inputEncoding;

  static CRFClassifier loadClassifier(String loadPath, Properties props) {
    CRFClassifier crf = new CRFClassifier(props);
    crf.loadClassifierNoExceptions(loadPath, props);
    return crf;
  }

  String runClassifier(CRFClassifier crf, String testFile) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      crf.classifyAndWriteAnswers(testFile, output, crf.makeReaderAndWriter(), true);
      return output.toString(inputEncoding);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  class CRFThread extends Thread {
    private final CRFClassifier crf;
    private final String filename;
    private final String threadName;

    private String resultsString = "";
    public String getResultsString() { return resultsString; }

    CRFThread(CRFClassifier crf, String filename, String threadName) {
      this.crf = crf;
      this.filename = filename;
      this.threadName = threadName;
    }

    @Override
    public void run() {
      Timing t = new Timing();
      resultsString = runClassifier(crf, filename);
      long millis = t.stop();
      System.out.println("Thread " + threadName + " took " + millis +
                         "ms to tag file " + filename);
    }
  }

  /**
   * Sample command line:
   * <br>
   * java -mx4g edu.stanford.nlp.ie.crf.TestThreadedCRFClassifier
   * -crf1 ../stanford-releases/stanford-ner-models/hgc_175m_600.ser.gz
   * -crf2 ../stanford-releases/stanford-ner-models/dewac_175m_600.ser.gz
   * -testFile ../data/german-ner/deu.testa -inputEncoding iso-8859-1
   */
  public static void main(String[] args) {
    try {
      System.setOut(new PrintStream(System.out, true, "UTF-8"));
      System.setErr(new PrintStream(System.err, true, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }

    runTest(StringUtils.argsToProperties(args));
  }

  static public void runTest(Properties props) {
    TestThreadedCRFClassifier test = new TestThreadedCRFClassifier(props);
    test.runThreadedTest(props);
  }


  void runThreadedTest(Properties props) {
    // TODO: check params
    final String testFile = props.getProperty("testFile");
    ArrayList<String> baseResults = new ArrayList<>();
    ArrayList<String> modelNames = new ArrayList<>();
    ArrayList<CRFClassifier> classifiers = new ArrayList<>();

    for (int i = 1;
         props.getProperty("crf" + Integer.toString(i)) != null; ++i) {
      String model = props.getProperty("crf" + Integer.toString(i));
      CRFClassifier crf = loadClassifier(model, props);
      System.out.println("Loaded model " + model);
      modelNames.add(model);
      classifiers.add(crf);

      String results = runClassifier(crf, testFile);
      // must run twice to account for "transductive learning"
      results = runClassifier(crf, testFile);
      baseResults.add(results);
      System.out.println("Stored base results for " + model +
                         "; length " + results.length());
    }

    // test to make sure loading and running multiple classifiers
    // hasn't messed with previous results
    for (int i = 0; i < classifiers.size(); ++i) {
      CRFClassifier crf = classifiers.get(i);
      String model = modelNames.get(i);
      String base = baseResults.get(i);

      String repeated = runClassifier(crf, testFile);
      if (!base.equals(repeated)) {
        throw new RuntimeException("Repeated unthreaded results " +
                                   "not the same for " + model +
                                   " run on file " + testFile);
      }
    }

    // test the first classifier in several simultaneous threads
    int numThreads = PropertiesUtils.getInt(props, "simThreads",
                                            DEFAULT_SIM_THREADS);

    ArrayList<CRFThread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; ++i) {
      threads.add(new CRFThread(classifiers.get(0), testFile,
                                "Simultaneous-" + i));
    }
    for (int i = 0; i < numThreads; ++i) {
      threads.get(i).start();
    }
    for (int i = 0; i < numThreads; ++i) {
      try {
        threads.get(i).join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (baseResults.get(0).equals(threads.get(i).getResultsString())) {
        System.out.println("Yay!");
      } else {
        throw new RuntimeException("Results not equal when running " +
                                   modelNames.get(0) + " under " +
                                   numThreads + " simultaneous threads");
      }
    }

    // test multiple classifiers (if given) in multiple threads each
    if (classifiers.size() > 1) {
      numThreads = PropertiesUtils.getInt(props, "multipleThreads",
                                          DEFAULT_MULTIPLE_THREADS);
      threads = new ArrayList<CRFThread>();
      for (int i = 0; i < numThreads * classifiers.size(); ++i) {
        int classifierNum = i % classifiers.size();
        int repeatNum = i / classifiers.size();
        threads.add(new CRFThread(classifiers.get(classifierNum), testFile,
                                  ("Simultaneous-" + classifierNum +
                                   "-" + repeatNum)));
      }
      for (CRFThread thread : threads) {
        thread.start();
      }
      for (int i = 0; i < threads.size(); ++i) {
        int classifierNum = i % classifiers.size();
        int repeatNum = i / classifiers.size();
        try {
          threads.get(i).join();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        String base = baseResults.get(classifierNum);
        String threadResults = threads.get(i).getResultsString();
        if (base.equals(threadResults)) {
          System.out.println("Yay!");
        } else {
          throw new RuntimeException("Results not equal when running " +
                                     modelNames.get(classifierNum) +
                                     " under " + numThreads +
                                     " threads with " +
                                     classifiers.size() +
                                     " total classifiers");
        }
      }
    }

    // if no exceptions thrown, great success
    System.out.println("Everything worked!");
  }

}
