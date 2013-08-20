package edu.stanford.nlp.util;

import edu.stanford.nlp.util.Function;

import edu.stanford.cs.ra.RA;
import edu.stanford.cs.ra.xml.XMLStream;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A <code>MemoFn</code> is a function which remembers (via "memos") the results
 * of its previous computations, so that they don't need to be repeated.  A
 * <code>MemoFn</code> is typically a function which is expensive to compute,
 * such as a parsing function.  If the function will be called repeatedly with
 * the same input, memoization can save time. <p/>
 *
 * A <code>MemoFn&lt;I, O&gt;</code> wraps around an existing {@link Function}
 * supplied at construction.  It also contains a collection of memos,
 * represented by a <code>Map&lt;I, O&gt;</code>.  When presented with an input
 * of type <code>I</code>, it first checks its memos to see if it already knows
 * the right output. If so, it returns it; if not, it invokes the {@link
 * Function#apply apply()} method of the wrapped <code>Function</code> to compute
 * the output, stores the result in the memos, and returns the result. <p/>
 *
 * This class includes the ability to save the memos of a <code>MemoFn</code> to
 * a serialization file, and to load memos into a <code>MemoFn</code> from such
 * a file. <p/>
 *
 * To do: XML serialization, a constructor which accepts a MapFactory, support
 * for lossy maps, maybe implement Map interface. <p/>
 *
 * You can add memoization functionality to an existing class (as opposed to
 * using an instance of <code>MemoFn</code>) by implementing the {@link
 * MemoizableFn} interface.  A well-behaved implementation of
 * <code>MemoizableFn</code> must adhere to some guidelines which aren't
 * enforced by the compiler.  These are exemplified by {@link
 * AbstractMemoizableFn AbstractMemoizableFn}.  When possible,
 * extend <code>AbstractMemoizableFn</code>. <p/>
 *
 * @author Bill MacCartney
 */
public  final class MemoFn<I, O> implements Function<I, O> {

  public interface MemoizableFn<I, O> extends Function<I, O> {

    /**
     * Causes the <code>MemoizableFn</code> to begin using memos.
     */
    public void memoize();
    
    /**
     * Computes the function without memoization.  Implementing classes will
     * typically define this method rather than <code>apply()</code>.
     */
    public O compute(I input);
    
    /**
     * Returns the <code>MemoFn</code> which is providing memoization for this
     * <code>MemoizableFn</code>, or <code>null</code> if the function has not
     * been memoized.
     */
    public MemoFn<I, O> getMemoFn();
    
  }    
  
  
  // AbstractMemoizableFn =================================================
  
  /**
   * Default implementation of <code>MemoizableFn</code>.  Alternative
   * implementations which do not extend this abstract class should replicate
   * its functionality.
   */
  public static abstract class AbstractMemoizableFn<I, O>
    implements MemoizableFn<I, O> {

    private MemoFn<I, O> mf;

    public final O apply(I input) {
      return (mf == null ? compute(input) : mf.apply(input));
    }

    public abstract O compute(I input);

    public void memoize() {
      mf = new MemoFn<I, O>(this, getClass().getSimpleName());
    }

    public MemoFn<I, O> getMemoFn() {
      return mf;
    }

  }


  // MemoFn =====================================================================

  private final Function<I, O> fn;
  private final String name;
  private final Map<I, O> memos;
  public boolean announceMemos = false;
  public boolean announceComputes = false;
  public boolean announceLoadsAndSaves = true;
  private boolean isChanged = false; // whether we've updated memos since last load
    
  /**
   * Constructs a new <code>MemoFn</code> with the given name which loads and
   * saves serialized memos to the given path wrapping the given
   * <code>Function</code>.
   */
  public MemoFn(Function<I, O> fn, String name, String memosPath) {
    this.fn = fn;
    this.memos = new HashMap<I, O>();
    this.name = name;
  }

  /**
   * Constructs a new <code>MemoFn</code> with the given name wrapping
   * the given <code>Function</code>.
   */
  public MemoFn(Function<I, O> fn, String name) {
    this(fn, name, null);
  }

  /**
   * Constructs a new <code>MemoFn</code> with no name wrapping the
   * given <code>Function</code>.
   */
  public MemoFn(Function<I, O> fn) {
    this(fn, null, null);
  }


  // --------------------------------------------------------------------------

  /**
   * Returns the result of applying the function to the given input.  This
   * method first checks whether there's a memo for this input, and if so,
   * returns the memoized output.  Otherwise, it invokes the {@link
   * Function#apply apply()} method of the wrapped function (or, if it's a
   * <code>MemoizableFn</code>, the {@link
   * MemoizableFn#compute compute()} method) to compute the
   * function for the new input, and memoizes the computed output before
   * returning.
   */
  public final O apply(I input) {
    O output;
    if (memos.containsKey(input)) {   // check memos
      output = memos.get(input);
      if (announceMemos) report("memo", input, output);
    } else {
      output = compute(input);        // compute freshly
      putMemo(input, output);         // save memo
      if (announceComputes) report("compute", input, output);
    }
    return output;
  }

  private final O compute(I input) {
    if (fn instanceof MemoizableFn) {
      return ((MemoizableFn<I, O>) fn).compute(input);
    } else {
      return fn.apply(input);
    }
  }
  
  private void report(String tag, I input, O output) {
    RA.stream.line(tag, String.format("%s(%s) = %s",
                                      (name == null ? "f" : name),
                                      input == null ? "null" : input.toString().trim(),
                                      output == null ? "null" : output.toString().trim()));
  }

  /**
   * Stores the given input-output pair as a memo.  Returns <code>true</code> if
   * the stored input-output pair is not equals() to a pair already in the
   * memoization. <p/>
   */
  public boolean putMemo(I input, O output) {
    boolean changed = false;
    if (memos.containsKey(input)) {
      O old = memos.get(input);
      if (old == null ? output == null : old.equals(output)) {
        changed = false;
      } else {
        changed = true;
      }
    } else {
      changed = true;
    }
    isChanged |= changed;
    memos.put(input, output);           // save memo
    return changed;
  }
  
  /**
   * Returns the set of inputs which have memos.
   */
  public Set<I> getMemosKeySet() {
    return Collections.unmodifiableSet(memos.keySet());
  }

  @Override
  public String toString() {
    return name;
  }


  // writing to XML -------------------------------------------------------------

  public void summarizeMemos(XMLStream out) {
    String n = (name == null ? this.toString() : name);
    out.begin("memos", "name", n, "size", memos.size());
    for (Map.Entry<I, O> entry : memos.entrySet()) {
      out.line("memo", entry.getValue(), "key", entry.getKey().toString());
    }
    out.end("memos");
  }


  // serialization ------------------------------------------------------------

  /**
   * Loads serialized memos from the given pathname (without exceptions).  If
   * the path ends with ".gz", a GZIPped file is assumed.  The loaded memos are
   * added to the current memoization, overwriting but not removing any existing
   * memos.  If an <code>IOException</code> occurs, a warning is printed, and
   * the method returns successfully.
   */
  public void loadMemos(String path) {
    try {
      loadMemosIfAble(path);
    } catch (IOException e) {
      System.err.println("Unable to load memos but continuing anyway: " + e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Loads serialized memos from the given pathname (or throws an exception).
   * If the path ends with ".gz", a GZIPped file is assumed.  The loaded memos
   * are added to the current memoization, overwriting but not removing any
   * existing memos.
   */
  public void loadMemosIfAble(String path) throws IOException, ClassNotFoundException {
    if (announceLoadsAndSaves)
      RA.stream.line("MemoFn", String.format("Loading memos from %s...", path));
    InputStream is = new FileInputStream(path);
    if (path.endsWith("gz")) is = new GZIPInputStream(is);
    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(is));
    Map<I, O> loadedMemos = ErasureUtils.uncheckedCast(ois.readObject());
    ois.close();
    int oldSize = memos.size();
    int loadSize = loadedMemos.size();
    int matchSize = 0;
    for (I input : loadedMemos.keySet()) {
      if (!putMemo(input, loadedMemos.get(input))) {
        matchSize++;
      }
    }
    int addSize = memos.size() - oldSize;
    if (announceLoadsAndSaves)
      RA.stream.line("MemoFn",
                     String.format("Loaded %d memos: %d added, %d changed, %d matched.",
                                   loadSize,
                                   addSize,
                                   loadSize - addSize - matchSize,
                                   matchSize));
    isChanged = false;
  }


  // ............................................................................

  /**
   * Saves serialized memos to the given pathname (without exceptions), but only
   * if there have been any changes to the memos since the last call to {@link
   * MemoFn#loadMemos(String) loadMemos()}.  If the path ends with ".gz", a
   * GZIPped file is created.  If an <code>IOException</code> occurs, a warning
   * is printed, and the method returns successfully.
   */
  public void saveMemos(String path) {
    try {
      saveMemosIfChanged(path);
    } catch (IOException e) {
      System.err.println("Unable to save memos but continuing anyway: " + e);
    }
  }

  /**
   * Saves serialized memos to the given pathname (or throws an exception), but
   * only if there have been any changes to the memos since the last call to
   * {@link MemoFn#loadMemos(String) loadMemos()}.  If the path ends with ".gz",
   * a GZIPped file is created.
   */
  public void saveMemosIfChanged(String path) throws IOException {
    if (isChanged) {
      saveMemosIfAble(path);
    }
    isChanged = false;                // ???
  }

  /**
   * Saves serialized memos to the given pathname (or throws an exception).  If
   * the path ends with ".gz", a GZIPped file is created.
   */
  public void saveMemosIfAble(String path) throws IOException {
    if (announceLoadsAndSaves)
      RA.stream.line("MemoFn", String.format("Saving memos to %s...", path));
    OutputStream os = new FileOutputStream(path);
    if (path.endsWith("gz")) os = new GZIPOutputStream(os);
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os));
    oos.writeObject(memos);
    oos.close();
    if (announceLoadsAndSaves)
      RA.stream.line("MemoFn", String.format("Saved %d memos.", memos.size()));
  }


  // ============================================================================

  /**
   * An example <code>MemoizableFn</code> that does not extend
   * <code>AbstractMemoizableFn</code>, but rather does everything from
   * scratch.  This is risky, because it still relies on the programmer to do
   * the proper conditional delegation in {@link #apply apply()}.
   */
  private static final class Capitalizer implements MemoizableFn<String, String> {
    private MemoFn<String, String> mf;
    public String apply(String s) { return (mf == null ? compute(s) : mf.apply(s)); }
    public String compute(String s) { return s.toUpperCase(); }
    public void memoize() { mf = new MemoFn<String, String>(this, getClass().getSimpleName()); }
    // public Memos<String, String> getMemos() { return (mf == null ? null : mf.getMemos()); }
    public MemoFn<String, String> getMemoFn() { return mf; }
  }

  /**
   * An example <code>MemoizableFn</code> that extends
   * <code>AbstractMemoizableFn</code>.
   */
  @SuppressWarnings("unused")
  private static final class Measurer extends AbstractMemoizableFn<String, Integer> {
    @Override
    public Integer compute(String s) { return s.length(); }
  }

  @SuppressWarnings("unused")
  private static final MemoFn<String, Boolean> parityChecker =
    new MemoFn<String, Boolean>(new Function<String, Boolean>() {
      public Boolean apply(String s) {
        return (s.length() % 2 == 0);
      }
    }, "parityChecker");

  /**
  private static void testOnce(Function<String, ? extends Object> fn) {
    System.out.printf("fn:        %s%n", fn);
    System.out.printf("fn(sew):   %s%n", fn.apply("sew"));
    System.out.printf("fn(nail):  %s%n", fn.apply("nail"));
    System.out.printf("fn(sew):   %s%n", fn.apply("sew"));
    System.out.println();
  }

  @SuppressWarnings("unchecked")
  private static void test(MemoFn fn) {
    testOnce(fn);
    fn.announceMemos = true;
    fn.announceComputes = true;
    testOnce(fn);
    fn.summarizeMemos(new XMLOutputStream(System.out));
    System.out.println();
    String path = String.format("/tmp/%s.memos.ser.gz", fn.toString());
    fn.saveMemos(path);
    fn.loadMemos(path);
    System.out.println();
  }

  private static void test(MemoizableFn<?, ?> fn) {
    testOnce(fn);
    fn.memoize();
    fn.getMemoFn().announceMemos = true;
    fn.getMemoFn().announceComputes = true;
    testOnce(fn);
    fn.getMemoFn().summarizeMemos(new XMLOutputStream(System.out));
    System.out.println();
    String path = String.format("/tmp/%s.memos.ser.gz", fn.getMemoFn().toString());
    fn.getMemoFn().saveMemos(path);
    fn.getMemoFn().loadMemos(path);
    System.out.println();
  }


  // ============================================================================

  public static void main(String[] args) {
    test(parityChecker);                // example of MemoFn
    test(new Measurer());               // example of MemoizableFn
    test(new Capitalizer());            // example of MemoizableFn
  }
  */

}

  
