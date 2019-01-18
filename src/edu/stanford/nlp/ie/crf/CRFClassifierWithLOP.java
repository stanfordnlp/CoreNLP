// CRFClassifier -- a probabilistic (CRF) sequence model, mainly used for NER.
// Copyright (c) 2002-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
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

package edu.stanford.nlp.ie.crf;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Subclass of {@link edu.stanford.nlp.ie.crf.CRFClassifier} for learning Logarithmic Opinion Pools.

 * @author Mengqiu Wang
 */
public class CRFClassifierWithLOP<IN extends CoreMap> extends CRFClassifier<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFClassifierWithLOP.class);

  private List<Set<Integer>> featureIndicesSetArray;
  private List<List<Integer>> featureIndicesListArray;

  protected CRFClassifierWithLOP() {
    super(new SeqClassifierFlags());
  }

  public CRFClassifierWithLOP(Properties props) {
    super(props);
  }

  public CRFClassifierWithLOP(SeqClassifierFlags flags) {
    super(flags);
  }

  private int[][][][] createPartialDataForLOP(int lopIter, int[][][][] data) {
    ArrayList<Integer> newFeatureList = new ArrayList<>(1000);
    Set<Integer> featureIndicesSet = featureIndicesSetArray.get(lopIter);

    int[][][][] newData = new int[data.length][][][];
    for (int i = 0; i < data.length; i++) {
      newData[i] = new int[data[i].length][][];
      for (int j = 0; j < data[i].length; j++) {
        newData[i][j] = new int[data[i][j].length][];
        for (int k = 0; k < data[i][j].length; k++) {
          int[] oldFeatures = data[i][j][k];
          newFeatureList.clear();
          for (int oldFeatureIndex : oldFeatures) {
            if (featureIndicesSet.contains(oldFeatureIndex)) {
              newFeatureList.add(oldFeatureIndex);
            }
          }
          newData[i][j][k] = new int[newFeatureList.size()];
          for (int l = 0; l < newFeatureList.size(); ++l) {
            newData[i][j][k][l] = newFeatureList.get(l);
          }
        }
      }
    }

    return newData;
  }

  private void getFeatureBoundaryIndices(int numFeatures, int numLopExpert) {
    // first find begin/end feature index for each expert
    int interval = numFeatures / numLopExpert;
    featureIndicesSetArray = new ArrayList<>(numLopExpert);
    featureIndicesListArray = new ArrayList<>(numLopExpert);
    for (int i = 0; i < numLopExpert; i++) {
      featureIndicesSetArray.add(Generics.<Integer>newHashSet(interval));
      featureIndicesListArray.add(Generics.<Integer>newArrayList(interval));
    }
    if (flags.randomLopFeatureSplit) {
      for (int fIndex = 0; fIndex < numFeatures; fIndex++) {
        int lopIter = random.nextInt(numLopExpert);
        featureIndicesSetArray.get(lopIter).add(fIndex);
        featureIndicesListArray.get(lopIter).add(fIndex);
      }
    } else {
      for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
        int beginIndex = lopIter * interval;
        int endIndex = (lopIter+1) * interval;
        if (lopIter == numLopExpert - 1) {
          endIndex = numFeatures;
        }
        for (int fIndex = beginIndex; fIndex < endIndex; fIndex++) {
          featureIndicesSetArray.get(lopIter).add(fIndex);
          featureIndicesListArray.get(lopIter).add(fIndex);
        }
      }
    }
    for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
      Collections.sort(featureIndicesListArray.get(lopIter));
    }
  }

  @Override
  protected double[] trainWeights(int[][][][] data, int[][] labels, Evaluator[] evaluators, int pruneFeatureItr, double[][][][] featureVals) {
    int numFeatures = featureIndex.size();
    int numLopExpert = flags.numLopExpert;
    double[][] lopExpertWeights = new double[numLopExpert][];

    getFeatureBoundaryIndices(numFeatures, numLopExpert);

    if (flags.initialLopWeights != null) {
      try (BufferedReader br = IOUtils.readerFromString(flags.initialLopWeights)) {
        log.info("Reading initial LOP weights from file " + flags.initialLopWeights + " ...");
        List<double[]> listOfWeights = new ArrayList<>(numLopExpert);
        for (String line; (line = br.readLine()) != null; ) {
          line = line.trim();
          String[] parts = line.split("\t");
          double[] wArr = new double[parts.length];
          for (int i = 0; i < parts.length; i++) {
            wArr[i] = Double.parseDouble(parts[i]);
          }
          listOfWeights.add(wArr);
        }
        assert(listOfWeights.size() == numLopExpert);
        log.info("Done!");
        for (int i = 0; i < numLopExpert; i++)
          lopExpertWeights[i] = listOfWeights.get(i);
        // DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(
        //     flags.initialLopWeights))));
        // initialScales = Convert.readDoubleArr(dis);
      } catch (IOException e) {
        throw new RuntimeException("Could not read from double initial LOP weights file " + flags.initialLopWeights);
      }
    } else {
      for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
        int[][][][] partialData = createPartialDataForLOP(lopIter, data);
        if (flags.randomLopWeights) {
          lopExpertWeights[lopIter] = super.getObjectiveFunction(partialData, labels).initial();
        } else {
          lopExpertWeights[lopIter] = super.trainWeights(partialData, labels, evaluators, pruneFeatureItr, null);
        }
      }
      if (flags.includeFullCRFInLOP) {
        double[][] newLopExpertWeights = new double[numLopExpert+1][];
        System.arraycopy(lopExpertWeights, 0, newLopExpertWeights, 0, lopExpertWeights.length);
        if (flags.randomLopWeights) {
          newLopExpertWeights[numLopExpert] = super.getObjectiveFunction(data, labels).initial();
        } else {
          newLopExpertWeights[numLopExpert] = super.trainWeights(data, labels, evaluators, pruneFeatureItr, null);
        }

        Set<Integer> newSet = Generics.newHashSet(numFeatures);
        List<Integer> newList = new ArrayList<>(numFeatures);
        for (int fIndex = 0; fIndex < numFeatures; fIndex++) {
          newSet.add(fIndex);
          newList.add(fIndex);
        }
        featureIndicesSetArray.add(newSet);
        featureIndicesListArray.add(newList);

        numLopExpert += 1;
        lopExpertWeights = newLopExpertWeights;
      }
    }

    // Dumb scales
    // double[] lopScales = new double[numLopExpert];
    // Arrays.fill(lopScales, 1.0);
    CRFLogConditionalObjectiveFunctionForLOP func = new CRFLogConditionalObjectiveFunctionForLOP(data, labels, lopExpertWeights,
        windowSize, classIndex, labelIndices, map, flags.backgroundSymbol, numLopExpert, featureIndicesSetArray, featureIndicesListArray,
        flags.backpropLopTraining);
    cliquePotentialFunctionHelper = func;

    Minimizer<DiffFunction> minimizer = getMinimizer(0, evaluators);

    double[] initialScales;
    //TODO(mengqiu) clean this part up when backpropLogTraining == true
    if (flags.initialLopScales == null) {
      initialScales = func.initial();
    } else {
      log.info("Reading initial LOP scales from file " + flags.initialLopScales);
      try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(
            flags.initialLopScales))))) {
        initialScales = ConvertByteArray.readDoubleArr(dis);
      } catch (IOException e) {
        throw new RuntimeException("Could not read from double initial LOP scales file " + flags.initialLopScales);
      }
    }

    double[] learnedParams = minimizer.minimize(func, flags.tolerance, initialScales);
    double[] rawScales = func.separateLopScales(learnedParams);
    double[] lopScales = ArrayMath.softmax(rawScales);
    log.info("After SoftMax Transformation, learned scales are:");
    for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
      log.info("lopScales[" + lopIter + "] = " + lopScales[lopIter]);
    }
    double[][] learnedLopExpertWeights = lopExpertWeights;
    if (flags.backpropLopTraining) {
      learnedLopExpertWeights = func.separateLopExpertWeights(learnedParams);
    }
    return CRFLogConditionalObjectiveFunctionForLOP.combineAndScaleLopWeights(numLopExpert, learnedLopExpertWeights, lopScales);
  }

} // end class CRFClassifierWithLOP
