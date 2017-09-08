package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.io.NumberRangesFileFilter;
import old.edu.stanford.nlp.io.PrintFile;
import old.edu.stanford.nlp.ling.TaggedWord;
import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.ling.Sentence;
import old.edu.stanford.nlp.maxent.CGRunner;
import old.edu.stanford.nlp.maxent.Problem;
import old.edu.stanford.nlp.trees.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Arrays;


/** Tags data and can handle either data with gold-standard tags (computing
 *  performance statistics) or unlabeled data.
 *  (The Constructor actually runs the tagger. The main entry points are the
 *  static methods at the bottom of the class.)
 *
 *  Also can train data using the saveModel method.  This class is really the entry
 *  point to all tagger operations, it seems.
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class TestClassifier {

  private String filename;
  private TestSentence ts;
  private int numRight;
  private int numWrong;
  private int unknownWords;
  private int numWrongUnknown;
  private int numCorrectSentences;
  private int numSentences;
  static boolean writeUnknDict = false;
  static boolean writeWords = false;
  static boolean writeTopWords = false;
  private Dictionary wrongWords = new Dictionary();
  // Dictionary unknownWordsDict = new Dictionary();


  TestClassifier(TaggerConfig config) throws Exception {
    /* format can be either of 1 and 2
     *  1 means the test file has the correct tags and is in format one word tag per line
     *  0 means the test file does not have the correct tags and is just tokenized.
     *  In this case the file is in the format one sentence per line (not ending in eos)
     */
    //Default format is 1
    this(config, 1);
  }

  /** format can be either of 1 or 0
   *  1 means the test file has the correct tags and is in format one word tag per line
   *  0 means the test file does not have the correct tags and is just tokenized.
   *  In this case the file is in the format one sentence per line (not ending in eos)
   *  @throws Exception
   */
  private TestClassifier(TaggerConfig config, int format) throws Exception {
    this.filename = config.getFile();
    ts = new TestSentence(GlobalHolder.getLambdaSolve());

    String dPrefix = config.getDebugPrefix();
    if (dPrefix == null || dPrefix.equals("")) {
      dPrefix = config.getFile();
    }
    if (config.getInitFromTrees()) {
      test(config, dPrefix);
    } else {
      //throw new UnsupportedOperationException();
      test(format, dPrefix, config.getTagSeparator(), config.getEncoding());
    }
  }

  /**
   * Begin tagging. The format variable (one of 0,1) determines whether data are assumed
   * to have gold standard tags (1) or to be unlabeled (0).
   * @throws IOException
   */
  private void test(int format, String saveRoot, String tagSeparator, String encoding) throws IOException {
    if ((format == 1)) {
      test(saveRoot, tagSeparator, encoding); //the data is tagged
    } else {
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
      PrintFile pf = null;
      PrintFile pf1 = null;
      if (writeWords) pf = new PrintFile(saveRoot + ".words");
      if (writeUnknDict) pf1 = new PrintFile(saveRoot + ".un.dict");

      for (String s; (s = in.readLine()) != null; ) {
        Sentence<Word> sent = Sentence.toSentence(Arrays.asList(s.split("\\s+")));
        ts.tagSentence(sent);
        if (pf != null) {
          pf.println(ts.getTaggedNice());
        }
      }

      in.close();
      if (pf != null) pf.close();
      if (pf1 != null) pf1.close();
    }
  }

  /**
   * Test on a file containing correct tags already when init'ing from trees
   * TODO: Add the ability to have a second transformer to transform output back; possibly combine this method
   * with method below
   */
  private void test(TaggerConfig config, String saveRoot) throws IOException {
    numSentences = 0;
    String eosTag = "EOS";
    String eosWord = "EOS";
    PrintFile pf = null;
    PrintFile pf1 = null;
    PrintFile pf3 = null;

    if(writeWords) pf = new PrintFile(saveRoot + ".words");
    if(writeUnknDict) pf1 = new PrintFile(saveRoot + ".un.dict");
    if(writeTopWords) pf3 = new PrintFile(saveRoot + ".words.top");
    TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();
    DiskTreebank treebank = new DiskTreebank(trf,config.getEncoding());
    TreeTransformer transformer = config.getTreeTransformer();
    TreeNormalizer normalizer = config.getTreeNormalizer();

    if (config.getTreeRange() != null) {
      treebank.loadPath(filename, new NumberRangesFileFilter(config.getTreeRange(), true));
    } else {
      treebank.loadPath(filename);
    }
    for (Tree t : treebank) {
      if (normalizer != null) {
        t = normalizer.normalizeWholeTree(t, t.treeFactory());
      }
      if (transformer != null) {
        t = t.transform(transformer);
      }

      List<String> sentence = new ArrayList<String>();
      List<String> tagsArr = new ArrayList<String>();

      for (TaggedWord cur : t.taggedYield()) {
        tagsArr.add(cur.tag());
        sentence.add(cur.word());
      }
      //the sentence is read already, add eos
      sentence.add(eosWord);
      tagsArr.add(eosTag);
      numSentences++;

      int len = sentence.size();
      String[] testSent = new String[len];
      String[] correctTags = new String[len];
      for (int i = 0; i < len; i++) {
        testSent[i] = sentence.get(i);
        correctTags[i] = tagsArr.get(i);
      }

      TestSentence testS = new TestSentence(GlobalHolder.getLambdaSolve(), testSent, correctTags, pf, wrongWords);
      if (writeUnknDict) testS.printUnknown(numSentences, pf1);
      if (writeTopWords) testS.printTop(pf3);

      numWrong = numWrong + testS.numWrong;
      numRight = numRight + testS.numRight;
      unknownWords = unknownWords + testS.numUnknown;
      numWrongUnknown = numWrongUnknown + testS.numWrongUnknown;
      if (testS.numWrong == 0) {
        numCorrectSentences++;
      }
      System.out.println("Sentence number: " + numSentences + "; length " + (len-1) + "; correct: " + testS.numRight + "; wrong: " + testS.numWrong + "; unknown wrong: " + testS.numWrongUnknown);
      System.out.println("  Total tags correct: " + numRight + "; wrong: " + numWrong + "; unknown wrong: " + numWrongUnknown);
    }

    if(pf != null) pf.close();
    if(pf1 != null) pf1.close();
    if(pf3 != null) pf3.close();
  }

  /**
   * Test on a file containing correct tags already.
   */
  private void test(String saveRoot, String tagSeparator, String encoding) throws IOException {
    numSentences = 0;
    String eosTag = "EOS";
    String eosWord = "EOS";
    PrintFile pf = null;
    PrintFile pf1 = null;
    PrintFile pf3 = null;

    BufferedReader rf = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
    if (writeWords) pf = new PrintFile(saveRoot + ".words");
    if (writeUnknDict) pf1 = new PrintFile(saveRoot + ".un.dict");
    if (writeTopWords) pf3 = new PrintFile(saveRoot + ".words.top");

    for (String s; (s = rf.readLine()) != null; ) {
      List<String> sentence = new ArrayList<String>();
      List<String> tagsArr = new ArrayList<String>();
      StringTokenizer st = new StringTokenizer(s);
      while (st.hasMoreTokens()) { // find the sentence there

        String token = st.nextToken();
        int index = token.lastIndexOf(tagSeparator);

        if (index == -1) {
          throw new RuntimeException("I was unable to find the delimiter '" + tagSeparator + "' in the token '" + token + "'. Consider using -delimiter.");
        }

        String w1 = token.substring(0, index);
        sentence.add(w1);
        String t1 = token.substring(index + 1);
        tagsArr.add(t1);
      }

      //the sentence is read already, add eos
      sentence.add(eosWord);
      tagsArr.add(eosTag);
      numSentences++;

      int len = sentence.size();
      String[] testSent = new String[len];
      String[] correctTags = new String[len];
      for (int i = 0; i < len; i++) {
        testSent[i] = sentence.get(i);
        correctTags[i] = tagsArr.get(i);
      }

      TestSentence testS = new TestSentence(GlobalHolder.getLambdaSolve(), testSent, correctTags, pf, wrongWords);
      if(writeUnknDict) testS.printUnknown(numSentences, pf1);
      if(writeTopWords) testS.printTop(pf3);

      numWrong = numWrong + testS.numWrong;
      numRight = numRight + testS.numRight;
      unknownWords = unknownWords + testS.numUnknown;
      numWrongUnknown = numWrongUnknown + testS.numWrongUnknown;
      if (testS.numWrong == 0) {
        numCorrectSentences++;
      }
      System.out.println("Sentence number: " + numSentences + "; length " + (len-1) + "; correct: " + testS.numRight + "; wrong: " + testS.numWrong + "; unknown wrong: " + testS.numWrongUnknown);
      System.out.println("  Total tags correct: " + numRight + "; wrong: " + numWrong + "; unknown wrong: " + numWrongUnknown);
    }

    rf.close();
    if (pf != null) pf.close();
    if (pf1 != null) pf1.close();
    if (pf3 != null) pf3.close();
  }

  /**
   * Warning: This method almost certainly no longer works.
   */
  /*
  @SuppressWarnings({"UnusedDeclaration"})
  private static void iterate(String filename) {
    try {
      GlobalHolder.readModelAndInit(filename);
      GlobalHolder.getLambdaSolve().improvedIterative();
      if (GlobalHolder.getLambdaSolve().checkCorrectness()) {
        System.out.println("model is correct");
      } else {
        System.out.println("model is not correct");
      }
      GlobalHolder.saveModel(filename, null);
    } catch (Exception e) {
      System.err.println("Exception while iterating.");
      e.printStackTrace();
    }
  }
  */

  /**
   * Reads in the training corpus from a filename and trains the tagger
   *
   * @param config Configuration parameters for training a model (filename, etc.
   * @throws IOException If IO problem
   */
  public static void trainAndSaveModel(TaggerConfig config) throws IOException {

    String modelName = config.getModel();
    GlobalHolder.init(config);

    // Allow clobbering.  You want it all the time when running experiments.

    TaggerExperiments samples = new TaggerExperiments(config);
    GlobalHolder.domain = samples;
    TaggerFeatures feats = samples.getTaggerFeatures();
    System.err.println("Samples from " + config.getFile());
    System.err.println("Number of features: " + feats.size());
    Problem p = new Problem(samples, feats);
    LambdaSolveTagger prob = new LambdaSolveTagger(p, 0.0001, 0.00001);
    GlobalHolder.prob = prob;

    if (config.getSearch().equals("owlqn")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveL1(config.getRegL1());
    } else if (config.getSearch().equals("cg")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveCG();
    } else if (config.getSearch().equals("qn")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveQN();
    } else {
      prob.improvedIterative(config.getIterations());
    }

    if (prob.checkCorrectness()) {
      System.out.println("Model is correct [empirical expec = model expec]");
    } else {
      System.out.println("Model is not correct");
    }
    GlobalHolder.saveModel(modelName, config);
  }


   /**
   * This saves the parameters in a file like for the Improved Iterative.
   * This calculates the model from a filename, with the specified
   * parameters for the history and saves the result back to that filename.
   *
   * Warning: This method almost certainly no longer works.
   */
