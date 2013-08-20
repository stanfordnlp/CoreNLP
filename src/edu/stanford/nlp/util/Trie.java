package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of Trie based on the "S-Q Course Book" by Daniel Ellard and Penelope Ellard
 * 
 * @author lmthang
 *
 * @param <K, V>
 */
class Trie<K, V> {
  protected List<Trie<K, V>> trieList;
  protected List<K> keyList;
  protected int size;
  protected boolean isEnd; // indicate if this trie is the end of a string, i.e. a sequence of E
  protected List<V> valueList; // list of values associated with the trie, only activates when isEnd = true

  Trie() {
    trieList = new ArrayList<Trie<K, V>>();
    keyList = new ArrayList<K>();
    size = 0;
    isEnd = false;
    valueList = new ArrayList<V>();
  }

  /**
   * Find a sub-trie corresponds to the input element.
   * 
   * @param element
   * @return null if no child is found
   */
  protected Trie<K, V> findChild(K element) {
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        return trieList.get(i);
      }
    }
    return null;
  }
  
  /**
   * Find if the sequence elements exists, return the values associated with it. 
   * Otherwise, return null.
   * 
   * @param elements
   * @return
   */
  public List<V> findAll(List<K> elements) {
    Trie<K, V> curTrie = this;
    
    // go through each element
    for(K element : elements){
      curTrie = curTrie.findChild(element);
      
      if(curTrie == null){ // not found, not end of the string s
        return null;
      }
    }
    
    // see if this is the end
    // if so, we have a value to return at this trie
    if (curTrie.isEnd()) {
      return curTrie.getValues();
    } else { // not found, end of string s
      return null;
    }
  }
  
  /**
   * Same as findAll, but return only the first value 
   * from the list of values associated with the key elements.
   * 
   * @param elements
   * @return
   */
  public V findFirst(List<K> elements) {
    List<V> values = findAll(elements);
    if (values != null){
      return values.get(0);
    } else {
      return null;
    }
  }
  
  /**
   * Insert a single element into the current trie.
   * 
   * @param element
   * @return
   */
  protected Trie<K, V> insertChild(K element) {
    // is element already there?
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        Trie<K, V> result = trieList.get(i);
        return result;
      }
    }
    
    // element is not in trie
    Trie<K, V> result = new Trie<K, V>();
    keyList.add(element);
    trieList.add(result);
    size++;
    return result;
  }

  /**
   * Append a single value into the value list of 
   * the trie associated with they key elements.
   * If the key doesn't exist, create a new trie
   * associated to that key.
   * 
   * @param elements
   * @param value
   */
  public void append(List<K> elements, V value) {
    List<V> singleList = new ArrayList<V>();
    singleList.add(value);
    insert(elements, singleList, 0);
  }
  
  /**
   * Append a list of values into the value list of 
   * the trie associated with they key elements.
   * If the key doesn't exist, create a new trie
   * associated to that key.
   * 
   * @param elements
   * @param values
   */
  public void insert(List<K> elements, List<V> values) {
    insert(elements, values, 0);
  }
  
  /**
   * Insert element i in elements to the current child, 
   * and set end/value once i >= numElements.
   * 
   * @param elements
   * @param values
   * @param i
   */
  protected void insert(List<K> elements, List<V> values, int i) {
    // we've gotten to the end and 
    // the trie passed to us is empty (not null!)
    // setEnd and setValue
    int length = elements.size();
    if (i >= length) {
      setEnd(true);
      valueList.addAll(values);
      return;
    }
    
    Trie<K, V> nextT = insertChild(elements.get(i));
    //System.err.println(elements + "\t" + i + "\n" + nextT);
    nextT.insert(elements, values, i + 1);
  }
  
  /* Getters and Setters */
  public int getSize() {
    return size;
  }

  public boolean isEnd() {
    return isEnd;
  }

  protected void setEnd(boolean isEnd) {
    this.isEnd = isEnd;
  }

  protected List<V> getValues(){
    return valueList;
  }

  protected String indent = ""; // for printing purpose only
  public void setIndent(String indent){
    this.indent = indent;
  }
  public String toString(){
    StringBuffer sb = new StringBuffer();
    
    if(isEnd()){
      sb.append(valueList.toString());
    }
    
    int i=0;
    for(K element : keyList){
      trieList.get(i).setIndent(indent + " ");
      sb.append("\n" + indent + element + ":" + trieList.get(i));
      ++i;
    }
    return sb.toString();
  }
  
  public static void main(String[] args) {
    Trie<String, Boolean> ts = new Trie<String, Boolean>();
    ts.append(Arrays.asList("o", "n", "e"), true);
    ts.append(Arrays.asList("o", "n", "l", "y"), true);
    ts.append(Arrays.asList("o", "n", "e", "t", "o", "n"), true);
    ts.append(Arrays.asList("t", "w", "o"), true);
    
    System.out.println("one " + ts.findFirst(Arrays.asList("o", "n", "e")));
    System.out.println("only " + ts.findFirst(Arrays.asList("o", "n", "l", "y")));
    System.out.println("on " + ts.findFirst(Arrays.asList("o", "n")));
    System.out.println("onesin " + ts.findFirst(Arrays.asList("o", "n", "e", "s", "i", "n")));
    System.out.println("onetonly " + ts.findFirst(Arrays.asList("o", "n", "e", "o", "n", "l", "y")));
    System.out.println("twofer " + ts.findFirst(Arrays.asList("t", "w", "o", "f", "e", "r")));
    System.out.println("tw " + ts.findFirst(Arrays.asList("t", "w")));
    System.out.println("twitch " + ts.findFirst(Arrays.asList("t", "w", "i", "c", "h")));
    System.out.println("super " + ts.findFirst(Arrays.asList("s", "u", "p", "e", "r")));
    System.out.println("<empty> " + ts.findFirst(Arrays.asList("")));
    
    System.out.println(ts);
  }
}