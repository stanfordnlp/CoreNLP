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
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;

/**
 * Edit an existing node to have new attributes.
 *<br>
 * The attribute values and the updated features may refer to variables
 * of the match, such as {@code -lemma ${stem}ar}, as {@link SsurgeonTemplate}
 * describes.  If a variable is not set, the node is left as it is.
 *
 * @author John Bauer
 */
public class EditNode extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "editNode";

  final String nodeName;
  final List<String> removedAttributes;
  final List<String> removedMorpho;
  /** The attribute values as written in the edit */
  final Map<String, String> attributes;
  /** The attribute values, parsed for variables */
  final Map<String, SsurgeonTemplate> attributeTemplates;
  /** The attribute values, if none of them refer to variables, or else null */
  final Map<String, String> literalAttributes;
  /** The features to update, if they do not refer to variables, or else empty */
  final Map<String, String> updateMorphoFeatures;
  /** The features to update, if they refer to variables, or else null */
  final SsurgeonTemplate updateMorphoTemplate;

  /**
   * Creates an edit which changes the attributes of a named node.
   *
   * @param nodeName The name of the node to edit
   * @param attributes Attributes to set, as attribute names and string values, which may refer to variables
   * @param updateMorphoFeatures CoNLL-U features to add or overwrite, which may refer to variables, or null for none
   * @param removedAttributes Names of attributes to remove from the node
   * @param removedMorpho Names of CoNLL-U features to remove from the node
   * @throws SsurgeonParseException if nodeName is null, there is nothing to edit,
   *   an attribute cannot be set (see {@link AddDep#checkIllegalAttributes}),
   *   a value refers to a variable in a malformed way, or a removed attribute is unknown
   */
  public EditNode(String nodeName, Map<String, String> attributes, String updateMorphoFeatures, List<String> removedAttributes, List<String> removedMorpho) {
    if (nodeName == null) {
      throw new SsurgeonParseException("Cannot make an EditNode with no nodeName");
    }
    if (attributes.size() == 0 && updateMorphoFeatures == null && removedAttributes.size() == 0 && removedMorpho.size() == 0) {
      throw new SsurgeonParseException("Cannot make an EditNode with no updated attributes, removed attributes, or updated/removed morphological features");
    }
    this.nodeName = nodeName;
    this.attributes = new TreeMap<>(attributes);
    this.attributeTemplates = SsurgeonTemplate.attributes(attributes);
    this.literalAttributes = literalValues(attributeTemplates);
    SsurgeonTemplate morphoTemplate = updateMorphoFeatures == null ? null : new SsurgeonTemplate(updateMorphoFeatures);
    if (morphoTemplate == null) {
      this.updateMorphoFeatures = Collections.emptyMap();
      this.updateMorphoTemplate = null;
    } else if (morphoTemplate.hasVariables()) {
      this.updateMorphoFeatures = Collections.emptyMap();
      this.updateMorphoTemplate = morphoTemplate;
    } else {
      this.updateMorphoFeatures = new CoNLLUFeatures(morphoTemplate.literal());
      this.updateMorphoTemplate = null;
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
   * The values of the attributes, if none of them refer to variables, or else null
   */
  static Map<String, String> literalValues(Map<String, SsurgeonTemplate> templates) {
    Map<String, String> values = new TreeMap<>();
    for (Map.Entry<String, SsurgeonTemplate> entry : templates.entrySet()) {
      if (entry.getValue().hasVariables()) {
        return null;
      }
      values.put(entry.getKey(), entry.getValue().literal());
    }
    return values;
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
      buf.append("\"");
      buf.append(attributes.get(key));
      buf.append("\"\t");
    }

    for (String remove : removedAttributes) {
      buf.append(Ssurgeon.REMOVE);
      buf.append(" ");
      buf.append(remove);
      buf.append("\t");
    }

    if (this.updateMorphoTemplate != null) {
      buf.append(Ssurgeon.UPDATE_MORPHO_FEATURES);
      buf.append(" ");
      buf.append(this.updateMorphoTemplate.text);
    } else if (this.updateMorphoFeatures.size() > 0) {
      buf.append(Ssurgeon.UPDATE_MORPHO_FEATURES);
      buf.append(" ");
      // a $ in a feature is written as $$, so it reads back as a $
      buf.append(CoNLLUFeatures.toFeatureString(this.updateMorphoFeatures).replace("$", "$$"));
    }

    for (String remove : removedMorpho) {
      buf.append(" ");
      buf.append(Ssurgeon.REMOVE_MORPHO_FEATURES);
      buf.append(" ");
      buf.append(remove);
    }

    return buf.toString();
  }

  /**
   * Edits the attributes and features of the named word.
   *<br>
   * The graphs share their words, so the edit is seen in all of them,
   * whichever graph the word was matched in.  That includes an empty word
   * found only in the enhanced graph.
   *<br>
   * If a value refers to a variable which is not set in this match,
   * nothing is edited.
   *
   * @throws SsurgeonRuntimeException if a value with variables comes out as one the attribute cannot take
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm) {
    IndexedWord word = sm.getNode(nodeName);
    if (word == null)
      return false;

    final Map<String, String> values;
    final CoreLabel other;
    if (literalAttributes != null) {
      values = literalAttributes;
      other = AddDep.fromCheapStrings(values);
    } else {
      values = SsurgeonTemplate.expand(attributeTemplates, sm);
      if (values == null) {
        return false;
      }
      try {
        other = AddDep.fromCheapStrings(values);
      } catch (RuntimeException e) {
        throw new SsurgeonRuntimeException("EditNode could not set the attributes " + values, e);
      }
    }
    Map<String, String> updateMorphoFeatures = this.updateMorphoFeatures;
    if (updateMorphoTemplate != null) {
      String features = updateMorphoTemplate.expand(sm);
      if (features == null) {
        return false;
      }
      updateMorphoFeatures = new CoNLLUFeatures(features);
    }

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
