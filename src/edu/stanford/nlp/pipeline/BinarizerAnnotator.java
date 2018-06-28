package edu.stanford.nlp.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ReflectionLoading;

/**
 * This annotator takes unbinarized trees (from the parser annotator
 * or elsewhere) and binarizes them in the attachment.
 * <p>
 * Note that this functionality is also built in to the
 * ParserAnnotator.  However, this can be used in situations where the
 * trees come from somewhere other than the parser.  Conversely, the
 * ParserAnnotator may have more options for the binarizer which are
 * not implemented here.
 *
 * @author John Bauer
 */
public class BinarizerAnnotator implements Annotator {

  private static final String DEFAULT_TLPP_CLASS = "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams";

  private final TreeBinarizer binarizer;
  private final String tlppClass;

  public BinarizerAnnotator(String annotatorName, Properties props) {
    this.tlppClass = props.getProperty(annotatorName + ".tlppClass", DEFAULT_TLPP_CLASS);
    TreebankLangParserParams tlpp = ReflectionLoading.loadByReflection(tlppClass);
    this.binarizer = TreeBinarizer.simpleTreeBinarizer(tlpp.headFinder(), tlpp.treebankLanguagePack());
  }

  public String signature(String annotatorName, Properties props) {
    // String tlppClass = props.getProperty(annotatorName + ".tlppClass", DEFAULT_TLPP_CLASS);
    return tlppClass;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        doOneSentence(sentence);
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  private void doOneSentence(CoreMap sentence) {
    Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    Tree binarized;
    if (isBinarized(tree)) {
      binarized = tree;
    } else {
      binarized = binarizer.transformTree(tree);
    }
    Trees.convertToCoreLabels(binarized);
    sentence.set(TreeCoreAnnotations.BinarizedTreeAnnotation.class, binarized);
  }

  /**
   * Recursively check that a tree is not already binarized.
   */
  private static boolean isBinarized(Tree tree) {
    if (tree.isLeaf()) {
      return true;
    }

    if (tree.children().length > 2) {
      return false;
    }

    for (Tree child : tree.children()) {
      if (!isBinarized(child)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        TreeCoreAnnotations.TreeAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
  }

}
