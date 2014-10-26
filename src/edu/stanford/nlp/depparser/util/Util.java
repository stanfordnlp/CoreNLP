
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-09-01
*/

package edu.stanford.nlp.depparser.util;

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

	public static void loadConllFile(String inFile, List<Sentence> sents, List<DependencyTree> trees, boolean labeled)
	{
		try
		{
			BufferedReader input = new BufferedReader(new FileReader(inFile));
			String s;
			Sentence sent = new Sentence();
			DependencyTree tree = new DependencyTree();

			while ((s = input.readLine()) != null)
			{
				String[] splits = s.split("\t");
				if (splits.length < 10)
				{
					trees.add(tree); 
					sents.add(sent);
					tree = new DependencyTree();	
					sent = new Sentence();
				} else
				{
					sent.add(splits[1], splits[4]);
					if (labeled)
						tree.add(Integer.parseInt(splits[6]), splits[7]);
					else
						tree.add(Integer.parseInt(splits[6]), CONST.UNKNOWN);
				}
			}
		}
		catch (Exception e) { System.out.println(e); };
	}

	public static void loadConllFile(String inFile, List<Sentence> sents, List<DependencyTree> trees)
	{
		loadConllFile(inFile, sents, trees, true);
	}

    public static void writeConllFile(String outFile, List<Sentence> sentences, List<DependencyTree> trees)
    {
        try
        {
            BufferedWriter output = new BufferedWriter(new FileWriter(outFile));
            for (int i = 0; i < sentences.size(); ++ i)
            {
                for (int j = 1; j <= sentences.get(i).n; ++ j)
                {
                    String w = sentences.get(i).getWord(j);
                    output.write(j + "\t" + w + "\t_\t" + sentences.get(i).getPOS(j) + "\t" + sentences.get(i).getPOS(j)
                            + "\t_\t" + trees.get(i).getHead(j) + "\t" + trees.get(i).getLabel(j) + "\t_\t_\n");
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
