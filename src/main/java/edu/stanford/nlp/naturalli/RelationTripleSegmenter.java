package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.EnglishPatterns;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class takes a {@link edu.stanford.nlp.naturalli.SentenceFragment} and converts it to a conventional
 * OpenIE triple, as materialized in the {@link RelationTriple} class.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("WeakerAccess")
public class RelationTripleSegmenter {

  private final boolean allowNominalsWithoutNER;

  private final Pattern NOT_PAT = Pattern.compile(EnglishPatterns.NOT_PAT_WORD,
                                                  Pattern.CASE_INSENSITIVE);

  // for semgrex patterns which don't want to be over a not word
  private final String NOT_OVER_NOT_WORD = "!> {word:/" + EnglishPatterns.NOT_PAT_WORD + "/}";

  /** A list of patterns to match relation extractions against */
  public final List<SemgrexPattern> VERB_PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { blue cats play [quietly] with yarn,
    //   Jill blew kisses at Jack,
    //   cats are standing next to dogs }
    add(SemgrexPattern.compile("{$}=verb ?>/cop|aux(:pass)?/ {}=be >/.subj(:pass)?/ {}=subject >/(nmod|obl|acl|advcl):.*/=prepEdge ( {}=object ?>appos {} = appos ?>case {}=prep) ?>obj {pos:/N.*/}=relObj"));
    // { cats are cute,
    //   horses are grazing peacefully }
    add(SemgrexPattern.compile("{$}=object >/.subj(:pass)?/ {}=subject >/cop|aux(:pass)?/ {}=verb ?>case {}=prep " + NOT_OVER_NOT_WORD));
    // { fish like to swim }
    add(SemgrexPattern.compile("{$}=verb >/.subj(:pass)?/ {}=subject >xcomp ( {}=object ?>appos {}=appos )"));
    // { cats have tails }
    // older versions of dependencies produce dobj, newer may just be obj.
    // this expression accommodates both
    add(SemgrexPattern.compile("{$}=verb ?>/aux(:pass)?/ {}=be >/.subj(:pass)?/ {}=subject >/[di]?obj|xcomp/ ( {}=object ?>appos {}=appos )"));
    // { Tom and Jerry were fighting }
    add(SemgrexPattern.compile("{$}=verb >/nsubj(:pass)?/ ( {}=subject >/conj:and/=subjIgnored ( {}=object ?>/cc/=objIgnored {} ))"));
    // { mass of iron is 55amu }
    add(SemgrexPattern.compile("{pos:/NNS?/}=object >cop {}=relappend1 >/nsubj(:pass)?/ ( {}=verb >/nmod:of/ ( {pos:/NNS?/}=subject >case {}=relappend0 ) )"));
  }});

  /**
   * <p>
   *   A set of derivative patterns from {@link RelationTripleSegmenter#VERB_PATTERNS} that ignore the subject
   *   arc. This is useful primarily for creating a training set for the clause splitter which emulates the
   *   behavior of the relation triple segmenter component.
   * </p>
   */
  public final List<SemgrexPattern> VP_PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    for (SemgrexPattern pattern : VERB_PATTERNS) {
      String fullPattern = pattern.pattern();
      String vpPattern = fullPattern
          .replace(">/.subj(:pass)?/ {}=subject", "")  // drop the subject
          .replace("{$}", "{pos:/V.*/}");                 // but, force the root to be on a verb
      add(SemgrexPattern.compile(vpPattern));
    }
  }});

  /**
   * A set of nominal patterns, that don't require being in a coherent clause, but do require NER information.
   */
  public final List<TokenSequencePattern> NOUN_TOKEN_PATTERNS = Collections.unmodifiableList(new ArrayList<TokenSequencePattern>() {{
    // { NER nominal_verb NER,
    //   United States president Obama }
    add(TokenSequencePattern.compile("(?$object [ner:/PERSON|ORGANIZATION|LOCATION+/]+ ) (?$beof_comp [ {tag:/NN.*/} & !{ner:/PERSON|ORGANIZATION|LOCATION/} ]+ ) (?$subject [ner:/PERSON|ORGANIZATION|LOCATION/]+ )"));
    // { NER 's nominal_verb NER,
    //   America 's president , Obama }
    add(TokenSequencePattern.compile("(?$object [ner:/PERSON|ORGANIZATION|LOCATION+/]+ ) /'s/ (?$beof_comp [ {tag:/NN.*/} & !{ner:/PERSON|ORGANIZATION|LOCATION/} ]+ ) /,/? (?$subject [ner:/PERSON|ORGANIZATION|LOCATION/]+ )"));
    // { NER , NER ,,
    //   Obama, 28, ...,
    //   Obama (28) ...}
    add(TokenSequencePattern.compile("(?$subject [ner:/PERSON|ORGANIZATION|LOCATION/]+ ) /,/ (?$object [ner:/NUMBER|DURATION|PERSON|ORGANIZATION/]+ ) /,/"));
    add(TokenSequencePattern.compile("(?$subject [ner:/PERSON|ORGANIZATION|LOCATION/]+ ) /\\(/ (?$object [ner:/NUMBER|DURATION|PERSON|ORGANIZATION/]+ ) /\\)/"));
  }});

  /**
   * A set of nominal patterns using dependencies, that don't require being in a coherent clause, but do require NER information.
   */
  private final List<SemgrexPattern> NOUN_DEPENDENCY_PATTERNS;


  /**
   * Create a new relation triple segmenter.
   *
   * @param allowNominalsWithoutNER If true, extract all nominal relations and not just those which are warranted based on
   *                                named entity tags. For most practical applications, this greatly over-produces trivial triples.
   */
  public RelationTripleSegmenter(boolean allowNominalsWithoutNER) {
    this.allowNominalsWithoutNER = allowNominalsWithoutNER;
    NOUN_DEPENDENCY_PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
      // { Durin, son of Thorin }
      add(SemgrexPattern.compile("{tag:/N.*/}=subject >appos ( {}=relation >/(nmod|obl):.*/=relaux {}=object)"));
      // { Thorin's son, Durin }
      add(SemgrexPattern.compile("{}=relation >/(nmod|obl):.*/=relaux {}=subject >appos {}=object"));
      // { Stanford's Chris Manning  }
      add(SemgrexPattern.compile("{tag:/N.*/}=object >/nmod:poss/=relaux ( {}=subject >case {} )"));
      // { Chris Manning of Stanford,
      //   [There are] cats with tails,
      if (allowNominalsWithoutNER) {
        add(SemgrexPattern.compile("{tag:/N.*/}=subject >/nmod:(?!poss).*/=relaux {}=object"));
      } else {
        add(SemgrexPattern.compile("{ner:/PERSON|ORGANIZATION|LOCATION/}=subject >/nmod:(?!poss).*/=relaux {ner:/..+/}=object"));
        add(SemgrexPattern.compile("{tag:/N.*/}=subject >/(nmod|obl):(in|with)/=relaux {}=object"));
      }
      //  { President Obama }
      if (allowNominalsWithoutNER) {
        add(SemgrexPattern.compile("{tag:/N.*/}=subject >/amod/=arc {}=object"));
      } else {
        add(SemgrexPattern.compile("{ner:/PERSON|ORGANIZATION|LOCATION/}=subject >/amod|compound/=arc {ner:/..+/}=object"));
      }
    }});
  }

  /**
   * @see RelationTripleSegmenter#RelationTripleSegmenter(boolean)
   */
  @SuppressWarnings("UnusedDeclaration")
  public RelationTripleSegmenter() {
    this(false);
  }

  /**
   * Extract the nominal patterns from this sentence.
   *
   * @see RelationTripleSegmenter#NOUN_TOKEN_PATTERNS
   * @see RelationTripleSegmenter#NOUN_DEPENDENCY_PATTERNS
   *
   * @param parse The parse tree of the sentence to annotate.
   * @param tokens The tokens of the sentence to annotate.
   * @return A list of {@link RelationTriple}s. Note that these do not have an associated tree with them.
   */
  @SuppressWarnings("unchecked")
  public List<RelationTriple> extract(SemanticGraph parse, List<CoreLabel> tokens) {
    List<RelationTriple> extractions = new ArrayList<>();
    Set<Triple<Span,String,Span>> alreadyExtracted = new HashSet<>();

    //
    // Run Token Patterns
    //
    for (TokenSequencePattern tokenPattern : NOUN_TOKEN_PATTERNS) {
      TokenSequenceMatcher tokenMatcher = tokenPattern.matcher(tokens);
      while (tokenMatcher.find()) {
        boolean missingPrefixBe;
        boolean missingSuffixOf = false;

        // Create subject
        List<? extends CoreMap> subject = tokenMatcher.groupNodes("$subject");
        Span subjectSpan = Util.extractNER(tokens, Span.fromValues(((CoreLabel) subject.get(0)).index() - 1, ((CoreLabel) subject.get(subject.size() - 1)).index()));
        List<CoreLabel> subjectTokens = new ArrayList<>();
        for (int i : subjectSpan) {
          subjectTokens.add(tokens.get(i));
        }

        // Create object
        List<? extends CoreMap> object = tokenMatcher.groupNodes("$object");
        Span objectSpan = Util.extractNER(tokens, Span.fromValues(((CoreLabel) object.get(0)).index() - 1, ((CoreLabel) object.get(object.size() - 1)).index()));
        if (Span.overlaps(subjectSpan, objectSpan)) {
          continue;
        }
        List<CoreLabel> objectTokens = new ArrayList<>();
        for (int i : objectSpan) {
          objectTokens.add(tokens.get(i));
        }

        // Create relation
        if (subjectTokens.size() > 0 && objectTokens.size() > 0) {
          List<CoreLabel> relationTokens = new ArrayList<>();
          // (add the 'be')
          missingPrefixBe = true;
          // (add a complement to the 'be')
          List<? extends CoreMap> beofComp = tokenMatcher.groupNodes("$beof_comp");
          if (beofComp != null) {
            // (add the complement
            for (CoreMap token : beofComp) {
              if (token instanceof CoreLabel) {
                relationTokens.add((CoreLabel) token);
              } else {
                relationTokens.add(new CoreLabel(token));
              }
            }
            // (add the 'of')
            missingSuffixOf = true;
          }
          // Add extraction
          String relationGloss = StringUtils.join(relationTokens.stream().map(CoreLabel::word), " ");
          if (!alreadyExtracted.contains(Triple.makeTriple(subjectSpan, relationGloss, objectSpan))) {
            RelationTriple extraction = new RelationTriple(subjectTokens, relationTokens, objectTokens);
            //noinspection ConstantConditions
            extraction.isPrefixBe(missingPrefixBe);
            extraction.isSuffixOf(missingSuffixOf);
            extractions.add(extraction);
            alreadyExtracted.add(Triple.makeTriple(subjectSpan, relationGloss, objectSpan));
          }
        }
      }

      //
      // Run Semgrex Matches
      //
      for (SemgrexPattern semgrex : NOUN_DEPENDENCY_PATTERNS) {
        SemgrexMatcher matcher = semgrex.matcher(parse);
        while (matcher.find()) {
          boolean missingPrefixBe = false;
          boolean missingSuffixBe = false;
          boolean istmod = false;

          // Get relaux if applicable
          String relaux = matcher.getRelnString("relaux");
          String ignoredArc = relaux;
          if (ignoredArc == null) {
            ignoredArc = matcher.getRelnString("arc");
          }

          // Create subject
          IndexedWord subject = matcher.getNode("subject");
          List<IndexedWord> subjectTokens = new ArrayList<>();
          Span subjectSpan;
          if (subject.ner() != null && !"O".equals(subject.ner())) {
            subjectSpan = Util.extractNER(tokens, Span.fromValues(subject.index() - 1, subject.index()));
            for (int i : subjectSpan) {
              subjectTokens.add(new IndexedWord(tokens.get(i)));
            }
          } else {
            subjectTokens = getValidChunk(parse, subject, VALID_SUBJECT_ARCS, Optional.ofNullable(ignoredArc), true).orElse(Collections.singletonList(subject));
            subjectSpan = Util.tokensToSpan(subjectTokens);
          }

          // Create object
          IndexedWord object = matcher.getNode("object");
          List<IndexedWord> objectTokens = new ArrayList<>();
          Span objectSpan;
          if (object.ner() != null && !"O".equals(object.ner())) {
            objectSpan = Util.extractNER(tokens, Span.fromValues(object.index() - 1, object.index()));
            for (int i : objectSpan) {
              objectTokens.add(new IndexedWord(tokens.get(i)));
            }
          } else {
            objectTokens = getValidChunk(parse, object, VALID_OBJECT_ARCS, Optional.ofNullable(ignoredArc), true).orElse(Collections.singletonList(object));
            objectSpan = Util.tokensToSpan(objectTokens);
          }

          // Check that the pair is valid
          if (Span.overlaps(subjectSpan, objectSpan)) {
            continue;  // We extracted an identity
          }
          if (subjectSpan.end() == objectSpan.start() - 1 &&
              (tokens.get(subjectSpan.end()).word().matches("[\\.,:;\\('\"]") ||
                  "CC".equals(tokens.get(subjectSpan.end()).tag()))) {
            continue; // We're straddling a clause
          }
          if (objectSpan.end() == subjectSpan.start() - 1 &&
              (tokens.get(objectSpan.end()).word().matches("[\\.,:;\\('\"]") ||
                  "CC".equals(tokens.get(objectSpan.end()).tag()))) {
            continue; // We're straddling a clause
          }

          // Get any prepositional edges
          String expected = relaux == null ? "" : relaux.substring(relaux.indexOf(":") + 1).replace("_", " ");
          IndexedWord prepWord = null;
          // (these usually come from the object)
          boolean prepositionIsPrefix = false;
          for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(object)) {
            if (edge.getRelation().toString().equals("case")) {
              prepWord = edge.getDependent();
            }
          }
          // (...but sometimes from the subject)
          if (prepWord == null) {
            for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(subject)) {
              if (edge.getRelation().toString().equals("case")) {
                prepositionIsPrefix = true;
                prepWord = edge.getDependent();
              }
            }
          }
          List<IndexedWord> prepChunk = Collections.EMPTY_LIST;
          if (prepWord != null && !expected.equals("tmod")) {
            Optional<List<IndexedWord>> optionalPrepChunk = getValidChunk(parse, prepWord, Collections.singleton("mwe"), Optional.empty(), true);
            if (!optionalPrepChunk.isPresent()) { continue; }
            prepChunk = optionalPrepChunk.get();
            Collections.sort(prepChunk, (a, b) -> {
              double val = a.pseudoPosition() - b.pseudoPosition();
              if (val < 0) { return -1; }
              if (val > 0) { return 1; }
              else { return 0; }
            });  // ascending sort
          }

          // Get the relation
          if (subjectTokens.size() > 0 && objectTokens.size() > 0) {
            LinkedList<IndexedWord> relationTokens = new LinkedList<>();
            IndexedWord relNode = matcher.getNode("relation");
            if (relNode != null) {

              // Case: we have a grounded relation span
              // (add the relation)
              relationTokens.add(relNode);
              // (add any prepositional case markings)
              if (prepositionIsPrefix) {
                missingSuffixBe = true;  // We're almost certainly missing a suffix 'be'
                for (int i = prepChunk.size() - 1; i >=0; --i) { relationTokens.addFirst(prepChunk.get(i)); }
              } else {
                relationTokens.addAll(prepChunk);
              }
              if (expected.equalsIgnoreCase("tmod")) {
                istmod = true;
              }

            } else {

              // Case: we have a hallucinated relation span
              // (mark it as missing a preceding 'be'
              if (!expected.equals("poss")) {
                missingPrefixBe = true;
              }
              // (add any prepositional case markings)
              if (prepositionIsPrefix) {
                for (int i = prepChunk.size() - 1; i >=0; --i) { relationTokens.addFirst(prepChunk.get(i)); }
              } else {
                relationTokens.addAll(prepChunk);
              }
              if (expected.equalsIgnoreCase("tmod")) {
                istmod = true;
              }
              // (some fine-tuning)
              if (allowNominalsWithoutNER && "of".equals(expected)) {
                continue;  // prohibit things like "conductor of electricity" -> "conductor; be of; electricity"
              }
            }


            // Add extraction
            String relationGloss = StringUtils.join(relationTokens.stream().map(IndexedWord::word), " ");
            if (!alreadyExtracted.contains(Triple.makeTriple(subjectSpan, relationGloss, objectSpan))) {
              RelationTriple extraction = new RelationTriple(
                  subjectTokens.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
                  relationTokens.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
                  objectTokens.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()));
              extraction.istmod(istmod);
              extraction.isPrefixBe(missingPrefixBe);
              extraction.isSuffixBe(missingSuffixBe);
              extractions.add(extraction);
              alreadyExtracted.add(Triple.makeTriple(subjectSpan, relationGloss, objectSpan));
            }
          }
        }
      }
    }

    //
    // Filter downward polarity extractions
    //
    Iterator<RelationTriple> iter = extractions.iterator();
    while (iter.hasNext()) {
      RelationTriple term = iter.next();
      boolean shouldRemove = true;
      for (CoreLabel token : term) {
        if (token.get(NaturalLogicAnnotations.PolarityAnnotation.class) == null ||
            !token.get(NaturalLogicAnnotations.PolarityAnnotation.class).isDownwards() ) {
          shouldRemove = false;
        }
      }
      if (shouldRemove) {
        iter.remove();   // Don't extract things in downward polarity contexts.
      }
    }

    // Return
    return extractions;
  }

