package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations.*;

import java.util.*;
import java.util.function.Function;

/**
 * An annotator marking operators with their scope.
 * Look at {@link NaturalLogicAnnotator#PATTERNS} for the full list of patterns, otherwise
 * {@link NaturalLogicAnnotator#doOneSentence(Annotation, CoreMap)} is the main interface for this class.
 *
 * TODO(gabor) annotate generics as "most"
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
public class NaturalLogicAnnotator extends SentenceAnnotator {

  /**
   * A regex for arcs that act as determiners.
   */
  private static final String DET = "/(pre)?det|a(dv)?mod|neg|num|nn/";
  /**
   * A regex for arcs that we pretend are subject arcs.
   */
  private static final String GEN_SUBJ = "/[ni]subj(pass)?/";
  /**
   * A regex for arcs that we pretend are object arcs.
   */
  private static final String GEN_OBJ = "/[di]obj|xcomp|advcl|acomp/";
  /**
   * A regex for arcs that we pretend are copula.
   */
  private static final String GEN_COP = "/cop|aux(pass)?/";
  /**
   * A regex for arcs which denote a sub-clause (e.g., "at Stanford" or "who are at Stanford")
   */
  private static final String GEN_CLAUSE = "/prep|rcmod/";
  /**
   * A regex for arcs which denote a preposition
   */
  private static final String GEN_PREP = "/prep|advcl|ccomp|advmod/";

  /**
   * A Semgrex fragment for matching a quantifier.
   */
  private static final String QUANTIFIER;

  static {
    Set<String> singleWordQuantifiers = new HashSet<>();
    for (Operator q : Operator.values()) {
      String[] tokens = q.surfaceForm.split("\\s+");
      if (!tokens[tokens.length - 1].startsWith("_")) {
        singleWordQuantifiers.add("(" + tokens[tokens.length - 1].toLowerCase() + ")");
      }
    }
    QUANTIFIER = "[ {lemma:/" + StringUtils.join(singleWordQuantifiers, "|") + "/}=quantifier | {pos:CD}=quantifier ]";
  }

  /**
   * The patterns to use for marking quantifier scopes.
   */
  private static final List<SemgrexPattern> PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { All cats eat mice,
    //   All cats want milk }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" ({}=subject >>"+DET+" "+QUANTIFIER+") >"+GEN_OBJ+" {}=object"));
    // { All cats are in boxes,
    //   All cats voted for Obama,
    //   All cats have voted for Obama }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ({}=subject >>"+DET+" "+QUANTIFIER+") >"+GEN_PREP+" {}=object"));
    // { All cats are cute,
    //   All cats can purr }
    add(SemgrexPattern.compile("{}=object >"+GEN_SUBJ+" ({}=subject >>"+DET+" "+QUANTIFIER+") >"+GEN_COP+" {}=pivot"));
    // { Everyone at Stanford likes cats,
    //   Everyone who is at Stanford likes cats }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" ( "+QUANTIFIER+" >"+GEN_CLAUSE+" {}=subject ) >"+GEN_OBJ+" {}=object"));
    // { Everyone at Stanford voted for Colbert }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ( "+QUANTIFIER+" >"+GEN_CLAUSE+" {}=subject ) >"+GEN_PREP+" {}=object"));
    // { Felix likes cat food }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" {pos:NNP}=Subject >"+GEN_OBJ+" {}=object"));
    // { Felix has spoken to Fido }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" {pos:NNP}=Subject >/prep|ccomp|[di]obj/ {}=object"));
    // { Felix is a cat,
    //   Felix is cute }
    add(SemgrexPattern.compile("{}=object >"+GEN_SUBJ+" {pos:NNP}=Subject >"+GEN_COP+" {}=pivot"));
    // { Some cats do n't like dogs }
    add(SemgrexPattern.compile("{}=pivot >neg "+QUANTIFIER+" >"+GEN_OBJ+" {}=object"));
    // { Obama was not born in Dallas }
    add(SemgrexPattern.compile("{}=pivot >/neg/ {}=quantifier >"+GEN_PREP+" {}=object"));
    // { All of the cats hate dogs. }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ( "+QUANTIFIER+" >prep {}=subject ) >"+GEN_OBJ+" {}=object"));
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >dep ( "+QUANTIFIER+" >prep {}=subject ) >"+GEN_SUBJ+" {}=object"));  // as above, but handle a common parse error
    // { Either cats or dogs have tails. }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >dep {lemma:either}=quantifier >"+GEN_SUBJ+" {}=subject >"+GEN_OBJ+" {}=object"));
    // { There are cats }
    add(SemgrexPattern.compile("{}=quantifier >"+GEN_SUBJ+" {}=pivot >>expl {}"));
  }});

  /** A helper method for
   * {@link NaturalLogicAnnotator#getModifierSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)} and
   * {@link NaturalLogicAnnotator#getSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)}.
   */
  private static Pair<Integer, Integer> getGeneralizedSubtreeSpan(SemanticGraph tree, IndexedWord root, Set<String> validArcs) {
    int min = root.index();
    int max = root.index();
    Queue<IndexedWord> fringe = new LinkedList<>();
    for (SemanticGraphEdge edge : tree.getOutEdgesSorted(root)) {
      String edgeLabel = edge.getRelation().getShortName();
      if ((validArcs == null || validArcs.contains(edgeLabel)) &&
          !"punct".equals(edgeLabel)) {
        fringe.add(edge.getDependent());
      }
    }
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      min = Math.min(node.index(), min);
      max = Math.max(node.index(), max);
      for (SemanticGraphEdge edge : tree.getOutEdgesSorted(node)) {
        if (edge.getGovernor() == node &&   // Sometimes multiple nodes have the same index?
            edge.getGovernor() != edge.getDependent() &&  // Just in case...
            !"punct".equals(edge.getRelation().getShortName())) {  // ignore punctuation
          fringe.add(edge.getDependent());
        }
      }
    }
    return Pair.makePair(min, max + 1);
  }

  private static final Set<String> MODIFIER_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("aux");
    add("prep");
  }});

  private static final Set<String> NOUN_COMPONENT_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("nn");
  }});

  /**
   * Returns the yield span for the word rooted at the given node, but only traversing a fixed set of relations.
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getModifierSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    return getGeneralizedSubtreeSpan(tree, root, MODIFIER_ARCS);
  }

  /**
   * Returns the yield span for the word rooted at the given node, but only traversing relations indicative
   * of staying in the same noun phrase.
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getProperNounSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    return getGeneralizedSubtreeSpan(tree, root, NOUN_COMPONENT_ARCS);
  }

  /**
   * Returns the yield span for the word rooted at the given node. So, for example, all cats like dogs rooted at the word
   * "cats" would yield a span (1, 3) -- "all cats".
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    return getGeneralizedSubtreeSpan(tree, root, null);
  }

  /**
   * Effectively, merge two spans
   */
  private static Pair<Integer, Integer> includeInSpan(Pair<Integer, Integer> span, Pair<Integer, Integer> toInclude) {
    return Pair.makePair(Math.min(span.first, toInclude.first), Math.max(span.second, toInclude.second));
  }

  /**
   * Exclude the second span from the first, if the second is on the edge of the first. If the second is in the middle, it's
   * unclear what this function should do, so it just returns the original span.
   */
  private static Pair<Integer, Integer> excludeFromSpan(Pair<Integer, Integer> span, Pair<Integer, Integer> toExclude) {
    if (toExclude.second <= span.first || toExclude.first >= span.second) {
      // Case: toExclude is outside of the span anyways
      return span;
    } else if (toExclude.first <= span.first && toExclude.second > span.first) {
      // Case: overlap on the front
      return Pair.makePair(toExclude.second, span.second);
    } else if (toExclude.first < span.second && toExclude.second >= span.second) {
      // Case: overlap on the front
      return Pair.makePair(span.first, toExclude.first);
    } else if (toExclude.first > span.first && toExclude.second < span.second) {
      // Case: toExclude is within the span
      return span;
    } else {
      throw new IllegalStateException("This case should be impossible");
    }
  }

  /**
   * Compute the span for a given matched pattern.
   * At a high level:
   *
   * <ul>
   *   <li>If both a subject and an object exist, we take the subject minus the quantifier, and the object plus the pivot. </li>
   *   <li>If only an object exists, we make the subject the object, and create a dummy object to signify a one-place quantifier. </li>
   *   <li>If neither the subject or object exist, the pivot is the subject and there is no object. </li>
   *   <li>If the subject is a proper noun, only mark the object itself with the subject span. </li>
   * </ul>
   *
   * But:
   *
   * <ul>
   *   <li>If we have a two-place quantifier, the object is allowed to absorb various specific arcs from the pivot.</li>
   *   <li>If we have a one-place quantifier, the object is allowed to absorb only prepositions from the pivot.</li>
   * </ul>
   */
  private OperatorSpec computeScope(SemanticGraph tree, Operator operator,
                                    IndexedWord pivot, Pair<Integer, Integer> quantifierSpan,
                                    IndexedWord subject, boolean isProperNounSubject, IndexedWord object) {
    Pair<Integer, Integer> subjSpan;
    Pair<Integer, Integer> objSpan;
    if (subject == null && object == null) {
      subjSpan = getSubtreeSpan(tree, pivot);
      objSpan = Pair.makePair(subjSpan.second, subjSpan.second);
    } else if (subject == null) {
      subjSpan = includeInSpan(getSubtreeSpan(tree, object), getGeneralizedSubtreeSpan(tree, pivot, Collections.singleton("prep")));
      objSpan = Pair.makePair(subjSpan.second, subjSpan.second);
    } else {
      Pair<Integer, Integer> subjectSubtree;
      if (isProperNounSubject) {
        subjectSubtree = getProperNounSubtreeSpan(tree, subject);
      } else {
        subjectSubtree = getSubtreeSpan(tree, subject);
      }
      subjSpan = excludeFromSpan(subjectSubtree, quantifierSpan);
      objSpan = excludeFromSpan(includeInSpan(getSubtreeSpan(tree, object), getModifierSubtreeSpan(tree, pivot)), subjectSubtree);
    }
    return new OperatorSpec(operator,
        quantifierSpan.first - 1, quantifierSpan.second - 1,
        subjSpan.first - 1, subjSpan.second - 1,
        objSpan.first - 1, objSpan.second - 1);
  }

  /**
   * Try to find which quantifier we matched, given that we matched the head of a quantifier at the given IndexedWord, and that
   * this whole deal is taking place in the given sentence.
   *
   * @param sentence The sentence we are matching.
   * @param quantifier The word at which we matched a quantifier.
   * @return An optional triple consisting of the particular quantifier we matched, as well as the span of that quantifier in the sentence.
   */
  private Optional<Triple<Operator,Integer,Integer>> validateQuantiferByHead(CoreMap sentence, IndexedWord quantifier) {
    int end = quantifier.index();
    for (int start = Math.max(0, end - 10); start < end; ++start) {
      Function<CoreLabel,String> glossFn = (label) -> "CD".equals(label.tag()) ? "--num--" : label.lemma();
      String gloss = StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class), " ", glossFn, start, end).toLowerCase();
      for (Operator q : Operator.values()) {
        if (q.surfaceForm.equals(gloss)) {
          return Optional.of(Triple.makeTriple(q, start + 1, end + 1));
        }
      }
    }
    return Optional.empty();
  }


  /**
   * Find the operators in this sentence, annotating the head word (only!) of each operator with the
   * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.OperatorAnnotation}.
   *
   * @param sentence As in {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator#doOneSentence(edu.stanford.nlp.pipeline.Annotation, edu.stanford.nlp.util.CoreMap)}
   */
  private void annotateOperators(CoreMap sentence) {
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    if (tree == null) {
      tree = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    }
    for (SemgrexPattern pattern : PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(tree);
      while (matcher.find()) {

        // Get terms
        IndexedWord properSubject = matcher.getNode("Subject");
        IndexedWord quantifier, subject;
        boolean namedEntityQuantifier = false;
        if (properSubject != null) {
          quantifier = subject = properSubject;
          namedEntityQuantifier = true;
        } else {
          quantifier = matcher.getNode("quantifier");
          subject = matcher.getNode("subject");
        }

        // Validate quantifier
        // At the end of this
        Optional<Triple<Operator,Integer,Integer>> quantifierInfo;
        if (namedEntityQuantifier) {
          // named entities have the "all" semantics by default.
          quantifierInfo = Optional.of(Triple.makeTriple(Operator.IMPLICIT_NAMED_ENTITY, quantifier.index(), quantifier.index()));  // note: empty quantifier span given
        } else {
          // find the quantifier, and return some info about it.
          quantifierInfo = validateQuantiferByHead(sentence, quantifier);
        }

        // Set tokens
        if (quantifierInfo.isPresent()) {
          // Compute span
          OperatorSpec scope = computeScope(tree, quantifierInfo.get().first,
              matcher.getNode("pivot"), Pair.makePair(quantifierInfo.get().second, quantifierInfo.get().third), subject,
              namedEntityQuantifier, matcher.getNode("object"));
          // Set annotation
          CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(quantifier.index() - 1);
          OperatorSpec oldScope = token.get(OperatorAnnotation.class);
          if (oldScope == null || oldScope.quantifierLength() < scope.quantifierLength() ||
              oldScope.instance != scope.instance) {
            token.set(OperatorAnnotation.class, scope);
          } else {
            token.set(OperatorAnnotation.class, OperatorSpec.merge(oldScope, scope));
          }
        }
      }
    }

    // Ensure we didn't select overlapping quantifiers. For example, "a" and "a few" can often overlap.
    // In these cases, take the longer quantifier match.
    List<OperatorSpec> quantifiers = new ArrayList<>();
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token.has(OperatorAnnotation.class)) {
        quantifiers.add(token.get(OperatorAnnotation.class));
      }
    }
    quantifiers.sort( (x, y) -> y.quantifierLength() - x.quantifierLength());
    for (OperatorSpec quantifier : quantifiers) {
      for (int i = quantifier.quantifierBegin; i < quantifier.quantifierEnd; ++i) {
        if (i != quantifier.quantifierHead) {
          sentence.get(CoreAnnotations.TokensAnnotation.class).get(i).remove(OperatorAnnotation.class);
        }
      }
    }
  }

  /**
   * Annotate every token for its polarity, based on the operators found. This function will set the
   * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.PolarityAnnotation} for every token.
   *
   * @param sentence As in {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator#doOneSentence(edu.stanford.nlp.pipeline.Annotation, edu.stanford.nlp.util.CoreMap)}
   */
  private void annotatePolarity(CoreMap sentence) {
    // Collect all the operators in this sentence
    List<OperatorSpec> operators = new ArrayList<>();
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      OperatorSpec specOrNull = token.get(OperatorAnnotation.class);
      if (specOrNull != null) {
        operators.add(specOrNull);
      }
    }

    // Set polarity for each token
    for (int i = 0; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);
      // Get operators in scope
      List<Triple<Integer, Monotonicity, MonotonicityType>> inScope = new ArrayList<>(4);
      for (OperatorSpec operator : operators) {
        if (i >= operator.subjectBegin && i < operator.subjectEnd) {
          inScope.add(Triple.makeTriple(operator.subjectEnd - operator.subjectBegin, operator.instance.subjMono, operator.instance.subjType));
        } else if (i >= operator.objectBegin && i < operator.objectEnd) {
          inScope.add(Triple.makeTriple(operator.objectEnd - operator.objectBegin, operator.instance.objMono, operator.instance.objType));
        }
      }
      // Sort the operators by their scope (approximated by the size of their argument span
      inScope.sort( (x, y) -> y.first - x.first);
      // Create polarity
      List<Pair<Monotonicity, MonotonicityType>> info = new ArrayList<>(inScope.size());
      for (Triple<Integer, Monotonicity, MonotonicityType> term : inScope) {
        info.add(Pair.makePair(term.second, term.third));
      }
      Polarity polarity = new Polarity(info);
      // Set polarity
      token.set(PolarityAnnotation.class, polarity);
    }
  }

  /**
   * If false, don't annotate tokens for polarity but only find the operators and their scopes.
   */
  public final boolean doPolarity;

  /**
   * Create a new annotator.
   * @param annotatorName The prefix for the properties for this annotator.
   * @param props The properties to configure this annotator with.
   */
  public NaturalLogicAnnotator(String annotatorName, Properties props) {
    this.doPolarity = Boolean.valueOf(props.getProperty(annotatorName + ".doPolarity", "true"));
  }

  /**
   * @see edu.stanford.nlp.naturalli.NaturalLogicAnnotator#NaturalLogicAnnotator(String, java.util.Properties)
   */
  public NaturalLogicAnnotator(Properties props) {
    this(STANFORD_NATLOG, props);

  }

  /** The default constructor */
  public NaturalLogicAnnotator() {
    this("__irrelevant__", new Properties());
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    annotateOperators(sentence);
    if (doPolarity) {
      annotatePolarity(sentence);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected int nThreads() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  protected long maxTime() {
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    System.err.println("Failed to annotate: " + sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  /** {@inheritDoc} */
  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(NATLOG_REQUIREMENT);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Requirement> requires() {
    return Collections.EMPTY_SET;  // TODO(gabor) set me!
  }
}
