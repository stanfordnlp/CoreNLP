package edu.stanford.nlp.naturalli;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

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
public class NaturalLogicAnnotator extends SentenceAnnotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NaturalLogicAnnotator.class);

  /**
   * A regex for arcs that act as determiners.
   */
  private static final String DET = "/det.*|a(dv)?mod|neg|nummod|compound|case/";
  /**
   * A regex for arcs that we pretend are subject arcs.
   */
  private static final String GEN_SUBJ = "/nsubj(:?pass)?/";
  /**
   * A regex for arcs that we pretend are object arcs.
   */
  private static final String GEN_OBJ = "/[i]?obj|xcomp|advcl/";
  /**
   * A regex for arcs that we pretend are copula.
   */
  private static final String GEN_COP = "/cop|aux(:pass)?/";
  /**
   * A regex for arcs which denote a sub-clause (e.g., "at Stanford" or "who are at Stanford")
   */
  private static final String GEN_CLAUSE = "/nmod|obl|acl:relcl/";
  /**
   * A regex for arcs which denote a preposition
   */
  private static final String GEN_PREP = "/(nmod|obl)(:.{1,10})?|advcl|ccomp|advmod/";

  /**
   * A Semgrex fragment for matching a negating quantifier.
   */
  private static final String NEG_QUANTIFIER = "{lemma:/(no)|(neither)|(no one)|(nobody)|(not)|(but)|(except)|(n['’]t)/}=quantifier";

  /**
   * A Semgrex pattern for matching a negating quantifier in a dependency graph.
   */
  private static final SemgrexPattern NEG_PATTERN = SemgrexPattern.compile("{}=gov >/det|advmod/=negreln " + NEG_QUANTIFIER);


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
    //nmod used to be prep - problem?
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" {pos:NNP}=Subject >/nmod|obl|ccomp|[i]?obj/ {}=object"));
    // { Felix is a cat,
    //   Felix is cute }
    add(SemgrexPattern.compile("{}=object >"+GEN_SUBJ+" {pos:NNP}=Subject >"+GEN_COP+" {}=pivot"));
    // { Some cats do n't like dogs }
    add(SemgrexPattern.compile("{}=pivot >/neg/ "+QUANTIFIER+" >"+GEN_OBJ+" {}=object"));
    // { Obama was not born in Dallas,
    //   Cats are not fluffy,
    //   Tuesday will not work }
    add(SemgrexPattern.compile("{}=pivot >/neg/ {}=quantifier >"+GEN_PREP+" {}=object >"+GEN_SUBJ+" {}=subject "));
    add(SemgrexPattern.compile("{pos:/J.*/}=object >/neg/ {}=quantifier >"+GEN_SUBJ+" {}=subject "));
    // { Anytime but next Tuesday,
    add(SemgrexPattern.compile("{pos:/V.*/}=object >/neg/ {}=quantifier >"+GEN_SUBJ+" {}=subject >aux {pos:MD}"));
    //   food but not water,
    //   not on Tuesday  }
    add(SemgrexPattern.compile("{}=pivot >>/cc/ "+QUANTIFIER+" >/conj/ {}=object"));
    add(SemgrexPattern.compile("{lemma:/not|no|but|except/}=quantifier >/conj|(nmod|obl)(:.*)?/ {}=object"));  // as above, but handle a common parse error
    // { Anything except cabbage }
    add(SemgrexPattern.compile("{}=object >/case/ "+QUANTIFIER));
    // { All of the cats hate dogs. }
    //nmod used to be prep - problem?
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ( "+QUANTIFIER+" >/(nmod|obl).*/ {}=subject ) >"+GEN_OBJ+" {}=object"));
//    add(SemgrexPattern.compile("{pos:/V.*/}=pivot > ( "+QUANTIFIER+" >/nmod.*/ {}=subject ) >"+GEN_SUBJ+" {}=object"));  // as above, but handle a common parse error
    // { Either cats or dogs have tails. }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot > {lemma:either}=quantifier >"+GEN_SUBJ+" {}=subject >"+GEN_OBJ+" {}=object"));
    // { There are cats }
    add(SemgrexPattern.compile("{}=quantifier >"+GEN_SUBJ+" {}=pivot >>expl {}"));
  }});

  // { Cats eat _some_ mice,
  //   Cats eat _most_ mice }
  /**
   * A pattern for just trivial unary quantification, in case a quantifier doesn't match any of the patterns in
   * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator#PATTERNS}.
   */
  private static final SemgrexPattern UNARY_PATTERN = SemgrexPattern.compile("{pos:/N.*/}=subject >"+DET+" "+QUANTIFIER);

  /**
   * A list of words that suggest their complement has downward polarity.
   * For example, "doubt" ("I doubt that X")
   */
  private static final List<String> DOUBT_WORDS = Arrays.asList("doubt", "skeptical");

  /**
   * A pattern for recognizing the words in {@link NaturalLogicAnnotator#DOUBT_WORDS}.
   */
  private static final TokenSequencePattern DOUBT_PATTERN
      = TokenSequencePattern.compile("(?$doubt [{ lemma:/" + StringUtils.join(DOUBT_WORDS, "|") + "/}]) (?$target [{lemma:/that|of/}] []+ )");

  /** A helper method for
   * {@link NaturalLogicAnnotator#getModifierSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)} and
   * {@link NaturalLogicAnnotator#getSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)}.
   */
  private static Pair<Integer, Integer> getGeneralizedSubtreeSpan(SemanticGraph tree, IndexedWord root, Set<String> validArcs) {
    int min = root.index();
    int max = root.index();
    Queue<IndexedWord> fringe = new LinkedList<>();
    for (SemanticGraphEdge edge : tree.outgoingEdgeIterable(root)) {
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
      // ignore punctuation
      fringe.addAll(tree.getOutEdgesSorted(node).stream().filter(edge -> edge.getGovernor().equals(node) &&
          !(edge.getGovernor().equals(edge.getDependent())) &&
          !"punct".equals(edge.getRelation().getShortName())).map(SemanticGraphEdge::getDependent).collect(Collectors.toList()));
    }
    return Pair.makePair(min, max + 1);
  }

  private static final Set<String> NMOD_OBL_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("nmod");
    add("obl");
  }});

  private static final Set<String> MODIFIER_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("aux");
    add("nmod");
    add("obl");
  }});

  private static final Set<String> NOUN_COMPONENT_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("compound");
  }});

  private static final Set<String> JJ_COMPONENT_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("advmod");
    add("amod");
  }});

  /**
   * Returns the yield span for the word rooted at the given node, but only traversing a fixed set of relations.
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getModifierSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    if (tree.outgoingEdgeList(root).stream().anyMatch(x -> "neg".equals(x.getRelation().getShortName()))) {
      return getGeneralizedSubtreeSpan(tree, root, NMOD_OBL_ARCS);
    } else {
      return getGeneralizedSubtreeSpan(tree, root, MODIFIER_ARCS);
    }
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
    } else if (toExclude.first <= span.first /* && toExclude.second > span.first */) {
      // Case: overlap on the front
      return Pair.makePair(toExclude.second, span.second);
    } else if (/*toExclude.first < span.second && */ toExclude.second >= span.second) {
      // Case: overlap on the front
      return Pair.makePair(span.first, toExclude.first);
    } else /* if (toExclude.first > span.first && toExclude.second < span.second) */ {
      // Case: toExclude is within the span
      return span;
    /*
    } else {
      throw new IllegalStateException("This case should be impossible");
    */
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
  private static OperatorSpec computeScope(SemanticGraph tree, Operator operator,
                                           IndexedWord pivot, Pair<Integer, Integer> quantifierSpan,
                                           IndexedWord subject, boolean isProperNounSubject, IndexedWord object,
                                           int sentenceLength) {
    Pair<Integer, Integer> subjSpan;
    Pair<Integer, Integer> objSpan;
    if (subject == null && object == null) {
      subjSpan = getSubtreeSpan(tree, pivot);
      if (Span.fromPair(subjSpan).contains(Span.fromPair(quantifierSpan))) {
        // Don't consume the quantifier -- take only the part after the quantifier
        subjSpan = Pair.makePair(Math.max(subjSpan.first, quantifierSpan.second), subjSpan.second);
        if (subjSpan.second <= subjSpan.first) {
          subjSpan = Pair.makePair(subjSpan.first, subjSpan.first + 1);
        }
      } else {
        // Exclude the quantifier from the span
        subjSpan = excludeFromSpan(subjSpan, quantifierSpan);
      }
      objSpan = Pair.makePair(subjSpan.second, subjSpan.second);
    } else if (subject == null) {
      subjSpan = includeInSpan(getSubtreeSpan(tree, object), getGeneralizedSubtreeSpan(tree, pivot, NMOD_OBL_ARCS));
      objSpan = Pair.makePair(subjSpan.second, subjSpan.second);
    } else {
      Pair<Integer, Integer> subjectSubtree;
      if (isProperNounSubject) {
        subjectSubtree = getProperNounSubtreeSpan(tree, subject);
      } else {
        subjectSubtree = getSubtreeSpan(tree, subject);
      }
      subjSpan = excludeFromSpan(subjectSubtree, quantifierSpan);
      Pair<Integer, Integer> vanillaObjectSpan = getGeneralizedSubtreeSpan(tree, object, object == pivot ? JJ_COMPONENT_ARCS : null);
      objSpan = excludeFromSpan(
          includeInSpan(
              vanillaObjectSpan,
              object == pivot ? vanillaObjectSpan : getModifierSubtreeSpan(tree, pivot)
          ),
          subjectSubtree);
    }

    // Return scopes
    if (subjSpan.first < quantifierSpan.second && subjSpan.second > quantifierSpan.second) {
      subjSpan = Pair.makePair(quantifierSpan.second, subjSpan.second);
    }
    return new OperatorSpec(operator,
        quantifierSpan.first - 1, quantifierSpan.second - 1,
        subjSpan.first - 1, subjSpan.second - 1,
        objSpan.first - 1, objSpan.second - 1,
        sentenceLength);
  }

  /**
   * Try to find which quantifier we matched, given that we matched the head of a quantifier at the given IndexedWord, and that
   * this whole deal is taking place in the given sentence.
   *
   * @param sentence The sentence we are matching.
   * @param quantifier The word at which we matched a quantifier.
   * @param isUnary If true, this is a unary quantifier
   *
   * @return An optional triple consisting of the particular quantifier we matched, as well as the span of that quantifier in the sentence.
   */
  private static Optional<Triple<Operator,Integer,Integer>> validateQuantifierByHead(CoreMap sentence, IndexedWord quantifier, boolean isUnary) {
    // Some useful variables
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    Function<CoreLabel, String> glossFn = (label) -> "CD".equals(label.tag()) ? "--NUM--" : label.lemma();
    int quantIndex = quantifier.index();

    // Look forward a bit too, if the head is a number.
    int[] positiveOffsetToCheck = "CD".equals(tokens.get(quantIndex - 1).tag()) ? new int[]{2, 1, 0} : new int[]{0};

    // Try searching backwards for the right quantifier
    for (int offsetEnd : positiveOffsetToCheck) {
      int end = quantIndex + offsetEnd;
      for (int start = Math.max(0, quantIndex - 10); start < quantIndex; ++start) {
        String gloss = StringUtils.join(tokens, " ", glossFn, start, end).toLowerCase();
        for (Operator q : Operator.valuesByLengthDesc) {
          if (q.surfaceForm.equals(gloss) && ( !q.isUnary() || isUnary ) ) {
            return Optional.of(Triple.makeTriple(q, start + 1, end + 1));
          }
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Pre-process dependency tree so that negating advmod relations get the "neg" label.
   * @param tree dependency tree to be processed.
   */
  private void addNegationToDependencyGraph(SemanticGraph tree) {
    // cdm: this code does work, checked 2020-02-05
    // System.out.println("addNegationToDependencyGraph");
    // System.out.println(tree);
    SemanticGraph sg = tree.makeSoftCopy();
    SemgrexMatcher matcher = NEG_PATTERN.matcher(sg);
    while (matcher.find()) {
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord dep = matcher.getNode("quantifier");
      tree.updateEdge(tree.getEdge(gov, dep), UniversalEnglishGrammaticalRelations.NEGATION_MODIFIER);
    }
    // System.out.println("becomes");
    // System.out.println(tree);
  }

  /**
   * Find the operators in this sentence, annotating the head word (only!) of each operator with the
   * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.OperatorAnnotation}.
   *
   * @param sentence As in {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator#doOneSentence(edu.stanford.nlp.pipeline.Annotation, edu.stanford.nlp.util.CoreMap)}
   */
  private void annotateOperators(CoreMap sentence) {
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tree == null) {
      tree = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    }
    this.addNegationToDependencyGraph(tree);
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
        IndexedWord object = matcher.getNode("object");

        // Validate quantifier
        // At the end of this
        Optional<Triple<Operator,Integer,Integer>> quantifierInfo;
        if (namedEntityQuantifier) {
          // named entities have the "all" semantics by default.
          if (!neQuantifiers) {
            continue;
          }
          quantifierInfo = Optional.of(Triple.makeTriple(Operator.IMPLICIT_NAMED_ENTITY, quantifier.index(), quantifier.index()));  // note: empty quantifier span given
        } else {
          // find the quantifier, and return some info about it.
          quantifierInfo = validateQuantifierByHead(sentence, quantifier, object == null || subject == null);
        }

        // Awful hacks to regularize the subject of things like "one of" and "there are"
        // (fix up 'there are')
        if ("be".equals(subject == null ? null : subject.lemma())) {
          boolean hasExpl = false;
          IndexedWord newSubject = null;
          for (SemanticGraphEdge outgoingEdge : tree.outgoingEdgeIterable(subject)) {
            if ("nsubj".equals(outgoingEdge.getRelation().toString())) {
              newSubject = outgoingEdge.getDependent();
            } else if ("expl".equals(outgoingEdge.getRelation().toString())) {
              hasExpl = true;
            }
          }
          if (hasExpl) {
            subject = newSubject;
          }
        }
        // (fix up '$n$ of')
        if ("CD".equals(subject == null ? null : subject.tag())) {
          for (SemanticGraphEdge outgoingEdge : tree.outgoingEdgeIterable(subject)) {
            String rel = outgoingEdge.getRelation().toString();
            if (rel.startsWith("nmod") || rel.startsWith("obl")) {
              subject = outgoingEdge.getDependent();
            }
          }
        }

        // Set tokens
        if (quantifierInfo.isPresent()) {
          // Compute span
          IndexedWord pivot = matcher.getNode("pivot");
          if (pivot == null) {
            pivot = object;
          }
          OperatorSpec scope = computeScope(tree, quantifierInfo.get().first,
              pivot, Pair.makePair(quantifierInfo.get().second, quantifierInfo.get().third),
              subject, namedEntityQuantifier, object, tokens.size());
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
    for (int i = 0; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);
      OperatorSpec operator;
      if ( (operator = token.get(OperatorAnnotation.class)) != null) {
        if (i == 0 && operator.instance == Operator.NO && tokens.size() > 2 && "PRP".equals(tokens.get(1).get(CoreAnnotations.PartOfSpeechAnnotation.class))) {
          // This is pragmatically not a negation -- ignore it
          // For example, "no I don't like candy" or "no you like cats"
          token.remove(OperatorAnnotation.class);
        } else {
          quantifiers.add(operator);
        }
      }
    }
    quantifiers.sort( (x, y) -> y.quantifierLength() - x.quantifierLength());
    for (OperatorSpec quantifier : quantifiers) {
      for (int i = quantifier.quantifierBegin; i < quantifier.quantifierEnd; ++i) {
        if (i != quantifier.quantifierHead) {
          tokens.get(i).remove(OperatorAnnotation.class);
        }
      }
    }
  }

  /**
   * Annotate any unary quantifiers that weren't found in the main {@link NaturalLogicAnnotator#annotateOperators(CoreMap)} method.
   * @param sentence The sentence to annotate.
   */
  private static void annotateUnaries(CoreMap sentence) {
    // Get tree and tokens
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    if (tree == null) {
      tree = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    }
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

    // Get operator exists mask
    boolean[] isOperator = new boolean[tokens.size()];
    for (int i = 0; i < isOperator.length; ++i) {
      OperatorSpec spec = tokens.get(i).get(OperatorAnnotation.class);
      if (spec != null) {
        for (int k = spec.quantifierBegin; k < spec.quantifierEnd; ++k) {
          isOperator[k] = true;
        }
      }
    }

    // Match Semgrex
    SemgrexMatcher matcher = UNARY_PATTERN.matcher(tree);
    while (matcher.find()) {
      // Get relevant nodes
      IndexedWord quantifier = matcher.getNode("quantifier");
      String word = quantifier.word().toLowerCase();
      if (word.equals("a") || word.equals("an") || word.equals("the") ||
          "CD".equals(quantifier.tag())) {
        continue;  // These are absurdly common, and uninformative, and we're just going to shoot ourselves in the foot from parsing errors and idiomatic expressions.
      }
      IndexedWord subject = matcher.getNode("subject");
      // ... If there is not already an operator there
      if (!isOperator[quantifier.index() - 1]) {
        Optional<Triple<Operator, Integer, Integer>> quantifierInfo = validateQuantifierByHead(sentence, quantifier, true);
        // ... and if we found a quantifier span
        if (quantifierInfo.isPresent()) {
          // Then add the unary operator!
          OperatorSpec scope = computeScope(tree, quantifierInfo.get().first,
              subject, Pair.makePair(quantifierInfo.get().second, quantifierInfo.get().third),
              null, false, null, tokens.size());
          CoreLabel token = tokens.get(quantifier.index() - 1);
          token.set(OperatorAnnotation.class, scope);
        }
      }
    }

    // Match TokensRegex
    TokenSequenceMatcher tokenMatcher = DOUBT_PATTERN.matcher(tokens);
    while (tokenMatcher.find()) {
      List<CoreLabel> doubt = (List<CoreLabel>) tokenMatcher.groupNodes("$doubt");
      List<CoreLabel> target = (List<CoreLabel>) tokenMatcher.groupNodes("$target");
      for (CoreLabel word : doubt) {
        OperatorSpec spec = new OperatorSpec(Operator.GENERAL_NEG_POLARITY,
            word.index() - 1, word.index(),
            target.get(0).index() - 1, target.get(target.size() - 1).index(),
            0, 0,
            tokens.size());
        word.set(OperatorAnnotation.class, spec);
      }

    }
  }

  /**
   * Annotate every token for its polarity, based on the operators found. This function will set the
   * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.PolarityAnnotation} for every token.
   *
   * @param sentence As in {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator#doOneSentence(edu.stanford.nlp.pipeline.Annotation, edu.stanford.nlp.util.CoreMap)}
   */
  private static void annotatePolarity(CoreMap sentence) {
    // Collect all the operators in this sentence
    List<OperatorSpec> operators = new ArrayList<>();
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      OperatorSpec specOrNull = token.get(OperatorAnnotation.class);
      if (specOrNull != null) {
        operators.add(specOrNull);
      }
    }

    // Make sure every node of the dependency tree has a polarity.
    // This is separate from the code below in case the tokens in the dependency
    // tree don't correspond to the tokens in the sentence. This happens at least
    // when the constituency parser craps out on a long sentence, and the
    // dependency tree is put together haphazardly.
    if (sentence.containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)) {
      for (IndexedWord token : sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class).vertexSet()) {
        token.set(PolarityAnnotation.class, Polarity.DEFAULT);
      }
    }
    if (sentence.containsKey(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)) {
      for (IndexedWord token : sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class).vertexSet()) {
        token.set(PolarityAnnotation.class, Polarity.DEFAULT);
      }
    }
    if (sentence.containsKey(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)) {
      for (IndexedWord token : sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class).vertexSet()) {
        token.set(PolarityAnnotation.class, Polarity.DEFAULT);
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
      // Sort the operators by their scope (approximated by the size of their argument span)
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

    // Set the PolarityDirectionAnnotation
    for (CoreLabel token : tokens) {
      Polarity polarity = token.get(PolarityAnnotation.class);
      if (polarity != null) {
        if (polarity.isUpwards()) {
          token.set(PolarityDirectionAnnotation.class, "up");
        } else if (polarity.isDownwards()) {
          token.set(PolarityDirectionAnnotation.class, "down");
        } else {
          token.set(PolarityDirectionAnnotation.class, "flat");
        }
      }
    }
  }

  /**
   * If false, don't annotate tokens for polarity but only find the operators and their scopes.
   */
  @ArgumentParser.Option(name="doPolarity", gloss="Mark polarity in addition to quantifier scopes")
  private boolean doPolarity = true;

  @SuppressWarnings("FieldCanBeLocal")
  @ArgumentParser.Option(name="neQuantifiers", gloss="If true, mark named entities as quantifiers.")
  private boolean neQuantifiers = false;

  /**
   * Create a new annotator.
   * @param annotatorName The prefix for the properties for this annotator.
   * @param props The properties to configure this annotator with.
   */
  public NaturalLogicAnnotator(String annotatorName, Properties props) {
    ArgumentParser.fillOptions(this, annotatorName, props);
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
    annotateUnaries(sentence);
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
    log.info("Failed to annotate: " + sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        doPolarity ? NaturalLogicAnnotations.PolarityAnnotation.class : null,
        NaturalLogicAnnotations.OperatorAnnotation.class
    )));
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class
    )));
  }

}
