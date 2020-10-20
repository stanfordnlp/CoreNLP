package edu.stanford.nlp.trees;

import java.util.List;

import edu.stanford.nlp.ling.HasLemma;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.Morphology;

public class TreeLemmatizer implements TreeTransformer {

  @Override
  public Tree transformTree(Tree t) {
    Morphology morphology = new Morphology();

    List<TaggedWord> tagged = null;
    int index = 0;

    for (Tree leaf : t.getLeaves()) {
      Label label = leaf.label();
      if (label == null) {
        continue;
      }

      String tag;
      if (!(label instanceof HasTag) || ((HasTag) label).tag() == null) {
        if (tagged == null) {
          tagged = t.taggedYield();
        }
        tag = tagged.get(index).tag();
      } else {
        tag = ((HasTag) label).tag();
      }

      if (!(label instanceof HasLemma)) {
        throw new IllegalArgumentException("Got a tree with labels which do not support lemma");
      }
      ((HasLemma) label).setLemma(morphology.lemma(label.value(), tag, true));
      ++index;
    }
    return t;
  }

}
