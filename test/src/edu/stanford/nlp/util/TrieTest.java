package edu.stanford.nlp.util;

import java.util.Arrays;

import junit.framework.TestCase;


public class TrieTest extends TestCase{
  public void testEmptyTrie(){
    Trie<String, Boolean> trie = new Trie<String, Boolean>();
    assertEquals(null, trie.findFirst(Arrays.asList("o", "n", "e")));
    assertEquals(null, trie.findFirst(Arrays.asList("")));
    trie.append(Arrays.asList("o", "n", "e"), true);
    assertEquals(true, trie.findFirst(Arrays.asList("o", "n", "e")).booleanValue());
    assertEquals(null, trie.findFirst(Arrays.asList("t")));
  }
  
  public void testBasicTrie(){
    Trie<String, Boolean> trie = new Trie<String, Boolean>();
    trie.append(Arrays.asList("o", "n", "e"), true);
    trie.append(Arrays.asList("o", "n", "l", "y"), true);
    trie.append(Arrays.asList("o", "n", "e", "t", "o", "n"), true);
    trie.append(Arrays.asList("t", "w", "o"), true);
    
    assertEquals(true, trie.findFirst(Arrays.asList("o", "n", "e")).booleanValue());
    assertEquals(true, trie.findFirst(Arrays.asList("o", "n", "l", "y")).booleanValue());
    assertEquals(true, trie.findFirst(Arrays.asList("o", "n", "e", "t", "o", "n")).booleanValue());
    assertEquals(true, trie.findFirst(Arrays.asList("t", "w", "o")).booleanValue());
    assertEquals(null, trie.findFirst(Arrays.asList("o", "n", "e", "s", "i", "n")));
    assertEquals(null, trie.findFirst(Arrays.asList("o", "n", "e", "t", "o", "n", "l", "y")));
    assertEquals(null, trie.findFirst(Arrays.asList("t", "w", "o", "f", "e", "r")));
    assertEquals(null, trie.findFirst(Arrays.asList("t", "w")));
    assertEquals(null, trie.findFirst(Arrays.asList("t", "w", "i", "c", "h")));
    assertEquals(null, trie.findFirst(Arrays.asList("s", "u", "p", "e", "r")));
    assertEquals(null, trie.findFirst(Arrays.asList("")));
    assertEquals("\no:\n n:\n  e:[true]\n   t:\n    o:\n     n:[true]\n  l:\n   y:[true]\nt:\n w:\n  o:[true]", 
        trie.toString());
  }
  
  public void testKeyAppendInsert(){
    Trie<String, Boolean> trie = new Trie<String, Boolean>();
    trie.append(Arrays.asList("o", "n", "e"), true);
    assertEquals("\no:\n n:\n  e:[true]", trie.toString());
    trie.insert(Arrays.asList("o", "n", "e"), Arrays.asList(true, false));
    assertEquals("\no:\n n:\n  e:[true, true, false]", trie.toString());
    
    trie.insert(Arrays.asList("o", "n", "l", "y"), Arrays.asList(true, false));
    assertEquals("\no:\n n:\n  e:[true, true, false]\n  l:\n   y:[true, false]", 
        trie.toString());
    trie.append(Arrays.asList("o", "n", "l", "y"), true);
    assertEquals("\no:\n n:\n  e:[true, true, false]\n  l:\n   y:[true, false, true]", 
        trie.toString());
    
    assertEquals("[true, false, true]", trie.findAll(Arrays.asList("o", "n", "l", "y")).toString());
    assertEquals("[true, true, false]", trie.findAll(Arrays.asList("o", "n", "e")).toString());
  }
}
