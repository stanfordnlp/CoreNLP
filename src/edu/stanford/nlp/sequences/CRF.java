package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.math.ArrayMath;

import java.util.Collection;
import java.util.Arrays;
import java.io.Serializable;

/**
 * @author Jenny Finkel
 */

public class CRF extends AbstractQueriableSequenceModel implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -6958556380850043338L;

  public CRF(CliqueDataset dataset) {
    super(dataset);
    initCliqueLabels();
  }
  
  public CRF(CliqueDataset dataset, double[] weights) {
    this(dataset);
    setParameters(weights);
  }

  private double[] weights;
  
  private double[][] factors;
  private Index<LabeledClique>[] cliqueLabels;
  private int[][] messageFromLeft;
  private int[][] messageFromRight;
  private int[][][] toPassMessagesRight;
  private int[][][] toPassMessagesLeft;
  private Index<LabeledClique>[] messageLabels;

  private double[][] condProbs;
  private double weightsScale = 1.0;

  public void clearFactors() {
//     System.err.println("num factors: "+factors.length);
//     System.err.println("num labels: "+factors[1].length);
//     int factorSize = 0;
//     for (double[] f : factors) { factorSize += f.length; }
//     System.err.println("factor size: "+factorSize);
//     System.err.println("weights size: "+weights.length);
//     int messagesFromSize = 0;
//     for (int[] f : messageFromLeft) { messagesFromSize += f.length; }
//     for (int[] f : messageFromRight) { messagesFromSize += f.length; }
//     System.err.println("messagesFrom* size: "+messagesFromSize);
//     int toPassMessagesSize = 0;
//     for (int[][] ii : toPassMessagesRight) {
//       for (int[] i : ii) { if (i != null) toPassMessagesSize += i.length; }
//     }
//     for (int[][] ii : toPassMessagesLeft) {
//       for (int[] i : ii) { if (i != null) toPassMessagesSize += i.length; }
//     }
//     System.err.println("toPassMessages size: "+toPassMessagesSize);
    factors = null;
    condProbs = null;
  }

  public void clearAll() {
    factors = null;
    condProbs = null;
    messageFromLeft = null;
    messageFromRight = null;
    toPassMessagesRight = null;
    toPassMessagesLeft = null;
  }
  
  public void setParameters(double[] weights) {
    setParameters(weights, 1.0);
  }

  public void setParameters(double[] weights, double weightsScale) {
    this.weights = weights;
    this.weightsScale = weightsScale;
    condProbs = new double[dataset.numDatums()][];
    calibrate();
  }

  private void initCliqueLabels() {
    cliqueLabels = new Index[dataset.numDatums()];
    messageLabels = new HashIndex[dataset.numDatums()];
    for (int i = 0; i < dataset.numDatums(); i++) {
      Collection<LabeledClique> keySet1 = dataset.features[i].keySet();
			//			System.err.println("CRF.initCliqueLabels(): keySet1="+keySet1);
      if (i > 0 && keySet1.equals(dataset.features[i-1].keySet())) {
        cliqueLabels[i] = cliqueLabels[i-1];
      } else {
        cliqueLabels[i] = new HashIndex<LabeledClique>(keySet1);
      }
      if (i < messageLabels.length) {
        messageLabels[i] = new HashIndex<LabeledClique>();
      }
      for (LabeledClique lc : cliqueLabels[i]) {
        if (i > 0) {
          messageLabels[i-1].add(lc.leftMessage());
        }
        if (i < messageLabels.length) {
          messageLabels[i].add(lc.rightMessage());
        }
      }
      if (i > 1 && messageLabels[i-2] == messageLabels[i-1]) {
        messageLabels[i-1] = messageLabels[i-2];
      }
      if (i == messageLabels.length-1 && i > 0 && messageLabels[i-1].equals(messageLabels[i])) {
        messageLabels[i] = messageLabels[i-1];
      }
//       if (i > 0 && i < messageLabels.length && cliqueLabels[i] == cliqueLabels[i-1]) {
//         messageLabels[i] = messageLabels[i-1];
//       } else {
//         for (LabeledClique lc : cliqueLabels[i]) {
//           if (i > 0) {
//             messageLabels[i-1].add(lc.leftMessage());
//           }
//           if (i < messageLabels.length) {
//             messageLabels[i].add(lc.rightMessage());
//           }
//         }
//       }
    }
  }
  
  public Counter<Integer> getFeatures(int[] sequence) {
    Counter<Integer> features = new ClassicCounter<Integer>();
    
    int backgroundSymbol = metaInfo().backgroundIndex();
    
    for (int position = 0; position < factors.length; position++) {
      int size = cliqueLabels[position].size();
      for (int lcIndex = 0; lcIndex < size; lcIndex++) {
        LabeledClique lc = cliqueLabels[position].get(lcIndex);
        
        LabeledClique actualLC = LabeledClique.valueOf(lc.clique, sequence, position, backgroundSymbol);
        if (!lc.equals(actualLC)) { continue; }
         
        Features labelInfo = dataset.features[position].get(lc);
        int[] featuresArray = labelInfo.features;
        
        for (int i = 0; i < featuresArray.length; i++) {
          features.incrementCount(featuresArray[i],labelInfo.value(i));
        }
      }
    }
    return features;
  }
  
  private boolean memorySave = false;
  
  private void calibrate() {
    // init factors and messages and cached stuff
    factors = new double[dataset.numDatums()][];
    double[][] messages = new double[factors.length - 1][];
    boolean init = false;
    if (messageFromLeft == null) {
      init = true;
      messageFromLeft = new int[messages.length][];
      messageFromRight = new int[messages.length][];
      toPassMessagesRight = new int[messages.length][][];
      toPassMessagesLeft = new int[messages.length][][];
    }
    
    for (int i = 0; i < factors.length; i++) {
      int size = cliqueLabels[i].size();
			//			System.err.println("CRF.calibrate(): cliqueLabels["+i+"].size()="+size);
      factors[i] = new double[size];
      if (i > 0 && messageFromLeft[i-1] == null) {
        messageFromLeft[i-1] = new int[size];
        Arrays.fill(messageFromLeft[i-1], -1);
      }
      if (i < messageFromRight.length && messageFromRight[i] == null) {
        messageFromRight[i] = new int[size];
        Arrays.fill(messageFromRight[i], -1);
      }
      for (int j = 0; j < size; j++) {
        LabeledClique lc = cliqueLabels[i].get(j);
        Features labelInfo = dataset.features[i].get(lc);

        int[] features = labelInfo.features;
        
        double score = 0.0f;
        for (int k = 0; k < features.length; k++) {
          score += labelInfo.value(k)*weights[features[k]]*weightsScale;
        }
        
        // multiply in message from the left    
        if (i > 0) {
          // figure out what message to multiply in
          if (messageFromLeft[i-1][j] < 0) {
            LabeledClique message = lc.leftMessage();
            messageFromLeft[i-1][j] = messageLabels[i-1].indexOf(message);
            if (messageFromLeft[i-1][j] < 0) {
              System.err.println(i+"\n"+lc+"\n"+message+"\n"+messageLabels[i-1]+"\n------\n"+cliqueLabels[i-1]+"\n========\n"+cliqueLabels[i]);
            }
          }
          score += messages[i-1][messageFromLeft[i-1][j]];
        }
        
        factors[i][j] = score;
      }

      // memory saving - is this array equal to the previous one? 
      if (init  && i > 1 && memorySave) {
        boolean same = true;
        for (int j = 0; j < messageFromLeft[i-1].length; j++) {
          if (messageFromLeft[i-1][j] != messageFromLeft[i-2][j]) {
            same = false;
            break;
          }
        }
        if (same) {
//          System.err.println(i+" sweet - messageFromLeft");
          messageFromLeft[i-1] = messageFromLeft[i-2];
        }
      }
      
      // sum out to pass message to the right
      if (i < messages.length) {
        if (toPassMessagesRight[i] == null) {
          toPassMessagesRight[i] = new int[messageLabels[i].size()][];

          CollectionValuedMap<LabeledClique, Integer> messageMap = new CollectionValuedMap<LabeledClique,Integer>();
          
          for (int j = 0; j < cliqueLabels[i].size(); j++) {
            LabeledClique lc = cliqueLabels[i].get(j);
            LabeledClique message = lc.rightMessage();
            messageMap.add(message, j);
          }

          for (LabeledClique message : messageMap.keySet()) {
            int messageIndex = messageLabels[i].indexOf(message);
            Collection<Integer> lcs = messageMap.get(message);
            toPassMessagesRight[i][messageIndex] = new int[lcs.size()];
            int j = 0;
            for (int lcIndex : lcs) {
              toPassMessagesRight[i][messageIndex][j++] = lcIndex;
            }
          }

          // memory saving - is this array equal to the previous one? 
          if (i > 0 && memorySave) {
            boolean same = true;
          LOOP: for (int j = 0; j < toPassMessagesRight[i].length; j++) {
              if (toPassMessagesRight[i][j] == null || toPassMessagesRight[i-1][j] == null) {
                same = false;
                break;
              }
              if (toPassMessagesRight[i][j].length != toPassMessagesRight[i-1][j].length) {
                same = false;
                break;
              }
              for (int k = 0; k < toPassMessagesRight[i][j].length; k++) {
                if (toPassMessagesRight[i][j][k] != toPassMessagesRight[i-1][j][k]) {
                  same = false;
                  break LOOP;
                }
              }
            }
            if (same) {
//              System.err.println(i+" sweet toPassMEssagesRight");
              toPassMessagesRight[i] = toPassMessagesRight[i-1];
            }
          }
          
        }
        
        if (messages[i] == null) {
          messages[i] = new double[messageLabels[i].size()];
        }
        
        for (int j = 0; j < toPassMessagesRight[i].length; j++) {
          if (toPassMessagesRight[i][j] == null) {
            messages[i][j] = Double.NEGATIVE_INFINITY;
          } else {
            double[] toSum = new double[toPassMessagesRight[i][j].length];
            for (int k = 0; k < toSum.length; k++) {
              toSum[k] = factors[i][toPassMessagesRight[i][j][k]];
            }
            messages[i][j] = ArrayMath.logSum(toSum);
          }
        }
      }
    }
    
    for (int i = factors.length-1; i >= 0; i--) {
      // multiply in message from right
      if (i < messages.length) {
        int size = cliqueLabels[i].size();
        for (int j = 0; j < size; j++) {
          // figure out what message to multiply in
          if (messageFromRight[i][j] < 0) {
            LabeledClique lc = cliqueLabels[i].get(j);
            LabeledClique message = lc.rightMessage();
            messageFromRight[i][j] = messageLabels[i].indexOf(message);
          }
          factors[i][j] += messages[i][messageFromRight[i][j]];
        }
        // memory saving - is this array equal to the previous one?
        if (init  && i < messageFromLeft.length-1 && memorySave) {
          boolean same = true;
          for (int j = 0; j < messageFromRight[i].length; j++) {
            if (messageFromRight[i][j] != messageFromRight[i+1][j]) {
              same = false;
              break;
            }
          }
          if (same) {
//            System.err.println(i+" sweet - messageFromRight");
            messageFromRight[i] = messageFromRight[i+1];
          }
        }        
      }
      if (i > 0) {
        // sum out to pass message to the left
        if (toPassMessagesLeft[i-1] == null) {
          toPassMessagesLeft[i-1] = new int[messageLabels[i-1].size()][];
          
          CollectionValuedMap<LabeledClique, Integer> messageMap = new CollectionValuedMap<LabeledClique,Integer>();
          
          for (int j = 0; j < cliqueLabels[i].size(); j++) {
            LabeledClique lc = cliqueLabels[i].get(j);
            LabeledClique message = lc.leftMessage();
            messageMap.add(message, j);
          }

          for (LabeledClique message : messageMap.keySet()) {
            int messageIndex = messageLabels[i-1].indexOf(message);
            Collection<Integer> lcs = messageMap.get(message);
            toPassMessagesLeft[i-1][messageIndex] = new int[lcs.size()];
            int j = 0;
            for (int lcIndex : lcs) {
              toPassMessagesLeft[i-1][messageIndex][j++] = lcIndex;
            }
          }

          // memory saving - is this array equal to the previous one?
          if (i > 0 && i < toPassMessagesLeft.length && memorySave) {
            boolean same = true;
          LOOP: for (int j = 0; j < toPassMessagesLeft[i-1].length; j++) {
              if (toPassMessagesLeft[i][j] == null || toPassMessagesLeft[i-1][j] == null) {
                same = false;
                break;
              }
              if (toPassMessagesLeft[i-1][j].length != toPassMessagesLeft[i][j].length) {
                same = false;
                break;
              }
              for (int k = 0; k < toPassMessagesLeft[i-1][j].length; k++) {
                if (toPassMessagesLeft[i-1][j][k] != toPassMessagesLeft[i][j][k]) {
                  same = false;
                  break LOOP;
                }
              }
            }
            if (same) {
              //            System.err.println(i+" sweet - toPassMessagesLeft");
              toPassMessagesLeft[i-1] = toPassMessagesLeft[i];
            }
          }             
        }

        for (int j = 0; j < toPassMessagesLeft[i-1].length; j++) {
          if (toPassMessagesLeft[i-1][j] == null) {
            messages[i-1][j] =  Double.NEGATIVE_INFINITY;
          } else {
            double[] toSum = new double[toPassMessagesLeft[i-1][j].length];
            for (int k = 0; k < toSum.length; k++) {
              toSum[k] = factors[i][toPassMessagesLeft[i-1][j][k]];
            }
            double m = ArrayMath.logSum(toSum);
            if (!(m == Double.NEGATIVE_INFINITY && messages[i-1][j] == Double.NEGATIVE_INFINITY)) {
              messages[i-1][j] = ArrayMath.logSum(toSum) - messages[i-1][j];
            }
          }
        }
      }
    }
    
    // now that we have calibrated, we are allowed to log-normalize
    if (factors.length > 0) {
      // get Z    
      double Z = ArrayMath.logSum(factors[0]);
      for (int i = 0; i < factors.length; i++) {
        for (int j = 0; j < factors[i].length; j++) {
          factors[i][j] -= Z;
        }
      }
    }
  }

  public double logProbOf(int[] sequenceLabels, int[] positions) {
    throw new UnsupportedOperationException();
  }

  public double logConditionalProbOf(int position, LabeledClique labeledClique) {
    if (labeledClique.clique != dataset.metaInfo().getMaxClique()) {
      throw new RuntimeException("This method is only valid for the maximum clique!");
    }

    int index = cliqueLabels[position].indexOf(labeledClique);

    if (index < 0) { return Double.NEGATIVE_INFINITY; }
    
    if (condProbs[position] == null) {
      condProbs[position] = new double[cliqueLabels[position].size()];
      Arrays.fill(condProbs[position], 1.0);
    }

    if (condProbs[position][index] > 0.0) {
      Collection<LabeledClique> otherLabels = dataset.getMaxCliqueConditionalLabels(position, labeledClique);
      int[] otherIndexes = new int[otherLabels.size()];
      double[] toSum = new double[otherLabels.size()];
      int i = 0;
      for (LabeledClique otherLC : otherLabels) {
        int otherIndex = cliqueLabels[position].indexOf(otherLC);
        otherIndexes[i] = otherIndex;
        toSum[i] = factors[position][otherIndex];
        i++;
      }

      double Z = ArrayMath.logSum(toSum);

      for (int j = 0; j < otherIndexes.length; j++) {
        condProbs[position][otherIndexes[j]] = factors[position][otherIndexes[j]] - Z;
      }
    }

    return condProbs[position][index];
  }

  public double logProbOf(int position, LabeledClique labeledClique) {
    if (labeledClique.clique != dataset.metaInfo().getMaxClique()) {
      throw new RuntimeException("This method is only valid for the maximum clique!");
    }

    int index = cliqueLabels[position].indexOf(labeledClique);

    if (index < 0) { return Double.NEGATIVE_INFINITY; }
    
    return factors[position][index];
  }
}
