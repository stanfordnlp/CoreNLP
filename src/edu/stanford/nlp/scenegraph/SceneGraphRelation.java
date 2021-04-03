package edu.stanford.nlp.scenegraph;


/**
 *
 * @author Sebastian Schuster
 *
 */

public class SceneGraphRelation implements Comparable<SceneGraphRelation> {

  private final SceneGraphNode source;
  private final SceneGraphNode target;
  private final String relation;


  public SceneGraphRelation(SceneGraphNode source, SceneGraphNode target, String relation) {
    this.source = source;
    this.target = target;
    this.relation = relation;
  }


  public SceneGraphNode getSource() {
    return source;
  }


  public SceneGraphNode getTarget() {
    return target;
  }


  public String getRelation() {
    return relation;
  }


  @Override
  public int compareTo(SceneGraphRelation o) {
    if (o == null) {
      return 1;
    }

    int ret = this.source.compareTo(o.source);
    if (ret != 0) {
      return ret;
    }

    ret = this.target.compareTo(o.target);

    if (ret != 0) {
      return ret;
    }

    ret = this.relation.compareTo(o.relation);

    return ret;
  }

  @Override
  public int hashCode() {
    return new int[]{this.source.hashCode(), this.target.hashCode(), this.relation.hashCode()}.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if ( ! (o instanceof SceneGraphRelation)) {
      return false;
    }

    SceneGraphRelation oReln = (SceneGraphRelation) o;

    return this.source.equals(oReln.source)
        && this.target.equals(oReln.target)
        && this.relation.equals(oReln.relation);
  }

}
