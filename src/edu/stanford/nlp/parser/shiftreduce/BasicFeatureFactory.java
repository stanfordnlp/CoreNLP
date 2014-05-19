package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.TreeShapedStack;

public class BasicFeatureFactory implements FeatureFactory {
  enum Transition {
    LEFT, RIGHT, UNARY
  };

  enum FeatureComponent {
    HEADWORD, HEADTAG, VALUE
  };

  public static State.HeadPosition getSeparator(TreeShapedStack<State.HeadPosition> separators, int nodeNum) {
    if (separators.size() <= nodeNum) {
      return null;
    }

    for (int i = 0; i < nodeNum; ++i) {
      separators = separators.pop();
    }

    return separators.peek();
  }

  public static CoreLabel getStackLabel(TreeShapedStack<Tree> stack, int nodeNum, Transition ... transitions) {
    if (stack.size() <= nodeNum) {
      return null;
    }

    for (int i = 0; i < nodeNum; ++i) {
      stack = stack.pop();
    }

    Tree node = stack.peek();

    // TODO: this is nice for code readability, but might be expensive
    for (Transition t : transitions) {
      switch (t) {
      case LEFT:
        if (node.children().length != 2) {
          return null;
        }
        node = node.children()[0];
        break;
      case RIGHT:
        if (node.children().length != 2) {
          return null;
        }
        node = node.children()[1];
        break;
      case UNARY:
        if (node.children().length != 1) {
          return null;
        }
        node = node.children()[0];
        break;
      default:
        throw new IllegalArgumentException("Unknown transition type " + t);
      }
    }

    if (!(node.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Can only featurize CoreLabel trees");
    }
    return (CoreLabel) node.label();
  }

  public static CoreLabel getQueueLabel(List<Tree> sentence, int tokenPosition, int nodeNum) {
    if (tokenPosition + nodeNum < 0 || tokenPosition + nodeNum >= sentence.size()) { 
      return null;
    }

    Tree node = sentence.get(tokenPosition + nodeNum);
    if (!(node.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Can only featurize CoreLabel trees");
    }
    return (CoreLabel) node.label();
  }

  static final String NULL = "*NULL*";

  public static void addUnaryStackFeatures(Set<String> features, CoreLabel label, String conFeature, String wordFeature, String tagFeature) {
    String constituent = (label == null) ? NULL : label.value();
    String tag = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
    String word = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();

    features.add(conFeature + constituent);
    features.add(wordFeature + word + "-" + constituent);
    features.add(tagFeature + tag + "-" + constituent);
  }

  public static void addUnaryQueueFeatures(Set<String> features, CoreLabel label, String wtFeature) {
    String tag = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
    String word = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();

    // TODO: check to see if this is slow because of the string concat
    features.add(wtFeature + tag + "-" + word);
  }

  public static void addUnaryFeature(Set<String> features, String featureType, CoreLabel label, FeatureComponent feature) {
    String value = null;
    switch(feature) {
    case HEADWORD:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value = (label == null) ? NULL : label.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature);
    }

    features.add(featureType + value);
  }

  public static void addBinaryFeature(Set<String> features, String featureType, CoreLabel label1, FeatureComponent feature1, CoreLabel label2, FeatureComponent feature2) {
    String value1 = null;
    switch(feature1) {
    case HEADWORD:
      value1 = (label1 == null) ? NULL : label1.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value1 = (label1 == null) ? NULL : label1.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value1 = (label1 == null) ? NULL : label1.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature1);
    }

    String value2 = null;
    switch(feature2) {
    case HEADWORD:
      value2 = (label2 == null) ? NULL : label2.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value2 = (label2 == null) ? NULL : label2.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value2 = (label2 == null) ? NULL : label2.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature2);
    }

