package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import static java.util.stream.Collectors.toList;

/**
 * This class defines a transition-based dependency parser which makes
 * use of a classifier powered by a neural network. The neural network
 * accepts distributed representation inputs: dense, continuous
 * representations of words, their part of speech tags, and the labels
 * which connect words in a partial dependency parse.
 *
 * This is an implementation of the method described in
 *
 *   Danqi Chen and Christopher Manning. A Fast and Accurate Dependency
 *   Parser Using Neural Networks. In EMNLP 2014.
 *
 * New models can be trained from the command line; see {@link #main}
 * for details on training options. This parser will also output
 * CoNLL-X format predictions; again see {@link #main} for available
 * options.
 *
 * This parser can also be used programmatically. The easiest way to
 * prepare the parser with a pre-trained model is to call
 * {@link #loadFromModelFile(String)}. Then call
 * {@link #predict(edu.stanford.nlp.util.CoreMap)} on the returned
 * parser instance in order to get new parses.
 *
 * @author Danqi Chen (danqi@cs.stanford.edu)
 * @author Jon Gauthier
 */
public class DependencyParser {
  public static final String DEFAULT_MODEL = "edu/stanford/nlp/models/parser/nndep/PTB_Stanford_params.txt.gz";

  /**
   * Words, parts of speech, and dependency relation labels which were
   * observed in our corpus / stored in the model
   *
   * @see #genDictionaries(java.util.List, java.util.List)
   */
  private List<String> knownWords, knownPos, knownLabels;

  /**
   * Mapping from word / POS / dependency relation label to integer ID
   */
  private Map<String, Integer> wordIDs, posIDs, labelIDs;

  List<Integer> preComputed;

  /**
   * Given a particular parser configuration, this classifier will
   * predict the best transition to make next.
   *
   * The {@link edu.stanford.nlp.parser.nndep.Classifier} class
   * handles both training and inference.
   */
  private Classifier classifier;

  private ParsingSystem system;

  private Map<String, Integer> embedID;
  private double[][] embeddings;

  private final Config config;

  DependencyParser() {
    this(new Properties());
  }

  public DependencyParser(Properties properties) {
    config = new Config(properties);
  }

  /**
   * Get an integer ID for the given word. This ID can be used to index
   * into the embeddings {@link #embeddings}.
   *
   * @return An ID for the given word, or an ID referring to a generic
   *         "unknown" word if the word is unknown
   */
  public int getWordID(String s) {
      return wordIDs.containsKey(s) ? wordIDs.get(s) : wordIDs.get(Config.UNKNOWN);
  }

  public int getPosID(String s) {
      return posIDs.containsKey(s) ? posIDs.get(s) : posIDs.get(Config.UNKNOWN);
  }

  public int getLabelID(String s) {
    return labelIDs.get(s);
  }

  public List<Integer> getFeatures(Configuration c) {
    // Presize the arrays for very slight speed gain. Hardcoded, but so is the current feature list.
    List<Integer> fWord = new ArrayList<Integer>(18);
    List<Integer> fPos = new ArrayList<Integer>(18);
    List<Integer> fLabel = new ArrayList<Integer>(12);
    for (int j = 2; j >= 0; --j) {
      int index = c.getStack(j);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
    }
    for (int j = 0; j <= 2; ++j) {
      int index = c.getBuffer(j);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
    }
    for (int j = 0; j <= 1; ++j) {
      int k = c.getStack(j);
      int index = c.getLeftChild(k);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));

