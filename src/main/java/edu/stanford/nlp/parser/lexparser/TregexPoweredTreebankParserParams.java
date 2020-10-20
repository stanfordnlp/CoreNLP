package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

import java.util.Collection;
import java.util.function.Function;
import java.util.Map;

/**
 * An extension of
 * {@link edu.stanford.nlp.parser.lexparser.AbstractTreebankParserParams}
 * which provides support for Tregex-powered annotations.
 *
 * Subclasses of this class provide collections of <em>features</em>
 * which are associated with annotation behaviors that seek out
 * and label matching trees in some way. For example, a <em>coord</em>
 * feature might have an annotation behavior which searches for
 * coordinating noun phrases and labels the associated constituent
 * with a suffix <code>-coordinating</code>.
 *
 * The "search" in this process is conducted via Tregex, and the
 * actual annotation is done through execution of an arbitrary
 * {@link java.util.function.Function} provided by the user.
 * This class carries as inner several classes several useful common
 * annotation functions.
 *
 * @see #annotations
 * @see SimpleStringFunction
 *
 * @author Jon Gauthier
 * @author Spence Green
 */
public abstract class TregexPoweredTreebankParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TregexPoweredTreebankParserParams.class);

  private static final long serialVersionUID = -1985603901694682420L;

  /**
   * This data structure dictates how an arbitrary tree should be
   * annotated. Subclasses should fill out the related member
   * {@link #annotations}.
   *
   * It is a collection of <em>features:</em> a map from feature name
   * to behavior, where each behavior is a tuple <code>(t, f)</code>.
   * <code>t</code> is a Tregex pattern which matches subtrees
   * corresponding to the feature, and <code>f</code> is a function which
   * accepts such matches and generates an annotation which the matched
   * subtree should be given.
   *
   * @see #annotations
   */
  private final Map<String, Pair<TregexPattern, Function<TregexMatcher, String>>> annotationPatterns
    = Generics.newHashMap();

  /**
   * This data structure dictates how an arbitrary tree should be
   * annotated.
   *
   * It is a collection of <em>features:</em> a map from feature name
   * to behavior, where each behavior is a tuple <code>(t, f)</code>.
   * <code>t</code> is a string form of a TregexPattern which matches
   * subtrees corresponding to the feature, and <code>f</code> is a
   * function which accepts such matches and generates an annotation
   * which the matched subtree should be given.
   *
   * @see #annotationPatterns
   * @see SimpleStringFunction
   */
  protected final Map<String, Pair<String, Function<TregexMatcher, String>>> annotations
    = Generics.newHashMap();

  /**
   * Features which should be enabled by default.
   */
  protected abstract String[] baselineAnnotationFeatures();

  /**
   * Extra features which have been requested. Use
   * {@link #addFeature(String)} to add features.
   */
  private final Collection<String> features;

  public TregexPoweredTreebankParserParams(TreebankLanguagePack tlp) {
    super(tlp);

    features = CollectionUtils.asSet(baselineAnnotationFeatures());
  }

  /**
   * Compile the {@link #annotations} collection given a
   * particular head finder. Subclasses should call this method at
   * least once before the class is used, and whenever the head finder
   * is changed.
   */
  protected void compileAnnotations(HeadFinder hf) {
    TregexPatternCompiler compiler = new TregexPatternCompiler(hf);

    annotationPatterns.clear();
    for (Map.Entry<String, Pair<String, Function<TregexMatcher, String>>> annotation : annotations.entrySet()) {
      TregexPattern compiled;
      try {
        compiled = compiler.compile(annotation.getValue().first());
      } catch (TregexParseException e) {
        int nth = annotationPatterns.size() + 1;
        log.info("Parse exception on annotation pattern #" + nth + " initialization: " + e);
        continue;
      }

      Pair<TregexPattern, Function<TregexMatcher, String>> behavior =
              new Pair<>(compiled, annotation.getValue().second());

      annotationPatterns.put(annotation.getKey(), behavior);
    }
  }

  /**
   * Enable an annotation feature. If the provided feature has already
   * been enabled, this method does nothing.
   *
   * @param featureName
   * @throws java.lang.IllegalArgumentException If the provided feature
   *           name is unknown (i.e., if there is no entry in the
   *           {@link #annotations} collection with the same name)
   */
  protected void addFeature(String featureName) {
    if (!annotations.containsKey(featureName))
      throw new IllegalArgumentException("Invalid feature name '" + featureName + "'");
    if (!annotationPatterns.containsKey(featureName))
      throw new RuntimeException("Compiled patterns out of sync with annotations data structure;" +
        "did you call compileAnnotations?");

    features.add(featureName);
  }

  /**
   * Disable a feature. If the feature was never enabled, this method
   * returns without error.
   *
   * @param featureName
   */
  protected void removeFeature(String featureName) {
    features.remove(featureName);
  }

  /**
   * This method does language-specific tree transformations such as annotating particular nodes with language-relevant
   * features. Such parameterizations should be inside the specific TreebankLangParserParams class.  This method is
   * recursively applied to each node in the tree (depth first, left-to-right), so you shouldn't write this method to
   * apply recursively to tree members.  This method is allowed to (and in some cases does) destructively change the
   * input tree <code>t</code>. It changes both labels and the tree shape.
   *
   * @param t    The input tree (with non-language specific annotation already done, so you need to strip back to basic
   *             categories)
   * @param root The root of the current tree (can be null for words)
   * @return The fully annotated tree node (with daughters still as you want them in the final result)
   */
  @Override
  public Tree transformTree(Tree t, Tree root) {
    String newCat = t.value() + getAnnotationString(t, root);
    t.setValue(newCat);
    if (t.isPreTerminal() && t.label() instanceof HasTag)
      ((HasTag) t.label()).setTag(newCat);

    return t;
  }

  /**
   * Build a string of annotations for the given tree.
   *
   * @param t The input tree (with non-language specific annotation
   *          already done, so you need to strip back to basic categories)
   * @param root The root of the current tree (can be null for words)
   * @return A (possibly empty) string of annotations to add to the
   *         given tree
   */
  protected String getAnnotationString(Tree t, Tree root) {
    // Accumulate all annotations in this string
    StringBuilder annotationStr = new StringBuilder();

    for (String featureName : features) {
      Pair<TregexPattern, Function<TregexMatcher, String>> behavior = annotationPatterns.get(featureName);
      TregexMatcher m = behavior.first().matcher(root);
      if (m.matchesAt(t))
        annotationStr.append(behavior.second().apply(m));
    }

    return annotationStr.toString();
  }

  /**
   * Output a description of the current annotation configuration to
   * standard error.
   */
  @Override
  public void display() {
    for (String feature : features)
      System.err.printf("%s ", feature);
    log.info();
  }

  /**
   * Annotates all nodes that match the tregex query with some string.
   */
  protected static class SimpleStringFunction implements SerializableFunction<TregexMatcher,
    String> {

    private static final long serialVersionUID = 6958776731059724396L;
    private String annotationMark;

    public SimpleStringFunction(String annotationMark) {
      this.annotationMark = annotationMark;
    }

    public String apply(TregexMatcher matcher) {
      return annotationMark;
    }

    @Override
    public String toString() {
      return "SimpleStringFunction[" + annotationMark + ']';
    }

  }

  /**
   * Annotate a tree constituent with its lexical head.
   */
  protected static class AnnotateHeadFunction implements SerializableFunction<TregexMatcher,
    String> {

    private static final long serialVersionUID = -4213299755069618322L;

    private final HeadFinder headFinder;
    private boolean lowerCase;

    public AnnotateHeadFunction(HeadFinder hf) {
      this(hf, true);
    }

    public AnnotateHeadFunction(HeadFinder hf, boolean lowerCase) {
      headFinder = hf;
      this.lowerCase = lowerCase;
    }

    public String apply(TregexMatcher matcher) {
      Tree matchedTree = matcher.getMatch();

      Tree head = headFinder.determineHead(matchedTree);
      if (!head.isPrePreTerminal())
        return "";

      Tree lexicalHead = head.firstChild().firstChild();
      String headValue = lexicalHead.value();

      if (headValue != null) {
        if (lowerCase) headValue = headValue.toLowerCase();
        return '[' + headValue + ']';
      } else {
        return "";
      }
    }

    @Override
    public String toString() {
      return "AnnotateHeadFunction[" + headFinder.getClass().getName() + ']';
    }

  }

}
