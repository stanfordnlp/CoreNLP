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

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Subclass of {@link edu.stanford.nlp.ie.crf.CRFClassifier} for implementing the nonlinear architecture in [Wang and Manning IJCNLP-2013 Effect of Nonlinear ...].
 *
 * @author Mengqiu Wang
 */
public class CRFClassifierNonlinear<IN extends CoreMap> extends CRFClassifier<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFClassifierNonlinear.class);

  /** Parameter weights of the classifier. */
  private double[][] linearWeights;
  private double[][] inputLayerWeights4Edge;
  private double[][] outputLayerWeights4Edge;
  private double[][] inputLayerWeights;
  private double[][] outputLayerWeights;

  protected CRFClassifierNonlinear() {
    super(new SeqClassifierFlags());
  }

  public CRFClassifierNonlinear(Properties props) {
    super(props);
  }

  public CRFClassifierNonlinear(SeqClassifierFlags flags) {
    super(flags);
  }

  @Override
  public Triple<int[][][], int[], double[][][]> documentToDataAndLabels(List<IN> document) {
    Triple<int[][][], int[], double[][][]> result = super.documentToDataAndLabels(document);
    int[][][] data = result.first();
    data = transformDocData(data);

    return new Triple<>(data, result.second(), result.third());
  }

  private int[][][] transformDocData(int[][][] docData) {
    int[][][] transData = new int[docData.length][][];
    for (int i = 0; i < docData.length; i++) {
      transData[i] = new int[docData[i].length][];
      for (int j = 0; j < docData[i].length; j++) {
        int[] cliqueFeatures = docData[i][j];
        transData[i][j] = new int[cliqueFeatures.length];
        for (int n = 0; n < cliqueFeatures.length; n++) {
          int transFeatureIndex = -1;
          if (j == 0) {
            transFeatureIndex = nodeFeatureIndicesMap.indexOf(cliqueFeatures[n]);
            if (transFeatureIndex == -1)
              throw new RuntimeException("node cliqueFeatures[n]="+cliqueFeatures[n]+" not found, nodeFeatureIndicesMap.size="+nodeFeatureIndicesMap.size());
          } else {
            transFeatureIndex = edgeFeatureIndicesMap.indexOf(cliqueFeatures[n]);
            if (transFeatureIndex == -1)
              throw new RuntimeException("edge cliqueFeatures[n]="+cliqueFeatures[n]+" not found, edgeFeatureIndicesMap.size="+edgeFeatureIndicesMap.size());
          }
          transData[i][j][n] = transFeatureIndex;
        }
      }
    }
    return transData;
  }

  @Override
  protected CliquePotentialFunction getCliquePotentialFunctionForTest() {
    if (cliquePotentialFunction == null) {
      if (flags.secondOrderNonLinear)
        cliquePotentialFunction = new NonLinearSecondOrderCliquePotentialFunction(inputLayerWeights4Edge, outputLayerWeights4Edge, inputLayerWeights, outputLayerWeights, flags);
      else
        cliquePotentialFunction = new NonLinearCliquePotentialFunction(linearWeights, inputLayerWeights, outputLayerWeights, flags);
    }
    return cliquePotentialFunction;
  }

  @Override
  protected double[] trainWeights(int[][][][] data, int[][] labels, Evaluator[] evaluators, int pruneFeatureItr, double[][][][] featureVals) {
    if (flags.secondOrderNonLinear) {
      CRFNonLinearSecondOrderLogConditionalObjectiveFunction func = new CRFNonLinearSecondOrderLogConditionalObjectiveFunction(data, labels,
        windowSize, classIndex, labelIndices, map, flags, nodeFeatureIndicesMap.size(), edgeFeatureIndicesMap.size());
      cliquePotentialFunctionHelper = func;
      double[] allWeights = trainWeightsUsingNonLinearCRF(func, evaluators);
      Quadruple<double[][], double[][], double[][], double[][]> params = func.separateWeights(allWeights);
      this.inputLayerWeights4Edge = params.first();
      this.outputLayerWeights4Edge = params.second();
      this.inputLayerWeights = params.third();
      this.outputLayerWeights = params.fourth();

    } else {
      CRFNonLinearLogConditionalObjectiveFunction func = new CRFNonLinearLogConditionalObjectiveFunction(data, labels,
        windowSize, classIndex, labelIndices, map, flags, nodeFeatureIndicesMap.size(), edgeFeatureIndicesMap.size(), featureVals);
      if (flags.useAdaGradFOBOS) {
        func.gradientsOnly = true;
      }
      cliquePotentialFunctionHelper = func;
      double[] allWeights = trainWeightsUsingNonLinearCRF(func, evaluators);
      Triple<double[][], double[][], double[][]> params = func.separateWeights(allWeights);
      this.linearWeights = params.first();
      this.inputLayerWeights = params.second();
      this.outputLayerWeights = params.third();
    }

    return null;
  }

  private double[] trainWeightsUsingNonLinearCRF(AbstractCachingDiffFunction func, Evaluator[] evaluators) {
    Minimizer<DiffFunction> minimizer = getMinimizer(0, evaluators);

    double[] initialWeights;
    if (flags.initialWeights == null) {
      initialWeights = func.initial();
    } else {
      log.info("Reading initial weights from file " + flags.initialWeights);
      try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(
            flags.initialWeights))))) {
        initialWeights = ConvertByteArray.readDoubleArr(dis);
      } catch (IOException e) {
        throw new RuntimeException("Could not read from double initial weight file " + flags.initialWeights);
      }
    }
    log.info("numWeights: " + initialWeights.length);

    if (flags.testObjFunction) {
      StochasticDiffFunctionTester tester = new StochasticDiffFunctionTester(func);
      if (tester.testSumOfBatches(initialWeights, 1e-4)) {
        log.info("Testing complete... exiting");
        System.exit(1);
      } else {
        log.info("Testing failed....exiting");
        System.exit(1);
      }

    }
    //check gradient
    if (flags.checkGradient) {
      if (func.gradientCheck()) {
        log.info("gradient check passed");
      } else {
        throw new RuntimeException("gradient check failed");
      }
    }
    return minimizer.minimize(func, flags.tolerance, initialWeights);
  }

  @Override
  protected void serializeTextClassifier(PrintWriter pw) throws Exception {
    super.serializeTextClassifier(pw);

    pw.printf("nodeFeatureIndicesMap.size()=\t%d%n", nodeFeatureIndicesMap.size());
    for (int i = 0; i < nodeFeatureIndicesMap.size(); i++) {
      pw.printf("%d\t%d%n", i, nodeFeatureIndicesMap.get(i));
    }

    pw.printf("edgeFeatureIndicesMap.size()=\t%d%n", edgeFeatureIndicesMap.size());
    for (int i = 0; i < edgeFeatureIndicesMap.size(); i++) {
      pw.printf("%d\t%d%n", i, edgeFeatureIndicesMap.get(i));
    }

    if (flags.secondOrderNonLinear) {
      pw.printf("inputLayerWeights4Edge.length=\t%d%n", inputLayerWeights4Edge.length);
      for (double[] ws : inputLayerWeights4Edge) {
        ArrayList<Double> list = new ArrayList<>();
        for (double w : ws) {
          list.add(w);
        }
        pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
      }
      pw.printf("outputLayerWeights4Edge.length=\t%d%n", outputLayerWeights4Edge.length);
      for (double[] ws : outputLayerWeights4Edge) {
        ArrayList<Double> list = new ArrayList<>();
        for (double w : ws) {
          list.add(w);
        }
        pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
      }
    } else {
      pw.printf("linearWeights.length=\t%d%n", linearWeights.length);
      for (double[] ws : linearWeights) {
        ArrayList<Double> list = new ArrayList<>();
        for (double w : ws) {
          list.add(w);
        }
        pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
      }
    }
    pw.printf("inputLayerWeights.length=\t%d%n", inputLayerWeights.length);
    for (double[] ws : inputLayerWeights) {
      ArrayList<Double> list = new ArrayList<>();
      for (double w : ws) {
        list.add(w);
      }
      pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
    }
    pw.printf("outputLayerWeights.length=\t%d%n", outputLayerWeights.length);
    for (double[] ws : outputLayerWeights) {
      ArrayList<Double> list = new ArrayList<>();
      for (double w : ws) {
        list.add(w);
      }
      pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
    }
  }

  @Override
  protected void loadTextClassifier(BufferedReader br) throws Exception {
    super.loadTextClassifier(br);

    String line = br.readLine();
    String[] toks = line.split("\\t");
    if (!toks[0].equals("nodeFeatureIndicesMap.size()=")) {
      throw new RuntimeException("format error in nodeFeatureIndicesMap");
    }
    int nodeFeatureIndicesMapSize = Integer.parseInt(toks[1]);
    nodeFeatureIndicesMap = new HashIndex<>();
    int count = 0;
    while (count < nodeFeatureIndicesMapSize) {
      line = br.readLine();
      toks = line.split("\\t");
      int idx = Integer.parseInt(toks[0]);
      if (count != idx) {
        throw new RuntimeException("format error");
      }
      nodeFeatureIndicesMap.add(Integer.parseInt(toks[1]));
      count++;
    }

    line = br.readLine();
    toks = line.split("\\t");
    if (!toks[0].equals("edgeFeatureIndicesMap.size()=")) {
      throw new RuntimeException("format error");
    }
    int edgeFeatureIndicesMapSize = Integer.parseInt(toks[1]);
    edgeFeatureIndicesMap = new HashIndex<>();
    count = 0;
    while (count < edgeFeatureIndicesMapSize) {
      line = br.readLine();
      toks = line.split("\\t");
      int idx = Integer.parseInt(toks[0]);
      if (count != idx) {
        throw new RuntimeException("format error");
      }
      edgeFeatureIndicesMap.add(Integer.parseInt(toks[1]));
      count++;
    }

    int  weightsLength = -1;
    if (flags.secondOrderNonLinear) {
      line = br.readLine();
      toks = line.split("\\t");
      if (!toks[0].equals("inputLayerWeights4Edge.length=")) {
        throw new RuntimeException("format error");
      }
      weightsLength = Integer.parseInt(toks[1]);
      inputLayerWeights4Edge = new double[weightsLength][];
      count = 0;
      while (count < weightsLength) {
        line = br.readLine();

        toks = line.split("\\t");
        int weights2Length = Integer.parseInt(toks[0]);
        inputLayerWeights4Edge[count] = new double[weights2Length];
        String[] weightsValue = toks[1].split(" ");
        if (weights2Length != weightsValue.length) {
          throw new RuntimeException("weights format error");
        }

        for (int i2 = 0; i2 < weights2Length; i2++) {
          inputLayerWeights4Edge[count][i2] = Double.parseDouble(weightsValue[i2]);
        }
        count++;
      }
      line = br.readLine();

      toks = line.split("\\t");
      if (!toks[0].equals("outputLayerWeights4Edge.length=")) {
        throw new RuntimeException("format error");
      }
      weightsLength = Integer.parseInt(toks[1]);
      outputLayerWeights4Edge = new double[weightsLength][];
      count = 0;
      while (count < weightsLength) {
        line = br.readLine();

        toks = line.split("\\t");
        int weights2Length = Integer.parseInt(toks[0]);
        outputLayerWeights4Edge[count] = new double[weights2Length];
        String[] weightsValue = toks[1].split(" ");
        if (weights2Length != weightsValue.length) {
          throw new RuntimeException("weights format error");
        }

        for (int i2 = 0; i2 < weights2Length; i2++) {
          outputLayerWeights4Edge[count][i2] = Double.parseDouble(weightsValue[i2]);
        }
        count++;
      }
    } else {
      line = br.readLine();

      toks = line.split("\\t");
      if (!toks[0].equals("linearWeights.length=")) {
        throw new RuntimeException("format error");
      }
      weightsLength = Integer.parseInt(toks[1]);
      linearWeights = new double[weightsLength][];
      count = 0;
      while (count < weightsLength) {
        line = br.readLine();

        toks = line.split("\\t");
        int weights2Length = Integer.parseInt(toks[0]);
        linearWeights[count] = new double[weights2Length];
        String[] weightsValue = toks[1].split(" ");
        if (weights2Length != weightsValue.length) {
          throw new RuntimeException("weights format error");
        }

        for (int i2 = 0; i2 < weights2Length; i2++) {
          linearWeights[count][i2] = Double.parseDouble(weightsValue[i2]);
        }
        count++;
      }
    }

    line = br.readLine();

    toks = line.split("\\t");
    if (!toks[0].equals("inputLayerWeights.length=")) {
      throw new RuntimeException("format error");
    }
    weightsLength = Integer.parseInt(toks[1]);
    inputLayerWeights = new double[weightsLength][];
    count = 0;
    while (count < weightsLength) {
      line = br.readLine();

      toks = line.split("\\t");
      int weights2Length = Integer.parseInt(toks[0]);
      inputLayerWeights[count] = new double[weights2Length];
      String[] weightsValue = toks[1].split(" ");
      if (weights2Length != weightsValue.length) {
        throw new RuntimeException("weights format error");
      }

      for (int i2 = 0; i2 < weights2Length; i2++) {
        inputLayerWeights[count][i2] = Double.parseDouble(weightsValue[i2]);
      }
      count++;
    }
    line = br.readLine();

    toks = line.split("\\t");
    if (!toks[0].equals("outputLayerWeights.length=")) {
      throw new RuntimeException("format error");
    }
    weightsLength = Integer.parseInt(toks[1]);
    outputLayerWeights = new double[weightsLength][];
    count = 0;
    while (count < weightsLength) {
      line = br.readLine();

      toks = line.split("\\t");
      int weights2Length = Integer.parseInt(toks[0]);
      outputLayerWeights[count] = new double[weights2Length];
      String[] weightsValue = toks[1].split(" ");
      if (weights2Length != weightsValue.length) {
        throw new RuntimeException("weights format error");
      }

      for (int i2 = 0; i2 < weights2Length; i2++) {
        outputLayerWeights[count][i2] = Double.parseDouble(weightsValue[i2]);
      }
      count++;
    }
  }

  @Override
  public void serializeClassifier(ObjectOutputStream oos) {
    try {
      super.serializeClassifier(oos);
      oos.writeObject(nodeFeatureIndicesMap);
      oos.writeObject(edgeFeatureIndicesMap);
      if (flags.secondOrderNonLinear) {
        oos.writeObject(inputLayerWeights4Edge);
        oos.writeObject(outputLayerWeights4Edge);
      } else {
        oos.writeObject(linearWeights);
      }
      oos.writeObject(inputLayerWeights);
      oos.writeObject(outputLayerWeights);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  @SuppressWarnings( { "unchecked" })
  // can't have right types in deserialization
  public void loadClassifier(ObjectInputStream ois, Properties props) throws ClassCastException, IOException,
      ClassNotFoundException {

    super.loadClassifier(ois, props);

    nodeFeatureIndicesMap = (Index<Integer>) ois.readObject();
    edgeFeatureIndicesMap = (Index<Integer>) ois.readObject();
    if (flags.secondOrderNonLinear) {
      inputLayerWeights4Edge = (double[][]) ois.readObject();
      outputLayerWeights4Edge = (double[][]) ois.readObject();
    } else {
      linearWeights = (double[][]) ois.readObject();
    }
    inputLayerWeights = (double[][]) ois.readObject();
    outputLayerWeights = (double[][]) ois.readObject();
  }

} // end class CRFClassifierNonlinear
