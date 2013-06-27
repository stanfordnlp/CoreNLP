package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.fsm.*;

import java.util.Arrays;

/**
 * @author Michel Galley
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */
public class ViterbiSearchGraphBuilder {

  public static DFSA<String, Integer> getGraph(SequenceModel ts, Index<String> classIndex) {

    DFSA<String, Integer> viterbiSearchGraph = new DFSA<String, Integer>(null);

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();

   assert (rightWindow == 0);
    
    int padLength = length + leftWindow + rightWindow;

    // NOTE: tags[i][j]  : i is index into pos, and j into product
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];    
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.getPossibleValues(pos);
      tagNum[pos] = tags[pos].length;
    }

    // Set up Viterbi search graph:
    DFSAState<String, Integer>[][] graphStates = null;
    DFSAState<String, Integer> startState = null, endState = null;
    if(viterbiSearchGraph != null) {
      int stateId = -1;
      startState = new DFSAState<String, Integer>(++stateId, viterbiSearchGraph, 0.0);
      viterbiSearchGraph.setInitialState(startState);
      graphStates = new DFSAState[length][];
      for(int pos = 0; pos<length; ++pos) {
        //System.err.printf("%d states at pos %d\n",tags[pos].length,pos);
        graphStates[pos] = new DFSAState[tags[pos].length];
        for(int product = 0; product < tags[pos].length; ++product) {
          graphStates[pos][product] = new DFSAState<String, Integer>(++stateId, viterbiSearchGraph);
        }
      }
      // Accepting state:
      endState = new DFSAState<String, Integer>(++stateId, viterbiSearchGraph, 0.0);
      endState.setAccepting(true);
    }

    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i = 0; i < leftWindow; i++) {
      curProduct *= tagNum[i];
    }
    for (int pos = leftWindow; pos < padLength; pos++) {
      if (pos > leftWindow + rightWindow) {
        curProduct /= tagNum[pos - leftWindow - rightWindow - 1]; // shift off
      }
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos - rightWindow] = curProduct;
    }

    double[][] windowScore = new double[padLength][];
    
    // Score all of each window's options
    for (int pos = leftWindow; pos < leftWindow + length; pos++) {
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags, tags[0][0]);

      for (int product = 0; product < productSizes[pos]; product++) {
        int p = product;
        int shift = 1;
        for (int curPos = pos; curPos >= pos - leftWindow; curPos--) {
          tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
          p /= tagNum[curPos];
          if (curPos > pos) {
            shift *= tagNum[curPos];
          }
        }
        if (tempTags[pos] == tags[pos][0]) {
          // get all tags at once
          double[] scores = ts.scoresOf(tempTags, pos);
          // fill in the relevant windowScores
          for (int t = 0; t < tagNum[pos]; t++) {
            windowScore[pos][product + t * shift] = scores[t];
          }
        }
      }
    }

    // loop over the classification spot
    for (int pos = leftWindow; pos < length + leftWindow; pos++) {
      // loop over window product types
      for (int product = 0; product < productSizes[pos]; product++) {
        if (pos == leftWindow) {
          // all nodes in the first spot link to startState:
          int curTag = tags[pos][product % tagNum[pos]];
          //System.err.printf("pos=%d, product=%d, tag=%d score=%.3f\n",pos,product,curTag,windowScore[pos][product]);
          DFSATransition<String, Integer> tr = 
            new DFSATransition<String, Integer>("",startState,graphStates[pos][product],classIndex.get(curTag),"",-windowScore[pos][product]);
          startState.addTransition(tr);
        } else {
          int sharedProduct = product / tagNum[pos + rightWindow];
          int factor = productSizes[pos] / tagNum[pos + rightWindow];

          for (int newTagNum = 0; newTagNum < tagNum[pos - leftWindow - 1]; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            int predTag = tags[pos-1][predProduct % tagNum[pos-1]];
            int curTag = tags[pos][product % tagNum[pos]];
            //System.err.println("pos: "+pos);
            //System.err.println("product: "+product);
            //System.err.printf("pos=%d-%d, product=%d-%d, tag=%d-%d score=%.3f\n",pos-1,pos,predProduct,product,predTag,curTag,
            //  windowScore[pos][product]);
            DFSAState<String, Integer> sourceState = graphStates[pos-leftWindow][predTag];
            DFSAState<String, Integer> destState = (pos-leftWindow+1==graphStates.length) ? endState : graphStates[pos-leftWindow+1][curTag];
            DFSATransition<String, Integer> tr = 
              new DFSATransition<String, Integer>("",sourceState,destState,classIndex.get(curTag),"",-windowScore[pos][product]);
            graphStates[pos-leftWindow][predTag].addTransition(tr);
          }
        }
      }
    }
    return viterbiSearchGraph;
  }
}
