package edu.stanford.nlp.trees;

import java.io.Serializable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;

/**
 * A <code>TypedDependency</code> is a relation between two words in a
 * <code>GrammaticalStructure</code>.  Each <code>TypedDependency</code>
 * consists of a governor word, a dependent word, and a relation, which is
 * normally an instance of {@link GrammaticalRelation
 * <code>GrammaticalRelation</code>}.
 *
 * @author Bill MacCartney
 */
public class TypedDependency implements Comparable<TypedDependency>, Serializable {

  private static final long serialVersionUID = -7690294213151279779L;

  // TODO FIXME: these should all be final.  That they are mutable is
  // awful design.  Awful.  It means that underlying data structures
  // can be mutated in ways you don't intend.  For example, there was
  // a time when you could call typedDependenciesCollapsed() and it
  // would change the GrammaticalStructure because of the way that
  // object mutated its TypedDependency objects.
  private GrammaticalRelation reln;
  private IndexedWord gov;
  private IndexedWord dep;
  private boolean extra; // = false; // to code whether the dependency preserves the tree structure or not
  // cdm: todo: remove this field and use typing on reln?  Expand implementation of SEMANTIC_DEPENDENT

  public TypedDependency(GrammaticalRelation reln, IndexedWord gov, IndexedWord dep) {
    this.reln = reln;
    this.gov = gov;
    this.dep = dep;
  }

  public TypedDependency(TypedDependency other) {
    this.reln = other.reln;
    this.gov = other.gov;
    this.dep = other.dep;
    this.extra = other.extra;
  }

  public GrammaticalRelation reln() {
    return reln;
  }

  public void setGov(IndexedWord gov) {
    this.gov = gov;
  }

  public void setDep(IndexedWord dep) {
    this.dep = dep;
  }


  public IndexedWord gov() {
    return gov;
  }

  public IndexedWord dep() {
    return dep;
  }

  public boolean extra() {
    return extra;
  }

  public void setReln(GrammaticalRelation reln) {
    this.reln = reln;
  }

  public void setExtra() {
    this.extra = true;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TypedDependency)) {
      return false;
    }
    final TypedDependency typedDep = (TypedDependency) o;

    if (reln != null ? !reln.equals(typedDep.reln) : typedDep.reln != null) {
      return false;
    }
    if (gov != null ? !gov.equals(typedDep.gov) : typedDep.gov != null) {
      return false;
    }
    if (dep != null ? !dep.equals(typedDep.dep) : typedDep.dep != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = (reln != null ? reln.hashCode() : 17);
    result = 29 * result + (gov != null ? gov.hashCode() : 0);
    result = 29 * result + (dep != null ? dep.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return toString(CoreLabel.OutputFormat.VALUE_INDEX);
  }

  public String toString(CoreLabel.OutputFormat format) {
    return reln + "(" + gov.toString(format) + ", " + dep.toString(format) + ")";
  }

  public int compareTo(TypedDependency tdArg) {
    IndexedWord depArg = tdArg.dep();
    IndexedWord depThis = this.dep();
    int indexArg = depArg.index();
    int indexThis = depThis.index();

    if (indexThis > indexArg) {
      return 1;
    } else if (indexThis < indexArg) {
      return -1;
    }

    // dependent indices are equal, check governor
    int govIndexArg  = tdArg.gov().index();
    int govIndexThis = this.gov().index();
    if (govIndexThis > govIndexArg) {
      return 1;
    } else if (govIndexThis < govIndexArg) {
      return -1;
    }

    // dependent and governor indices equal, the relation decides
    return this.reln().compareTo(tdArg.reln());
  }

}
