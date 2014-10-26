
package edu.stanford.nlp.depparser.util;

import java.util.*;

//FIXME: change to generic type Counter<K, Integer>.. not very familar what to do..

class StrCounter extends HashMap<String, Integer>
{
    public int getCount(String key)
    {
        return containsKey(key) ? get(key) : 0;
    }

    public void add(String key)
    {
        put(key, getCount(key) + 1);
    }

    public ArrayList<String> getSortedKeys()
    {
        ArrayList<String> ls = new ArrayList<String>(keySet());
        Collections.sort( ls , new Comparator<String>() {
            public int compare( String s1, String s2 )
            {
                return get(s2).compareTo(get(s1));
            }
        });
        return ls;
    }

    public ArrayList<String> getSortedKeys(int K)
    {
        ArrayList<String> ls = getSortedKeys();
        ArrayList<String> kls = new ArrayList<String>();
        for (int i = 0; i < ls.size() && i < K; ++ i)
            kls.add(ls.get(i));
        return kls;
    }
}

class IntCounter extends HashMap<Integer, Integer>
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
        Collections.sort( ls , new Comparator<Integer>() {
            public int compare( Integer s1, Integer s2 )
            {
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