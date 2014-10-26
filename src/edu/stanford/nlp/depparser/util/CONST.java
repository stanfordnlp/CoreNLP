
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-09-30
*/

package edu.stanford.nlp.depparser.util;

public class CONST 
{
	//TODO: write everything in a prop file, and call -prop
	//TODO: change all variables to CAPS

	public static final String UNKNOWN = "-UNKNOWN-";
	public static final String ROOT = "-ROOT-";
	public static final String NULL = "-NULL-";
	public static final int NONEXIST = -1;
	public static final String SEPARATOR = "###################";

	public static final int wordCutOff = 1;
	public static final double initRange = 0.01;
	public static final int maxIter = 100000;
	public static final int batchSize = 1000;

	public static final double adaEps = 1e-6;
	public static final double adaAlpha = 0.01;
	public static final double regParameter = 1e-8;
	public static final double dropProb = 0.5;

	public static int hiddenSize = 200;
	public static int embeddingSize = 50;
	public static int numTokens = 48;

	public static int numPreComputed = 100000;
	public static int evalPerIter = 100;
}
