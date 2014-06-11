/**
 * 
 */
package edu.stanford.nlp.neural;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.util.Generics;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>
 * @author John Bauer
 * @author Richard Socher
 */
public class Embedding {  
  private Map<String, SimpleMatrix> wordVectors;
  private int embeddingSize;

  static final String START_WORD = "*START*";
  static final String END_WORD = "*END*";
  
  static final String UNKNOWN_WORD = "*UNK*";
  static final String UNKNOWN_NUMBER = "*NUM*";
  static final String UNKNOWN_CAPS = "*CAPS*";
  static final String UNKNOWN_CHINESE_YEAR = "*ZH_YEAR*";
  static final String UNKNOWN_CHINESE_NUMBER = "*ZH_NUM*";
  static final String UNKNOWN_CHINESE_PERCENT = "*ZH_PERCENT*";

  static final Pattern NUMBER_PATTERN = Pattern.compile("-?[0-9][-0-9,.:]*");
  static final Pattern CAPS_PATTERN = Pattern.compile("[a-zA-Z]*[A-Z][a-zA-Z]*");
  static final Pattern CHINESE_YEAR_PATTERN = Pattern.compile("[〇零一二三四五六七八九０１２３４５６７８９]{4}+年");
  static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile("(?:[〇０零一二三四五六七八九０１２３４５６７８９十百万千亿]+[点多]?)+");
  static final Pattern CHINESE_PERCENT_PATTERN = Pattern.compile("百分之[〇０零一二三四五六七八九０１２３４５６７８９十点]+");

  /**
   * Some word vectors are trained with DG representing number.
   * We mix all of those into the unknown number vectors.
   */
  static final Pattern DG_PATTERN = Pattern.compile(".*DG.*");

  
  public Embedding(Map<String, SimpleMatrix> wordVectors) {
    this.wordVectors = wordVectors;
    this.embeddingSize = getEmbedingSize(wordVectors);
  }
  
  public Embedding(String wordVectorFile) {
    this(wordVectorFile, 0);
  }

  public Embedding(String wordVectorFile, int embeddingSize) {
    this.wordVectors = Generics.newHashMap();
    this.embeddingSize = embeddingSize;
    loadWordVectors(wordVectorFile);
  }

  public Embedding(String wordFile, String vectorFile) {
    this(wordFile, vectorFile, 0);
  }
  
  public Embedding(String wordFile, String vectorFile, int embeddingSize) {
    this.wordVectors = Generics.newHashMap();
    this.embeddingSize = embeddingSize;
    loadWordVectors(wordFile, vectorFile);
  }

