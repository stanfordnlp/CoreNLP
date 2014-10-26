
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-09-01
*/

package edu.stanford.nlp.depparser.util;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;
import java.io.*;

public class Util 
{
	
	// return strings sorted by frequency, and filter out those with freq. less than cutOff.
	public static List<String> generateDict(List<String> str, int cutOff)
	{
		StrCounter freq = new StrCounter();
		for (int i = 0; i < str.size(); ++ i) 
			freq.add(str.get(i));
		
		List<String> keys = freq.getSortedKeys();
		List<String> dict = new ArrayList<String>();
		for (int i = 0; i < keys.size(); ++ i)
		{
			String s = keys.get(i);
			if (freq.getCount(s) >= cutOff) dict.add(s);
		}
		return dict;
	}

	public static List<String> generateDict(List<String> str)
	{
		return generateDict(str, 1);
	}

	public static <T> List<T> getRandomSubList(List<T> input, int subsetSize)
	{
	    Random r = new Random();
	    int inputSize = input.size();
	    if (subsetSize > inputSize)
	    	subsetSize = inputSize;

	    for (int i = 0; i < subsetSize; i++)
	    {
	        int indexToSwap = i + r.nextInt(inputSize - i);
	        T temp = input.get(i);
	        input.set(i, input.get(indexToSwap));
	        input.set(indexToSwap, temp);
	    }
	    return input.subList(0, subsetSize);
	}

  // TODO replace with GrammaticalStructure#readCoNLLGrammaticalStructureCollection
	public static void loadConllFile(String inFile, List<CoreMap> sents, List<DependencyTree> trees, boolean labeled)
	{
    CoreLabelTokenFactory tf = new CoreLabelTokenFactory(false);

		try
		{
      BufferedReader reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem(inFile);

      CoreMap sentence = new CoreLabel();
      List<CoreLabel> sentenceTokens = new ArrayList<>();
      sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);

			DependencyTree tree = new DependencyTree();

			for (String line : IOUtils.getLineIterable(reader, false))
			{
				String[] splits = line.split("\t");
				if (splits.length < 10)
				{
					trees.add(tree);
					sents.add(sentence);
					tree = new DependencyTree();

					sentence = new CoreLabel();
				} else {
          String word = splits[1],
              pos = splits[4],
              depType = splits[7];
          int head = Integer.parseInt(splits[6]);

          CoreLabel token = tf.makeToken(word, 0, 0);
          token.setTag(pos);
          token.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, head);
          token.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, depType);

					if (labeled)
						tree.add(head, depType);
					else
						tree.add(head, CONST.UNKNOWN);
				}
			}
		}
		catch (Exception e) { System.out.println(e); };
	}

	public static void loadConllFile(String inFile, List<CoreMap> sents, List<DependencyTree> trees)
	{
		loadConllFile(inFile, sents, trees, true);
	}

    public static void writeConllFile(String outFile, List<CoreMap> sentences, List<DependencyTree> trees)
    {
        try
        {
          PrintWriter output = IOUtils.getPrintWriter(outFile);
            for (CoreMap sentence : sentences)
            {
              List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

                for (int j = 1; j <= tokens.size(); ++ j)
                {
                  CoreLabel token = tokens.get(j - 1);
                  output.printf("%d\t%s\t_\t%s\t%s\t_\t%d\t%s\t_\t_%n",
                      j, token.word(), token.tag(), token.tag(),
                      token.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class),
                      token.get(CoreAnnotations.CoNLLDepTypeAnnotation.class));
                }
                output.write("\n");
            }
            output.close();
        }
        catch (Exception e) { System.out.println(e); }
    }

	public static void printTreeStats(String str, List<DependencyTree> trees)
	{
		System.out.println(CONST.SEPARATOR + " " + str);
		System.out.println("#Trees: " + trees.size());
		int nonTrees = 0;
		int nonProjective = 0;
		for (int k = 0; k < trees.size(); ++ k)
		{
			if (!trees.get(k).isTree())
				++ nonTrees;
			else if (!trees.get(k).isProjective())
				++ nonProjective;
		}
		System.out.println(nonTrees + " tree(s) are illegal.");
		System.out.println(nonProjective + " tree(s) are legal but not projective.");
	}

	public static void printTreeStats(List<DependencyTree> trees)
	{
		printTreeStats("", trees);
	}
}