//  public static void save_param(String filename, String delimiter, String encoding) throws Exception {
//    GlobalHolder.init();
//    TaggerExperiments samples = new TaggerExperiments(filename, null, delimiter, encoding);
//    GlobalHolder.domain = samples;
//    TaggerFeatures feats = TaggerExperiments.feats;
//    System.out.println("Before" + feats.size());
//    //feats.print_by_numbers();
//    Problem p = new Problem(samples, feats);
//    System.out.println(" Entering lambda solve ");
//    LambdaSolveTagger prob = new LambdaSolveTagger(p, 0.0001, 0.00001);
//    GlobalHolder.prob = prob;
//    OutDataStreamFile rf = new OutDataStreamFile(filename);
//    GlobalHolder.save_prev(null, rf);
//    Runtime rt = Runtime.getRuntime();
//    System.out.println(" before " + rt.freeMemory());
//    GlobalHolder.release_mem();
//    rt.gc();
//    System.out.println(" after " + rt.freeMemory());
//    //prob.improvedIterative();
//    //prob.save_problem(filename+".math");
//    GlobalHolder.prob = prob;
//    if (prob.checkCorrectness()) {
//      System.out.println("model is correct");
//    } else {
//      System.out.println("model is not correct");
//    }
//    GlobalHolder.save_after(rf);
//    rf.close();
//
//  }


  /**
   * Warning: This method almost certainly no longer works.
   */
