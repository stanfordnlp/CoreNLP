package edu.stanford.nlp.depparser.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class IntCounter extends HashMap<Integer, Integer>
{
  public int getCount(int key)
  {
    return containsKey(key) ? get(key) : 0;
  }

  public void add(int key)
  {
    put(key, getCount(key) + 1);
  }

  public ArrayList<Integer> getSortedKeys()
  {
    ArrayList<Integer> ls = new ArrayList<Integer>(keySet());
    Collections.sort(ls, new Comparator<Integer>() {
      public int compare(Integer s1, Integer s2) {
        return get(s2).compareTo(get(s1));
      }
    });
    return ls;
  }

  public ArrayList<Integer> getSortedKeys(int K)
  {
    ArrayList<Integer> ls = getSortedKeys();
    ArrayList<Integer> kls = new ArrayList<Integer>();
    for (int i = 0; i < ls.size() && i < K; ++ i)
      kls.add(ls.get(i));
    return kls;
  }
}