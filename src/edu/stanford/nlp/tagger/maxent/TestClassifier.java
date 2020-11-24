package edu.stanford.nlp.tagger.maxent; 

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.util.ConfusionMatrix;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;

/** Tags data and can handle either data with gold-standard tags (computing
 *  performance statistics) or unlabeled data.
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
// TODO: can we break this class up in some way?  Perhaps we can
// spread some functionality into TestSentence and some into MaxentTagger
// TODO: at the very least, it doesn't seem to make sense to make it
// an object with state, rather than just some static methods
public class TestClassifier  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TestClassifier.class);

  private final TaggedFileRecord fileRecord;
  private int numRight;
  private int numWrong;
  private int unknownWords;
  private int numWrongUnknown;
  private int numCorrectSentences;
  private int numSentences;

  private ConfusionMatrix<String> confusionMatrix;

  // TODO: only one boolean here instead of 4? They all use the same debug status.
  private boolean writeUnknDict;
  private boolean writeWords;
  private boolean writeTopWords;
  private boolean writeConfusionMatrix;

  private MaxentTagger maxentTagger;
  TaggerConfig config;
  private String saveRoot;

  public TestClassifier(MaxentTagger maxentTagger) throws IOException {
    this(maxentTagger, maxentTagger.config.getFile());
  }

  public TestClassifier(MaxentTagger maxentTagger, String testFile) throws IOException {
    this.maxentTagger = maxentTagger;
    this.config = maxentTagger.config;
    setDebug(config.getDebug());

    fileRecord = TaggedFileRecord.createRecord(config, testFile);

    saveRoot = config.getDebugPrefix();
    if (saveRoot == null || saveRoot.isEmpty()) {
      saveRoot = fileRecord.filename();
    }

    test();

    if (writeConfusionMatrix) {
      PrintFile pf = new PrintFile(saveRoot + ".confusion");
      pf.print(confusionMatrix);
      pf.close();
    }
  }

  private void processResults(TestSentence testS,
                              PrintFile unknDictFile,
                              PrintFile topWordsFile, boolean verboseResults) {
    numSentences++;

    testS.writeTagsAndErrors(testS.finalTags, unknDictFile, verboseResults);
    if (writeUnknDict) testS.printUnknown(numSentences, unknDictFile);
    if (writeTopWords) testS.printTop(topWordsFile);

    testS.updateConfusionMatrix(testS.finalTags, confusionMatrix);

    numWrong = numWrong + testS.numWrong;
    numRight = numRight + testS.numRight;
    unknownWords = unknownWords + testS.numUnknown;
    numWrongUnknown = numWrongUnknown + testS.numWrongUnknown;
    if (testS.numWrong == 0) {
      numCorrectSentences++;
    }
    if (verboseResults) {
      log.info("Sentence number: " + numSentences + "; length " + (testS.size-1) +
                         "; correct: " + testS.numRight + "; wrong: " + testS.numWrong +
                         "; unknown wrong: " + testS.numWrongUnknown);
      // log.info("  Total tags correct: " + numRight + "; wrong: " + numWrong +
      //                    "; unknown wrong: " + numWrongUnknown);
    }
  }

  /**
   * Test on a file containing correct tags already. when init'ing from trees
   * TODO: Add the ability to have a second transformer to transform output back; possibly combine this method
   * with method below
   */
  private void test() throws IOException {
    numSentences = 0;
    confusionMatrix = new ConfusionMatrix<>();

    PrintFile pf = null;
    PrintFile pf1 = null;
    PrintFile pf3 = null;

    if (writeWords) pf = new PrintFile(saveRoot + ".words");
    if (writeUnknDict) pf1 = new PrintFile(saveRoot + ".un.dict");
    if (writeTopWords) pf3 = new PrintFile(saveRoot + ".words.top");

    boolean verboseResults = config.getVerboseResults();

    if (config.getNThreads() != 1) {
      MulticoreWrapper<List<TaggedWord>, TestSentence> wrapper = new MulticoreWrapper<>(config.getNThreads(), new TestSentenceProcessor(maxentTagger));
      for (List<TaggedWord> taggedSentence : fileRecord.reader()) {
        wrapper.put(taggedSentence);
        while (wrapper.peek()) {
          processResults(wrapper.poll(), pf1, pf3, verboseResults);
        }
      }
      wrapper.join();
      while (wrapper.peek()) {
        processResults(wrapper.poll(), pf1, pf3, verboseResults);
      }
    } else{
      for (List<TaggedWord> taggedSentence : fileRecord.reader()) {
        TestSentence testS = new TestSentence(maxentTagger);
        testS.setCorrectTags(taggedSentence);
        testS.tagSentence(taggedSentence, false);
        processResults(testS, pf1, pf3, verboseResults);
      }
    }

    if (pf != null) pf.close();
    if (pf1 != null) pf1.close();
    if (pf3 != null) pf3.close();
  }


  public String resultsString(MaxentTagger maxentTagger) {
    StringBuilder output = new StringBuilder();
    output.append(String.format("Model %s has xSize=%d, ySize=%d, and numFeatures=%d.%n",
            maxentTagger.config.getModel(),
            maxentTagger.xSize,
            maxentTagger.ySize,
            maxentTagger.getLambdaSolve().lambda.length));
    output.append(String.format("Results on %d sentences and %d words, of which %d were unknown.%n",
            numSentences, numRight + numWrong, unknownWords));
    output.append(String.format("Total sentences right: %d (%f%%); wrong: %d (%f%%).%n",
                                numCorrectSentences, numCorrectSentences * 100.0 / numSentences,
                                numSentences - numCorrectSentences,
                                (numSentences - numCorrectSentences) * 100.0 / (numSentences)));
    output.append(String.format("Total tags right: %d (%f%%); wrong: %d (%f%%).%n",
                                numRight, numRight * 100.0 / (numRight + numWrong), numWrong,
                                numWrong * 100.0 / (numRight + numWrong)));

    if (unknownWords > 0) {
      output.append(String.format("Unknown words right: %d (%f%%); wrong: %d (%f%%).%n",
                                  (unknownWords - numWrongUnknown),
                                  100.0 - (numWrongUnknown * 100.0 / unknownWords),
                                  numWrongUnknown, numWrongUnknown * 100.0 / unknownWords));
    }

    return output.toString();
  }

  public double tagAccuracy() {
    return (numRight * 100.0) / (numRight + numWrong);
  }

  void printModelAndAccuracy(MaxentTagger maxentTagger) {
    // print the output all at once so that multiple threads don't clobber each other's output
    log.info(resultsString(maxentTagger));
  }


  int getNumWords() {
    return numRight + numWrong;
  }

  private void setDebug(boolean status) {
    writeUnknDict = status;
    writeWords = status;
    writeTopWords = status;
    writeConfusionMatrix = status;
  }

  static class TestSentenceProcessor implements ThreadsafeProcessor<List<TaggedWord>, TestSentence> {
    MaxentTagger maxentTagger;

    public TestSentenceProcessor(MaxentTagger maxentTagger) {
      this.maxentTagger = maxentTagger;
    }

    @Override
    public TestSentence process(List<TaggedWord> taggedSentence) {
      TestSentence testS = new TestSentence(maxentTagger);
      testS.setCorrectTags(taggedSentence);
      testS.tagSentence(taggedSentence, false);
      return testS;
    }

    @Override
    public ThreadsafeProcessor<List<TaggedWord>, TestSentence> newInstance() {
      // MaxentTagger is threadsafe
      return this;
    }
  }

}
