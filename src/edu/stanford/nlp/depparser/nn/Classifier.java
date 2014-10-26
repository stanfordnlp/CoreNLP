
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-10-05
*/

package edu.stanford.nlp.depparser.nn;

import edu.stanford.nlp.depparser.util.Util;

import java.util.*;

public class Classifier 
{
    // E: numFeatures x embeddingSize
    // W1: hiddenSize x (embeddingSize x numFeatures)
    // b1: hiddenSize
    // W2: numLabels x hiddenSize

	double[][] W1, W2, E;
	double[] b1;

	double[][] eg2W1, eg2W2, eg2E;
	double[] eg2b1;

	double[][] gradW1, gradW2, gradE;
	double[] gradb1;

	double[][] saved, gradSaved;

	Dataset dataset;
	Map<Integer, Integer> preMap;

	Random random;

    private int embeddingSize, hiddenSize;
    private int numTokens, numLabels, numPreComputed;

	public Classifier(Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2)
	{
		this(dataset, E, W1, b1, W2, new ArrayList<Integer>());
	}

    public Classifier(double[][] E, double[][] W1, double[] b1, double[][] W2, List<Integer> preComputed)
    {
        this(null, E, W1, b1, W2, preComputed);
    }

	public Classifier(Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2, List<Integer> preComputed)
	{
		this.dataset = dataset;

		this.E = E;
		this.W1 = W1;
		this.b1 = b1;
		this.W2 = W2;

        embeddingSize = E[0].length;
        hiddenSize = W1.length;
        numTokens = W1[0].length / embeddingSize;
        numLabels = W2.length;
		
		eg2E = new double[E.length][E[0].length];
		eg2W1 = new double[W1.length][W1[0].length];
		eg2b1 = new double[b1.length];
		eg2W2 = new double[W2.length][W2[0].length];
		
		preMap = new HashMap<Integer, Integer>();
		numPreComputed = preComputed.size();
        for (int i = 0; i < preComputed.size(); ++ i)
                preMap.put(preComputed.get(i), i);

        random = new Random();
	}

    public double[][] getW1()
    {
        return W1;
    }

    public double[] getb1()
    {
        return b1;
    }

    public double[][] getW2()
    {
        return W2;
    }

    public double[][] getE()
    {
        return E;
    }

  public double computeCostFunction(int batchSize, double regParameter, double dropOutProb) {
    gradW1 = new double[W1.length][W1[0].length];
    gradb1 = new double[b1.length];
    gradW2 = new double[W2.length][W2[0].length];
    gradE = new double[E.length][E[0].length];
    gradSaved = new double[numPreComputed][hiddenSize];

    List<Example> examples = Util.getRandomSubList(dataset.examples, batchSize);
    preCompute();

    double cost = 0.0;
    double correct = 0.0;
    for (int ex = 0; ex < examples.size(); ++ex) {
      List<Integer> feature = examples.get(ex).getFeature();
      List<Integer> label = examples.get(ex).getLabel();

      double[] scores = new double[numLabels];
      double[] hidden = new double[hiddenSize];
      double[] hidden3 = new double[hiddenSize];

      List<Integer> unDropped = new ArrayList<Integer>();
      int numH = 0;
      for (int i = 0; i < hiddenSize; ++i)
        if (random.nextDouble() > dropOutProb) {
          numH += 1;
          unDropped.add(i);
        }
      int[] ls = new int[numH];
      for (int i = 0; i < numH; ++i)
        ls[i] = unDropped.get(i);

      int offset = 0;
      for (int j = 0; j < numTokens; ++j) {
        int tok = feature.get(j);
        int index = tok * numTokens + j;
        if (preMap.containsKey(index)) {
          int id = preMap.get(index);
          for (int li = 0; li < numH; ++li)
            hidden[ls[li]] += saved[id][ls[li]];
        } else {
          for (int li = 0; li < numH; ++li) {
            int i = ls[li];
            for (int k = 0; k < embeddingSize; ++k)
              hidden[i] += W1[i][offset + k] * E[tok][k];
          }
        }
        offset += embeddingSize;
      }
      for (int li = 0; li < numH; ++li) {
        int i = ls[li];
        hidden[i] += b1[i];
        hidden3[i] = hidden[i] * hidden[i] * hidden[i];
      }
      int optLabel = -1;
      for (int i = 0; i < numLabels; ++i)
        if (label.get(i) >= 0) {
          for (int j = 0; j < numH; ++j)
            scores[i] += W2[i][ls[j]] * hidden3[ls[j]];
          if (optLabel < 0 || scores[i] > scores[optLabel])
            optLabel = i;
        }

      double sum1 = 0.0;
      double sum2 = 0.0;
      double maxScore = scores[optLabel];
      for (int i = 0; i < numLabels; ++i)
        if (label.get(i) >= 0) {
          scores[i] = Math.exp(scores[i] - maxScore);
          if (label.get(i) == 1) sum1 += scores[i];
          sum2 += scores[i];
        }

      cost += (Math.log(sum2) - Math.log(sum1)) / examples.size();
      if (label.get(optLabel) == 1)
        correct += +1.0 / examples.size();

      double[] gradHidden3 = new double[hiddenSize];
      for (int i = 0; i < numLabels; ++i)
        if (label.get(i) >= 0) {
          double delta = -(label.get(i) - scores[i] / sum2) / examples.size();
          for (int lj = 0; lj < numH; ++lj) {
            int j = ls[lj];
            gradW2[i][j] += delta * hidden3[j];
            gradHidden3[j] += delta * W2[i][j];
          }
        }

      double[] gradHidden = new double[hiddenSize];
      for (int li = 0; li < numH; ++li) {
        int i = ls[li];
        gradHidden[i] = gradHidden3[i] * 3 * hidden[i] * hidden[i];
        gradb1[i] += gradHidden3[i];
      }

      offset = 0;
      for (int j = 0; j < numTokens; ++j) {
        int tok = feature.get(j);
        int index = tok * numTokens + j;
        if (preMap.containsKey(index)) {
          int id = preMap.get(index);
          for (int li = 0; li < numH; ++li)
            gradSaved[id][ls[li]] += gradHidden[ls[li]];
        } else {
          for (int li = 0; li < numH; ++li) {
            int i = ls[li];
            for (int k = 0; k < embeddingSize; ++k) {
              gradW1[i][offset + k] += gradHidden[i] * E[tok][k];
              gradE[tok][k] += gradHidden[i] * W1[i][offset + k];
            }
          }
        }
        offset += embeddingSize;
      }
    }

    for (int x : preMap.keySet()) {
      int mapX = preMap.get(x);
      int tok = x / numTokens;
      int offset = (x % numTokens) * embeddingSize;
      for (int j = 0; j < hiddenSize; ++j) {
        double delta = gradSaved[mapX][j];
        for (int k = 0; k < embeddingSize; ++k) {
          gradW1[j][offset + k] += delta * E[tok][k];
          gradE[tok][k] += delta * W1[j][offset + k];
        }
      }
    }

    for (int i = 0; i < W1.length; ++i)
      for (int j = 0; j < W1[i].length; ++j) {
        cost += regParameter * W1[i][j] * W1[i][j] / 2.0;
        gradW1[i][j] += regParameter * W1[i][j];
      }

    for (int i = 0; i < b1.length; ++i) {
      cost += regParameter * b1[i] * b1[i] / 2.0;
      gradb1[i] += regParameter * b1[i];
    }

    for (int i = 0; i < W2.length; ++i)
      for (int j = 0; j < W2[i].length; ++j) {
        cost += regParameter * W2[i][j] * W2[i][j] / 2.0;
        gradW2[i][j] += regParameter * W2[i][j];
      }

    for (int i = 0; i < E.length; ++i)
      for (int j = 0; j < E[i].length; ++j) {
        cost += regParameter * E[i][j] * E[i][j] / 2.0;
        gradE[i][j] += regParameter * E[i][j];
      }
    System.out.println("Cost = " + cost + ", Correct(%) = " + correct);
    return cost;
  }

