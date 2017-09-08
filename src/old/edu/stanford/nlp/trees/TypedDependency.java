package old.edu.stanford.nlp.trees;

import java.io.Serializable;

import old.edu.stanford.nlp.ling.CoreAnnotations.CopyAnnotation;

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
  private GrammaticalRelation reln;
  private TreeGraphNode gov;
  private TreeGraphNode dep;

  public TypedDependency(GrammaticalRelation reln, TreeGraphNode gov, TreeGraphNode dep) {
    this.reln = reln;
    this.gov = gov;
    this.dep = dep;
  }

  public GrammaticalRelation reln() {
    return reln;
  }

  public TreeGraphNode gov() {
    return gov;
  }

  public TreeGraphNode dep() {
    return dep;
  }

  public void setReln(GrammaticalRelation reln) {
    this.reln = reln;
  }

  public void setGov(TreeGraphNode gov) {
    this.gov = gov;
  }

  public void setDep(TreeGraphNode dep) {
    this.dep = dep;
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
    if (dep.label.get(CopyAnnotation.class) != null && dep.label.get(CopyAnnotation.class)) {
      return reln + "(" + gov + ", " + dep + "')";
    } else if (gov.label.get(CopyAnnotation.class) != null && gov.label.get(CopyAnnotation.class)) {
      return reln + "(" + gov + "', " + dep + ')';
    } else {
      return reln + "(" + gov + ", " + dep + ')';
    }
  }

  /*public int compareTo(TypedDependency other) {
    return this.dep().index() - other.dep().index();
  }*/

  public int compareTo(TypedDependency tdArg) {
     TreeGraphNode depArg = tdArg.dep();
     TreeGraphNode depThis = this.dep();
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