  /**
   * This method reads a file of raw word vectors, with a given expected size, and returns a map of word to vector.
   * <br>
   * The file should be in the format <br>
   * <code>WORD X1 X2 X3 ...</code> <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * truncated and a warning is printed.
   */
  private void loadWordVectors(String wordVectorFile) {
    System.err.println("# Loading embedding ...\n  word vector file = " + wordVectorFile);
    int dimOfWords = 0;
    boolean warned = false;
    
    for (String line : IOUtils.readLines(wordVectorFile, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = lineSplit[0];

      // check for unknown token
      if(word.equals("UNKNOWN") || word.equals("UUUNKKK") || word.equals("UNK") || word.equals("*UNKNOWN*")){
        word = UNKNOWN_WORD;
      }
      // check for start token
      if(word.equals("<s>")){
        word = START_WORD;
      }
      // check for end token
      if(word.equals("</s>")){
        word = START_WORD;
      }
      
      dimOfWords = lineSplit.length - 1;
      if (embeddingSize <= 0) {
        embeddingSize = dimOfWords;
        System.err.println("  detected embedding size = " + dimOfWords);
      }
      // the first entry is the word itself
      // the other entries will all be entries in the word vector
      if (dimOfWords > embeddingSize) {
        if (!warned) {
          warned = true;
          System.err.println("WARNING: Dimensionality of numHid parameter and word vectors do not match, deleting word vector dimensions to fit!");
        }
        dimOfWords = embeddingSize;
      } else if (dimOfWords < embeddingSize) {
        throw new RuntimeException("Word vectors file has dimension too small for requested numHid of " + embeddingSize);
      }
      double vec[][] = new double[dimOfWords][1];
      for (int i = 1; i <= dimOfWords; i++) {
        vec[i-1][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);
    }
  }

  /**
   * This method takes as input two files: wordFile (one word per line) and a raw word vector file
   * with a given expected size, and returns a map of word to vector.
   * <br>
   * The word vector file should be in the format <br>
   * <code>X1 X2 X3 ...</code> <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * truncated and a warning is printed.
   */
  private void loadWordVectors(String wordFile, String vectorFile) {
    System.err.println("# Loading embedding ...\n  word file = " + wordFile + "\n  vector file = " + vectorFile);
    int dimOfWords = 0;
    boolean warned = false;
    
    Iterator<String> wordIterator = IOUtils.readLines(wordFile, "utf-8").iterator();
    for (String line : IOUtils.readLines(vectorFile, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = wordIterator.next();
      
      // check for unknown token
      if(word.equals("UNKNOWN") || word.equals("UUUNKKK") || word.equals("UNK") || word.equals("*UNKNOWN*")){
        word = UNKNOWN_WORD;
      }
      // check for start token
      if(word.equals("<s>")){
        word = START_WORD;
      }
      // check for end token
      if(word.equals("</s>")){
        word = START_WORD;
      }
      
      dimOfWords = lineSplit.length;
      
      if (embeddingSize <= 0) {
        embeddingSize = dimOfWords;
        System.err.println("  detected embedding size = " + dimOfWords);
      }
      // the first entry is the word itself
      // the other entries will all be entries in the word vector
      if (dimOfWords > embeddingSize) {
        if (!warned) {
          warned = true;
          System.err.println("WARNING: Dimensionality of numHid parameter and word vectors do not match, deleting word vector dimensions to fit!");
        }
        dimOfWords = embeddingSize;
      } else if (dimOfWords < embeddingSize) {
        throw new RuntimeException("Word vectors file has dimension too small for requested numHid of " + embeddingSize);
      }
      
      double vec[][] = new double[dimOfWords][1];
      for (int i = 0; i < dimOfWords; i++) {
        vec[i][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);
    }
  }
  
  
  // refactor from DVModel.readWordVectors()
  public void postProcessWordVectors(Options op) {
    SimpleMatrix unknownNumberVector = null;
    SimpleMatrix unknownCapsVector = null;
    SimpleMatrix unknownChineseYearVector = null;
    SimpleMatrix unknownChineseNumberVector = null;
    SimpleMatrix unknownChinesePercentVector = null;

    Random rand = new Random(op.trainOptions.dvSeed);
    wordVectors = Generics.newTreeMap();
    int numberCount = 0;
    int capsCount = 0;
    int chineseYearCount = 0;
    int chineseNumberCount = 0;
    int chinesePercentCount = 0;

    for (String word : wordVectors.keySet()) {
      SimpleMatrix vector = wordVectors.get(word);

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
      System.err.println("Matched " + chineseYearCount + " chinese year vectors");
      if (chineseYearCount > 0) {
        unknownChineseYearVector = unknownChineseYearVector.divide(chineseYearCount);
      } else {
        unknownChineseYearVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_YEAR, unknownChineseYearVector);
    }

    if (op.trainOptions.unknownChineseNumberVector) {
      System.err.println("Matched " + chineseNumberCount + " chinese number vectors");
      if (chineseNumberCount > 0) {
        unknownChineseNumberVector = unknownChineseNumberVector.divide(chineseNumberCount);
      } else {
        unknownChineseNumberVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_NUMBER, unknownChineseNumberVector);
    }

    if (op.trainOptions.unknownChinesePercentVector) {
      System.err.println("Matched " + chinesePercentCount + " chinese percent vectors");
      if (chinesePercentCount > 0) {
        unknownChinesePercentVector = unknownChinesePercentVector.divide(chinesePercentCount);
      } else {
        unknownChinesePercentVector = new SimpleMatrix(unknownWordVector);
      }
      wordVectors.put(UNKNOWN_CHINESE_PERCENT, unknownChinesePercentVector);
    }

    if (op.trainOptions.useContextWords) {
      SimpleMatrix start = SimpleMatrix.random(op.lexOptions.numHid, 1, -0.5, 0.5, rand);
      SimpleMatrix end = SimpleMatrix.random(op.lexOptions.numHid, 1, -0.5, 0.5, rand);
      wordVectors.put(START_WORD, start);
      wordVectors.put(END_WORD, end);
    }
  }

  // refactor from DVModel.getVocabWord()
  public String getPostProcessedWord(String word, Options op) {
    if (op.wordFunction != null) {
      word = op.wordFunction.apply(word);
    }
    if (op.trainOptions.lowercaseWordVectors) {
      word = word.toLowerCase();
    }
    if (wordVectors.containsKey(word)) {
      return word;
    }
    //System.err.println("Unknown word: [" + word + "]");
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
        String wv = getPostProcessedWord(lastPiece, op);
        if (wv != null) {
          return wv;
        }
      }
    }
    return UNKNOWN_WORD;
  }

  /*** Getters & Setters ***/
  public int size(){
    return wordVectors.size();
  }
  
  public Collection<SimpleMatrix> values(){
    return wordVectors.values();
  }
  
  public Set<String> keySet(){
    return wordVectors.keySet();
  }
  
  public Set<Entry<String, SimpleMatrix>> entrySet(){
    return wordVectors.entrySet();
  }
  
  public SimpleMatrix get(String word) {
    if(wordVectors.containsKey(word)){
      return wordVectors.get(word);
    } else {
      return wordVectors.get(UNKNOWN_WORD);
    }
  }
  
  public SimpleMatrix getStartWordVector() {
    return wordVectors.get(START_WORD);
  }

  public SimpleMatrix getEndWordVector() {
    return wordVectors.get(END_WORD);
  }
  
  public SimpleMatrix getUnknownWordVector() {
    return wordVectors.get(UNKNOWN_WORD);
  }
  
  public Map<String, SimpleMatrix> getWordVectors() {
    return wordVectors;
  }
    
  public int getEmbeddingSize() {
    return embeddingSize;
  }

 
  public void setWordVectors(Map<String, SimpleMatrix> wordVectors) {
    this.wordVectors = wordVectors;
    this.embeddingSize = getEmbedingSize(wordVectors);
  }
  
  private int getEmbedingSize(Map<String, SimpleMatrix> wordVectors){
    if (!wordVectors.containsKey(UNKNOWN_WORD)){
      System.err.println("! wordVectors used to initialize Embedding doesn't contain " + UNKNOWN_WORD);
      System.exit(1);
    }
    return wordVectors.get(UNKNOWN_WORD).getNumElements();
  }
}