	public void takeAdaGradientStep(double adaAlpha, double adaEps)
	{
		for (int i = 0; i < W1.length; ++ i)
			for (int j = 0; j < W1[i].length; ++ j)
			{
				eg2W1[i][j] += gradW1[i][j] * gradW1[i][j];
				W1[i][j] -= adaAlpha * gradW1[i][j] / Math.sqrt(eg2W1[i][j] + adaEps);
			}

		for (int i = 0; i < b1.length; ++ i)
		{
			eg2b1[i] += gradb1[i] * gradb1[i];
			b1[i] -= adaAlpha * gradb1[i] / Math.sqrt(eg2b1[i] + adaEps);
		}

		for (int i = 0; i < W2.length; ++ i)
			for (int j = 0; j < W2[i].length; ++ j)
			{
				eg2W2[i][j] += gradW2[i][j] * gradW2[i][j];
				W2[i][j] -= adaAlpha * gradW2[i][j] / Math.sqrt(eg2W2[i][j] + adaEps);
			}

		for (int i = 0; i < E.length; ++ i)
			for (int j = 0; j < E[i].length; ++ j)
			{
				eg2E[i][j] += gradE[i][j] * gradE[i][j];
				E[i][j] -= adaAlpha * gradE[i][j] / Math.sqrt(eg2E[i][j] + adaEps);
			}
	}

	public void preCompute()
	{
		long startTime = System.currentTimeMillis();
		saved = new double[numPreComputed][hiddenSize];
        for (int x: preMap.keySet())
        {
            int mapX = preMap.get(x);
            int tok = x / numTokens;
            int pos = x % numTokens;
            for (int j = 0; j < hiddenSize; ++ j)
                for (int k = 0; k < embeddingSize; ++ k)
                    saved[mapX][j] += W1[j][pos * embeddingSize + k] * E[tok][k];
        }
		System.out.println("PreComputed " + numPreComputed + ", Elapsed Time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " (s)");
	}

	public double[] computeScores(List<Integer> feature)
	{
		double[] scores = new double[numLabels];
		double[] hidden = new double[hiddenSize];
		int offset = 0;
        for (int j = 0; j < numTokens; ++ j)
        {
            int tok = feature.get(j);
            int index = tok * numTokens + j;
            if (preMap.containsKey(index))
            {
                int id = preMap.get(index);
                for (int i = 0; i < hiddenSize; ++ i)
                    hidden[i] += saved[id][i];
            }
            else
            {
                for (int i = 0; i < hiddenSize; ++ i)
                    for (int k = 0; k < embeddingSize; ++ k)
                        hidden[i] += W1[i][offset + k] * E[tok][k];
            }
            offset += embeddingSize;
        }

        for (int i = 0; i < hiddenSize; ++ i)
        {
            hidden[i] += b1[i];
            hidden[i] = hidden[i] * hidden[i] * hidden[i];
        }

		for (int i = 0; i < numLabels; ++ i)
			for (int j = 0; j < hiddenSize; ++ j)
				scores[i] += W2[i][j] * hidden[j];
		return scores;
	}
}