    features.add(featureType + value1 + "-" + value2);
  }

  public static void addTrigramFeature(Set<String> features, String featureType, CoreLabel label1, FeatureComponent feature1, CoreLabel label2, FeatureComponent feature2, CoreLabel label3, FeatureComponent feature3) {
    String value1 = null;
    switch(feature1) {
    case HEADWORD:
      value1 = (label1 == null) ? NULL : label1.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value1 = (label1 == null) ? NULL : label1.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value1 = (label1 == null) ? NULL : label1.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature1);
    }

    String value2 = null;
    switch(feature2) {
    case HEADWORD:
      value2 = (label2 == null) ? NULL : label2.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value2 = (label2 == null) ? NULL : label2.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value2 = (label2 == null) ? NULL : label2.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature2);
    }

    String value3 = null;
    switch(feature3) {
    case HEADWORD:
      value3 = (label3 == null) ? NULL : label3.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value3 = (label3 == null) ? NULL : label3.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value3 = (label3 == null) ? NULL : label3.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature3);
    }

    features.add(featureType + value1 + "-" + value2 + "-" + value3);
  }

  public static void addPositionFeatures(Set<String> features, State state) {
    if (state.tokenPosition >= state.sentence.size()) {
      features.add("QUEUE_FINISHED");
    }
    if (state.tokenPosition >= state.sentence.size() && state.stack.size() == 1) {
      features.add("QUEUE_FINISHED_STACK_SINGLETON");
    }
  }

  public static void addSeparatorFeature(Set<String> features, String featureType, State.HeadPosition separator) {
    if (separator == null) {
      return;
    }
    features.add(featureType + separator);
  }

  public static void addSeparatorFeature(Set<String> features, String featureType, CoreLabel label, FeatureComponent feature, State.HeadPosition separator) {
    if (separator == null) {
      return;
    }
    
    String value = null;
    switch(feature) {
    case HEADWORD:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadWordAnnotation.class).label().value();
      break;
    case HEADTAG:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadTagAnnotation.class).label().value();
      break;
    case VALUE:
      value = (label == null) ? NULL : label.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature);
    }

    features.add(featureType + value + "-" + separator);
  }

  public static void addSeparatorFeatures(Set<String> features, CoreLabel s0Label, CoreLabel s1Label, State.HeadPosition s0Separator, State.HeadPosition s1Separator) {
    addSeparatorFeature(features, "s0sep-", s0Separator);
    addSeparatorFeature(features, "s1sep-", s1Separator);

    addSeparatorFeature(features, "s0ws0sep-", s0Label, FeatureComponent.HEADWORD, s0Separator);
    addSeparatorFeature(features, "s0ws1sep-", s0Label, FeatureComponent.HEADWORD, s1Separator);
    addSeparatorFeature(features, "s1ws0sep-", s1Label, FeatureComponent.HEADWORD, s0Separator);
    addSeparatorFeature(features, "s1ws1sep-", s1Label, FeatureComponent.HEADWORD, s1Separator);

    addSeparatorFeature(features, "s0cs0sep-", s0Label, FeatureComponent.VALUE, s0Separator);
    addSeparatorFeature(features, "s0cs1sep-", s0Label, FeatureComponent.VALUE, s1Separator);
    addSeparatorFeature(features, "s1cs0sep-", s1Label, FeatureComponent.VALUE, s0Separator);
    addSeparatorFeature(features, "s1cs1sep-", s1Label, FeatureComponent.VALUE, s1Separator);

    addSeparatorFeature(features, "s0ts0sep-", s0Label, FeatureComponent.HEADTAG, s0Separator);
    addSeparatorFeature(features, "s0ts1sep-", s0Label, FeatureComponent.HEADTAG, s1Separator);
    addSeparatorFeature(features, "s1ts0sep-", s1Label, FeatureComponent.HEADTAG, s0Separator);
    addSeparatorFeature(features, "s1ts1sep-", s1Label, FeatureComponent.HEADTAG, s1Separator);
  }

  public Set<String> featurize(State state) {
    Set<String> features = Generics.newHashSet();

    final TreeShapedStack<Tree> stack = state.stack;
    final List<Tree> sentence = state.sentence;
    final int tokenPosition = state.tokenPosition;

    CoreLabel s0Label = getStackLabel(stack, 0); // current top of stack
    CoreLabel s1Label = getStackLabel(stack, 1); // one previous
    CoreLabel s2Label = getStackLabel(stack, 2); // two previous
    CoreLabel s3Label = getStackLabel(stack, 3); // three previous

    CoreLabel s0LLabel = getStackLabel(stack, 0, Transition.LEFT);
    CoreLabel s0RLabel = getStackLabel(stack, 0, Transition.RIGHT);
    CoreLabel s0ULabel = getStackLabel(stack, 0, Transition.UNARY);

    CoreLabel s1LLabel = getStackLabel(stack, 1, Transition.LEFT);
    CoreLabel s1RLabel = getStackLabel(stack, 1, Transition.RIGHT);
    CoreLabel s1ULabel = getStackLabel(stack, 1, Transition.UNARY);

    CoreLabel q0Label = getQueueLabel(sentence, tokenPosition, 0); // current location in queue
    CoreLabel q1Label = getQueueLabel(sentence, tokenPosition, 1); // next location in queue
    CoreLabel q2Label = getQueueLabel(sentence, tokenPosition, 2); // two locations later in queue
    CoreLabel q3Label = getQueueLabel(sentence, tokenPosition, 3); // three locations later in queue
    CoreLabel qP1Label = getQueueLabel(sentence, tokenPosition, -1); // previous location in queue
    CoreLabel qP2Label = getQueueLabel(sentence, tokenPosition, -2); // two locations prior in queue

    addUnaryStackFeatures(features, s0Label, "S0C-", "S0WC-", "S0TC-");
    addUnaryStackFeatures(features, s1Label, "S1C-", "S1WC-", "S1TC-");
    addUnaryStackFeatures(features, s2Label, "S2C-", "S2WC-", "S2TC-");
    addUnaryStackFeatures(features, s3Label, "S3C-", "S3WC-", "S3TC-");

    addUnaryStackFeatures(features, s0LLabel, "S0LC-", "S0LWC-", "S0LTC-");
    addUnaryStackFeatures(features, s0RLabel, "S0RC-", "S0RWC-", "S0RTC-");
    addUnaryStackFeatures(features, s0ULabel, "S0UC-", "S0UWC-", "S0UTC-");

    addUnaryStackFeatures(features, s1LLabel, "S1LC-", "S1LWC-", "S1LTC-");
    addUnaryStackFeatures(features, s1RLabel, "S1RC-", "S1RWC-", "S1RTC-");
    addUnaryStackFeatures(features, s1ULabel, "S1UC-", "S1UWC-", "S1UTC-");

    addUnaryQueueFeatures(features, q0Label, "Q0WT-");
    addUnaryQueueFeatures(features, q1Label, "Q1WT-");
    addUnaryQueueFeatures(features, q2Label, "Q2WT-");
    addUnaryQueueFeatures(features, q3Label, "Q3WT-");
    addUnaryQueueFeatures(features, qP1Label, "QP1WT-");
    addUnaryQueueFeatures(features, qP2Label, "QP2WT-");

    addBinaryFeature(features, "S0WS1W-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S0WS1C-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE);
    addBinaryFeature(features, "S0CS1W-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S0CS1C-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE);

    addBinaryFeature(features, "S0WQ0W-", s0Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S0WQ0T-", s0Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addBinaryFeature(features, "S0CQ0W-", s0Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S0CQ0T-", s0Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);

    addBinaryFeature(features, "Q0WQ1W-", q0Label, FeatureComponent.HEADWORD, q1Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "Q0WQ1T-", q0Label, FeatureComponent.HEADWORD, q1Label, FeatureComponent.HEADTAG);
    addBinaryFeature(features, "Q0TQ1W-", q0Label, FeatureComponent.HEADTAG, q1Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "Q0TQ1T-", q0Label, FeatureComponent.HEADTAG, q1Label, FeatureComponent.HEADTAG);

    addBinaryFeature(features, "S1WQ0W-", s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S1WQ0T-", s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addBinaryFeature(features, "S1CQ0W-", s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADWORD);
    addBinaryFeature(features, "S1CQ0T-", s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);

    addTrigramFeature(features, "S0cS1cS2c-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0wS1cS2c-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0cS1wQ0t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cS1cS2w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.HEADWORD);
    addTrigramFeature(features, "S0cS1cQ0t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0wS1cQ0t-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cS1wQ0t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cS1cQ0w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADWORD);

    addPositionFeatures(features, state);

    State.HeadPosition s0Separator = getSeparator(state.separators, 0);
    State.HeadPosition s1Separator = getSeparator(state.separators, 1);
    addSeparatorFeatures(features, s0Label, s1Label, s0Separator, s1Separator);

    return features;
  }

  private static final long serialVersionUID = 1;  
}

