
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

import java.util.*;

public class Configuration
{

	public List<Integer> stack;
	public List<Integer> buffer;
	
	public DependencyTree tree;
	public final CoreMap sentence;

	public Configuration(Configuration config)
	{
		stack = new ArrayList<Integer>(config.stack);
		buffer = new ArrayList<Integer>(config.buffer);
		tree = new DependencyTree(config.tree);
		sentence = new CoreLabel(config.sentence);
	}

	public Configuration(CoreMap sentence)
	{
		this.stack = new ArrayList<Integer>();
    this.buffer = new ArrayList<Integer>();
		this.tree = new DependencyTree();
    this.sentence = sentence;
	}

	public boolean shift()
	{
		int k = getBuffer(0);  
		if (k == CONST.NONEXIST)
			return false;
		buffer.remove(0); 
		stack.add(k);
		return true;
	}

	public boolean removeSecondTopStack()
	{
		int nStack = getStackSize();
		if (nStack < 2) 
			return false;
		stack.remove(nStack - 2);
		return true;
	}

	public boolean removeTopStack()
	{
		int nStack = getStackSize();
		if (nStack < 1) 
			return false;
		stack.remove(nStack - 1);
		return true;
	}

	public int getStackSize()
	{
		return stack.size();
	}

	public int getBufferSize()
	{
		return buffer.size();
	}

	public int getHead(int k)
	{
		return tree.getHead(k);
	}

	public String getLabel(int k)
	{
		return tree.getLabel(k);
	}

	public int getStack(int k)
	{
		int nStack = getStackSize();
		return (k >= 0 && k < nStack) ? stack.get(nStack - 1 - k) : CONST.NONEXIST;
	}

	public int getBuffer(int k)
	{
		return (k >= 0 && k < getBufferSize()) ? buffer.get(k) : CONST.NONEXIST; 
	}

  public List<CoreLabel> getCoreLabels() {
    return sentence.get(CoreAnnotations.TokensAnnotation.class);
  }

	public String getWord(int k)
	{
    if (k == 0) return CONST.ROOT;
    else k--;

    List<CoreLabel> lbls = getCoreLabels();
		return k < 0 || k >= lbls.size() ? CONST.NULL : lbls.get(k).word();
	}

	public String getPOS(int k)
	{
    if (k == 0) return CONST.ROOT;
    else k--;

    List<CoreLabel> lbls = getCoreLabels();
		return k < 0 || k >= lbls.size() ? CONST.NULL : lbls.get(k).tag();
	}

	public void addArc(int h, int t, String l)
	{
		tree.set(t, h, l);
	}

	public int getLeftChild(int k, int cnt)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NONEXIST;

		int c = 0;
		for (int i = 1; i < k; ++ i)
			if (tree.getHead(i) == k) 
				if ((++c) == cnt) 
					return i;
		return CONST.NONEXIST;
	}

	public int getLeftChild(int k)
	{
		return getLeftChild(k, 1);
	}

	public int getRightChild(int k, int cnt)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NONEXIST;

		int c = 0;
		for (int i = tree.n; i > k; -- i)
			if (tree.getHead(i) == k)
				if ((++c) == cnt)
					return i; 
		return CONST.NONEXIST;
	}

	public int getRightChild(int k)
	{
		return getRightChild(k, 1);
	}


	public boolean hasOtherChild(int k, DependencyTree goldTree)
	{
		for (int i = 1; i <= tree.n; ++ i)
			if (goldTree.getHead(i) == k && tree.getHead(i) != k) return true;
		return false;
	}

	public int getLeftValency(int k)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NONEXIST;
		int cnt = 0;
		for (int i = 1; i < k; ++ i)
			if (tree.getHead(i) == k) 
				++ cnt;
		return cnt;
	}

	public int getRightValency(int k)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NONEXIST;
		int cnt = 0;
		for (int i = k + 1; i <= tree.n; ++ i)
			if (tree.getHead(i) == k) 
				++ cnt;
		return cnt;
	}

	public String getLeftLabelSet(int k)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NULL;

		HashSet<String> labelSet = new HashSet<String>();
		for (int i = 1; i < k; ++ i)
			if (tree.getHead(i) == k)
				labelSet.add(tree.getLabel(i));

		List<String> ls = new ArrayList<String>(labelSet);
		Collections.sort(ls);
		String s = "";
		for (int i = 0; i < ls.size(); ++ i) 
			s = s + "/" + ls.get(i);
		return s;
	}

	public String getRightLabelSet(int k)
	{
		if (k < 0 || k > tree.n) 
			return CONST.NULL;

		HashSet<String> labelSet = new HashSet<String>();
		for (int i = k + 1; i <= tree.n; ++ i)
			if (tree.getHead(i) == k)
				labelSet.add(tree.getLabel(i));

		List<String> ls = new ArrayList<String>(labelSet);
		Collections.sort(ls);
		String s = "";
		for (int i = 0; i < ls.size(); ++ i) 
			s = s + "/" + ls.get(i);
		return s;
	}

	//returns a string that concatenates all elements on the stack and buffer, and head / label.
	public String getStr()
	{
		String s = "[S]";
		for (int i = 0; i < getStackSize(); ++ i)
		{
			if (i > 0) s = s + ",";
			s = s + stack.get(i);
		}
		s = s + "[B]";
		for (int i = 0; i < getBufferSize(); ++ i)
		{
			if (i > 0) s = s + ",";
			s = s + buffer.get(i);
		}
		s = s + "[H]";
		for (int i = 1; i <= tree.n; ++ i)
		{
			if (i > 1) s = s + ",";
			s = s + getHead(i) + "(" + getLabel(i) + ")";
		}
		return s;
	}
}