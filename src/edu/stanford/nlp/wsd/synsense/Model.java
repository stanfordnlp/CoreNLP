package edu.stanford.nlp.wsd.synsense;

import java.util.List;

/**
 *  @author Teg Grenager (grenager@cs.stanford.edu)
 */
public interface Model {

  public void train(List<Instance> data);

  public List test(List<Instance> data);

}