//  /**
//   * A counter keeping track of how many times a given pattern has matched. This allows us to learn to iterate
//   * over patterns in the optimal order; this is just an efficiency tweak (but an effective one!).
//   */
//  private final Counter<SemgrexPattern> VERB_PATTERN_HITS = new ClassicCounter<>();

  /** A set of valid arcs denoting a subject entity we are interested in */
  public final Set<String> VALID_SUBJECT_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("compound"); add("aux"); add("nummod"); add("nmod:poss"); add("nmod:tmod"); add("expl");
    add("obl:tmod");
    add("nsubj"); add("case"); add("mark");
  }});

  /** A set of valid arcs denoting an object entity we are interested in */
  public final Set<String> VALID_OBJECT_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("compound"); add("aux"); add("nummod"); add("nmod"); add("nsubj"); add("nmod:*"); add("nmod:poss");
    add("nmod:tmod"); add("conj:and"); add("advmod"); add("acl"); add("case"); add("mark");
    add("obl"); add("obl:*"); add("obl:tmod");
    // add("advcl"); // Born in Hawaii, Obama is a US citizen; citizen -advcl-> Born.
  }});

  /** A set of valid arcs denoting an adverbial modifier we are interested in */
  public final Set<String> VALID_ADVERB_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("advmod"); add("conj"); add("cc"); add("conj:and"); add("conj:or");
    add("auxpass"); add("compound:*"); add("obl:*");  add("obl");
  }});

  /**
   * @see RelationTripleSegmenter#getValidSubjectChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord, Optional)
   * @see RelationTripleSegmenter#getValidObjectChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord, Optional)
   * @see RelationTripleSegmenter#getValidAdverbChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord, Optional)
   */
  @SuppressWarnings("StatementWithEmptyBody")
  protected Optional<List<IndexedWord>> getValidChunk(SemanticGraph parse, IndexedWord originalRoot,
                                                    Set<String> validArcs, Optional<String> ignoredArc,
                                                    boolean allowExtraArcs) {
    PriorityQueue<IndexedWord> chunk = new FixedPrioritiesPriorityQueue<>();
    Set<Double> seenIndices = new HashSet<>();
    Queue<IndexedWord> fringe = new LinkedList<>();
    IndexedWord root = originalRoot;
    fringe.add(root);

    boolean isCopula = false;
    IndexedWord primaryCase = null;
    for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(originalRoot)) {
      String shortName = edge.getRelation().getShortName();
      if (shortName.equals("cop") || shortName.equals("aux:pass")) {
        isCopula = true;
      }
      if (shortName.equals("case")) {
        primaryCase = edge.getDependent();
      }
    }

    while (!fringe.isEmpty()) {
      root = fringe.poll();
      chunk.add(root, -root.pseudoPosition());

      // Sanity check to prevent infinite loops
      if (seenIndices.contains(root.pseudoPosition())) {
        // TODO(gabor) Indicates a cycle in the tree!
        return Optional.empty();
      }
      seenIndices.add(root.pseudoPosition());

      // Check outgoing edges
      boolean hasConj = false;
      boolean hasCC = false;
      for (SemanticGraphEdge edge : parse.getOutEdgesSorted(root)) {
        String shortName = edge.getRelation().getShortName();
        String name = edge.getRelation().toString();
        if (shortName.startsWith("conj")) {
          hasConj = true;
          // "cc" is now supposed to be attached to the other side of
          // the "conj".  Check that the child has a "CC" coming off
          // it somewhere.  If not here, perhaps one of the other
          // children has a CC child, so we don't immediately abort if
          // not found here
          for (SemanticGraphEdge ccEdge :
                 parse.getOutEdgesSorted(edge.getDependent())) {
            if (ccEdge.getRelation().getShortName().equals("cc")) {
              hasCC = true;
              break;
            }
          }
        }

        if (shortName.equals("cc")) {
          // If we have a "cc" below us, we should be part of a
          // "conj" relation above us.  Double check that
          boolean hasParentConj = false;
          for (SemanticGraphEdge conjEdge :
                 parse.getIncomingEdgesSorted(edge.getGovernor())) {
            if (conjEdge.getRelation().getShortName().startsWith("conj")) {
              hasParentConj = true;
              break;
            }
          }
          if (!hasParentConj) {
            // If we found a CC and are not part of a conj, perhaps
            // that means something went haywire in the parse.
            // Regardless, we abort immediately
            return Optional.empty();
          }
        }

        //noinspection StatementWithEmptyBody
        if (isCopula && (shortName.equals("cop") || shortName.contains("subj") || shortName.equals("aux:pass") )) {
          // noop; ignore nsubj, cop for extractions with copula
        } else if (edge.getDependent() == primaryCase) {
          // noop: ignore case edge
        } else if (ignoredArc.isPresent() &&
                   (ignoredArc.get().equals(name) || (ignoredArc.get().startsWith("conj") && name.equals("cc")))) {
          // noop; ignore explicitly requested noop arc, or "CC" if the noop arc is a conj:*
        } else if (!validArcs.contains(edge.getRelation().getShortName()) && !validArcs.contains(edge.getRelation().getShortName().replaceAll(":.*",":*"))) {
          if (!allowExtraArcs) {
            return Optional.empty();
          } else {
            // noop: just some dangling arc
          }
        } else {
          fringe.add(edge.getDependent());
        }
      }

      // Ensure that we don't have a conj without a cc, or vice versa
      if (Boolean.logicalXor(hasConj, hasCC)) {
        return Optional.empty();
      }
    }

    return Optional.of(chunk.toSortedList());
  }

  /**
   * @see RelationTripleSegmenter#getValidChunk(SemanticGraph, IndexedWord, Set, Optional, boolean)
   */
  protected Optional<List<IndexedWord>> getValidChunk(SemanticGraph parse, IndexedWord originalRoot,
                                                    Set<String> validArcs, Optional<String> ignoredArc) {
    return getValidChunk(parse, originalRoot, validArcs, ignoredArc, false);
  }

  /**
   * Get the yield of a given subtree, if it is a valid subject.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @param noopArc An optional edge type to ignore in gathering the chunk.
   * @return If this subtree is a valid entity, we return its yield. Otherwise, we return empty.
   */
  protected Optional<List<IndexedWord>> getValidSubjectChunk(SemanticGraph parse, IndexedWord root, Optional<String> noopArc) {
    return getValidChunk(parse, root, VALID_SUBJECT_ARCS, noopArc);
  }

  /**
   * Get the yield of a given subtree, if it is a valid object.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @param noopArc An optional edge type to ignore in gathering the chunk.
   * @return If this subtree is a valid entity, we return its yield. Otherwise, we return empty.
   */
  protected Optional<List<IndexedWord>> getValidObjectChunk(SemanticGraph parse, IndexedWord root, Optional<String> noopArc) {
    return getValidChunk(parse, root, VALID_OBJECT_ARCS, noopArc);
  }

  /**
   * Get the yield of a given subtree, if it is a adverb chunk.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @param noopArc An optional edge type to ignore in gathering the chunk.
   * @return If this subtree is a valid adverb, we return its yield. Otherwise, we return empty.
   */
  protected Optional<List<IndexedWord>> getValidAdverbChunk(SemanticGraph parse, IndexedWord root, Optional<String> noopArc) {
    return getValidChunk(parse, root, VALID_ADVERB_ARCS, noopArc);
  }

  /**
   * <p>
   * Try to segment this sentence as a relation triple.
   * This sentence must already match one of a few strict patterns for a valid OpenIE extraction.
   * If it does not, then no relation triple is created.
   * That is, this is <b>not</b> a relation extractor; it is just a utility to segment what is already a
   * (subject, relation, object) triple into these three parts.
   * </p>
   *
   * <p>
   *   This method will only run the verb-centric patterns
   * </p>
   *
   * @param parse The sentence to process, as a dependency tree.
   * @param confidence An optional confidence to pass on to the relation triple.
   * @param consumeAll if true, force the entire parse to be consumed by the pattern.
   * @return A relation triple, if this sentence matches one of the patterns of a valid relation triple.
   */
  @SuppressWarnings("UnnecessaryLabelOnContinueStatement")
  private Optional<RelationTriple> segmentVerb(SemanticGraph parse,
                                               Optional<Double> confidence,
                                               boolean consumeAll) {
    // Run pattern loop
    PATTERN_LOOP: for (SemgrexPattern pattern : VERB_PATTERNS) {  // For every candidate pattern...
      SemgrexMatcher m = pattern.matcher(parse);
      if (m.matches()) {  // ... see if it matches the sentence
        if ("nmod:poss".equals(m.getRelnString("prepEdge"))) {
          continue PATTERN_LOOP;   // nmod:poss is not a preposition!
        }
        int numKnownDependents = 2;  // subject and object, at minimum
        boolean istmod = false;      // this is a tmod relation

        // Object
        IndexedWord object = m.getNode("appos");
        if (object == null) {
          object = m.getNode("object");
        }
        if (object != null && object.tag() != null && object.tag().startsWith("W")) {
          continue;  // don't extract WH arguments
        }
        assert object != null;

        // Verb
        PriorityQueue<IndexedWord> verbChunk = new FixedPrioritiesPriorityQueue<>();
        IndexedWord verb = m.getNode("verb");
        List<IndexedWord> adverbs = new ArrayList<>();
        Optional<String> subjNoopArc = Optional.empty();
        Optional<String> objNoopArc = Optional.empty();
        assert verb != null;
        // Case: a standard extraction with a main verb
        IndexedWord relObj = m.getNode("relObj");
        for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(verb)) {
          if ("advmod".equals(edge.getRelation().toString()) ||
              "amod".equals(edge.getRelation().toString()) ||
              "compound:*".equals(edge.getRelation().toString().replaceAll(":.*", ":*"))) {
            // Add adverb modifiers
            String tag = edge.getDependent().backingLabel().tag();
            String word = edge.getDependent().backingLabel().word();
            if (tag == null ||
                (!tag.startsWith("W") && // prohibit advmods like "where"
                 !word.equalsIgnoreCase("then") &&
                 !NOT_PAT.matcher(word).matches())) { // prohibit "not"
              adverbs.add(edge.getDependent());
            }
          } else if (edge.getDependent().equals(relObj)) {
            // Add additional object to the relation
            Optional<List<IndexedWord>> relObjSpan = getValidChunk(parse, relObj, Collections.singleton("compound"), Optional.empty());
            if (!relObjSpan.isPresent()) {
              continue PATTERN_LOOP;
            } else {
              for (IndexedWord token : relObjSpan.get()) {
                verbChunk.add(token, -token.pseudoPosition());
              }
              numKnownDependents += 1;
            }
          }
        }
        verbChunk.add(verb, -verb.pseudoPosition());

        // Prepositions
        IndexedWord prep = m.getNode("prep");
        String prepEdge = m.getRelnString("prepEdge");
        if (prep != null) {
          // (get the preposition chunk)
          Optional<List<IndexedWord>> chunk = getValidChunk(parse, prep, Collections.singleton("mwe"), Optional.empty(), true);
          // (continue if no chunk found)
          if (!chunk.isPresent()) {
            continue PATTERN_LOOP;  // Probably something like a conj w/o a cc
          }
          // (add the preposition)
          for (IndexedWord word : chunk.get()) {
            verbChunk.add(word, Integer.MIN_VALUE / 2 - word.pseudoPosition());
          }
        }
        // (handle special prepositions)
        if (prepEdge != null) {
          String prepStringFromEdge = prepEdge.substring(prepEdge.indexOf(":") + 1).replace("_", " ");
          if ("tmod".equals(prepStringFromEdge)) {
            istmod = true;
          }
        }

        // Auxilliary "be"
        IndexedWord be = m.getNode("be");
        if (be != null) { verbChunk.add(be, -be.pseudoPosition()); numKnownDependents += 1; }
        // (adverbs have to be well-formed)
        if (!adverbs.isEmpty()) {
          Set<IndexedWord> adverbialModifiers = new HashSet<>();
          for (IndexedWord adv : adverbs) {
            Optional<List<IndexedWord>> adverbChunk = getValidAdverbChunk(parse, adv, Optional.empty());
            if (adverbChunk.isPresent()) {
              adverbialModifiers.addAll(adverbChunk.get().stream().collect(Collectors.toList()));
            } else {
              continue PATTERN_LOOP;  // Invalid adverbial phrase
            }
            numKnownDependents += 1;
          }
          for (IndexedWord adverbToken : adverbialModifiers) {
            verbChunk.add(adverbToken, -adverbToken.pseudoPosition());
          }
        }

        // (check for additional edges)
        if (consumeAll && parse.outDegree(verb) > numKnownDependents) {
          //noinspection UnnecessaryLabelOnContinueStatement
          continue PATTERN_LOOP;  // Too many outgoing edges; we didn't consume them all.
        }
        List<IndexedWord> relation = verbChunk.toSortedList();
        int appendI = 0;
        IndexedWord relAppend = m.getNode("relappend" + appendI);
        while (relAppend != null) {
          relation.add(relAppend);
          appendI += 1;
          relAppend = m.getNode("relappend" + appendI);
        }

        // Last chance to register ignored edges
        if (!subjNoopArc.isPresent()) {
          subjNoopArc = Optional.ofNullable(m.getRelnString("subjIgnored"));
          if (!subjNoopArc.isPresent()) {
            subjNoopArc = Optional.ofNullable(m.getRelnString("prepEdge"));  // For some strange "there are" cases
          }
        }
        if (!objNoopArc.isPresent()) {
          objNoopArc = Optional.ofNullable(m.getRelnString("objIgnored"));
        }

        // Find the subject
        // By default, this is just the subject node; but, occasionally we want to follow a
        // csubj clause to find the real subject.
        IndexedWord subject = m.getNode("subject");
        if (subject != null && subject.tag() != null && subject.tag().startsWith("W")) {
          continue;  // don't extract WH subjects
        }

        // Subject+Object
        Optional<List<IndexedWord>> subjectSpan = getValidSubjectChunk(parse, subject, subjNoopArc);
        Optional<List<IndexedWord>> objectSpan = getValidObjectChunk(parse, object, objNoopArc);
        // Create relation
        if (subjectSpan.isPresent() && objectSpan.isPresent() &&
            CollectionUtils.intersection(new HashSet<>(subjectSpan.get()), new HashSet<>(objectSpan.get())).isEmpty()
            ) {  // ... and has a valid subject+object
          // Success! Found a valid extraction.
          RelationTriple.WithTree extraction = new RelationTriple.WithTree(
              subjectSpan.get().stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              relation.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              objectSpan.get().stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              parse, confidence.orElse(1.0));
          extraction.istmod(istmod);
          return Optional.of(extraction);
        }
      }
    }
    // Failed to match any pattern; return failure
    return Optional.empty();
  }

  /**
   * Same as {@link RelationTripleSegmenter#segmentVerb}, but with ACL clauses.
   * This is a bit out of the ordinary, logic-wise, so it sits in its own function.
   */
  private Optional<RelationTriple> segmentACL(SemanticGraph parse, Optional<Double> confidence, boolean consumeAll) {
    IndexedWord subject = parse.getFirstRoot();
    Optional<List<IndexedWord>> subjectSpan = getValidSubjectChunk(parse, subject, Optional.of("acl"));
    if (subjectSpan.isPresent()) {
      // found a valid subject
      for (SemanticGraphEdge edgeFromSubj : parse.outgoingEdgeIterable(subject)) {
        if ("acl".equals(edgeFromSubj.getRelation().toString())) {
          // found a valid relation
          IndexedWord relation = edgeFromSubj.getDependent();
          List<IndexedWord> relationSpan = new ArrayList<>();
          relationSpan.add(relation);
          List<IndexedWord> objectSpan = new ArrayList<>();
          List<IndexedWord> ppSpan = new ArrayList<>();
          Optional<IndexedWord> pp = Optional.empty();

          // Get other arguments
          for (SemanticGraphEdge edgeFromRel : parse.outgoingEdgeIterable(relation)) {
            String rel = edgeFromRel.getRelation().toString();
            // Collect adverbs
            if ("advmod".equals(rel)) {
              Optional<List<IndexedWord>> advSpan = getValidAdverbChunk(parse, edgeFromRel.getDependent(), Optional.empty());
              if (!advSpan.isPresent()) {
                return Optional.empty();  // bad adverb span!
              }
              relationSpan.addAll(advSpan.get());
            }
            // Collect object
            else if (rel.endsWith("obj")) {
              if (!objectSpan.isEmpty()) {
                return Optional.empty();  // duplicate objects!
              }
              Optional<List<IndexedWord>> maybeObjSpan = getValidObjectChunk(parse, edgeFromRel.getDependent(), Optional.empty());
              if (!maybeObjSpan.isPresent()) {
                return Optional.empty();  // bad object span!
              }
              objectSpan.addAll(maybeObjSpan.get());
            }
            // Collect pp
            else if (rel.startsWith("nmod:") || rel.startsWith("obl:")) {
              if (!ppSpan.isEmpty()) {
                return Optional.empty();  // duplicate objects!
              }
              Optional<List<IndexedWord>> maybePPSpan = getValidObjectChunk(parse, edgeFromRel.getDependent(), Optional.of("case"));
              if (!maybePPSpan.isPresent()) {
                return Optional.empty();  // bad object span!
              }
              ppSpan.addAll(maybePPSpan.get());
              // Add the actual preposition, if we can find it
              for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(edgeFromRel.getDependent())) {
                if ("case".equals(edge.getRelation().toString())) {
                  pp = Optional.of(edge.getDependent());
                }
              }
            }
            else if (consumeAll) {
              return Optional.empty();  // bad edge out of the relation
            }
          }

          // Construct a triple
          // (canonicalize the triple to be subject; relation; object, folding in the PP)
          if (!ppSpan.isEmpty() && !objectSpan.isEmpty()) {
            relationSpan.addAll(objectSpan);
            objectSpan = ppSpan;
          } else if (!ppSpan.isEmpty()) {
            objectSpan = ppSpan;
          }
          // (last error checks -- shouldn't ever fire)
          if (!subjectSpan.isPresent() || subjectSpan.get().isEmpty() || relationSpan.isEmpty() || objectSpan.isEmpty()) {
            return Optional.empty();
          }
          // (sort the relation span)
          Collections.sort(relationSpan, (a, b) -> {
                double val = a.pseudoPosition() - b.pseudoPosition();
                if (val < 0) {
                  return -1;
                }
                if (val > 0) {
                  return 1;
                } else {
                  return 0;
                }
              });
          // (add in the PP node, if it exists)
          if (pp.isPresent()) {
            relationSpan.add(pp.get());
          }
          // (success!)
          RelationTriple.WithTree extraction = new RelationTriple.WithTree(
              subjectSpan.get().stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              relationSpan.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              objectSpan.stream().map(IndexedWord::backingLabel).collect(Collectors.toList()),
              parse, confidence.orElse(1.0));
          return Optional.of(extraction);
        }
      }
    }

    // Nothing found; return
    return Optional.empty();
  }

  /**
   * <p>
   * This is the main entry point from the Annotator.
   * </p>
   * <p>
   * Tries to segment this sentence as a relation triple.
   * This sentence must already match one of a few strict patterns for a valid OpenIE extraction.
   * If it does not, then no relation triple is created.
   * That is, this is <b>not</b> a relation extractor; it is just a utility to segment what is already a
   * (subject, relation, object) triple into these three parts.
   * </p>
   * <p>
   * Relations are verified using semgrex expressions.  For example,
   * look at VERB_PATTERNS for a list of semgrex expressions involving
   * verbs.
   * </p>
   * <p>
   * Once a relation is potentially here, this method goes through
   * some pruning steps to eliminate invalid relations.  For example,
   * if one of the get clauses contains a NOT or similar word, we
   * eliminate that, since the system has not been written to handle
   * negation.  Other possible eliminations are for having arcs
   * which were not expected as part of the semgrex expression used to
   * identify the triple.
   * </p>
   * <p>
   *   This method will attempt to use both the verb-centric patterns and the ACL-centric patterns.
   * </p>
   *
   * @param parse The sentence to process, as a dependency tree.
   * @param confidence An optional confidence to pass on to the relation triple.
   * @param consumeAll if true, force the entire parse to be consumed by the pattern.
   * @return A relation triple, if this sentence matches one of the patterns of a valid relation triple.
   */
  public Optional<RelationTriple> segment(SemanticGraph parse, Optional<Double> confidence, boolean consumeAll) {
    // Copy and clean the tree
    parse = new SemanticGraph(parse);

    // Special case "there is <something>". Arguably this is a job for the clause splitter, but the <something> is
    // sometimes not _really_ its own clause
    IndexedWord root = parse.getFirstRoot();
    if ( (root.lemma() != null && root.lemma().equalsIgnoreCase("be")) ||
         (root.lemma() == null && ("is".equalsIgnoreCase(root.word()) ||
                                   "are".equalsIgnoreCase(root.word()) ||
                                   "were".equalsIgnoreCase(root.word()) ||
                                   "be".equalsIgnoreCase(root.word())))) {
      // Check for the "there is" construction
      boolean foundThere = false;
      boolean tooMayArcs = false;  // an indicator for there being too much nonsense hanging off of the root
      Optional<SemanticGraphEdge> newRoot = Optional.empty();
      for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(root)) {
        if (edge.getRelation().toString().equals("expl") && edge.getDependent().word().equalsIgnoreCase("there")) {
          foundThere = true;
        } else if (edge.getRelation().toString().equals("nsubj")) {
          newRoot = Optional.of(edge);
        } else {
          tooMayArcs = true;
        }
      }
      // Split off "there is")
      if (foundThere && newRoot.isPresent() && !tooMayArcs) {
        ClauseSplitterSearchProblem.splitToChildOfEdge(parse, newRoot.get());
      }
    }

    // Run the patterns
    Optional<RelationTriple> extraction = segmentVerb(parse, confidence, consumeAll);
    if (!extraction.isPresent()) {
      extraction = segmentACL(parse, confidence, consumeAll);
    }

    //
    // Remove downward polarity extractions
    //
    if (extraction.isPresent()) {
      boolean shouldRemove = true;
      for (CoreLabel token : extraction.get()) {
        if (token.get(NaturalLogicAnnotations.PolarityAnnotation.class) == null ||
            !token.get(NaturalLogicAnnotations.PolarityAnnotation.class).isDownwards()) {
          shouldRemove = false;
        }
      }
      if (shouldRemove) {
        return Optional.empty();
      }
    }

    // Return
    return extraction;
  }

  /**
   * Segment the given parse tree, forcing all nodes to be consumed.
   * @see RelationTripleSegmenter#segment(edu.stanford.nlp.semgraph.SemanticGraph, Optional)
   */
  public Optional<RelationTriple> segment(SemanticGraph parse, Optional<Double> confidence) {
    return segment(parse, confidence, true);
  }
}
