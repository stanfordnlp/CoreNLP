// TestThreadedTagger -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2011 Leland Stanford Junior University
//
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tagger.shtml
package edu.stanford.nlp.tagger.maxent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.StringUtils;

/**
 * First, this runs a tagger once to see what results it comes up with.
 * Then it runs the same tagger in two separate threads to make sure the results are the same.
 * The results are printed to stdout; the user is expected to verify they are as expected.
 *
 * Normally you would run MaxentTagger with command line arguments such as:
 *
 * -model ../data/tagger/my-left3words-distsim-wsj-0-18.tagger
 * -testFile ../data/tagger/test-wsj-19-21 -verboseResults false
 *
 * If you provide the same arguments to this program, it will first
 * run the given tagger on the given test file once to establish the
 * "baseline" results.  It will then run the same tagger in more than
 * one thread at the same time; the output for both threads should be
 * the same if the MaxentTagger is re-entrant.  The number of threads
 * to be run can be specified with -numThreads; the default is
 * DEFAULT_NUM_THREADS.
 *
 * You can also provide multiple models.  After performing that test
 * on model1, it will then run the same test file on model2, model3,
 * etc to establish baseline results for that tagger.  After that, it
 * runs both taggers at the same time.  The taggers should be
 * completely separate structures.  In other words, the second tagger
 * should not have clobbered any static state initialized by the first
 * tagger.  Thus, the results of the two simultaneous taggers should
 * be the same as the two taggers' baselines.
 *
 * Example arguments for the more complicated test:
 *
 * -model1 ../data/pos-tagger/newmodels/left3words-distsim-wsj-0-18.tagger
 * -model2 ../data/pos-tagger/newmodels/left3words-wsj-0-18.tagger
 * -testFile ../data/pos-tagger/training/english/test-wsj-19-21
 * -verboseResults false
 *
 * @author John Bauer
 */
class TestThreadedTagger {
  /**
   * Default number of threads to launch in the first test.
   * Can be specified with -numThreads.
   */
  static final int DEFAULT_NUM_THREADS = 2;

  static final String THREAD_FLAG = "numThreads";


  private TestThreadedTagger() {} // static methods


  /**
   * This internal class takes a config, a tagger, and a thread name.
   * The "run" method then runs the given tagger on the data file
   * specified in the config.
   */
  private static class TaggerThread extends Thread {

    private final MaxentTagger tagger;
    private final String threadName;

    private String resultsString = "";
    public String getResultsString() { return resultsString; }

    TaggerThread(MaxentTagger tagger, String name) {
      this.tagger = tagger;
      this.threadName = name;
    }

    @Override
    public void run() {
      try {
        Timing t = new Timing();
        TestClassifier testClassifier = new TestClassifier(tagger);
        long millis = t.stop();
        resultsString = testClassifier.resultsString(tagger);
        System.out.println("Thread " + threadName + " took " + millis +
                           " milliseconds to tag " + testClassifier.getNumWords() +
                           " words.\n" + resultsString);
      } catch(IOException e) {
        throw new RuntimeException(e);
      }
    }
  } // end class TaggerThread

  public static void compareResults(String results, String baseline) {
    if (!results.equals(baseline)) {
      throw new RuntimeException("Results different from expected baseline");
    }
  }

  public static void main(final String[] args)
    throws ClassNotFoundException, IOException, InterruptedException
  {
    Properties props = StringUtils.argsToProperties(args);
    runThreadedTest(props);
  }

  public static void runThreadedTest(Properties props)
    throws ClassNotFoundException, IOException, InterruptedException
  {
    ArrayList<Properties> configs = new ArrayList<>();
    ArrayList<MaxentTagger> taggers = new ArrayList<>();
    int numThreads = DEFAULT_NUM_THREADS;

    // let the user specify how many threads to run in the first test case
    if (props.getProperty(THREAD_FLAG) != null) {
      numThreads = Integer.valueOf(props.getProperty(THREAD_FLAG));
    }

    // read in each of the taggers specified on the command line
    System.out.println();
    System.out.println("Loading taggers...");
    System.out.println();

    if (props.getProperty("model") != null) {
      configs.add(props);
      taggers.add(new MaxentTagger(configs.get(0).getProperty("model"), configs.get(0)));
    } else {
      int taggerNum = 1;
      String taggerName = "model" + taggerNum;
      while (props.getProperty(taggerName) != null) {
        Properties newProps = new Properties();
        newProps.putAll(props);
        newProps.setProperty("model", props.getProperty(taggerName));
        configs.add(newProps);
        taggers.add(new MaxentTagger(configs.get(taggerNum - 1).getProperty("model"),
                                     configs.get(taggerNum - 1)));

        ++taggerNum;
        taggerName = "model" + taggerNum;
      }
    }

    // no models at all => bad
    if (taggers.isEmpty()) {
      throw new IllegalArgumentException("Please specify at least one of " +
                                         "-model or -model1");
    }

    System.out.println();
    System.out.println("Running the baseline results for tagger 1");
    System.out.println();

    // run baseline results for the first tagger model
    TaggerThread baselineThread =
      new TaggerThread(taggers.get(0), "BaseResults-1");
    baselineThread.start();
    baselineThread.join();

    ArrayList<String> baselineResults = new ArrayList<>();
    baselineResults.add(baselineThread.getResultsString());

    System.out.println();
    System.out.println("Running " + numThreads + " threads of tagger 1");
    System.out.println();

    // run the first tagger in X separate threads at the same time
    // at the end of this test, those X threads should produce the same results
    ArrayList<TaggerThread> threads = new ArrayList<>();
    for (int i = 0; i < numThreads; ++i) {
      threads.add(new TaggerThread(taggers.get(0),
                                   "Simultaneous-" + (i + 1)));
    }
    for (TaggerThread thread : threads) {
      thread.start();
    }
    for (TaggerThread thread : threads) {
      thread.join();
      compareResults(thread.getResultsString(),
                     baselineResults.get(0));
    }

    // if we have more than one model...
    if (taggers.size() > 1) {
      // first, produce baseline results for the other models
      // do this one thread at a time so we know there are no
      // thread-related screwups
      // TODO: would iterables be cleaner?
      for (int i = 1; i < taggers.size(); ++i) {
        System.out.println();
        System.out.println("Running the baseline results for tagger " + (i + 1));
        System.out.println();

        baselineThread = new TaggerThread(taggers.get(i),
                                          "BaseResults-" + (i + 1));
        baselineThread.start();
        baselineThread.join();
        baselineResults.add(baselineThread.getResultsString());
      }

      System.out.println();
      System.out.println("Running " + taggers.size() +
                         " threads of different taggers");
      System.out.println();

      // now, run the X models at the same time.  there used to be a
      // whole bunch of static state in the tagger, which used to mean
      // such a thing was not be possible to do.  now that should not
      // be a problem any more
      threads.clear();
      for (int i = 0; i < taggers.size(); ++i) {
        threads.add(new TaggerThread(taggers.get(i),
                                     "DifferentTaggers-" + (i + 1)));
      }
      for (TaggerThread thread : threads) {
        thread.start();
      }
      for (int i = 0; i < taggers.size(); ++i) {
        TaggerThread thread = threads.get(i);
        thread.join();
        compareResults(thread.getResultsString(),
                       baselineResults.get(i));
      }
    }

    System.out.println("Done!");
  }
}
