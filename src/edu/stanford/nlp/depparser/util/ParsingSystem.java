
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-31
* 	@Last Modified:  2014-09-01
*/

package edu.stanford.nlp.depparser.util;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class ParsingSystem
{

	public String rootLabel;
	public List<String> labels, transitions;

	abstract void makeTransitions();
	public abstract boolean canApply(Configuration c, String t);
	//abstract boolean canApplyWithDictionary(Configuration c, String t);
	public abstract void apply(Configuration c, String t);
	public abstract String getOracle(Configuration c, DependencyTree dTree);
	abstract boolean isOracle(Configuration c, String t, DependencyTree dTree);
	public abstract Configuration initialConfiguration(CoreMap sentence);
	abstract boolean isTerminal(Configuration c);

  private static final Pattern pPunct = Pattern.compile("``|''|[,.:]");

	public ParsingSystem(List<String> labels)
	{
		this.labels = new ArrayList<String>(labels);

		//NOTE: assume that the first element of labels is rootLabel
		rootLabel = labels.get(0);
		makeTransitions();

		System.out.println(CONST.SEPARATOR);
		System.out.println("#Transitions: " + transitions.size());
		System.out.println("#Labels: " + labels.size());
		System.out.println("ROOTLABEL: " + rootLabel);
	}

	public int getTransitionID(String s)
	{
		for (int k = 0; k < transitions.size(); ++ k)
			if (transitions.get(k).equals(s))
				return k;
		return -1;
	}

	public Map<String, Double> evaluate(List<CoreMap> sentences, List<DependencyTree> trees, List<DependencyTree> goldTrees)
	{
		Map<String, Double> result = new HashMap<String, Double>();

		if (trees.size() != goldTrees.size())
		{
			System.out.println("[Error] Incorrect number of trees.");
			return null;
		}
		
		int correctArcs = 0;
		int correctArcsWoPunc = 0;
		int correctHeads = 0;
		int correctHeadsWoPunc = 0;
	
		int correctTrees = 0;
		int correctTreesWoPunc = 0;
		int correctRoot = 0;

		int sumArcs = 0;
		int sumArcsWoPunc = 0;

		for (int i = 0; i < trees.size(); ++ i)
		{
      List<CoreLabel> tokens = sentences.get(i).get(CoreAnnotations.TokensAnnotation.class);

			if (trees.get(i).n != goldTrees.get(i).n)
			{
				System.out.println("[Error] Tree " + (i + 1) + ": incorrect number of nodes.");
				return null;
			}
			if (!trees.get(i).isTree())
			{
				System.out.println("[Error] Tree " + (i + 1) + ": illegal.");
				return null;
			}

			int nCorrectHead = 0;
			int nCorrectHeadwoPunc = 0;
			int nonPunc = 0;

			for (int j = 1; j <= trees.get(i).n; ++ j)
			{
				if (trees.get(i).getHead(j) == goldTrees.get(i).getHead(j))
				{
					++ correctHeads;
					++ nCorrectHead;
					if (trees.get(i).getLabel(j).equals(goldTrees.get(i).getLabel(j))) 
						++ correctArcs;
				}
				++ sumArcs;
				if (!pPunct.matcher(tokens.get(j).tag()).matches())
				{
					++ sumArcsWoPunc;
					++ nonPunc;
					if (trees.get(i).getHead(j) == goldTrees.get(i).getHead(j))
					{
						++ correctHeadsWoPunc;
						++ nCorrectHeadwoPunc;
						if (trees.get(i).getLabel(j).equals(goldTrees.get(i).getLabel(j))) 
							++ correctArcsWoPunc;
					}
				}
			}
			if (nCorrectHead == trees.get(i).n) 
				++ correctTrees;
			if (nCorrectHeadwoPunc == nonPunc) 
				++ correctTreesWoPunc;
			if (trees.get(i).getRoot() == goldTrees.get(i).getRoot())
				++ correctRoot;
		}

		result.put("UAS", correctHeads * 100.0 / sumArcs);
		result.put("UASwoPunc", correctHeadsWoPunc * 100.0 / sumArcsWoPunc);
		result.put("LAS", correctArcs * 100.0 / sumArcs);
		result.put("LASwoPunc", correctArcsWoPunc * 100.0 / sumArcsWoPunc);;
		result.put("UEM", correctTrees * 100.0 / trees.size());
		result.put("UEMwoPunc", correctTreesWoPunc * 100.0 / trees.size());
		result.put("ROOT", correctRoot * 100.0 / trees.size());;

		return result;
	}

	public double getUASScore(List<CoreMap> sentences, List<DependencyTree> trees, List<DependencyTree> goldTrees)
	{
		Map<String, Double> result = evaluate(sentences, trees, goldTrees);
		return result == null || !result.containsKey("UASwoPunc") ? -1.0 : result.get("UASwoPunc");
	}
}