//  public static void expandModel(String filename, String oldModelFile, int iters, String delimiter, String encoding) throws Exception {
//    GlobalHolder.init();
//    TaggerExperiments samples = new TaggerExperiments(filename, arg_outputs, delimiter, encoding);
//    GlobalHolder.domain = samples;
//    TaggerFeatures feats = TaggerExperiments.feats;
//    System.out.println("Before" + feats.size());
//    //feats.print_by_numbers();
//    Problem p = new Problem(samples, feats);
//    LambdaSolveTagger prob = new LambdaSolveTagger(p, 0.0001, 0.00001);
//    GlobalHolder.prob = prob;
//    OutDataStreamFile rf = new OutDataStreamFile(filename);
//    GlobalHolder.save_prev(null, rf);
//    Runtime rt = Runtime.getRuntime();
//    System.out.println(" before " + rt.freeMemory());
//    GlobalHolder.release_mem();
//    rt.gc();
//    System.out.println(" after " + rt.freeMemory());
//    prob.readOldLambdas(filename, oldModelFile);
//    GlobalHolder.getLambdaSolve().improvedIterative(iters);
//    if (prob.checkCorrectness()) {
//      System.out.println("model is correct");
//    } else {
//      System.out.println("model is not correct");
//    }
//    GlobalHolder.save_after(rf);
//    rf.close();
//  }


  void printModelAndAccuracy(TaggerConfig config) {
    System.out.println("Model " + config.getModel() + " has xSize=" + GlobalHolder.xSize + ", ySize=" + GlobalHolder.ySize + ", and numFeatures=" + GlobalHolder.prob.lambda.length + '.');
    System.out.println("Results on " + numSentences + " sentences and " + (numRight + numWrong) + " words, of which " + unknownWords + " were unknown.");
    System.out.printf("Total sentences right: %d (%f%%); wrong: %d (%f%%).\n", numCorrectSentences, numCorrectSentences * 100.0 / numSentences, numSentences - numCorrectSentences, (numSentences - numCorrectSentences) * 100.0 / (numSentences));
    System.out.printf("Total tags right: %d (%f%%); wrong: %d (%f%%).\n", numRight, numRight * 100.0 / (numRight + numWrong), numWrong, numWrong * 100.0 / (numRight + numWrong));
    if (unknownWords > 0) { System.out.printf("Unknown words right: %d (%f%%); wrong: %d (%f%%).\n", (unknownWords - numWrongUnknown), 100.0 - (numWrongUnknown * 100.0 / unknownWords), numWrongUnknown, numWrongUnknown * 100.0 / unknownWords); }
  }


  int getNumWords() {
    return numRight + numWrong;
  }

  static void setDebug(boolean status) {
    writeUnknDict = status;
    writeWords = status;
    writeTopWords = status;
  }


}
