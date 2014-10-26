
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-31
* 	@Last Modified:  2014-09-01
*/

package edu.stanford.nlp.depparser.util;

import java.io.*;
import java.util.*;

public class DependencyTree
{
	public int n;
	List<Integer> head;
	List<String> label;
	private int counter;

	public DependencyTree()
	{
		n = 0;
		head = new ArrayList<Integer>();
		head.add(CONST.NONEXIST);
		label = new ArrayList<String>();
		label.add(CONST.UNKNOWN);
	}

	public DependencyTree(DependencyTree tree)
	{
		n = tree.n;
		head = new ArrayList<Integer>(tree.head);
		label = new ArrayList<String>(tree.label);
	}

	public void add(int h, String l)
	{
		++ n;
		head.add(h); 
		label.add(l);
	}

	public void set(int k, int h, String l)
	{
		head.set(k, h); 
		label.set(k, l);
	}

	public int getHead(int k)
	{
		if (k <= 0 || k > n) 
			return CONST.NONEXIST; 
		else 
			return head.get(k);
	}

	public String getLabel(int k)
	{
		if (k <= 0 || k > n) 
			return CONST.NULL; 
		else 
			return label.get(k);
	}

	public int getRoot()
	{
		for (int k = 1; k <= n; ++ k)
			if (getHead(k) == 0) 
				return k;
		return 0;
	}

	// check if there is only one root
	public boolean isSingleRoot()
	{
		int roots = 0;
		for (int k = 1; k <= n; ++ k)
			if (getHead(k) == 0)
				roots = roots + 1;
		return (roots == 1);
	}
	
	// check if the tree is legal, O(n)
	public boolean isTree()
	{
		List<Integer> h = new ArrayList<Integer>();
		h.add(-1);
		for (int i = 1; i <= n; ++ i)
		{
			if (getHead(i) < 0 || getHead(i) > n) 
				return false;
			h.add(-1);
		}
		for (int i = 1; i <= n; ++ i)
		{
			int k = i;
			while (k > 0)
			{
				if (h.get(k) >= 0 && h.get(k) < i) break;
				if (h.get(k) == i) 
					return false;
				h.set(k, i);
				k = getHead(k);
			}
		}
		return true;
	}

	private boolean visitTree(int w)
	{
		for (int i = 1; i < w; ++ i)
			if (getHead(i) == w && visitTree(i) == false)
				return false;
		counter = counter + 1;
		if (w != counter) 
			return false;
		for (int i = w + 1; i <= n; ++ i)
			if (getHead(i) == w && visitTree(i) == false)
				return false;
		return true;
	}

	// check if the tree is projective, O(n^2)
	public boolean isProjective()
	{
		if (!isTree())
			return false;
		counter = -1;
		return visitTree(0);
	}

	public boolean equal(DependencyTree t)
	{
		if (t.n != n) 
			return false;
		for (int i = 1; i <= n; ++ i)
		{
			if (getHead(i) != t.getHead(i)) 
				return false;
			if (!getLabel(i).equals(t.getLabel(i))) 
				return false;
		}
		return true;
	}

	public void print()
	{
		for (int i = 1; i <= n; ++ i)
			System.out.println(i + " " + getHead(i) + " " + getLabel(i));
		System.out.println();
	}
}