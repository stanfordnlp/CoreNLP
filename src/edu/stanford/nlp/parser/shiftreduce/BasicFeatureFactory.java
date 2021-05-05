package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.TreeShapedStack;

public class BasicFeatureFactory extends FeatureFactory {
  public static void addUnaryStackFeatures(List<String> features, CoreLabel label, String conFeature, String wordTagFeature, String tagFeature, String wordConFeature, String tagConFeature) {
    if (label == null) {
      features.add(conFeature + NULL);
      return;
    }
    String constituent = getFeatureFromCoreLabel(label, FeatureComponent.VALUE);
    String tag = getFeatureFromCoreLabel(label, FeatureComponent.HEADTAG);
    String word = getFeatureFromCoreLabel(label, FeatureComponent.HEADWORD);

    features.add(conFeature + constituent);
    features.add(wordTagFeature + word + "-" + tag);
    features.add(tagFeature + tag);
    features.add(wordConFeature + word + "-" + constituent);
    features.add(tagConFeature + tag + "-" + constituent);
  }

  public static void addUnaryQueueFeatures(List<String> features, CoreLabel label, String wtFeature) {
    if (label == null) {
      features.add(wtFeature + NULL);
      return;
    }
    String tag = label.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class).value();
    String word = label.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class).value();

    // TODO: check to see if this is slow because of the string concat
    features.add(wtFeature + tag + "-" + word);
  }

  public static void addBinaryFeatures(List<String> features,
                                       String name1, CoreLabel label1, FeatureComponent feature11, FeatureComponent feature12,
                                       String name2, CoreLabel label2, FeatureComponent feature21, FeatureComponent feature22) {
    if (label1 == null) {
      if (label2 == null) {
        features.add(name1 + "n" + name2 + "n");
      } else {
        addUnaryFeature(features, name1 + "n" + name2 + feature21.shortName() + "-", label2, feature21);
        addUnaryFeature(features, name1 + "n" + name2 + feature22.shortName() + "-", label2, feature22);
      }
    } else if (label2 == null) {
      addUnaryFeature(features, name1 + feature11.shortName() + name2 + "n-", label1, feature11);
      addUnaryFeature(features, name1 + feature12.shortName() + name2 + "n-", label1, feature12);
    } else {
      addBinaryFeature(features, name1 + feature11.shortName() + name2 + feature21.shortName() + "-", label1, feature11, label2, feature21);
      addBinaryFeature(features, name1 + feature11.shortName() + name2 + feature22.shortName() + "-", label1, feature11, label2, feature22);
      addBinaryFeature(features, name1 + feature12.shortName() + name2 + feature21.shortName() + "-", label1, feature12, label2, feature21);
      addBinaryFeature(features, name1 + feature12.shortName() + name2 + feature22.shortName() + "-", label1, feature12, label2, feature22);
    }
  }

  public static void addUnaryFeature(List<String> features, String featureType, CoreLabel label, FeatureComponent feature) {
    String value = getFeatureFromCoreLabel(label, feature);
    features.add(featureType + value);
  }

  public static void addBinaryFeature(List<String> features, String featureType, CoreLabel label1, FeatureComponent feature1, CoreLabel label2, FeatureComponent feature2) {
    String value1 = getFeatureFromCoreLabel(label1, feature1);
    String value2 = getFeatureFromCoreLabel(label2, feature2);
    features.add(featureType + value1 + "-" + value2);
  }

  public static void addTrigramFeature(List<String> features, String featureType, CoreLabel label1, FeatureComponent feature1, CoreLabel label2, FeatureComponent feature2, CoreLabel label3, FeatureComponent feature3) {
    String value1 = getFeatureFromCoreLabel(label1, feature1);
    String value2 = getFeatureFromCoreLabel(label2, feature2);
    String value3 = getFeatureFromCoreLabel(label3, feature3);

    features.add(featureType + value1 + "-" + value2 + "-" + value3);
  }

  public static void addPositionFeatures(List<String> features, State state) {
    if (state.tokenPosition >= state.sentence.size()) {
      features.add("QUEUE_FINISHED");
    }
    if (state.tokenPosition >= state.sentence.size() && state.stack.size() == 1) {
      features.add("QUEUE_FINISHED_STACK_SINGLETON");
    }
    // turns out it's quite hard to distinguish between IdleTransition
    // and FinalizeTransition without this
    if (state.isFinished()) {
      features.add("FINALIZED");
    }
  }

  public static void addSeparatorFeature(List<String> features, String featureType, State.HeadPosition separator) {
    if (separator == null) {
      return;
    }
    features.add(featureType + separator);
  }

  public static void addSeparatorFeature(List<String> features, String featureType, CoreLabel label, FeatureComponent feature, State.HeadPosition separator) {
    if (separator == null) {
      return;
    }

    String value = getFeatureFromCoreLabel(label, feature);

    features.add(featureType + value + "-" + separator);
  }

  public static void addSeparatorFeature(List<String> features, String featureType, CoreLabel label, FeatureComponent feature, boolean between) {
    String value = getFeatureFromCoreLabel(label, feature);

    features.add(featureType + value + "-" + between);
  }

  public static void addSeparatorFeature(List<String> features, String featureType, CoreLabel label1, FeatureComponent feature1, CoreLabel label2, FeatureComponent feature2, boolean between) {
    String value1 = getFeatureFromCoreLabel(label1, feature1);
    String value2 = getFeatureFromCoreLabel(label2, feature2);

    features.add(featureType + value1 + "-" + value2 + "-" + between);
  }

  public static void addSeparatorFeatures(List<String> features, String name1, CoreLabel label1, String name2, CoreLabel label2, String separatorBetween, int countBetween) {
    if (label1 == null || label2 == null) {
      return;
    }

    // 0 separators is captured by the countBetween features
    if (separatorBetween != null) {
      String separatorBetweenName = "Sepb" + name1 + name2 + "-" + separatorBetween + "-";
      addUnaryFeature(features, name1 + "w" + separatorBetweenName, label1, FeatureComponent.HEADWORD);
      addBinaryFeature(features, name1 + "wc" + separatorBetweenName, label1, FeatureComponent.HEADWORD, label1, FeatureComponent.VALUE);
      addUnaryFeature(features, name2 + "w" + separatorBetweenName, label2, FeatureComponent.HEADWORD);
      addBinaryFeature(features, name2 + "wc" + separatorBetweenName, label2, FeatureComponent.HEADWORD, label2, FeatureComponent.VALUE);
      addBinaryFeature(features, name1 + "c" + name2 + "c" + separatorBetweenName, label1, FeatureComponent.VALUE, label2, FeatureComponent.VALUE);
    }

    String countBetweenName = "Sepb" + name1 + name2 + "-" + countBetween + "-";
    addUnaryFeature(features, name1 + "w" + countBetweenName, label1, FeatureComponent.HEADWORD);
    addBinaryFeature(features, name1 + "wc" + countBetweenName, label1, FeatureComponent.HEADWORD, label1, FeatureComponent.VALUE);
    addUnaryFeature(features, name2 + "w" + countBetweenName, label2, FeatureComponent.HEADWORD);
    addBinaryFeature(features, name2 + "wc" + countBetweenName, label2, FeatureComponent.HEADWORD, label2, FeatureComponent.VALUE);
    addBinaryFeature(features, name1 + "c" + name2 + "c" + countBetweenName, label1, FeatureComponent.VALUE, label2, FeatureComponent.VALUE);
  }

  public static void addSeparatorFeatures(List<String> features, CoreLabel s0Label, CoreLabel s1Label, State.HeadPosition s0Separator, State.HeadPosition s1Separator) {
    boolean between = false;
    if ((s0Separator != null && (s0Separator == State.HeadPosition.BOTH || s0Separator == State.HeadPosition.LEFT)) ||
        (s1Separator != null && (s1Separator == State.HeadPosition.BOTH || s1Separator == State.HeadPosition.RIGHT))) {
      between = true;
    }

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

    if (s0Label != null && s1Label != null) {
      addSeparatorFeature(features, "s0wsb-", s0Label, FeatureComponent.HEADWORD, between);
      addSeparatorFeature(features, "s1wsb-", s1Label, FeatureComponent.HEADWORD, between);

      addSeparatorFeature(features, "s0csb-", s0Label, FeatureComponent.VALUE, between);
      addSeparatorFeature(features, "s1csb-", s1Label, FeatureComponent.VALUE, between);

      addSeparatorFeature(features, "s0tsb-", s0Label, FeatureComponent.HEADTAG, between);
      addSeparatorFeature(features, "s1tsb-", s1Label, FeatureComponent.HEADTAG, between);

      addSeparatorFeature(features, "s0cs1csb-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, between);
    }
  }

  /**
   * Could potentially add the tags and words for the left and right
   * ends of the tree.  Also adds notes about the sizes of the given
   * tree.  However, it seems somewhat slow and doesn't help accuracy.
   */
  public void addEdgeFeatures(List<String> features, State state, String nodeName, String neighborName, Tree node, Tree neighbor) {
    if (node == null) {
      return;
    }

    int left = ShiftReduceUtils.leftIndex(node);
    int right = ShiftReduceUtils.rightIndex(node);

    // Trees of size one are already featurized
    if (right == left) {
      features.add(nodeName + "SZ1");
      return;
    }

    addUnaryQueueFeatures(features, getCoreLabel(state.sentence.get(left)), nodeName + "EL-");
    addUnaryQueueFeatures(features, getCoreLabel(state.sentence.get(right)), nodeName + "ER-");

    if (neighbor != null) {
      addBinaryFeatures(features, nodeName, getCoreLabel(state.sentence.get(right)), FeatureComponent.HEADWORD, FeatureComponent.HEADTAG, neighborName, getCoreLabel(neighbor), FeatureComponent.HEADWORD, FeatureComponent.HEADTAG);
    }

    if (right - left == 1) {
      features.add(nodeName + "SZ2");
      return;
    }

    if (right - left == 2) {
      features.add(nodeName + "SZ3");
      addUnaryQueueFeatures(features, getCoreLabel(state.sentence.get(left + 1)), nodeName + "EM-");
      return;
    }

    features.add(nodeName + "SZB");
    addUnaryQueueFeatures(features, getCoreLabel(state.sentence.get(left + 1)), nodeName + "El-");
    addUnaryQueueFeatures(features, getCoreLabel(state.sentence.get(right - 1)), nodeName + "Er-");
  }

  /** This option also does not seem to help */
  public void addEdgeFeatures2(List<String> features, State state, String nodeName, Tree node) {
    if (node == null) {
      return;
    }

    int left = ShiftReduceUtils.leftIndex(node);
    int right = ShiftReduceUtils.rightIndex(node);

    CoreLabel nodeLabel = getCoreLabel(node);
    String nodeValue = getFeatureFromCoreLabel(nodeLabel, FeatureComponent.VALUE) + "-";
    CoreLabel leftLabel = getQueueLabel(state, left);
    CoreLabel rightLabel = getQueueLabel(state, right);

    addUnaryQueueFeatures(features, leftLabel, nodeName + "EL-" + nodeValue);
    addUnaryQueueFeatures(features, rightLabel, nodeName + "ER-" + nodeValue);

    CoreLabel previousLabel = getQueueLabel(state, left - 1);
    addUnaryQueueFeatures(features, previousLabel, nodeName + "EP-" + nodeValue);

    CoreLabel nextLabel = getQueueLabel(state, right + 1);
    addUnaryQueueFeatures(features, nextLabel, nodeName + "EN-" + nodeValue);
  }

  /**
   * Also did not seem to help
   */
  public void addExtraTrigramFeatures(List<String> features, CoreLabel s0Label, CoreLabel s1Label, CoreLabel s2Label, CoreLabel q0Label, CoreLabel q1Label) {
    addTrigramFeature(features, "S0wS1wS2c-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADWORD, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0wS1cS2w-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.HEADWORD);
    addTrigramFeature(features, "S0cS1wS2w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, s2Label, FeatureComponent.HEADWORD);

    addTrigramFeature(features, "S0wS1wQ0t-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0wS1cQ0w-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADWORD);
    addTrigramFeature(features, "S0cS1wQ0w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADWORD);

    addTrigramFeature(features, "S0cQ0tQ1t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADTAG, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0wQ0tQ1t-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADTAG, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cQ0wQ1t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cQ0tQ1w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADTAG, q0Label, FeatureComponent.HEADWORD);
    addTrigramFeature(features, "S0wQ0wQ1t-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0wQ0tQ1w-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.HEADTAG, q0Label, FeatureComponent.HEADWORD);
    addTrigramFeature(features, "S0cQ0wQ1w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADWORD);
  }

  @Override
  public List<String> featurize(State state, List<String> features) {
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

    CoreLabel s0LLLabel = getStackLabel(stack, 0, Transition.LEFT, Transition.LEFT);
    CoreLabel s0LRLabel = getStackLabel(stack, 0, Transition.LEFT, Transition.RIGHT);
    CoreLabel s0LULabel = getStackLabel(stack, 0, Transition.LEFT, Transition.UNARY);

    CoreLabel s0RLLabel = getStackLabel(stack, 0, Transition.RIGHT, Transition.LEFT);
    CoreLabel s0RRLabel = getStackLabel(stack, 0, Transition.RIGHT, Transition.RIGHT);
    CoreLabel s0RULabel = getStackLabel(stack, 0, Transition.RIGHT, Transition.UNARY);

    CoreLabel s0ULLabel = getStackLabel(stack, 0, Transition.UNARY, Transition.LEFT);
    CoreLabel s0URLabel = getStackLabel(stack, 0, Transition.UNARY, Transition.RIGHT);
    CoreLabel s0UULabel = getStackLabel(stack, 0, Transition.UNARY, Transition.UNARY);

    CoreLabel s1LLabel = getStackLabel(stack, 1, Transition.LEFT);
    CoreLabel s1RLabel = getStackLabel(stack, 1, Transition.RIGHT);
    CoreLabel s1ULabel = getStackLabel(stack, 1, Transition.UNARY);

    CoreLabel q0Label = getQueueLabel(sentence, tokenPosition, 0); // current location in queue
    CoreLabel q1Label = getQueueLabel(sentence, tokenPosition, 1); // next location in queue
    CoreLabel q2Label = getQueueLabel(sentence, tokenPosition, 2); // two locations later in queue
    CoreLabel q3Label = getQueueLabel(sentence, tokenPosition, 3); // three locations later in queue
    CoreLabel qP1Label = getQueueLabel(sentence, tokenPosition, -1); // previous location in queue
    CoreLabel qP2Label = getQueueLabel(sentence, tokenPosition, -2); // two locations prior in queue

    // It's kind of unpleasant having this magic order of feature names.
    // On the other hand, it does save some time with string concatenation.
    addUnaryStackFeatures(features, s0Label, "S0C-", "S0WT-", "S0T-", "S0WC-", "S0TC-");
    addUnaryStackFeatures(features, s1Label, "S1C-", "S1WT-", "S1T-", "S1WC-", "S1TC-");
    addUnaryStackFeatures(features, s2Label, "S2C-", "S2WT-", "S2T-", "S2WC-", "S2TC-");
    addUnaryStackFeatures(features, s3Label, "S3C-", "S3WT-", "S3T-", "S3WC-", "S3TC-");

    addUnaryStackFeatures(features, s0LLabel, "S0LC-", "S0LWT-", "S0LT-", "S0LWC-", "S0LTC-");
    addUnaryStackFeatures(features, s0RLabel, "S0RC-", "S0RWT-", "S0RT-", "S0RWC-", "S0RTC-");
    addUnaryStackFeatures(features, s0ULabel, "S0UC-", "S0UWT-", "S0UT-", "S0UWC-", "S0UTC-");

    addUnaryStackFeatures(features, s0LLLabel, "S0LLC-", "S0LLWT-", "S0LLT-", "S0LLWC-", "S0LLTC-");
    addUnaryStackFeatures(features, s0LRLabel, "S0LRC-", "S0LRWT-", "S0LRT-", "S0LRWC-", "S0LRTC-");
    addUnaryStackFeatures(features, s0LULabel, "S0LUC-", "S0LUWT-", "S0LUT-", "S0LUWC-", "S0LUTC-");

    addUnaryStackFeatures(features, s0RLLabel, "S0RLC-", "S0RLWT-", "S0RLT-", "S0RLWC-", "S0RLTC-");
    addUnaryStackFeatures(features, s0RRLabel, "S0RRC-", "S0RRWT-", "S0RRT-", "S0RRWC-", "S0RRTC-");
    addUnaryStackFeatures(features, s0RULabel, "S0RUC-", "S0RUWT-", "S0RUT-", "S0RUWC-", "S0RUTC-");

    addUnaryStackFeatures(features, s0ULLabel, "S0ULC-", "S0ULWT-", "S0ULT-", "S0ULWC-", "S0ULTC-");
    addUnaryStackFeatures(features, s0URLabel, "S0URC-", "S0URWT-", "S0URT-", "S0URWC-", "S0URTC-");
    addUnaryStackFeatures(features, s0UULabel, "S0UUC-", "S0UUWT-", "S0UUT-", "S0UUWC-", "S0UUTC-");

    addUnaryStackFeatures(features, s1LLabel, "S1LC-", "S1LWT-", "S1LT-", "S1LWC-", "S1LTC-");
    addUnaryStackFeatures(features, s1RLabel, "S1RC-", "S1RWT-", "S1RT-", "S1RWC-", "S1RTC-");
    addUnaryStackFeatures(features, s1ULabel, "S1UC-", "S1UWT-", "S1UT-", "S1UWC-", "S1UTC-");

    addUnaryQueueFeatures(features, q0Label, "Q0WT-");
    addUnaryQueueFeatures(features, q1Label, "Q1WT-");
    addUnaryQueueFeatures(features, q2Label, "Q2WT-");
    addUnaryQueueFeatures(features, q3Label, "Q3WT-");
    addUnaryQueueFeatures(features, qP1Label, "QP1WT-");
    addUnaryQueueFeatures(features, qP2Label, "QP2WT-");

    // Figure out which are the most recent left and right node
    // attachments to the heads of the given nodes.  It seems like it
    // should be more efficient to keep track of this in the state, as
    // that would have a constant cost per transformation, but it is
    // actually faster to find it by walking down the tree each time
    CoreLabel recentL0Label = getRecentDependent(stack, Transition.LEFT, 0);
    CoreLabel recentR0Label = getRecentDependent(stack, Transition.RIGHT, 0);
    CoreLabel recentL1Label = getRecentDependent(stack, Transition.LEFT, 1);
    CoreLabel recentR1Label = getRecentDependent(stack, Transition.RIGHT, 1);
    addUnaryStackFeatures(features, recentL0Label, "recL0C-", "recL0WT-", "recL0T-", "recL0WC-", "recL0TC-");
    addUnaryStackFeatures(features, recentR0Label, "recR0C-", "recR0WT-", "recR0T-", "recR0WC-", "recR0TC-");
    addUnaryStackFeatures(features, recentL1Label, "recL1C-", "recL1WT-", "recL1T-", "recL1WC-", "recL1TC-");
    addUnaryStackFeatures(features, recentR1Label, "recR1C-", "recR1WT-", "recR1T-", "recR1WC-", "recR1TC-");

    addBinaryFeatures(features, "S0", s0Label, FeatureComponent.HEADWORD, FeatureComponent.VALUE, "S1", s1Label, FeatureComponent.HEADWORD, FeatureComponent.VALUE);
    addBinaryFeatures(features, "S0", s0Label, FeatureComponent.HEADWORD, FeatureComponent.VALUE, "Q0", q0Label, FeatureComponent.HEADWORD, FeatureComponent.HEADTAG);
    addBinaryFeatures(features, "S1", s1Label, FeatureComponent.HEADWORD, FeatureComponent.VALUE, "Q0", q0Label, FeatureComponent.HEADWORD, FeatureComponent.HEADTAG);
    addBinaryFeatures(features, "Q0", q0Label, FeatureComponent.HEADWORD, FeatureComponent.HEADTAG, "Q1", q1Label, FeatureComponent.HEADWORD, FeatureComponent.HEADTAG);

    addTrigramFeature(features, "S0cS1cS2c-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0wS1cS2c-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0cS1wS2c-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, s2Label, FeatureComponent.VALUE);
    addTrigramFeature(features, "S0cS1cS2w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, s2Label, FeatureComponent.HEADWORD);

    addTrigramFeature(features, "S0cS1cQ0t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0wS1cQ0t-", s0Label, FeatureComponent.HEADWORD, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cS1wQ0t-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.HEADWORD, q0Label, FeatureComponent.HEADTAG);
    addTrigramFeature(features, "S0cS1cQ0w-", s0Label, FeatureComponent.VALUE, s1Label, FeatureComponent.VALUE, q0Label, FeatureComponent.HEADWORD);

    addPositionFeatures(features, state);

    // State.HeadPosition s0Separator = state.getSeparator(0);
    // State.HeadPosition s1Separator = state.getSeparator(1);
    // addSeparatorFeatures(features, s0Label, s1Label, s0Separator, s1Separator);

    Tree s0Node = state.getStackNode(0);
    Tree s1Node = state.getStackNode(1);
    Tree q0Node = state.getQueueNode(0);
    addSeparatorFeatures(features, "S0", s0Label, "S1", s1Label, state.getSeparatorBetween(s0Node, s1Node), state.getSeparatorCount(s0Node, s1Node));
    addSeparatorFeatures(features, "S0", s0Label, "Q0", q0Label, state.getSeparatorBetween(q0Node, s0Node), state.getSeparatorCount(q0Node, s0Node));

    return features;
  }

  private static final long serialVersionUID = 1;
}