      index = c.getRightChild(k);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));

      index = c.getLeftChild(k, 2);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));

      index = c.getRightChild(k, 2);
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));

      index = c.getLeftChild(c.getLeftChild(k));
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));

      index = c.getRightChild(c.getRightChild(k));
      fWord.add(getWordID(c.getWord(index)));
      fPos.add(getPosID(c.getPOS(index)));
      fLabel.add(getLabelID(c.getLabel(index)));
    }

    List<Integer> feature = new ArrayList<>(48);
    feature.addAll(fWord);
    feature.addAll(fPos);
    feature.addAll(fLabel);
    return feature;
  }

  private static final int POS_OFFSET = 18;
  private static final int DEP_OFFSET = 36;
  private static final int STACK_OFFSET = 6;
  private static final int STACK_NUMBER = 6;

  private int[] getFeatureArray(Configuration c) {
    int[] feature = new int[config.numTokens];  // positions 0-17 hold fWord, 18-35 hold fPos, 36-47 hold fLabel

    for (int j = 2; j >= 0; --j) {
      int index = c.getStack(j);
      feature[2-j] = getWordID(c.getWord(index));
      feature[POS_OFFSET + (2-j)] = getPosID(c.getPOS(index));
    }

    for (int j = 0; j <= 2; ++j) {
      int index = c.getBuffer(j);
      feature[3 + j] = getWordID(c.getWord(index));
      feature[POS_OFFSET + 3 + j] = getPosID(c.getPOS(index));
    }

    for (int j = 0; j <= 1; ++j) {
      int k = c.getStack(j);

      int index = c.getLeftChild(k);
      feature[STACK_OFFSET + j * STACK_NUMBER] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER] = getLabelID(c.getLabel(index));

      index = c.getRightChild(k);
      feature[STACK_OFFSET + j * STACK_NUMBER + 1] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER + 1] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER + 1] = getLabelID(c.getLabel(index));

      index = c.getLeftChild(k, 2);
      feature[STACK_OFFSET + j * STACK_NUMBER + 2] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER + 2] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER + 2] = getLabelID(c.getLabel(index));

      index = c.getRightChild(k, 2);
      feature[STACK_OFFSET + j * STACK_NUMBER + 3] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER + 3] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER + 3] = getLabelID(c.getLabel(index));

      index = c.getLeftChild(c.getLeftChild(k));
      feature[STACK_OFFSET + j * STACK_NUMBER + 4] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER + 4] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER + 4] = getLabelID(c.getLabel(index));

      index = c.getRightChild(c.getRightChild(k));
      feature[STACK_OFFSET + j * STACK_NUMBER + 5] = getWordID(c.getWord(index));
      feature[POS_OFFSET + STACK_OFFSET + j * STACK_NUMBER + 5] = getPosID(c.getPOS(index));
      feature[DEP_OFFSET + j * STACK_NUMBER + 5] = getLabelID(c.getLabel(index));
    }

    return feature;
  }

  public Dataset genTrainExamples(List<CoreMap> sents, List<DependencyTree> trees) {
    Dataset ret = new Dataset(config.numTokens, system.transitions.size());

    Counter<Integer> tokPosCount = new IntCounter<>();
    System.err.println(Config.SEPARATOR);
    System.err.println("Generate training examples...");

    for (int i = 0; i < sents.size(); ++i) {

      if (i > 0) {
        if (i % 1000 == 0)
          System.err.print(i + " ");
        if (i % 10000 == 0 || i == sents.size() - 1)
          System.err.println();
      }

      if (trees.get(i).isProjective()) {
        Configuration c = system.initialConfiguration(sents.get(i));

        while (!system.isTerminal(c)) {
          String oracle = system.getOracle(c, trees.get(i));
          List<Integer> feature = getFeatures(c);
          List<Integer> label = new ArrayList<>();
          for (int j = 0; j < system.transitions.size(); ++j) {
            String str = system.transitions.get(j);
            if (str.equals(oracle)) label.add(1);
            else if (system.canApply(c, str)) label.add(0);
            else label.add(-1);
          }

          ret.addExample(feature, label);
          for (int j = 0; j < feature.size(); ++j)
            tokPosCount.incrementCount(feature.get(j) * feature.size() + j);
          system.apply(c, oracle);
        }
      }
    }
    System.err.println("#Train Examples: " + ret.n);

    Counters.retainTop(tokPosCount, config.numPreComputed);
    preComputed = new ArrayList<>(tokPosCount.keySet());

    return ret;
  }

  /**
   * Generate unique integer IDs for all known words / part-of-speech
   * tags / dependency relation labels.
   *
   * All three of the aforementioned types are assigned IDs from a
   * continuous range of integers; all IDs 0 <= ID < n_w are word IDs,
   * all IDs n_w <= ID < n_w + n_pos are POS tag IDs, and so on.
   */
  private void generateIDs() {
    wordIDs = new HashMap<>();
    posIDs = new HashMap<>();
    labelIDs = new HashMap<>();

    int index = 0;
    for (String word : knownWords)
      wordIDs.put(word, (index++));
    for (String pos : knownPos)
      posIDs.put(pos, (index++));
    for (String label : knownLabels)
      labelIDs.put(label, (index++));
  }

  /**
   * Scan a corpus and store all words, part-of-speech tags, and
   * dependency relation labels observed. Prepare other structures
   * which support word / POS / label lookup at train- / run-time.
   */
  private void genDictionaries(List<CoreMap> sents, List<DependencyTree> trees) {
    // Collect all words (!), etc. in lists, tacking on one sentence
    // after the other
    List<String> word = new ArrayList<>();
    List<String> pos = new ArrayList<>();
    List<String> label = new ArrayList<>();

    for (CoreMap sentence : sents) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

      for (CoreLabel token : tokens) {
        word.add(token.word());
        pos.add(token.tag());
      }
    }

    String rootLabel = null;
    for (DependencyTree tree : trees)
      for (int k = 1; k <= tree.n; ++k)
        if (tree.getHead(k) == 0)
          rootLabel = tree.getLabel(k);
        else
          label.add(tree.getLabel(k));

    // Generate "dictionaries," possibly with frequency cutoff
    knownWords = Util.generateDict(word, config.wordCutOff);
    knownPos = Util.generateDict(pos);
    knownLabels = Util.generateDict(label);
    knownLabels.add(0, rootLabel);

    knownWords.add(0, Config.UNKNOWN);
    knownWords.add(1, Config.NULL);
    knownWords.add(2, Config.ROOT);

    knownPos.add(0, Config.UNKNOWN);
    knownPos.add(1, Config.NULL);
    knownPos.add(2, Config.ROOT);

    knownLabels.add(0, Config.NULL);
    generateIDs();

    System.out.println(Config.SEPARATOR);
    System.out.println("#Word: " + knownWords.size());
    System.out.println("#POS:" + knownPos.size());
    System.out.println("#Label: " + knownLabels.size());
  }

  public void writeModelFile(String modelFile) {
    try {
      double[][] W1 = classifier.getW1();
      double[] b1 = classifier.getb1();
      double[][] W2 = classifier.getW2();
      double[][] E = classifier.getE();

      Writer output = IOUtils.getPrintWriter(modelFile);

      output.write("dict=" + knownWords.size() + "\n");
      output.write("pos=" + knownPos.size() + "\n");
      output.write("label=" + knownLabels.size() + "\n");
      output.write("embeddingSize=" + E[0].length + "\n");
      output.write("hiddenSize=" + b1.length + "\n");
      output.write("numTokens=" + (W1[0].length / E[0].length) + "\n");
      output.write("preComputed=" + preComputed.size() + "\n");

      int index = 0;

      // First write word / POS / label embeddings
      for (String word : knownWords) {
        output.write(word);
        for (int k = 0; k < E[index].length; ++k)
          output.write(" " + E[index][k]);
        output.write("\n");
        index = index + 1;
      }
      for (String pos : knownPos) {
        output.write(pos);
        for (int k = 0; k < E[index].length; ++k)
          output.write(" " + E[index][k]);
        output.write("\n");
        index = index + 1;
      }
      for (String label : knownLabels) {
        output.write(label);
        for (int k = 0; k < E[index].length; ++k)
          output.write(" " + E[index][k]);
        output.write("\n");
        index = index + 1;
      }

      // Now write classifier weights
      for (int j = 0; j < W1[0].length; ++j)
        for (int i = 0; i < W1.length; ++i) {
          output.write("" + W1[i][j]);
          if (i == W1.length - 1)
            output.write("\n");
          else
            output.write(" ");
        }
      for (int i = 0; i < b1.length; ++i) {
        output.write("" + b1[i]);
        if (i == b1.length - 1)
          output.write("\n");
        else
          output.write(" ");
      }
      for (int j = 0; j < W2[0].length; ++j)
        for (int i = 0; i < W2.length; ++i) {
          output.write("" + W2[i][j]);
          if (i == W2.length - 1)
            output.write("\n");
          else
            output.write(" ");
        }

      // Finish with pre-computation info
      for (int i = 0; i < preComputed.size(); ++i) {
        output.write("" + preComputed.get(i));
        if ((i + 1) % 100 == 0 || i == preComputed.size() - 1)
          output.write("\n");
        else
          output.write(" ");
      }

      output.close();
    } catch (IOException e) {
      System.out.println(e);
    }
  }

  /**
   * Convenience method; see {@link #loadFromModelFile(String, java.util.Properties)}.
   *
   * @see #loadFromModelFile(String, java.util.Properties)
   */
  public static DependencyParser loadFromModelFile(String modelFile) {
    return loadFromModelFile(modelFile, null);
  }

  /**
   * Load a saved parser model.
   *
   * @param modelFile       Path to serialized model (may be GZipped)
   * @param extraProperties Extra test-time properties not already associated with model (may be null)
   *
   * @return Loaded and initialized (see {@link #initialize()} model
   */
  public static DependencyParser loadFromModelFile(String modelFile, Properties extraProperties) {
    DependencyParser parser = extraProperties == null ? new DependencyParser() : new DependencyParser(extraProperties);
    parser.loadModelFile(modelFile);
    parser.initialize();
    return parser;
  }

  private void loadModelFile(String modelFile) {
    try {
      System.err.println(Config.SEPARATOR);
      System.err.println("Loading Model File: " + modelFile);
      String s;
      BufferedReader input = IOUtils.readerFromString(modelFile);

      int nDict, nPOS, nLabel;
      int eSize, hSize, nTokens, nPreComputed;
      nDict = nPOS = nLabel = eSize = hSize = nTokens = nPreComputed = 0;

      for (int k = 0; k < 7; ++k) {
        s = input.readLine();
        System.err.println(s);
        int number = Integer.parseInt(s.substring(s.indexOf('=') + 1));
        switch (k) {
          case 0:
            nDict = number;
            break;
          case 1:
            nPOS = number;
            break;
          case 2:
            nLabel = number;
            break;
          case 3:
            eSize = number;
            break;
          case 4:
            hSize = number;
            break;
          case 5:
            nTokens = number;
            break;
          case 6:
            nPreComputed = number;
            break;
          default:
            break;
        }
      }

      knownWords = new ArrayList<String>();
      knownPos = new ArrayList<String>();
      knownLabels = new ArrayList<String>();
      double[][] E = new double[nDict + nPOS + nLabel][eSize];
      String[] splits;
      int index = 0;

      for (int k = 0; k < nDict; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        knownWords.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      for (int k = 0; k < nPOS; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        knownPos.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      for (int k = 0; k < nLabel; ++k) {
        s = input.readLine();
        splits = s.split(" ");
        knownLabels.add(splits[0]);
        for (int i = 0; i < eSize; ++i)
          E[index][i] = Double.parseDouble(splits[i + 1]);
        index = index + 1;
      }
      generateIDs();

      double[][] W1 = new double[hSize][eSize * nTokens];
      for (int j = 0; j < W1[0].length; ++j) {
        s = input.readLine();
        splits = s.split(" ");
        for (int i = 0; i < W1.length; ++i)
          W1[i][j] = Double.parseDouble(splits[i]);
      }

      double[] b1 = new double[hSize];
      s = input.readLine();
      splits = s.split(" ");
      for (int i = 0; i < b1.length; ++i)
        b1[i] = Double.parseDouble(splits[i]);

      double[][] W2 = new double[nLabel * 2 - 1][hSize];
      for (int j = 0; j < W2[0].length; ++j) {
        s = input.readLine();
        splits = s.split(" ");
        for (int i = 0; i < W2.length; ++i)
          W2[i][j] = Double.parseDouble(splits[i]);
      }

      preComputed = new ArrayList<Integer>();
      while (preComputed.size() < nPreComputed) {
        s = input.readLine();
        splits = s.split(" ");
        for (String split : splits) {
          preComputed.add(Integer.parseInt(split));
        }
      }
      input.close();
      classifier = new Classifier(config, E, W1, b1, W2, preComputed);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private void readEmbedFile(String embedFile) {
    embedID = new HashMap<String, Integer>();
    if (embedFile == null)
      return;
    BufferedReader input = null;
    try {
      input = IOUtils.readerFromString(embedFile);
      List<String> lines = new ArrayList<String>();
      for (String s; (s = input.readLine()) != null; ) {
        lines.add(s);
      }

      int nWords = lines.size();
      String[] splits = lines.get(0).split("\\s+");

      int dim = splits.length - 1;
      embeddings = new double[nWords][dim];
      System.err.println("Embedding File " + embedFile + ": #Words = " + nWords + ", dim = " + dim);

      //TODO: how if the embedding dim. does not match..?
      if (dim != config.embeddingSize)
        System.err.println("ERROR: embedding dimension mismatch");

      for (int i = 0; i < lines.size(); ++i) {
        splits = lines.get(i).split("\\s+");
        embedID.put(splits[0], i);
        for (int j = 0; j < dim; ++j)
          embeddings[i][j] = Double.parseDouble(splits[j + 1]);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(input);
    }
  }

  /**
   * Train a new dependency parser model.
   *
   * @param trainFile Training data
   * @param devFile Development data (used for regular UAS evaluation
   *                of model)
   * @param modelFile String to which model should be saved
   * @param embedFile File containing word embeddings for words used in
   *                  training corpus
   */
  public void train(String trainFile, String devFile, String modelFile, String embedFile) {
    System.err.println("Train File: " + trainFile);
    System.err.println("Dev File: " + devFile);
    System.err.println("Model File: " + modelFile);
    System.err.println("Embedding File: " + embedFile);

    List<CoreMap> trainSents = new ArrayList<>();
    List<DependencyTree> trainTrees = new ArrayList<DependencyTree>();
    Util.loadConllFile(trainFile, trainSents, trainTrees);
    Util.printTreeStats("Train", trainTrees);

    List<CoreMap> devSents = new ArrayList<CoreMap>();
    List<DependencyTree> devTrees = new ArrayList<DependencyTree>();
    if (devFile != null) {
      Util.loadConllFile(devFile, devSents, devTrees);
      Util.printTreeStats("Dev", devTrees);
    }
    genDictionaries(trainSents, trainTrees);

    //NOTE: remove -NULL-, and the pass it to ParsingSystem
    List<String> lDict = new ArrayList<String>(knownLabels);
    lDict.remove(0);
    system = new ArcStandard(config.tlp, lDict);

    double[][] E = new double[knownWords.size() + knownPos.size() + knownLabels.size()][config.embeddingSize];
    double[][] W1 = new double[config.hiddenSize][config.embeddingSize * config.numTokens];
    double[] b1 = new double[config.hiddenSize];
    double[][] W2 = new double[knownLabels.size() * 2 - 1][config.hiddenSize];

    Random random = Util.getRandom();
    for (int i = 0; i < W1.length; ++i)
      for (int j = 0; j < W1[i].length; ++j)
        W1[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;

    for (int i = 0; i < b1.length; ++i)
      b1[i] = random.nextDouble() * 2 * config.initRange - config.initRange;

    for (int i = 0; i < W2.length; ++i)
      for (int j = 0; j < W2[i].length; ++j)
        W2[i][j] = random.nextDouble() * 2 * config.initRange - config.initRange;

    readEmbedFile(embedFile);
    int foundEmbed = 0;
    for (int i = 0; i < E.length; ++i) {
      int index = -1;
      if (i < knownWords.size()) {
        String str = knownWords.get(i);
        //NOTE: exact match first, and then try lower case..
        if (embedID.containsKey(str)) index = embedID.get(str);
        else if (embedID.containsKey(str.toLowerCase())) index = embedID.get(str.toLowerCase());
      }

      if (index >= 0) {
        ++foundEmbed;
        for (int j = 0; j < E[i].length; ++j)
          E[i][j] = embeddings[index][j];
      } else {
        for (int j = 0; j < E[i].length; ++j)
          E[i][j] = random.nextDouble() * config.initRange * 2 - config.initRange;
      }
    }
    System.err.println("Found embeddings: " + foundEmbed + " / " + knownWords.size());

    Dataset trainSet = genTrainExamples(trainSents, trainTrees);
    classifier = new Classifier(config, trainSet, E, W1, b1, W2, preComputed);

    System.err.println(Config.SEPARATOR);
    config.printParameters();

    long startTime = System.currentTimeMillis();
    /**
     * Track the best UAS performance we've seen.
     */
    double bestUAS = 0;

    for (int iter = 0; iter < config.maxIter; ++iter) {
      System.err.println("##### Iteration " + iter);

      Classifier.Cost cost = classifier.computeCostFunction(config.batchSize, config.regParameter, config.dropProb);
      System.err.println("Cost = " + cost.getCost() + ", Correct(%) = " + cost.getPercentCorrect());
      classifier.takeAdaGradientStep(cost, config.adaAlpha, config.adaEps);

      System.err.println("Elapsed Time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " (s)");

      // UAS evaluation
      if (devFile != null && iter % config.evalPerIter == 0) {
        // Redo precomputation with updated weights. This is only
        // necessary because we're updating weights -- for normal
        // prediction, we just do this once in #initialize
        classifier.preCompute();

        List<DependencyTree> predicted = devSents.stream().map(this::predictInner).collect(toList());

        double uas = system.getUASScore(devSents, predicted, devTrees);
        System.err.println("UAS: " + uas);

        if (config.saveIntermediate && uas > bestUAS) {
          System.err.printf("Exceeds best previous UAS of %f. Saving model file..%n", bestUAS);

          bestUAS = uas;
          writeModelFile(modelFile);
        }
      }

      // Clear gradients
      if (config.clearGradientsPerIter > 0 && iter % config.clearGradientsPerIter == 0) {
        System.err.println("Clearing gradient histories..");
        classifier.clearGradientHistories();
      }
    }

    classifier.finalizeTraining();

    if (devFile != null) {
      // Do final UAS evaluation and save if final model beats the
      // best intermediate one
      List<DependencyTree> predicted = devSents.stream().map(this::predictInner).collect(toList());
      double uas = system.getUASScore(devSents, predicted, devTrees);

      if (uas > bestUAS) {
        System.err.printf("Final model UAS: %f%n", uas);
        System.err.printf("Exceeds best previous UAS of %f. Saving model file..%n", bestUAS);

        writeModelFile(modelFile);
      }
    } else {
      writeModelFile(modelFile);
    }
  }

  public void train(String trainFile, String devFile, String modelFile) {
    train(trainFile, devFile, modelFile, null);
  }

  public void train(String trainFile, String modelFile) {
    train(trainFile, null, modelFile);
  }

  /**
   * Determine the dependency parse of the given sentence.
   * <p>
   * This "inner" method returns a structure unique to this package; use {@link #predict(edu.stanford.nlp.util.CoreMap)}
   * for general parsing purposes.
   */
  private DependencyTree predictInner(CoreMap sentence) {
    int numTrans = system.transitions.size();

    Configuration c = system.initialConfiguration(sentence);
    while (!system.isTerminal(c)) {
      double[] scores = classifier.computeScores(getFeatureArray(c));

      double optScore = Double.NEGATIVE_INFINITY;
      String optTrans = null;

      for (int j = 0; j < numTrans; ++j) {
        if (scores[j] > optScore && system.canApply(c, system.transitions.get(j))) {
          optScore = scores[j];
          optTrans = system.transitions.get(j);
        }
      }
      system.apply(c, optTrans);
    }
    return c.tree;
  }

  /**
   * Determine the dependency parse of the given sentence using the loaded model. You must first initialize the parser
   * after loading or training a model using {@link #initialize()}.
   *
   * @throws java.lang.IllegalStateException If parser has not yet been properly initialized (see {@link #initialize()}
   */
  public GrammaticalStructure predict(CoreMap sentence) {
    if (system == null)
      throw new IllegalStateException("Parser has not been properly " +
          "initialized; first load a model and call .initialize()");

    DependencyTree result = predictInner(sentence);

    // The rest of this method is just busy-work to convert the
    // package-local representation into a CoreNLP-standard
    // GrammaticalStructure.

    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<TypedDependency> dependencies = new ArrayList<>();

    IndexedWord root = new IndexedWord(new Word("ROOT-" + (tokens.size() + 1)));
    root.set(CoreAnnotations.IndexAnnotation.class, -1);

    for (int i = 1; i < result.n; i++) {
      int head = result.getHead(i);
      String label = result.getLabel(i);

      IndexedWord thisWord = new IndexedWord(tokens.get(i - 1));
      IndexedWord headWord = head == 0 ? root
                                       : new IndexedWord(tokens.get(head - 1));

      GrammaticalRelation relation = head == 0
                                     ? GrammaticalRelation.ROOT
                                     : new GrammaticalRelation(GrammaticalRelation.Language.Any, label, null,
                                         GrammaticalRelation.DEPENDENT);

      dependencies.add(new TypedDependency(relation, headWord, thisWord));
    }

    // Build GrammaticalStructure
    // TODO ideally submodule should just return GrammaticalStructure
    TreeGraphNode rootNode = new TreeGraphNode(root);
    return new EnglishGrammaticalStructure(dependencies, rootNode);
  }

  /**
   * Convenience method for {@link #predict(edu.stanford.nlp.util.CoreMap)}. The tokens of the provided sentence must
   * also have tag annotations (the parser requires part-of-speech tags).
   *
   * @see #predict(edu.stanford.nlp.util.CoreMap)
   */
  public GrammaticalStructure predict(List<? extends HasWord> sentence) {
    CoreLabel sentenceLabel = new CoreLabel();
    List<CoreLabel> tokens = new ArrayList<>();

    for (HasWord wd : sentence) {
      CoreLabel label;
      if (wd instanceof CoreLabel) {
        label = (CoreLabel) wd;
        if (label.tag() == null)
          throw new IllegalArgumentException("Parser requires words " +
              "with part-of-speech tag annotations");
      } else {
        label = new CoreLabel();
        label.setValue(wd.word());
        label.setWord(wd.word());

        if (!(wd instanceof HasTag))
          throw new IllegalArgumentException("Parser requires words " +
              "with part-of-speech tag annotations");

        label.setTag(((HasTag) wd).tag());
      }

      tokens.add(label);
    }

    sentenceLabel.set(CoreAnnotations.TokensAnnotation.class, tokens);

    return predict(sentenceLabel);
  }

  //TODO: support sentence-only files as input

  /** Loads a parser
   *
   *  @param modelFile The file (classpath resource, etc.) to load the model from.
   */
  public void load(String modelFile) {
    Timing t = new Timing();
    System.err.println("Model File: " + modelFile);

    loadModelFile(modelFile);
    initialize();
    t.done("Initializing dependency parser");
  }

  /** Run the parser in the modelFile on a testFile and perhaps save output.
   *
   *  @param testFile File to parse. In CoNLL-X format. Assumed to have gold answers included.
   *  @param outFile File to write results to in CoNLL-X format.  If null, no output is written
   *  @return The LAS score on the dataset
   */
  public double testCoNLL(String testFile, String outFile) {
    System.err.println("Test File: " + testFile);
    Timing timer = new Timing();
    List<CoreMap> testSents = new ArrayList<>();
    List<DependencyTree> testTrees = new ArrayList<DependencyTree>();
    Util.loadConllFile(testFile, testSents, testTrees);
    // count how much to parse
    int numWords = 0;
    int numSentences = 0;
    for (CoreMap testSent : testSents) {
      numSentences += 1;
      numWords += testSent.get(CoreAnnotations.TokensAnnotation.class).size();
    }

    List<DependencyTree> predicted = testSents.stream().map(this::predictInner).collect(toList());
    Map<String, Double> result = system.evaluate(testSents, predicted, testTrees);
    double lasNoPunc = result.get("LASwoPunc");
    System.err.printf("UAS = %.4f%n", result.get("UASwoPunc"));
    System.err.printf("LAS = %.4f%n", lasNoPunc);
    long millis = timer.stop();
    double wordspersec = numWords / (((double) millis) / 1000);
    double sentspersec = numSentences / (((double) millis) / 1000);
    System.err.printf("%s tagged %d words in %d sentences in %.1fs at %.1f w/s, %.1f sent/s.%n",
            StringUtils.getShortClassName(this), numWords, numSentences, millis / 1000.0, wordspersec, sentspersec);

    if (outFile != null) {
        Util.writeConllFile(outFile, testSents, predicted);
    }
    return lasNoPunc;
  }

  private void parseTextFile(BufferedReader input, PrintWriter output) {
    DocumentPreprocessor preprocessor = new DocumentPreprocessor(input);
    preprocessor.setSentenceFinalPuncWords(config.tlp.sentenceFinalPunctuationWords());
    preprocessor.setEscaper(config.escaper);
    preprocessor.setSentenceDelimiter(config.sentenceDelimiter);
    preprocessor.setTokenizerFactory(config.tlp.getTokenizerFactory());

    Timing timer = new Timing();

    MaxentTagger tagger = new MaxentTagger(config.tagger);
    List<List<TaggedWord>> tagged = new ArrayList<>();
    for (List<HasWord> sentence : preprocessor) {
      tagged.add(tagger.tagSentence(sentence));
    }

    System.err.printf("Tagging completed in %.2f sec.%n",
        timer.stop() / 1000.0);

    timer.start();

    int numSentences = 0;
    for (List<TaggedWord> taggedSentence : tagged) {
      GrammaticalStructure parse = predict(taggedSentence);
      output.println(parse);
      numSentences++;
    }

    long millis = timer.stop();
    double seconds = millis / 1000.0;
    System.err.printf("Parsed %d sentences in %.2f seconds (%.2f sents/sec).%n",
        numSentences, seconds, numSentences / seconds);
  }

  /**
   * Prepare for parsing after a model has been loaded.
   */
  public void initialize() {
    if (knownLabels == null)
      throw new IllegalStateException("Model has not been loaded or trained");

    //NOTE: remove -NULL-, and the pass it to ParsingSystem
    List<String> lDict = new ArrayList<>(knownLabels);
    lDict.remove(0);

    system = new ArcStandard(config.tlp, lDict);

    // Pre-compute matrix multiplications
    if (config.numPreComputed > 0)
      classifier.preCompute();
  }

  /**
   * TODO document
   */
  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);
    DependencyParser parser = new DependencyParser(props);

    // Train with CoNLL-X data
    if (props.containsKey("trainFile"))
      parser.train(props.getProperty("trainFile"), props.getProperty("devFile"), props.getProperty("model"),
          props.getProperty("embedFile"));

    boolean loaded = false;
    // Test with CoNLL-X data
    if (props.containsKey("testFile")) {
      parser.load(props.getProperty("model"));
      loaded = true;

      parser.testCoNLL(props.getProperty("testFile"), props.getProperty("outFile"));
    }

    // Parse raw text data
    if (props.containsKey("parseFile")) {
      if (!loaded) {
        parser.load(props.getProperty("model"));
        loaded = true;
      }

      String encoding = parser.config.tlp.getEncoding();
      String inputFilename = props.getProperty("parseFile");
      BufferedReader input;
      try {
        input = inputFilename.equals("-")
                ? new BufferedReader(new InputStreamReader(System.in, encoding))
                : IOUtils.readerFromString(inputFilename, encoding);
      } catch (IOException e) {
        throw new RuntimeIOException("No input file provided (use -parseFile)", e);
      }

      String outputFilename = props.getProperty("outFile");
      PrintWriter output;
      try {
        output = outputFilename == null || outputFilename.equals("-")
            ? IOUtils.encodedOutputStreamPrintWriter(System.out, encoding, true)
            : IOUtils.getPrintWriter(outputFilename, encoding);
      } catch (IOException e) {
        throw new RuntimeIOException("Error opening output file", e);
      }

      parser.parseTextFile(input, output);
    }
  }
}