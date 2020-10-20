
package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Describe the current configuration of a parser (i.e., parser state).
 *
 * This class uses an indexing scheme where an index of zero refers to
 * the ROOT node and actual word indices begin at one.
 *
 * @author Danqi Chen
 */
public class Configuration {

  final List<Integer> stack;
  final List<Integer> buffer;

  final DependencyTree tree;
  final CoreMap sentence;

  public Configuration(Configuration config) {
    stack = new ArrayList<>(config.stack);
    buffer = new ArrayList<>(config.buffer);
    tree = new DependencyTree(config.tree);
    sentence = new CoreLabel(config.sentence);
  }

  public Configuration(CoreMap sentence) {
    this.stack = new ArrayList<>();
    this.buffer = new ArrayList<>();
    this.tree = new DependencyTree();
    this.sentence = sentence;
  }

  public boolean shift() {
    int k = getBuffer(0);
    if (k == Config.NONEXIST)
      return false;
    buffer.remove(0);
    stack.add(k);
    return true;
  }

  public boolean removeSecondTopStack() {
    int nStack = getStackSize();
    if (nStack < 2)
      return false;
    stack.remove(nStack - 2);
    return true;
  }

  public boolean removeTopStack() {
    int nStack = getStackSize();
    if (nStack < 1)
      return false;
    stack.remove(nStack - 1);
    return true;
  }

  public int getStackSize() {
    return stack.size();
  }

  public int getBufferSize() {
    return buffer.size();
  }

  public int getSentenceSize() {
    return getCoreLabels().size();
  }
  /**
   * @param k Word index (zero = root node; actual word indexing
   *          begins at 1)
   */
  public int getHead(int k) {
    return tree.getHead(k);
  }

  /**
   * @param k Word index (zero = root node; actual word indexing
   *          begins at 1)
   */
  public String getLabel(int k) {
    return tree.getLabel(k);
  }

  /**
   * Get the sentence index of the kth word on the stack.
   *
   * @return Sentence index or {@link Config#NONEXIST} if stack doesn't
   *         have an element at this index
   */
  public int getStack(int k) {
    int nStack = getStackSize();
    return (k >= 0 && k < nStack) ? stack.get(nStack - 1 - k) : Config.NONEXIST;
  }

  /**
   * Get the sentence index of the kth word on the buffer.
   *
   * @return Sentence index or {@link Config#NONEXIST} if stack doesn't
   *         have an element at this index
   */
  public int getBuffer(int k) {
    return (k >= 0 && k < getBufferSize()) ? buffer.get(k) : Config.NONEXIST;
  }

  public List<CoreLabel> getCoreLabels() {
    return sentence.get(CoreAnnotations.TokensAnnotation.class);
  }

  /**
   * @param k Word index (zero = root node; actual word indexing
   *          begins at 1)
   */
  public String getWord(int k) {
    if (k == 0) return Config.ROOT;
    else k--;

    List<CoreLabel> lbls = getCoreLabels();
    return k < 0 || k >= lbls.size() ? Config.NULL : lbls.get(k).word();
  }

  /**
   * @param k Word index (zero = root node; actual word indexing
   *          begins at 1)
   */
  public String getPOS(int k) {
    if (k == 0) return Config.ROOT;
    else k--;

    List<CoreLabel> lbls = getCoreLabels();
    return k < 0 || k >= lbls.size() ? Config.NULL : lbls.get(k).tag();
  }

  /**
   * @param h Word index of governor (zero = root node; actual word
   *          indexing begins at 1)
   * @param t Word index of dependent (zero = root node; actual word
   *          indexing begins at 1)
   * @param l Arc label
   */
  public void addArc(int h, int t, String l) {
    tree.set(t, h, l);
  }

  public int getLeftChild(int k, int cnt) {
    if (k < 0 || k > tree.n)
      return Config.NONEXIST;

    int c = 0;
    for (int i = 1; i < k; ++i)
      if (tree.getHead(i) == k)
        if ((++c) == cnt)
          return i;
    return Config.NONEXIST;
  }

  public int getLeftChild(int k) {
    return getLeftChild(k, 1);
  }

  public int getRightChild(int k, int cnt) {
    if (k < 0 || k > tree.n)
      return Config.NONEXIST;

    int c = 0;
    for (int i = tree.n; i > k; --i)
      if (tree.getHead(i) == k)
        if ((++c) == cnt)
          return i;
    return Config.NONEXIST;
  }

  public int getRightChild(int k) {
    return getRightChild(k, 1);
  }


  public boolean hasOtherChild(int k, DependencyTree goldTree) {
    for (int i = 1; i <= tree.n; ++i)
      if (goldTree.getHead(i) == k && tree.getHead(i) != k) return true;
    return false;
  }

  public int getLeftValency(int k) {
    if (k < 0 || k > tree.n)
      return Config.NONEXIST;
    int cnt = 0;
    for (int i = 1; i < k; ++i)
      if (tree.getHead(i) == k)
        ++cnt;
    return cnt;
  }

  public int getRightValency(int k) {
    if (k < 0 || k > tree.n)
      return Config.NONEXIST;
    int cnt = 0;
    for (int i = k + 1; i <= tree.n; ++i)
      if (tree.getHead(i) == k)
        ++cnt;
    return cnt;
  }

  public String getLeftLabelSet(int k) {
    if (k < 0 || k > tree.n)
      return Config.NULL;

    HashSet<String> labelSet = new HashSet<>();
    for (int i = 1; i < k; ++i)
      if (tree.getHead(i) == k)
        labelSet.add(tree.getLabel(i));

    return makeLabelSetString(labelSet);
  }

  public String getRightLabelSet(int k) {
    if (k < 0 || k > tree.n)
      return Config.NULL;

    HashSet<String> labelSet = new HashSet<>();
    for (int i = k + 1; i <= tree.n; ++i)
      if (tree.getHead(i) == k)
        labelSet.add(tree.getLabel(i));

    return makeLabelSetString(labelSet);
  }

  private static String makeLabelSetString(Set<String> labelSet) {
    List<String> ls = new ArrayList<>(labelSet);
    Collections.sort(ls);
    StringBuilder s = new StringBuilder(128);
    s.append("[S]");
    for (String l : ls) {
      s.append('/').append(l);
    }
    return s.toString();
  }

  //returns a string that concatenates all elements on the stack and buffer, and head / label.
  public String getStr() {
    StringBuilder s = new StringBuilder(128);
    s.append("[S]");
    for (int i = 0; i < getStackSize(); ++i) {
      if (i > 0) {
        s.append(',');
      }
      s.append(stack.get(i));
    }
    s.append("[B]");
    for (int i = 0; i < getBufferSize(); ++i) {
      if (i > 0) {
        s.append(',');
      }
      s.append(buffer.get(i));
    }
    s.append("[H]");
    for (int i = 1; i <= tree.n; ++i) {
      if (i > 1) {
        s.append(',');
      }
      s.append(getHead(i)).append('(').append(getLabel(i)).append(')');
    }
    return s.toString();
  }

}