package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.TreeShapedStack;

public abstract class FeatureFactory implements Serializable {
  public List<String> featurize(State state) {
    return featurize(state, Generics.<String>newArrayList(200));
  }

  abstract public List<String> featurize(State state, List<String> features);

  enum Transition {
    LEFT, RIGHT, UNARY
  };

  enum FeatureComponent {
    HEADWORD ("W"), HEADTAG ("T"), VALUE ("C");

    private final String shortName;
    FeatureComponent(String shortName) {
      this.shortName = shortName;
    }

    public String shortName() { return shortName; }
  };

  static final String NULL = "*NULL*";

  public static String getFeatureFromCoreLabel(CoreLabel label, FeatureComponent feature) {
    String value = null;
    switch(feature) {
    case HEADWORD:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class).value();
      break;
    case HEADTAG:
      value = (label == null) ? NULL : label.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class).value();
      break;
    case VALUE:
      value = (label == null) ? NULL : label.value();
      break;
    default:
      throw new IllegalArgumentException("Unexpected feature type: " + feature);
    }
    return value;
  }

  public static CoreLabel getRecentDependent(TreeShapedStack<Tree> stack, Transition transition, int nodeNum) {
    if (stack.size() <= nodeNum) {
      return null;
    }

    for (int i = 0; i < nodeNum; ++i) {
      stack = stack.pop();
    }

    Tree node = stack.peek();
    if (node == null) {
      return null;
    }
    if (!(node.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Can only featurize CoreLabel trees");
    }
    CoreLabel head = ((CoreLabel) node.label()).get(TreeCoreAnnotations.HeadWordLabelAnnotation.class);

    switch (transition) {
    case LEFT: {
      while (true) {
        if (node.children().length == 0) {
          return null;
        }
        Tree child = node.children()[0];
        if (!(child.label() instanceof CoreLabel)) {
          throw new IllegalArgumentException("Can only featurize CoreLabel trees");
        }
        if (((CoreLabel) child.label()).get(TreeCoreAnnotations.HeadWordLabelAnnotation.class) != head) {
          return (CoreLabel) child.label();
        }
        node = child;
      }
    }
    case RIGHT: {
      while (true) {
        if (node.children().length == 0) {
          return null;
        }
        if (node.children().length == 1) {
          node = node.children()[0];
          continue;
        }
        Tree child = node.children()[1];
        if (!(child.label() instanceof CoreLabel)) {
          throw new IllegalArgumentException("Can only featurize CoreLabel trees");
        }
        if (((CoreLabel) child.label()).get(TreeCoreAnnotations.HeadWordLabelAnnotation.class) != head) {
          return (CoreLabel) child.label();
        }
        node = child;
      }
    }
    default:
      throw new IllegalArgumentException("Can only get left or right heads");
    }
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

  public static CoreLabel getQueueLabel(State state, int offset) {
    return getQueueLabel(state.sentence, state.tokenPosition, offset);
  }

  public static CoreLabel getQueueLabel(List<Tree> sentence, int tokenPosition, int offset) {
    if (tokenPosition + offset < 0 || tokenPosition + offset >= sentence.size()) { 
      return null;
    }

    Tree node = sentence.get(tokenPosition + offset);
    if (!(node.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Can only featurize CoreLabel trees");
    }
    return (CoreLabel) node.label();
  }

  public static CoreLabel getCoreLabel(Tree node) {
    if (!(node.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Can only featurize CoreLabel trees");
    }
    return (CoreLabel) node.label();
  }  

  private static final long serialVersionUID = -9086427962537286031L;
}
