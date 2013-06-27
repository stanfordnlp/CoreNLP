package edu.stanford.nlp.ling;

import junit.framework.TestCase;

/** Tests the behavior of things implementing the Label interface and the
 *  traditional behavior of things now in the ValueLabel hierarchy.
 *
 *  @author Christopher Manning
 */
public class LabelsTest extends TestCase {

  @Override
  public void setUp() {
  }

  private static void internalValidation(String type, Label lab, String val) {
    assertEquals(type + " does not have value it was constructed with", lab.value(), val);
    String newVal = "feijoa";
    lab.setValue(newVal);
    assertEquals(type + " does not have value set with setValue", newVal, lab.value());
    // restore value
    lab.setValue(val);
    String out = lab.toString();
    Label lab3 = lab.labelFactory().newLabel(val);
    assertEquals(type + " made by label factory has diferent value", lab.value(), lab3.value());
    lab3 = lab.labelFactory().newLabel(lab);
    assertEquals(type + " made from label factory is not equal", lab, lab3);
    try {
      Label lab2 = lab.labelFactory().newLabelFromString(out);
      assertEquals(type + " factory fromString and toString are not inverses", lab, lab2);
      lab3.setFromString(out);
      assertEquals(type + " setFromString and toString are not inverses", lab, lab3);
    } catch (UnsupportedOperationException uoe) {
      // It's okay to not support the fromString operation
    }
  }

  private static void validateHasTag(String type, HasTag lab, String tag) {
    assertEquals(type + " does not have tag it was constructed with", lab.tag(), tag);
    String newVal = "feijoa";
    lab.setTag(newVal);
    assertEquals(type + " does not have tag set with setTag", newVal, lab.tag());
    // restore value
    lab.setTag(tag);
  }

  public void testStringLabel() {
    String val = "octopus";
    Label sl = new StringLabel(val);
    internalValidation("StringLabel ", sl, val);
  }

  public void testWord() {
    String val = "octopus";
    Label sl = new Word(val);
    internalValidation("Word ", sl, val);
  }

  public void testTaggedWord() {
    String val = "fish";
    TaggedWord sl = new TaggedWord(val);
    internalValidation("TaggedWord", sl, val);
    String tag = "NN";
    sl = new TaggedWord(val, tag);
    internalValidation("TaggedWord", sl, val);
    validateHasTag("TaggedWord", sl, tag);
    TaggedWord tw2 = new TaggedWord(sl);
    internalValidation("TaggedWord", tw2, val);
    validateHasTag("TaggedWord", tw2, tag);
  }

  public void testWordTag() {
    String val = "fowl";
    WordTag sl = new WordTag(val);
    internalValidation("WordTag", sl, val);
    String tag = "NN";
    sl = new WordTag(val, tag);
    internalValidation("WordTag", sl, val);
    validateHasTag("WordTag", sl, tag);
    WordTag wt2 = new WordTag(sl);
    internalValidation("WordTag", wt2, val);
    validateHasTag("WordTag", wt2, tag);
  }


}
