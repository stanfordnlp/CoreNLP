package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;


/**
 * A factory for dependencies of a certain type.
 *
 * @author Christopher Manning
 */
public interface DependencyFactory {

  public Dependency newDependency(Label regent, Label dependent);

  public Dependency newDependency(Label regent, Label dependent, Object name);

}
