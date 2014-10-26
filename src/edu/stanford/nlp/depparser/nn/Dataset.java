
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-09-01
* 	@Last Modified:  2014-09-30
*/

package edu.stanford.nlp.depparser.nn;

import java.util.*;

class Example
{
	List<Integer> feature;
	List<Integer> label;

	public Example(List<Integer> feature, List<Integer> label)
	{
		this.feature = feature;
		this.label = label;
	}

	public List<Integer> getFeature()
	{
		return feature;
	}

	public List<Integer> getLabel()
	{
		return label;
	}
}

public class Dataset
{
	int n;
	int numFeatures, numLabels;
	List<Example> examples;

	Dataset(int numFeatures, int numLabels)
	{
		n = 0;
		this.numFeatures = numFeatures;
		this.numLabels = numLabels;
		examples = new ArrayList<Example>();
	}

	public void addExample(List<Integer> feature, List<Integer> label)
	{
		Example data = new Example(feature, label);
		n += 1;
		examples.add(data);
	}
}
