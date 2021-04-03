package edu.stanford.nlp.scenegraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;


import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageObject;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Triple;

public class SceneGraphEvaluation {

  public Triple<Double,Double,Double> evaluate(SceneGraph scene, SceneGraphImageRegion region) {
    Counter<SceneGraphRelationTriplet> goldTriplets = new ClassicCounter<SceneGraphRelationTriplet>();
    Counter<SceneGraphRelationTriplet> predictedTriplets = new ClassicCounter<SceneGraphRelationTriplet>();

    for (SceneGraphNode node : scene.nodeListSorted()) {
      for (SceneGraphAttribute attr : node.getAttributes()) {
        SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(node.value().backingLabel(), attr.value().backingLabel(), "is");
        predictedTriplets.incrementCount(t);
      }
    }

    for (SceneGraphRelation reln : scene.relationListSorted()) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(reln.getSource().value().backingLabel(), reln.getTarget().value().backingLabel(), reln.getRelation());
      predictedTriplets.incrementCount(t);
    }


    for (SceneGraphImageAttribute attr : region.attributes) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(attr);
      goldTriplets.incrementCount(t);
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(reln);
      goldTriplets.incrementCount(t);
    }


    double predictedCount = predictedTriplets.totalCount();
    double goldCount = goldTriplets.totalCount();

    double numerator = 0.0;

    for (SceneGraphRelationTriplet t : goldTriplets.keySet()) {
      double gold = goldTriplets.getCount(t);
      double pred = predictedTriplets.getCount(t);
      numerator += Math.min(gold, pred);
    }


    double precision = predictedCount > 0 ? numerator / predictedCount : 1.0;
    double recall = goldCount > 0 ? numerator / goldCount : 1.0;
    double f1 = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;


    Triple<Double, Double, Double> scores = new Triple<Double,Double,Double>(precision, recall, f1);
    return scores;
  }

  public Triple<Double,Double,Double> evaluate(SceneGraphImageRegion predicted, SceneGraphImageRegion region) {
    Counter<SceneGraphRelationTriplet> goldTriplets = new ClassicCounter<SceneGraphRelationTriplet>();
    Counter<SceneGraphRelationTriplet> predictedTriplets = new ClassicCounter<SceneGraphRelationTriplet>();

    for (SceneGraphImageAttribute attr : predicted.attributes) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(attr);
      predictedTriplets.incrementCount(t);
    }

    for (SceneGraphImageRelationship reln : predicted.relationships) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(reln);
      predictedTriplets.incrementCount(t);
    }


    for (SceneGraphImageAttribute attr : region.attributes) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(attr);
      goldTriplets.incrementCount(t);
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      SceneGraphRelationTriplet t = new SceneGraphRelationTriplet(reln);
      goldTriplets.incrementCount(t);
    }


    double predictedCount = predictedTriplets.totalCount();
    double goldCount = goldTriplets.totalCount();

    double numerator = 0.0;

    for (SceneGraphRelationTriplet t : goldTriplets.keySet()) {
      double gold = goldTriplets.getCount(t);
      double pred = predictedTriplets.getCount(t);
      numerator += Math.min(gold, pred);
    }


    double precision = predictedCount > 0 ? numerator / predictedCount : 1.0;
    double recall = goldCount > 0 ? numerator / goldCount : 1.0;
    double f1 = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;


    Triple<Double, Double, Double> scores = new Triple<Double,Double,Double>(precision, recall, f1);
    return scores;
  }




  public void toSmatchString(SceneGraph scene, SceneGraphImageRegion region, PrintWriter predWriter, PrintWriter goldWriter) throws IOException {
    StringBuffer predString = new StringBuffer();
    StringBuffer goldString = new StringBuffer();

    List<SceneGraphNode> nodes = scene.nodeListSorted();

    boolean first = true;
    for (int i = 0, sz = nodes.size(); i < sz; i++) {
      SceneGraphNode node = nodes.get(i);
      if ( ! first) {
        predString.append("|||");
      } else {
        first = false;
      }
      predString.append(String.format("instance###a%d###%s", i, node.toJSONString()));
      for (SceneGraphAttribute attr : node.getAttributes()) {
        predString.append("|||");
        predString.append(String.format("is###a%d###%s", i, attr.toString()));

      }
    }

    for (SceneGraphRelation reln : scene.relationListSorted()) {
      int node1Idx = nodes.indexOf(reln.getSource());
      int node2Idx = nodes.indexOf(reln.getTarget());
      predString.append("|||");
      predString.append(String.format("%s###a%d###%s", reln.getRelation(),  node1Idx, node2Idx));
    }

    if (first) {
      predString.append("-");
    }

    predWriter.println(predString.toString());

    Map<SceneGraphImageObject,Integer> objects = Generics.newHashMap();

    first = true;
    int i = 0;
    for (SceneGraphImageAttribute attr : region.attributes) {
      if ( ! objects.containsKey(attr.subject)) {
        if ( ! first) {
          goldString.append("|||");
        } else {
          first = false;
        }
        goldString.append(String.format("instance###b%d###%s", i, attr.subjectLemmaGloss()));
        objects.put(attr.subject, i++);
      }
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      if ( ! objects.containsKey(reln.subject)) {
        if ( ! first) {
          goldString.append("|||");
        } else {
          first = false;
        }
        goldString.append(String.format("instance###b%d###%s", i, reln.subjectLemmaGloss()));
        objects.put(reln.subject, i++);
      }
      if ( ! objects.containsKey(reln.object)) {
        goldString.append("|||");
        goldString.append(String.format("instance###b%d###%s", i, reln.objectLemmaGloss()));
        objects.put(reln.object, i++);
      }
    }


    for (SceneGraphImageAttribute attr : region.attributes) {
      goldString.append("|||");
      goldString.append(String.format("is###b%d###%s", objects.get(attr.subject), attr.attributeLemmaGloss()));
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      goldString.append("|||");
      goldString.append(String.format("%s###b%d###b%d", reln.predicateLemmaGloss(), objects.get(reln.subject), objects.get(reln.object)));
    }


    goldWriter.println(goldString.toString());


  }

  public void toSmatchString(SceneGraphImageRegion predicted, SceneGraphImageRegion region, PrintWriter predWriter, PrintWriter goldWriter) throws IOException {
    StringBuffer predString = new StringBuffer();

    Map<SceneGraphImageObject,Integer> predictedObjects = Generics.newHashMap();


    boolean first = true;
    int i = 0;
    for (SceneGraphImageAttribute attr : predicted.attributes) {
      if ( ! predictedObjects.containsKey(attr.subject)) {
        if ( ! first) {
          predString.append("|||");
        } else {
          first = false;
        }
        predString.append(String.format("instance###a%d###%s", i, attr.subjectLemmaGloss()));
        predictedObjects.put(attr.subject, i++);
      }
    }

    for (SceneGraphImageRelationship reln : predicted.relationships) {
      if ( ! predictedObjects.containsKey(reln.subject)) {
        if ( ! first) {
          predString.append("|||");
        } else {
          first = false;
        }
        predString.append(String.format("instance###a%d###%s", i, reln.subjectLemmaGloss()));
        predictedObjects.put(reln.subject, i++);
      }
      if ( ! predictedObjects.containsKey(reln.object)) {
        predString.append("|||");
        predString.append(String.format("instance###a%d###%s", i, reln.objectLemmaGloss()));
        predictedObjects.put(reln.object, i++);
      }
    }


    for (SceneGraphImageAttribute attr : predicted.attributes) {
      predString.append("|||");
      predString.append(String.format("is###a%d###%s", predictedObjects.get(attr.subject), attr.attributeLemmaGloss()));
    }

    for (SceneGraphImageRelationship reln : predicted.relationships) {
      predString.append("|||");
      predString.append(String.format("%s###a%d###a%d", reln.predicateLemmaGloss(), predictedObjects.get(reln.subject), predictedObjects.get(reln.object)));
    }


    predWriter.println(predString.toString());

    StringBuffer goldString = new StringBuffer();


    Map<SceneGraphImageObject,Integer> objects = Generics.newHashMap();

    first = true;
    i = 0;
    for (SceneGraphImageAttribute attr : region.attributes) {
      if ( ! objects.containsKey(attr.subject)) {
        if ( ! first) {
          goldString.append("|||");
        } else {
          first = false;
        }
        goldString.append(String.format("instance###b%d###%s", i, attr.subjectLemmaGloss()));
        objects.put(attr.subject, i++);
      }
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      if ( ! objects.containsKey(reln.subject)) {
        if ( ! first) {
          goldString.append("|||");
        } else {
          first = false;
        }
        goldString.append(String.format("instance###b%d###%s", i, reln.subjectLemmaGloss()));
        objects.put(reln.subject, i++);
      }
      if ( ! objects.containsKey(reln.object)) {
        goldString.append("|||");
        goldString.append(String.format("instance###b%d###%s", i, reln.objectLemmaGloss()));
        objects.put(reln.object, i++);
      }
    }


    for (SceneGraphImageAttribute attr : region.attributes) {
      goldString.append("|||");
      goldString.append(String.format("is###b%d###%s", objects.get(attr.subject), attr.attributeLemmaGloss()));
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      goldString.append("|||");
      goldString.append(String.format("%s###b%d###b%d", reln.predicateLemmaGloss(), objects.get(reln.subject), objects.get(reln.object)));
    }


    goldWriter.println(goldString.toString());


  }


  private class SceneGraphRelationTriplet {

    protected String subject;
    protected String object;
    protected String subjectLemma;
    protected String objectLemma;
    protected String relation;
    protected String predSubj;
    protected String predObj;

    public SceneGraphRelationTriplet(SceneGraphImageAttribute attr) {
      this.subject = attr.subjectGloss();
      this.object = attr.attributeGloss();
      this.subjectLemma = attr.subjectLemmaGloss();
      this.objectLemma = attr.attributeLemmaGloss();
      this.relation = "is";
      this.predSubj = this.subjectLemma;
      this.predObj = this.objectLemma;

    }

    public SceneGraphRelationTriplet(SceneGraphImageRelationship reln) {
      this.subject = reln.subjectGloss();
      this.object = reln.objectGloss();
      this.subjectLemma = reln.subjectLemmaGloss();
      this.objectLemma = reln.objectLemmaGloss();
      this.predSubj = this.subjectLemma;
      this.predObj = this.objectLemma;
      this.relation = reln.predicateLemmaGloss();
    }

    public SceneGraphRelationTriplet(CoreLabel subj, CoreLabel obj, String relation) {
      this.subject = subj.word();
      this.object = obj.word();
      this.subjectLemma = subj.lemma();
      this.objectLemma = obj.lemma();
      this.relation = relation;
      this.predSubj = subj.getString(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class);
      if (this.predSubj == null) this.predSubj = this.subjectLemma;
      this.predObj = obj.getString(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class);
      if (this.predObj == null) this.predObj = this.objectLemma;
    }

    @Override
    public int hashCode() {
      return this.relation.hashCode();
    }


    @Override
    public boolean equals(Object otherObj) {
      if (otherObj == null) return false;
      if ( ! (otherObj instanceof SceneGraphRelationTriplet)) return false;

      SceneGraphRelationTriplet o = (SceneGraphRelationTriplet) otherObj;

      if ( ! this.subject.equals(o.subject) && ! this.subjectLemma.equals(o.subjectLemma)) {
        return false;
      }

      if ( ! this.object.equals(o.object) && ! this.objectLemma.equals(o.objectLemma)) {
        return false;
      }

      if ( ! this.relation.equals(o.relation)) {
        return false;
      }

      return true;
    }
  }

}
