package edu.stanford.nlp.parser.dvparser;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.TwoDimensionalSet;
import edu.stanford.nlp.util.logging.Redwood;


public class DVModel implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DVModel.class);
  // Maps from basic category to the matrix transformation matrices for
  // binary nodes and unary nodes.
  // The indices are the children categories.  For binaryTransform, for
  // example, we have a matrix for each type of child that appears.
  public TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform;
  public Map<String, SimpleMatrix> unaryTransform;

  // score matrices for each node type
  public TwoDimensionalMap<String, String, SimpleMatrix> binaryScore;
  public Map<String, SimpleMatrix> unaryScore;

  public Map<String, SimpleMatrix> wordVectors;

  // cache these for easy calculation of "theta" parameter size
  int numBinaryMatrices, numUnaryMatrices;
  int binaryTransformSize, unaryTransformSize;
  int binaryScoreSize, unaryScoreSize;

  Options op;

  final int numCols;
  final int numRows;

  // we just keep this here for convenience
  transient SimpleMatrix identity;

  // the seed we used to use was 19580427
  Random rand;

  private static final String UNKNOWN_WORD = "*UNK*";
  private static final String UNKNOWN_NUMBER = "*NUM*";
  private static final String UNKNOWN_CAPS = "*CAPS*";
  private static final String UNKNOWN_CHINESE_YEAR = "*ZH_YEAR*";
  private static final String UNKNOWN_CHINESE_NUMBER = "*ZH_NUM*";
  private static final String UNKNOWN_CHINESE_PERCENT = "*ZH_PERCENT*";

  private static final String START_WORD = "*START*";
  private static final String END_WORD = "*END*";

  private static final Function<SimpleMatrix, DMatrixRMaj> convertSimpleMatrix = matrix -> matrix.getMatrix();

  private static final Function<DMatrixRMaj, SimpleMatrix> convertDenseMatrix = matrix -> SimpleMatrix.wrap(matrix);

  /*
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();

    // TODO: get rid of this when ejml is upgraded to a version which can deserialize SimpleMatrix
    // ejml 0.38 had a bug when reading matrices
    binaryTransform.replaceAll(x -> new SimpleMatrix(x));
    unaryTransform.replaceAll((x, y) -> new SimpleMatrix(y));
    binaryScore.replaceAll(x -> new SimpleMatrix(x));
    unaryScore.replaceAll((x, y) -> new SimpleMatrix(y));
    wordVectors.replaceAll((x, y) -> new SimpleMatrix(y));

    identity = SimpleMatrix.identity(numRows);
  }
  */


  /**
   * @param op the parameters of the parser
   */
  public DVModel(Options op, Index<String> stateIndex, UnaryGrammar unaryGrammar, BinaryGrammar binaryGrammar) {
    this.op = op;

    rand = new Random(op.trainOptions.randomSeed);

    readWordVectors();

    // Binary matrices will be n*2n+1, unary matrices will be n*n+1
    numRows = op.lexOptions.numHid;
    numCols = op.lexOptions.numHid;

    // Build one matrix for each basic category.
    // We assume that each state that has the same basic
    // category is using the same transformation matrix.
    // Use TreeMap for because we want values to be
    // sorted by key later on when building theta vectors
    binaryTransform = TwoDimensionalMap.treeMap();
    unaryTransform = Generics.newTreeMap();
    binaryScore = TwoDimensionalMap.treeMap();
    unaryScore = Generics.newTreeMap();

    numBinaryMatrices = 0;
    numUnaryMatrices = 0;
    binaryTransformSize = numRows * (numCols * 2 + 1);
    unaryTransformSize = numRows * (numCols + 1);
    binaryScoreSize = numCols;
    unaryScoreSize = numCols;

    if (op.trainOptions.useContextWords) {
      binaryTransformSize += numRows * numCols * 2;
      unaryTransformSize += numRows * numCols * 2;
    }

    identity = SimpleMatrix.identity(numRows);

    for (UnaryRule unaryRule : unaryGrammar) {
      // only make one matrix for each parent state, and only use the
      // basic category for that
      String childState = stateIndex.get(unaryRule.child);
      String childBasic = basicCategory(childState);

      addRandomUnaryMatrix(childBasic);
    }

    for (BinaryRule binaryRule : binaryGrammar) {
      // only make one matrix for each parent state, and only use the
      // basic category for that
      String leftState = stateIndex.get(binaryRule.leftChild);
      String leftBasic = basicCategory(leftState);
      String rightState = stateIndex.get(binaryRule.rightChild);
      String rightBasic = basicCategory(rightState);

      addRandomBinaryMatrix(leftBasic, rightBasic);
    }
  }

  public DVModel(TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform, Map<String, SimpleMatrix> unaryTransform,
                 TwoDimensionalMap<String, String, SimpleMatrix> binaryScore, Map<String, SimpleMatrix> unaryScore,
                 Map<String, SimpleMatrix> wordVectors, Options op) {
    this.op = op;
    this.binaryTransform = binaryTransform;
    this.unaryTransform = unaryTransform;
    this.binaryScore = binaryScore;
    this.unaryScore = unaryScore;
    this.wordVectors = wordVectors;

    this.numBinaryMatrices = binaryTransform.size();
    this.numUnaryMatrices = unaryTransform.size();
    if (numBinaryMatrices > 0) {
      this.binaryTransformSize = binaryTransform.iterator().next().getValue().getNumElements();
      this.binaryScoreSize = binaryScore.iterator().next().getValue().getNumElements();
    } else {
      this.binaryTransformSize = 0;
      this.binaryScoreSize = 0;
    }
    if (numUnaryMatrices > 0) {
      this.unaryTransformSize = unaryTransform.values().iterator().next().getNumElements();
      this.unaryScoreSize = unaryScore.values().iterator().next().getNumElements();
    } else {
      this.unaryTransformSize = 0;
      this.unaryScoreSize = 0;
    }

    this.numRows = op.lexOptions.numHid;
    this.numCols = op.lexOptions.numHid;

    this.identity = SimpleMatrix.identity(numRows);

    this.rand = new Random(op.trainOptions.randomSeed);
  }

  /**
   * Creates a random context matrix.  This will be numRows x
   * 2*numCols big.  These can be appended to the end of either a
   * unary or binary transform matrix to get the transform matrix
   * which uses context words.
   */
  private SimpleMatrix randomContextMatrix() {
    SimpleMatrix matrix = new SimpleMatrix(numRows, numCols * 2);
    matrix.insertIntoThis(0, 0, identity.scale(op.trainOptions.scalingForInit * 0.1));
    matrix.insertIntoThis(0, numCols, identity.scale(op.trainOptions.scalingForInit * 0.1));
    matrix = matrix.plus(SimpleMatrix.random_DDRM(numRows,numCols * 2,-1.0/Math.sqrt((double)numCols * 100.0),1.0/Math.sqrt((double)numCols * 100.0),rand));
    return matrix;
  }

  /**
   * Create a random transform matrix based on the initialization
   * parameters.  This will be numRows x numCols big.  These can be
   * plugged into either unary or binary transform matrices.
   */
  private SimpleMatrix randomTransformMatrix() {
    SimpleMatrix matrix;
    switch (op.trainOptions.transformMatrixType) {
    case DIAGONAL:
      matrix = SimpleMatrix.random_DDRM(numRows,numCols,-1.0/Math.sqrt((double)numCols * 100.0),1.0/Math.sqrt((double)numCols * 100.0),rand).plus(identity);
      break;
    case RANDOM:
      matrix = SimpleMatrix.random_DDRM(numRows,numCols,-1.0/Math.sqrt((double)numCols),1.0/Math.sqrt((double)numCols),rand);
      break;
    case OFF_DIAGONAL:
      matrix = SimpleMatrix.random_DDRM(numRows,numCols,-1.0/Math.sqrt((double)numCols * 100.0),1.0/Math.sqrt((double)numCols * 100.0),rand).plus(identity);
      for (int i = 0; i < numCols; ++i) {
        int x = rand.nextInt(numCols);
        int y = rand.nextInt(numCols);
        int scale = rand.nextInt(3) - 1;  // -1, 0, or 1
        matrix.set(x, y, matrix.get(x, y) + scale);
      }
      break;
    case RANDOM_ZEROS:
      matrix = SimpleMatrix.random_DDRM(numRows,numCols,-1.0/Math.sqrt((double)numCols * 100.0),1.0/Math.sqrt((double)numCols * 100.0),rand).plus(identity);
      for (int i = 0; i < numCols; ++i) {
        int x = rand.nextInt(numCols);
        int y = rand.nextInt(numCols);
        matrix.set(x, y, 0.0);
      }
      break;
    default:
      throw new IllegalArgumentException("Unexpected matrix initialization type " + op.trainOptions.transformMatrixType);
    }
    return matrix;
  }

  public void addRandomUnaryMatrix(String childBasic) {
    if (unaryTransform.get(childBasic) != null) {
      return;
    }

    ++numUnaryMatrices;

    // scoring matrix
    SimpleMatrix score = SimpleMatrix.random_DDRM(1, numCols, -1.0/Math.sqrt((double)numCols),1.0/Math.sqrt((double)numCols),rand);
    unaryScore.put(childBasic, score.scale(op.trainOptions.scalingForInit));

    SimpleMatrix transform;
    if (op.trainOptions.useContextWords) {
      transform = new SimpleMatrix(numRows, numCols * 3 + 1);
      // leave room for bias term
      transform.insertIntoThis(0,numCols + 1, randomContextMatrix());
    } else {
      transform = new SimpleMatrix(numRows, numCols + 1);
    }
    SimpleMatrix unary = randomTransformMatrix();
    transform.insertIntoThis(0, 0, unary);
    unaryTransform.put(childBasic, transform.scale(op.trainOptions.scalingForInit));
  }

  public void addRandomBinaryMatrix(String leftBasic, String rightBasic) {
    if (binaryTransform.get(leftBasic, rightBasic) != null) {
      return;
    }

    ++numBinaryMatrices;

    // scoring matrix
    SimpleMatrix score = SimpleMatrix.random_DDRM(1, numCols, -1.0/Math.sqrt((double)numCols),1.0/Math.sqrt((double)numCols),rand);
    binaryScore.put(leftBasic, rightBasic, score.scale(op.trainOptions.scalingForInit));

    SimpleMatrix binary;
    if (op.trainOptions.useContextWords) {
      binary = new SimpleMatrix(numRows, numCols * 4 + 1);
      // leave room for bias term
      binary.insertIntoThis(0,numCols*2+1, randomContextMatrix());
    } else {
      binary = new SimpleMatrix(numRows, numCols * 2 + 1);
    }
    SimpleMatrix left = randomTransformMatrix();
    SimpleMatrix right = randomTransformMatrix();
    binary.insertIntoThis(0, 0, left);
    binary.insertIntoThis(0, numCols, right);
    binaryTransform.put(leftBasic, rightBasic, binary.scale(op.trainOptions.scalingForInit));
  }

  public void setRulesForTrainingSet(List<Tree> sentences, Map<Tree, byte[]> compressedTrees) {
    TwoDimensionalSet<String, String> binaryRules = TwoDimensionalSet.treeSet();
    Set<String> unaryRules = new HashSet<>();
    Set<String> words = new HashSet<>();
    for (Tree sentence : sentences) {
      searchRulesForBatch(binaryRules, unaryRules, words, sentence);

      for (Tree hypothesis : CacheParseHypotheses.convertToTrees(compressedTrees.get(sentence))) {
        searchRulesForBatch(binaryRules, unaryRules, words, hypothesis);
      }
    }

    for (Pair<String, String> binary : binaryRules) {
      addRandomBinaryMatrix(binary.first, binary.second);
    }
    for (String unary : unaryRules) {
      addRandomUnaryMatrix(unary);
    }

    filterRulesForBatch(binaryRules, unaryRules, words);
  }

  /**
   * Filters the transform and score rules so that we only have the
   * ones which appear in the trees given
   */
  public void filterRulesForBatch(Collection<Tree> trees) {
    TwoDimensionalSet<String, String> binaryRules = TwoDimensionalSet.treeSet();
    Set<String> unaryRules = new HashSet<>();
    Set<String> words = new HashSet<>();
    for (Tree tree : trees) {
      searchRulesForBatch(binaryRules, unaryRules, words, tree);
    }

    filterRulesForBatch(binaryRules, unaryRules, words);
  }

  public void filterRulesForBatch(Map<Tree, byte[]> compressedTrees) {
    TwoDimensionalSet<String, String> binaryRules = TwoDimensionalSet.treeSet();
    Set<String> unaryRules = new HashSet<>();
    Set<String> words = new HashSet<>();
    for (Map.Entry<Tree, byte[]> entry : compressedTrees.entrySet()) {
      searchRulesForBatch(binaryRules, unaryRules, words, entry.getKey());

      for (Tree hypothesis : CacheParseHypotheses.convertToTrees(entry.getValue())) {
        searchRulesForBatch(binaryRules, unaryRules, words, hypothesis);
      }
    }

    filterRulesForBatch(binaryRules, unaryRules, words);
  }

  public void filterRulesForBatch(TwoDimensionalSet<String, String> binaryRules, Set<String> unaryRules, Set<String> words) {
    TwoDimensionalMap<String, String, SimpleMatrix> newBinaryTransforms = TwoDimensionalMap.treeMap();
    TwoDimensionalMap<String, String, SimpleMatrix> newBinaryScores = TwoDimensionalMap.treeMap();
    for (Pair<String, String> binaryRule : binaryRules) {
      SimpleMatrix transform = binaryTransform.get(binaryRule.first(), binaryRule.second());
      if (transform != null) {
        newBinaryTransforms.put(binaryRule.first(), binaryRule.second(), transform);
      }
      SimpleMatrix score = binaryScore.get(binaryRule.first(), binaryRule.second());
      if (score != null) {
        newBinaryScores.put(binaryRule.first(), binaryRule.second(), score);
      }
      if ((transform == null && score != null) ||
          (transform != null && score == null)) {
        throw new AssertionError();
      }
    }
    binaryTransform = newBinaryTransforms;
    binaryScore = newBinaryScores;
    numBinaryMatrices = binaryTransform.size();

    Map<String, SimpleMatrix> newUnaryTransforms = Generics.newTreeMap();
    Map<String, SimpleMatrix> newUnaryScores = Generics.newTreeMap();
    for (String unaryRule : unaryRules) {
      SimpleMatrix transform = unaryTransform.get(unaryRule);
      if (transform != null) {
        newUnaryTransforms.put(unaryRule, transform);
      }
      SimpleMatrix score = unaryScore.get(unaryRule);
      if (score != null) {
        newUnaryScores.put(unaryRule, score);
      }
      if ((transform == null && score != null) ||
          (transform != null && score == null)) {
        throw new AssertionError();
      }
    }
    unaryTransform = newUnaryTransforms;
    unaryScore = newUnaryScores;
    numUnaryMatrices = unaryTransform.size();

    Map<String, SimpleMatrix> newWordVectors = Generics.newTreeMap();
    for (String word : words) {
      SimpleMatrix wordVector = wordVectors.get(word);
      if (wordVector != null) {
        newWordVectors.put(word, wordVector);
      }
    }
    wordVectors = newWordVectors;
  }

  private void searchRulesForBatch(TwoDimensionalSet<String, String> binaryRules,
                                   Set<String> unaryRules, Set<String> words,
                                   Tree tree) {
    if (tree.isLeaf()) {
      return;
    }
    if (tree.isPreTerminal()) {
      words.add(getVocabWord(tree.children()[0].value()));
      return;
    }
    Tree[] children = tree.children();
    if (children.length == 1) {
      unaryRules.add(basicCategory(children[0].value()));
      searchRulesForBatch(binaryRules, unaryRules, words, children[0]);
    } else if (children.length == 2) {
      binaryRules.add(basicCategory(children[0].value()),
                      basicCategory(children[1].value()));
      searchRulesForBatch(binaryRules, unaryRules, words, children[0]);
      searchRulesForBatch(binaryRules, unaryRules, words, children[1]);
    } else {
      throw new AssertionError("Expected a binarized tree");
    }
  }

  public String basicCategory(String category) {
    if (op.trainOptions.dvSimplifiedModel) {
      return "";
    } else {
      String basic = op.langpack().basicCategory(category);
      // TODO: if we can figure out what is going on with the grammar
      // compaction, perhaps we don't want this any more
      if (basic.length() > 0 && basic.charAt(0) == '@') {
        basic = basic.substring(1);
      }
      return basic;
    }
  }

  private static final Pattern NUMBER_PATTERN = Pattern.compile("-?[0-9][-0-9,.:]*");

  private static final Pattern CAPS_PATTERN = Pattern.compile("[a-zA-Z]*[A-Z][a-zA-Z]*");

  private static final Pattern CHINESE_YEAR_PATTERN = Pattern.compile("[〇零一二三四五六七八九０１２３４５６７８９]{4}+年");

  private static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile("(?:[〇零一二三四五六七八九０１２３４５６７８９十百万千亿]+[点多]?)+");

  private static final Pattern CHINESE_PERCENT_PATTERN = Pattern.compile("百分之[〇零一二三四五六七八九０１２３４５６７８９十点]+");

  /**
   * Some word vectors are trained with DG representing number.
   * We mix all of those into the unknown number vectors.
   */
  private static final Pattern DG_PATTERN = Pattern.compile(".*DG.*");

  public void readWordVectors() {
    SimpleMatrix unknownNumberVector = null;
    SimpleMatrix unknownCapsVector = null;
    SimpleMatrix unknownChineseYearVector = null;
    SimpleMatrix unknownChineseNumberVector = null;
    SimpleMatrix unknownChinesePercentVector = null;

    wordVectors = Generics.newTreeMap();
    int numberCount = 0;
    int capsCount = 0;
    int chineseYearCount = 0;
    int chineseNumberCount = 0;
    int chinesePercentCount = 0;

    //Map<String, SimpleMatrix> rawWordVectors = NeuralUtils.readRawWordVectors(op.lexOptions.wordVectorFile, op.lexOptions.numHid);
    Embedding rawWordVectors = new Embedding(op.lexOptions.wordVectorFile, op.lexOptions.numHid);

    for (Map.Entry<String, SimpleMatrix> entry : rawWordVectors.entrySet()) {
      String word = entry.getKey();
      SimpleMatrix vector = entry.getValue();

      if (op.wordFunction != null) {
        word = op.wordFunction.apply(word);
      }

      wordVectors.put(word, vector);

      if (op.lexOptions.numHid <= 0) {
        op.lexOptions.numHid = vector.getNumElements();
      }

      // TODO: factor out all of these identical blobs
      if (op.trainOptions.unknownNumberVector &&
          (NUMBER_PATTERN.matcher(word).matches() || DG_PATTERN.matcher(word).matches())) {
        ++numberCount;
        if (unknownNumberVector == null) {
          unknownNumberVector = new SimpleMatrix(vector);
        } else {
          unknownNumberVector = unknownNumberVector.plus(vector);
        }
      }

      if (op.trainOptions.unknownCapsVector && CAPS_PATTERN.matcher(word).matches()) {
        ++capsCount;
        if (unknownCapsVector == null) {
          unknownCapsVector = new SimpleMatrix(vector);
        } else {
          unknownCapsVector = unknownCapsVector.plus(vector);
        }
      }

      if (op.trainOptions.unknownChineseYearVector && CHINESE_YEAR_PATTERN.matcher(word).matches()) {
        ++chineseYearCount;
        if (unknownChineseYearVector == null) {
          unknownChineseYearVector = new SimpleMatrix(vector);
        } else {
          unknownChineseYearVector = unknownChineseYearVector.plus(vector);
        }
      }

      if (op.trainOptions.unknownChineseNumberVector &&
          (CHINESE_NUMBER_PATTERN.matcher(word).matches() || DG_PATTERN.matcher(word).matches())) {
        ++chineseNumberCount;
        if (unknownChineseNumberVector == null) {
          unknownChineseNumberVector = new SimpleMatrix(vector);
        } else {
          unknownChineseNumberVector = unknownChineseNumberVector.plus(vector);
        }
      }

      if (op.trainOptions.unknownChinesePercentVector && CHINESE_PERCENT_PATTERN.matcher(word).matches()) {
        ++chinesePercentCount;
        if (unknownChinesePercentVector == null) {
          unknownChinesePercentVector = new SimpleMatrix(vector);
        } else {
          unknownChinesePercentVector = unknownChinesePercentVector.plus(vector);
        }
      }
    }

    String unkWord = op.trainOptions.unkWord;
    if (op.wordFunction != null) {
      unkWord = op.wordFunction.apply(unkWord);
    }
    SimpleMatrix unknownWordVector = wordVectors.get(unkWord);
    wordVectors.put(UNKNOWN_WORD, unknownWordVector);
    if (unknownWordVector == null) {
      throw new RuntimeException("Unknown word vector not specified in the word vector file");
    }

    if (op.trainOptions.unknownNumberVector) {
      if (numberCount > 0) {
        unknownNumberVector = unknownNumberVector.divide(numberCount);
      } else {
        unknownNumberVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_NUMBER, unknownNumberVector);
    }

    if (op.trainOptions.unknownCapsVector) {
      if (capsCount > 0) {
        unknownCapsVector = unknownCapsVector.divide(capsCount);
      } else {
        unknownCapsVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CAPS, unknownCapsVector);
    }

    if (op.trainOptions.unknownChineseYearVector) {
      log.info("Matched " + chineseYearCount + " chinese year vectors");
      if (chineseYearCount > 0) {
        unknownChineseYearVector = unknownChineseYearVector.divide(chineseYearCount);
      } else {
        unknownChineseYearVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_YEAR, unknownChineseYearVector);
    }

    if (op.trainOptions.unknownChineseNumberVector) {
      log.info("Matched " + chineseNumberCount + " chinese number vectors");
      if (chineseNumberCount > 0) {
        unknownChineseNumberVector = unknownChineseNumberVector.divide(chineseNumberCount);
      } else {
        unknownChineseNumberVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_NUMBER, unknownChineseNumberVector);
    }

    if (op.trainOptions.unknownChinesePercentVector) {
      log.info("Matched " + chinesePercentCount + " chinese percent vectors");
      if (chinesePercentCount > 0) {
        unknownChinesePercentVector = unknownChinesePercentVector.divide(chinesePercentCount);
      } else {
        unknownChinesePercentVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_PERCENT, unknownChinesePercentVector);
    }

    if (op.trainOptions.useContextWords) {
      SimpleMatrix start = SimpleMatrix.random_DDRM(op.lexOptions.numHid, 1, -0.5, 0.5, rand);
      SimpleMatrix end = SimpleMatrix.random_DDRM(op.lexOptions.numHid, 1, -0.5, 0.5, rand);
      wordVectors.put(START_WORD, start);
      wordVectors.put(END_WORD, end);
    }
  }


  public int totalParamSize() {
    int totalSize = 0;
    totalSize += numBinaryMatrices * (binaryTransformSize + binaryScoreSize);
    totalSize += numUnaryMatrices * (unaryTransformSize + unaryScoreSize);
    if (op.trainOptions.trainWordVectors) {
      totalSize += wordVectors.size() * op.lexOptions.numHid;
    }
    return totalSize;
  }


  @SuppressWarnings("unchecked")
  public double[] paramsToVector(double scale) {
    int totalSize = totalParamSize();
    if (op.trainOptions.trainWordVectors) {
      return NeuralUtils.paramsToVector(scale, totalSize,
                                        binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                        binaryScore.valueIterator(), unaryScore.values().iterator(),
                                        wordVectors.values().iterator());
    } else {
      return NeuralUtils.paramsToVector(scale, totalSize,
                                        binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                        binaryScore.valueIterator(), unaryScore.values().iterator());
    }
  }


  @SuppressWarnings("unchecked")
  public double[] paramsToVector() {
    int totalSize = totalParamSize();
    if (op.trainOptions.trainWordVectors) {
      return NeuralUtils.paramsToVector(totalSize,
                                        binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                        binaryScore.valueIterator(), unaryScore.values().iterator(),
                                        wordVectors.values().iterator());
    } else {
      return NeuralUtils.paramsToVector(totalSize,
                                        binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                        binaryScore.valueIterator(), unaryScore.values().iterator());
    }
  }

  @SuppressWarnings("unchecked")
  public void vectorToParams(double[] theta) {
    if (op.trainOptions.trainWordVectors) {
      NeuralUtils.vectorToParams(theta,
                                 binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                 binaryScore.valueIterator(), unaryScore.values().iterator(),
                                 wordVectors.values().iterator());
    } else {
      NeuralUtils.vectorToParams(theta,
                                 binaryTransform.valueIterator(), unaryTransform.values().iterator(),
                                 binaryScore.valueIterator(), unaryScore.values().iterator());
    }
  }

  public SimpleMatrix getWForNode(Tree node) {
    if (node.children().length == 1) {
      String childLabel = node.children()[0].value();
      String childBasic = basicCategory(childLabel);
      return unaryTransform.get(childBasic);
    } else if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryTransform.get(leftBasic, rightBasic);
    }
    throw new AssertionError("Should only have unary or binary nodes");
  }

  public SimpleMatrix getScoreWForNode(Tree node) {
    if (node.children().length == 1) {
      String childLabel = node.children()[0].value();
      String childBasic = basicCategory(childLabel);
      return unaryScore.get(childBasic);
    } else if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryScore.get(leftBasic, rightBasic);
    }
    throw new AssertionError("Should only have unary or binary nodes");
  }

  public SimpleMatrix getStartWordVector() {
    return wordVectors.get(START_WORD);
  }

  public SimpleMatrix getEndWordVector() {
    return wordVectors.get(END_WORD);
  }

  public SimpleMatrix getWordVector(String word) {
    return wordVectors.get(getVocabWord(word));
  }

  public String getVocabWord(String word) {
    if (op.wordFunction != null) {
      word = op.wordFunction.apply(word);
    }
    if (op.trainOptions.lowercaseWordVectors) {
      word = word.toLowerCase();
    }
    if (wordVectors.containsKey(word)) {
      return word;
    }
    //log.info("Unknown word: [" + word + "]");
    if (op.trainOptions.unknownNumberVector && NUMBER_PATTERN.matcher(word).matches()) {
      return UNKNOWN_NUMBER;
    }
    if (op.trainOptions.unknownCapsVector && CAPS_PATTERN.matcher(word).matches()) {
      return UNKNOWN_CAPS;
    }
    if (op.trainOptions.unknownChineseYearVector && CHINESE_YEAR_PATTERN.matcher(word).matches()) {
      return UNKNOWN_CHINESE_YEAR;
    }
    if (op.trainOptions.unknownChineseNumberVector && CHINESE_NUMBER_PATTERN.matcher(word).matches()) {
      return UNKNOWN_CHINESE_NUMBER;
    }
    if (op.trainOptions.unknownChinesePercentVector && CHINESE_PERCENT_PATTERN.matcher(word).matches()) {
      return UNKNOWN_CHINESE_PERCENT;
    }
    if (op.trainOptions.unknownDashedWordVectors) {
      int index = word.lastIndexOf('-');
      if (index >= 0 && index < word.length()) {
        String lastPiece = word.substring(index + 1);
        String wv = getVocabWord(lastPiece);
        if (wv != null) {
          return wv;
        }
      }
    }
    return UNKNOWN_WORD;
  }

  public SimpleMatrix getUnknownWordVector() {
    return wordVectors.get(UNKNOWN_WORD);
  }

  public void printMatrixNames(PrintStream out) {
    out.println("Binary matrices:");
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryTransform) {
      out.println("  " + binary.getFirstKey() + ":" + binary.getSecondKey());
    }
    out.println("Unary matrices:");
    for (String unary : unaryTransform.keySet()) {
      out.println("  " + unary);
    }
  }

  public void printMatrixStats(PrintStream out) {
    log.info("Model loaded with " + numUnaryMatrices + " unary and " + numBinaryMatrices + " binary");
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryTransform) {
      out.println("Binary transform " + binary.getFirstKey() + ":" + binary.getSecondKey());
      double normf = binary.getValue().normF();
      out.println("  Total norm " + (normf * normf));
      normf = binary.getValue().extractMatrix(0, op.lexOptions.numHid, 0, op.lexOptions.numHid).normF();
      out.println("  Left norm (" + binary.getFirstKey() + ") " + (normf * normf));
      normf = binary.getValue().extractMatrix(0, op.lexOptions.numHid, op.lexOptions.numHid, op.lexOptions.numHid*2).normF();
      out.println("  Right norm (" + binary.getSecondKey() + ") " + (normf * normf));

    }
  }

  public void printAllMatrices(PrintStream out) {
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryTransform) {
      out.println("Binary transform " + binary.getFirstKey() + ":" + binary.getSecondKey());
      out.println(binary.getValue());
    }
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryScore) {
      out.println("Binary score " + binary.getFirstKey() + ":" + binary.getSecondKey());
      out.println(binary.getValue());
    }
    for (Map.Entry<String, SimpleMatrix> unary : unaryTransform.entrySet()) {
      out.println("Unary transform " + unary.getKey());
      out.println(unary.getValue());
    }
    for (Map.Entry<String, SimpleMatrix> unary : unaryScore.entrySet()) {
      out.println("Unary score " + unary.getKey());
      out.println(unary.getValue());
    }
  }


  public int binaryTransformIndex(String leftChild, String rightChild) {
    int pos = 0;
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryTransform) {
      if (binary.getFirstKey().equals(leftChild) && binary.getSecondKey().equals(rightChild)) {
        return pos;
      }
      pos += binary.getValue().getNumElements();
    }
    return -1;
  }

  public int unaryTransformIndex(String child) {
    int pos = binaryTransformSize * numBinaryMatrices;
    for (Map.Entry<String, SimpleMatrix> unary : unaryTransform.entrySet()) {
      if (unary.getKey().equals(child)) {
        return pos;
      }
      pos += unary.getValue().getNumElements();
    }
    return -1;
  }

  public int binaryScoreIndex(String leftChild, String rightChild) {
    int pos = binaryTransformSize * numBinaryMatrices + unaryTransformSize * numUnaryMatrices;
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> binary : binaryScore) {
      if (binary.getFirstKey().equals(leftChild) && binary.getSecondKey().equals(rightChild)) {
        return pos;
      }
      pos += binary.getValue().getNumElements();
    }
    return -1;
  }

  public int unaryScoreIndex(String child) {
    int pos = (binaryTransformSize + binaryScoreSize) * numBinaryMatrices + unaryTransformSize * numUnaryMatrices;
    for (Map.Entry<String, SimpleMatrix> unary : unaryScore.entrySet()) {
      if (unary.getKey().equals(child)) {
        return pos;
      }
      pos += unary.getValue().getNumElements();
    }
    return -1;
  }

  public Pair<String, String> indexToBinaryTransform(int pos) {
    if (pos < numBinaryMatrices * binaryTransformSize) {
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : binaryTransform) {
        if (binaryTransformSize < pos) {
          pos -= binaryTransformSize;
        } else {
          return Pair.makePair(entry.getFirstKey(), entry.getSecondKey());
        }
      }
    }
    return null;
  }

  public String indexToUnaryTransform(int pos) {
    pos -= numBinaryMatrices * binaryTransformSize;
    if (pos < numUnaryMatrices * unaryTransformSize && pos >= 0) {
      for (Map.Entry<String, SimpleMatrix> entry : unaryTransform.entrySet()) {
        if (unaryTransformSize < pos) {
          pos -= unaryTransformSize;
        } else {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  public Pair<String, String> indexToBinaryScore(int pos) {
    pos -= (numBinaryMatrices * binaryTransformSize + numUnaryMatrices * unaryTransformSize);
    if (pos < numBinaryMatrices * binaryScoreSize && pos >= 0) {
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : binaryScore) {
        if (binaryScoreSize < pos) {
          pos -= binaryScoreSize;
        } else {
          return Pair.makePair(entry.getFirstKey(), entry.getSecondKey());
        }
      }
    }
    return null;
  }

  public String indexToUnaryScore(int pos) {
    pos -= (numBinaryMatrices * (binaryTransformSize + binaryScoreSize) + numUnaryMatrices * unaryTransformSize);
    if (pos < numUnaryMatrices * unaryScoreSize && pos >= 0) {
      for (Map.Entry<String, SimpleMatrix> entry : unaryScore.entrySet()) {
        if (unaryScoreSize < pos) {
          pos -= unaryScoreSize;
        } else {
          return entry.getKey();
        }
      }
    }
    return null;
  }



  /**
   * Prints to stdout the type and key for the given location in the parameter stack
   */
  public void printParameterType(int pos, PrintStream out) {
    int originalPos = pos;

    Pair<String, String> binary = indexToBinaryTransform(pos);
    if (binary != null) {
      pos = pos % binaryTransformSize;
      out.println("Entry " + originalPos + " is entry " + pos + " of binary transform " + binary.first() + ":" + binary.second());
      return;
    }

    String unary = indexToUnaryTransform(pos);
    if (unary != null) {
      pos = (pos - numBinaryMatrices * binaryTransformSize) % unaryTransformSize;
      out.println("Entry " + originalPos + " is entry " + pos + " of unary transform " + unary);
      return;
    }

    binary = indexToBinaryScore(pos);
    if (binary != null) {
      pos = (pos - numBinaryMatrices * binaryTransformSize - numUnaryMatrices * unaryTransformSize) % binaryScoreSize;
      out.println("Entry " + originalPos + " is entry " + pos + " of binary score " + binary.first() + ":" + binary.second());
      return;
    }

    unary = indexToUnaryScore(pos);
    if (unary != null) {
      pos = (pos - (numBinaryMatrices * (binaryTransformSize + binaryScoreSize)) - numUnaryMatrices * unaryTransformSize) % unaryScoreSize;
      out.println("Entry " + originalPos + " is entry " + pos + " of unary score " + unary);
      return;
    }

    out.println("Index " + originalPos + " unknown");
  }

  private static final long serialVersionUID = 1;

}
