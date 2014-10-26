
/*
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-10-03
* 	@Last Modified:  2014-10-05
*/

public class Config
{
    public static int wordCutOff = 1;
    public static double initRange = 0.01;
    public static int maxIter = 20000;
    public static int batchSize = 10000;

    public static double adaEps = 1e-6;
    public static double adaAlpha = 0.01;
    public static double regParameter = 1e-8;
    public static double dropProb = 0.5;

    public static int hiddenSize = 200;
    public static int embeddingSize = 50;
    public static int numTokens = 48;

    public static int numPreComputed = 100000;
    public static int evalPerIter = 100;
}
