
package edu.stanford.nlp.parser.nndep.util;

import java.util.*;

public class Counter<T> extends HashMap<T, Integer>
{
    public int getCount(T key)
    {
        return containsKey(key) ? get(key) : 0;
    }

    public void add(T key)
    {
        put(key, getCount(key) + 1);
    }

    public List<T> getSortedKeys()
    {
        List<T> ls = new ArrayList<T>(keySet());
        Collections.sort( ls , new Comparator<T>() {
            public int compare( T s1, T s2 )
            {
                return get(s2).compareTo(get(s1));
            }
        });
        return ls;
    }

    public List<T> getSortedKeys(int K)
    {
        List<T> ls = getSortedKeys();
        List<T> kls = new ArrayList<T>();
        for (int i = 0; i < ls.size() && i < K; ++ i)
            kls.add(ls.get(i));
        return kls;
    }

    public void printTopKeys(int K)
    {
        List<T> ls = getSortedKeys(K);
        for (int i = 0; i < ls.size(); ++ i)
        {
            T key = ls.get(i);
            System.out.println(key.toString() + " " + getCount(key));
        }
    }
}
