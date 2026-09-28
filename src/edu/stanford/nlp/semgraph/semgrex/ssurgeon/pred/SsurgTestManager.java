package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import java.util.*;

import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonRuntimeException;
import edu.stanford.nlp.util.Generics;

/**
 * This manages the set of available custom Node and Edge tests.  
 * This is a singleton, so use <code>inst</code> to
 * get the current instance.
 * @author Eric Yeh
 *
 */
public class SsurgTestManager {
  Map<String, Class<?>> nodeTests = Generics.newHashMap();
  
  private SsurgTestManager() { init();}
  private static SsurgTestManager instance  = null;
  
  /**
   * Once initialized, registers self with default node handlers.  
   */
  private void init() {
//  
  }
  
  /**
   * Returns the singleton instance, creating it on first use.
   *
   * @return The shared SsurgTestManager
   */
  public static SsurgTestManager inst() {
    if (instance == null)
      instance = new SsurgTestManager();
    return instance;
  }

  /**
   * Registers the class of {@code nodeTestObj} under its ID.
   * The class must have a public constructor taking the match name as a String.
   *
   * @param nodeTestObj An example of the test to register
   */
  public void registerNodeTest(NodeTest nodeTestObj) {
    nodeTests.put(nodeTestObj.getID(), nodeTestObj.getClass());
  }

  /**
   * Given the id of the test, and the match name argument, returns a new instance
   * of the given NodeTest, otherwise throws an exception if not available.
   *
   * @param id The ID the test was registered under
   * @param matchName The match name passed to the test's constructor
   * @return A new instance of the test.  An unregistered id results in a NullPointerException
   * @throws SsurgeonRuntimeException if the test could not be constructed
   */
  public NodeTest getNodeTest(String id, String matchName) {
    try {
      NodeTest test = (NodeTest) nodeTests.get(id).getConstructor(String.class).newInstance(matchName);
      return test;
    } catch (ReflectiveOperationException e) {
      throw new SsurgeonRuntimeException("Could not create a new instance of test " + id, e);
    }
  }
}
