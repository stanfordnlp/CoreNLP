package edu.stanford.nlp.neural;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * @author Minh-Thang Luong {@code <lmthang@stanford.edu>}
 * @author John Bauer
 * @author Richard Socher
 * @author Kevin Clark
 */
public class Embedding implements Serializable  {

  private static final long serialVersionUID = 4925779982530239054L;
  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Embedding.class);
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
    this.embeddingSize = getEmbeddingSize(wordVectors);
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

  /*
  // This hack was for ejml 0.38
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();

    ConvertModels.transformMap(wordVectors, x -> new SimpleMatrix(x));
  }
  */

  /*
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    Map<String, List<List<Double>>> map = ErasureUtils.uncheckedCast(in.readObject());
    wordVectors = ConvertModels.transformMap(map, x -> ConvertModels.toMatrix(x));
    embeddingSize = in.readInt();
  }
  */

  /*
  private void writeObject(ObjectOutputStream out)
    throws IOException
  {
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> ConvertModels.fromMatrix(x);
    out.writeObject(ConvertModels.transformMap(wordVectors, f));
    out.writeInt(embeddingSize);
  }
  */


  /**
   * This method reads a file of raw word vectors, with a given expected size, and returns a map of word to vector.
   * <br>
   * The file should be in the format <br>
   * {@code WORD X1 X2 X3 ...} <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * truncated and a warning is printed.
   */
  private void loadWordVectors(String wordVectorFile) {
    log.info("# Loading embedding ...\n  word vector file = " + wordVectorFile);
    boolean warned = false;

    int numWords = 0;
    for (String line : IOUtils.readLines(wordVectorFile, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = lineSplit[0];

      // check for unknown token
      if(word.equals("UNKNOWN") || word.equals("UUUNKKK") || word.equals("UNK") || word.equals("*UNKNOWN*") || word.equals("<unk>")){
        word = UNKNOWN_WORD;
      }
      // check for start token
      if(word.equals("<s>")){
        word = START_WORD;
      }
      // check for end token
      if(word.equals("</s>")){
        word = END_WORD;
      }

      int dimOfWords = lineSplit.length - 1;
      if (embeddingSize <= 0) {
        embeddingSize = dimOfWords;
        log.info("  detected embedding size = " + dimOfWords);
      }
      // the first entry is the word itself
      // the other entries will all be entries in the word vector
      if (dimOfWords > embeddingSize) {
        if (!warned) {
          warned = true;
          log.info("WARNING: Dimensionality of numHid parameter and word vectors do not match, deleting word vector dimensions to fit!");
        }
        dimOfWords = embeddingSize;
      } else if (dimOfWords < embeddingSize) {
        throw new RuntimeException("Word vectors file has dimension too small for requested numHid of " + embeddingSize);
      }
      double[][] vec = new double[dimOfWords][1];
      for (int i = 1; i <= dimOfWords; i++) {
        vec[i-1][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);

      numWords++;
    }
    log.info("  num words = " + numWords);
  }

  /**
   * This method takes as input two files: wordFile (one word per line) and a raw word vector file
   * with a given expected size, and returns a map of word to vector.
   * <p>
   * The word vector file should be in the format <br>
   * {@code X1 X2 X3 ...} <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * truncated and a warning is printed.
   */
  private void loadWordVectors(String wordFile, String vectorFile) {
    log.info("# Loading embedding ...\n  word file = " + wordFile + "\n  vector file = " + vectorFile);
    boolean warned = false;

    int numWords = 0;
    Iterator<String> wordIterator = IOUtils.readLines(wordFile, "utf-8").iterator();
    for (String line : IOUtils.readLines(vectorFile, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = wordIterator.next();

      // check for unknown token
      // FIXME cut and paste code
    if(word.equals("UNKNOWN") || word.equals("UUUNKKK") || word.equals("UNK") || word.equals("*UNKNOWN*") || word.equals("<unk>")){
        word = UNKNOWN_WORD;
      }
      // check for start token
      if(word.equals("<s>")){
        word = START_WORD;
      }
      // check for end token
      if(word.equals("</s>")){
        word = END_WORD;
      }

      int dimOfWords = lineSplit.length;

      if (embeddingSize <= 0) {
        embeddingSize = dimOfWords;
        log.info("  detected embedding size = " + dimOfWords);
      }
      // the first entry is the word itself
      // the other entries will all be entries in the word vector
      if (dimOfWords > embeddingSize) {
        if (!warned) {
          warned = true;
          log.info("WARNING: Dimensionality of numHid parameter and word vectors do not match, deleting word vector dimensions to fit!");
        }
        dimOfWords = embeddingSize;
      } else if (dimOfWords < embeddingSize) {
        throw new RuntimeException("Word vectors file has dimension too small for requested numHid of " + embeddingSize);
      }

      double[][] vec = new double[dimOfWords][1];
      for (int i = 0; i < dimOfWords; i++) {
        vec[i][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);
      numWords++;
    }

    log.info("  num words = " + numWords);
  }

  public void writeToFile(String filename) throws IOException {
    IOUtils.writeObjectToFile(wordVectors, filename);
  }

  /* -- Getters and Setters -- */

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

  public boolean containsWord(String word) {
    return wordVectors.containsKey(word);
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
    this.embeddingSize = getEmbeddingSize(wordVectors);
  }

  private static int getEmbeddingSize(Map<String, SimpleMatrix> wordVectors){
    if (!wordVectors.containsKey(UNKNOWN_WORD)){
      // find if there's any other unk string
      String unkStr = "";
      if (wordVectors.containsKey("UNK")) { unkStr = "UNK"; }
      if (wordVectors.containsKey("UUUNKKK")) { unkStr = "UUUNKKK"; }
      if (wordVectors.containsKey("UNKNOWN")) { unkStr = "UNKNOWN"; }
      if (wordVectors.containsKey("*UNKNOWN*")) { unkStr = "*UNKNOWN*"; }
      if (wordVectors.containsKey("<unk>")) { unkStr = "<unk>"; }

      // set UNKNOWN_WORD
      if ( ! unkStr.isEmpty()){
        wordVectors.put(UNKNOWN_WORD, wordVectors.get(unkStr));
      } else {
        throw new RuntimeException("! wordVectors used to initialize Embedding doesn't contain any recognized form of " + UNKNOWN_WORD);
      }
    }

    return wordVectors.get(UNKNOWN_WORD).getNumElements();
  }

}
