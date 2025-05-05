package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;

/**
 * Edit an existing node to have new attributes.
 *
 * @author John Bauer
 */
public class EditNode extends SsurgeonEdit {
  public static final String LABEL = "editNode";

  final String nodeName;
  final List<String> removedAttributes;
  final List<String> removedMorpho;
  final Map<String, String> attributes;
  final Map<String, String> updateMorphoFeatures;

  public EditNode(String nodeName, Map<String, String> attributes, String updateMorphoFeatures, List<String> removedAttributes, List<String> removedMorpho) {
    if (nodeName == null) {
      throw new SsurgeonParseException("Cannot make an EditNode with no nodeName");
    }
    if (attributes.size() == 0 && updateMorphoFeatures == null && removedAttributes.size() == 0 && removedMorpho.size() == 0) {
      throw new SsurgeonParseException("Cannot make an EditNode with no updated attributes, removed attributes, or updated/removed morphological features");
    }
    AddDep.checkIllegalAttributes(attributes);
    this.nodeName = nodeName;
    this.attributes = new TreeMap<>(attributes);
    if (updateMorphoFeatures != null) {
      this.updateMorphoFeatures = new CoNLLUFeatures(updateMorphoFeatures);
    } else {
      this.updateMorphoFeatures = Collections.emptyMap();
    }
    this.removedAttributes = new ArrayList<>(removedAttributes);
    for (String attr : removedAttributes) {
      if (AnnotationLookup.toCoreKey(attr) == null) {
        throw new SsurgeonParseException("Unknown attribute |" + attr + "| when building an EditNode operation");
      }
    }
    this.removedMorpho = new ArrayList<>(removedMorpho);
  }


  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringBuilder buf = new StringBuilder();
    buf.append(LABEL);  buf.append("\t");
    buf.append(Ssurgeon.NODENAME_ARG);buf.append(" ");
    buf.append(nodeName); buf.append("\t");

    for (String key : attributes.keySet()) {
      buf.append("-");
      buf.append(key);
      buf.append(" ");
      buf.append(attributes.get(key));
      // TODO: why the stray quote characters?
      buf.append("\"\t");
    }

    if (this.updateMorphoFeatures.size() > 0) {
      buf.append(Ssurgeon.UPDATE_MORPHO_FEATURES);
      buf.append(" ");
      buf.append(CoNLLUFeatures.toFeatureString(this.updateMorphoFeatures));
    }

    for (String remove : removedMorpho) {
      buf.append(" ");
      buf.append(Ssurgeon.REMOVE_MORPHO_FEATURES);
      buf.append(" ");
      buf.append(remove);
    }

    return buf.toString();
  }

  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord word = sm.getNode(nodeName);
    if (word == null)
      return false;

    CoreLabel other = AddDep.fromCheapStrings(attributes);
    boolean changed = false;
    for (Class key : other.keySet()) {
      Object thisV = word.get(key);
      Object otherV = other.get(key);
      if (thisV == null || otherV == null) {
        if (thisV != null || otherV != null) {
          changed = true;
          word.set(key, otherV);
        }
      } else {
        if (!thisV.equals(otherV)) {
          changed = true;
          word.set(key, otherV);
        }
      }
    }

    for (String key : updateMorphoFeatures.keySet()) {
      CoNLLUFeatures features = word.get(CoreAnnotations.CoNLLUFeats.class);
      if (features == null) {
        changed = true;
        features = new CoNLLUFeatures();
        word.set(CoreAnnotations.CoNLLUFeats.class, features);
      }

      // this test will catch null, eg not yet assigned, features as well
      if (!updateMorphoFeatures.get(key).equals(features.get(key))) {
        changed = true;
        features.put(key, updateMorphoFeatures.get(key));
      }
    }

    for (String key : removedMorpho) {
      CoNLLUFeatures features = word.get(CoreAnnotations.CoNLLUFeats.class);
      if (features == null) {
        continue;
      }
      if (features.get(key) != null) {
        changed = true;
        features.remove(key);
      }
    }

    for (String key : removedAttributes) {
      Class<? extends CoreAnnotation<?>> clazz = AnnotationLookup.toCoreKey(key);
      if (word.remove((Class) clazz) != null) {
        changed = true;
      }
    }

    return changed;
  }
}
