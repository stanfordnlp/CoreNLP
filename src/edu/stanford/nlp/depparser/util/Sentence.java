
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-08-31
*/

package edu.stanford.nlp.depparser.util;

import java.io.*;
import java.util.*;

public class Sentence
{
	
	public int n;
	List<String> word, pos;

	public Sentence()
	{
		n = 0;
		word = new ArrayList<String>();
		pos = new ArrayList<String>();
		word.add(CONST.ROOT); pos.add(CONST.ROOT);
	}

	Sentence(Sentence sentence)
	{	
		n = sentence.n;
		word = new ArrayList<String>(sentence.word);
		pos = new ArrayList<String>(sentence.pos);
	}

	public void add(String w, String t)
	{
		++ n; 
		word.add(w); 
		pos.add(t);
	}

	public String getWord(int k)
	{
		if (k >= 0 && k <= n)
			return word.get(k);
		else
			return CONST.NULL;
	}

	public String getPOS(int k)
	{
		if (k >= 0 && k <= n)
			return pos.get(k);
		else
			return CONST.NULL;
	}

	String getStr(int k)
	{
		return getWord(k) + "_" + getPOS(k);
	}

	//TODO: implement isPunc for other languages
	boolean isPunc(int k)
	{
		String s = getPOS(k);
		return s.equals("``") || s.equals("''") || s.equals(",") || s.equals(".") | s.equals(":");
	}

	void print()
	{
		for (int k = 1; k <= n; ++ k)
			System.out.print(getStr(k) + " ");
		System.out.println();
	}

	void printRaw()
	{
		for (int k = 1; k <= n; ++ k)
			System.out.print(getWord(k) + " ");
		System.out.println();
	}
}
