package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.*;

/**
 * Combines the output of several {@link CMMClassifier}s using user-specified precedence rules.
 * The precedence orders are specified by <i>label=index1;index2;...</i> properties where
 * <i>label</i> specifies the label that the precedence rule is active for, and <i>indexX</i> is
 * a 0-based index of a classifier considered an "expert" for that label - a label will be accepted iff
 * the classifier proposing that label is considered an expert for that label. By default, each
 * classifier is considered an expert for all labels it recognizes.
 * Useful for combining the NER results from systems trained to recognize different types of targets.
 * <p/>
 * <p>This class does not offer any training capabilities. To train a group of CMMClassifiers, refer to
 * {@link CMMEnsembleClassifier}.</p>
 * <p/>
 * Properties: <br />
 * classifiers - a semi-colon delimited list of serialized classifier files <br />
 * labels - a semi-colon delimited list of labels, specifying the precedence ordering of labels when
 * there is a conflict.  Put the label that is most precisely recognized first. <br />
 * testFile - the file to label <br />
 * </p>
 * <pre>
 * # Sample Properties Files
 * # In this example only conll.ser.gz and ace.ser.gz can recognize the PER class, and only
 * # ace.ser.gz can recognize the LOC class.
 * classifiers=muc.ser.gz;conll.ser.gz;ace.ser.gz
 * PER=1,2
 * LOC=2
 * testFile=test.iob2
 * </pre>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class CMMClassifierCombiner {

  private static List labels = new ArrayList(); // a List of Lists that contain the proposed labels from each of the Classifiers
  private static CollectionValuedMap preferred = new CollectionValuedMap(); // Map from Label -> index of preferred classifier
  private static Index labelOrder = new HashIndex();
  private static LabelComparator cmp = new LabelComparator();

  private static boolean isPreferredClassifier(String label, int classifier) {
    if (preferred.containsKey(label)) {
      Collection c = preferred.get(label);
      return c.contains(Integer.valueOf(classifier));
    }
    return false;
  }

  private static void setDefaults(Set labels, int classifier) {
    for (Iterator iter = labels.iterator(); iter.hasNext();) {
      String label = (String) iter.next();
      preferred.add(label, Integer.valueOf(classifier));
      labelOrder.add(label);
    }
  }

  /**
   * Sets the preferences from a property file.
   */
  private static void setPreferences(Properties props) {
    if (props.containsKey("labels")) {
      // clear the defaults
      labelOrder = new HashIndex();
      String[] labels = props.getProperty("labels").split(";");
      for (int i = 0; i < labels.length; i++) {
        labelOrder.add(labels[i]);
      }
    }
    // remove all of the reserved properties
    props.remove("classifiers");
    props.remove("labels");
    props.remove("testFile");
    props.remove("testOutput");

    for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
      String key = (String) iter.next();
      // clear the defaults
      preferred.remove(key);
      String value = props.getProperty(key);
      String[] preferredIndices = value.split(";");
      for (int i = 0; i < preferredIndices.length; i++) {
        try {
          int classifier = Integer.parseInt(preferredIndices[i]);
          preferred.add(key, Integer.valueOf(classifier));
        } catch (Exception e) {
          // do nothing;
        }
      }
    }
  }

  private static List<CoreLabel> combine() {
    List<CoreLabel> combined = new ArrayList<CoreLabel>();

    int numCols = labels.size();

    List<CoreLabel> firstCol = (List<CoreLabel>) labels.get(0);
    int length = firstCol.size();

    // make a copy of one of the WordInfos, so we can override the answer column
    for (Iterator<CoreLabel> iter = firstCol.iterator(); iter.hasNext();) {
      CoreLabel wi = iter.next();
      combined.add(new CoreLabel(wi));
    }

    for (int i = 0; i < length; i++) {
      List<String> proposed = new ArrayList<String>();
      for (int j = 0; j < numCols; j++) {
        List<CoreLabel> l = (List<CoreLabel>) labels.get(j);

        CoreLabel wi = l.get(i);
        if (isPreferredClassifier(wi.get(AnswerAnnotation.class), j)) {
          proposed.add(wi.get(AnswerAnnotation.class));
        }
      }

      CoreLabel copy = combined.get(i);
      if (proposed.size() > 0) {
        Collections.sort(proposed, cmp);
        // pick the preferred label
        copy.set(AnswerAnnotation.class, proposed.get(0));
      } else {
        //copy.setAnswer(CMMClassifier.BACKGROUND);
        copy.set(AnswerAnnotation.class, "O");
      }

    }
    return combined;
  }

  /**
   * Compares two labels based on precedence.  If not all of the labels
   * are specified in the ordering, prefers the specified ones to the
   * unspecified, and then sorts the unspecified alphabetically.
   */
  private static class LabelComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      int index1;
      int index2;

      index1 = labelOrder.indexOf(o1);
      index2 = labelOrder.indexOf(o2);
      // if both unspecified, sort alphabetically
      if (index1 < 0 && index2 < 0) {
        return ((String) o1).compareTo((String) o2);
      }
      // specified goes before unspecified
      if (index1 < 0) {
        return -1;
      }
      if (index2 < 0) {
        return 1;
      }
      // if both labels were specified, honor the ordering
      return index1 - index2;
    }
  }

  public static void main(String[] args) {
//     Properties props = StringUtils.argsToProperties(args);

//     if (props.getProperty("testFile") == null) {
//       System.err.println("Must specify test file with the \"testFile\" property.");
//       System.exit(1);
//     }

//     String testFile = props.getProperty("testFile");

//     if (props.getProperty("classifiers") == null) {
//       System.err.println("Must specify serialized classifiers with the \"classifiers\" property");
//       System.exit(1);
//     }

//     String[] classifiers = props.getProperty("classifiers").split(";");

//     for (int i = 0; i < classifiers.length; i++) {
//       CMMClassifier classifier = new CMMClassifier();
//       classifier.loadClassifierNoExceptions(classifiers[i]);
//       // the preferred classifier for a given label defaults
//       // to the first classifier that can recognize that label
//       setDefaults(classifier.getTags(), i);
//       labels.add(classifier.test(testFile));
//     }

//     setPreferences(props);
//     System.err.println("Preferred classifiers by class: " + preferred);

//     List combined = combine();
//     // print out the combined labels, one per line
//     // HN: TODO: figure out what the desired output is
//     for (Iterator iter = combined.iterator(); iter.hasNext();) {
//       CoreLabel wi = (CoreLabel) iter.next();
//       System.out.println(wi.word() + "\t" + wi.get(AnswerAnnotation.class));
//     }
  }
}
