package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.GeneralizedCounter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.ling.Word;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author grenager
 */
public class BigramSequenceModel implements SequenceModel {

  public Index<Object> index;
  private int[] possibleValues;

  private double totalCount;
  private ClassicCounter<String> unigramCounter;
  private TwoDimensionalCounter<String, String> bigramCounter;
  private double[] conditionalProbs;

  // these must sum to 1.0
  private double bigramWeight = 0.99;
  private double unigramWeight = 0.00999999;
  private double smoothWeight = 0.0000001;

  /**
   * Get an {@link edu.stanford.nlp.util.Index} to map between class name Strings and ints indices.
   */
  public Index<Object> classIndex() {
    return index;
  }

  /**
   * Get the background symbol.
   */
  public String backgroundSymbol() {
    return "O";
  }

  public int leftWindow() {
    return 1;
  }

  public int rightWindow() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int[] getPossibleValues(int position)  {
    return possibleValues;
  }

  public double[] scoresOf(int[] sequence, int pos) {
    String prevWord = "**BEGIN**";
    if (pos>0) {
      prevWord = (String) index.get(sequence[pos-1]);
    }
    if (pos>=sequence.length-1 || sequence[pos+1]<0) {
      // next word hasn't yet been initialized
      for (int i=0; i<conditionalProbs.length; i++) {
        String word = (String) index.get(i);
        conditionalProbs[i] = logConditionalProbOfWord(prevWord, word);
      }
    } else {
      String nextWord = (String) index.get(sequence[pos+1]);
      for (int i=0; i<conditionalProbs.length; i++) {
        String word = (String) index.get(i);
        conditionalProbs[i] = logConditionalProbOfWord(prevWord, word) + logConditionalProbOfWord(word, nextWord);
      }
    }
//      System.out.println("conditionalProbs[" + i + "]=" + conditionalProbs[i]);
//    ArrayMath.normalize(conditionalProbs);
//    int best = ArrayMath.argmax(conditionalProbs);
//    System.out.println("conditioned on " + prevWord + " and " + nextWord + " best is " + index.get(best));
    return conditionalProbs;
  }

  public double scoreOf(int[] sequence, int pos) {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public double scoreOf(int[] sequence) {
    double result = 0.0;
    int pos = 0;
    String word = (String) index.get(sequence[pos]);
    result += conditionalProbOfWord("**BEGIN**", word);
    String lastWord = word;
    for (; pos<sequence.length; pos++) {
      word = (String) index.get(sequence[pos]);
      result += logConditionalProbOfWord(lastWord, word);
      lastWord = word;
    }
    return result;
  }

  private double logConditionalProbOfWord(String word1, String word2) {
    return Math.log(conditionalProbOfWord(word1, word2));
  }

  private double conditionalProbOfWord(String word1, String word2) {
    double unigramCount1 = unigramCounter.getCount(word1);
    double unigramCount2 = unigramCounter.getCount(word2);
    double bigramCount = bigramCounter.getCount(word1, word2);
    double bigramProb = unigramCount1==0.0 ? 0.0 : (bigramCount/unigramCount1);
    double unigramProb = (unigramCount2/totalCount);
    double prob = bigramWeight*bigramProb
                  + unigramWeight*unigramProb
                  + smoothWeight;
    if (Double.isNaN(prob)) throw new RuntimeException();
//    System.out.println("word1=" + word1 +
//                       " word2=" + word2 +
//                       " bigramCount=" + bigramCount +
//                       " unigramCount1=" + unigramCount1 +
//                       " unigramCount2=" + unigramCount2 +
//                       " prob=" + prob);
    return prob;
  }

  public int length() {
    return 100; // arbitrary
  }

  public int getNumValues() {
    return unigramCounter.size();
  }

  public Object[] translateSequence(int[] sequence) {
    Object[] result = new Object[sequence.length];
    for (int i=0; i<sequence.length; i++) {
      result[i] = index.get(sequence[i]);
    }
    return result;
  }

  public int[] translateSequence(Object[] sequence) {
    int[] result = new int[sequence.length];
    for (int i=0; i<sequence.length; i++) {
      result[i] = index.indexOf(sequence[i]);
    }
    return result;
  }

  public BigramSequenceModel(String trainPath, String fileExtension) {
    System.err.print("Learning bigram model...");
    unigramCounter = new ClassicCounter<String>();
    bigramCounter = new TwoDimensionalCounter<String, String>();
    index = new HashIndex<Object>();
    Collection c = new FileSequentialCollection(new File(trainPath), fileExtension, true);
    ReaderIteratorFactory rif = new ReaderIteratorFactory(c);
    ObjectBank bank = new ObjectBank(rif, PTBTokenizer.PTBTokenizerFactory.newPTBTokenizerFactory(true));
    // this bank should now vend tokens
    String lastToken = null;
    int i=0;
    for (Iterator tokIter = bank.iterator(); tokIter.hasNext(); ) {
      String thisToken = ((Word) tokIter.next()).word();
      thisToken = thisToken.toLowerCase();
      index.add(thisToken);
      unigramCounter.incrementCount(thisToken);
      bigramCounter.incrementCount(lastToken, thisToken);
      lastToken = thisToken;
      i++;
      if (i%10000 == 0) System.err.println("Processed " + i);
    }
    totalCount = unigramCounter.totalCount();
    conditionalProbs = new double[unigramCounter.size()];
    System.err.println("done. numTokens="+i+" vocabSize=" + unigramCounter.size());
    possibleValues = new int[index.size()];
    for (int j=0; j<index.size(); j++) {
      possibleValues[j] = j;
    }
  }

  public static void main(String[] args) {
    BigramSequenceModel model = new BigramSequenceModel(args[0], args[1]);
    SequenceGibbsSampler sampler = new SequenceGibbsSampler(0, 0, null);
    for (int j=0; j<50; j++) {
      System.out.println("**************************************************");
      int[] sequence = sampler.findBestUsingAnnealing(model, CoolingSchedule.getExponentialSchedule(1.0, 0.9, 20));
      Object[] words = model.translateSequence(sequence);
      for (int i=0; i<words.length; i++) {
        if ("*cr*".equals(words[i])) {
          System.out.println();
        } else {
          System.out.print(words[i] + " ");
        }
      }
      System.out.println();
    }
  }

}
