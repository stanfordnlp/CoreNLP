package edu.stanford.nlp.dcoref; 

import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.ParserAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.*;
// import edu.stanford.nlp.util.logging.Redwood;

public class RuleBasedCorefMentionFinder implements CorefMentionFinder  {

  // /** A logger for this class */
  // private static Redwood.RedwoodChannels log = Redwood.channels(RuleBasedCorefMentionFinder.class);

  private boolean assignIds = true;
//  protected int maxID = -1;
  private final HeadFinder headFinder;
  private Annotator parserProcessor;

  private final boolean allowReparsing;

  public RuleBasedCorefMentionFinder() {
    this(Constants.ALLOW_REPARSING);
  }

  public RuleBasedCorefMentionFinder(boolean allowReparsing) {
    SieveCoreferenceSystem.logger.fine("Using SEMANTIC HEAD FINDER!!!!!!!!!!!!!!!!!!!");
    this.headFinder = new SemanticHeadFinder();
    this.allowReparsing = allowReparsing;
  }

  /** When mention boundaries are given */
  public List<List<Mention>> filterPredictedMentions(List<List<Mention>> allGoldMentions, Annotation doc, Dictionaries dict){
    List<List<Mention>> predictedMentions = new ArrayList<>();

    for(int i = 0 ; i < allGoldMentions.size(); i++){
      CoreMap s = doc.get(CoreAnnotations.SentencesAnnotation.class).get(i);
      List<Mention> goldMentions = allGoldMentions.get(i);
      List<Mention> mentions = new ArrayList<>();
      predictedMentions.add(mentions);
      mentions.addAll(goldMentions);
      findHead(s, mentions);

      // todo [cdm 2013]: This block seems to do nothing - the two sets are never used
      Set<IntPair> mentionSpanSet = Generics.newHashSet();
      Set<IntPair> namedEntitySpanSet = Generics.newHashSet();
      for(Mention m : mentions) {
        mentionSpanSet.add(new IntPair(m.startIndex, m.endIndex));
        if(!m.headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("O")) {
          namedEntitySpanSet.add(new IntPair(m.startIndex, m.endIndex));
        }
      }

      setBarePlural(mentions);
      removeSpuriousMentions(s, mentions, dict);
    }
    return predictedMentions;
  }

  /** Main method of mention detection.
   *  Extract all NP, PRP or NE, and filter out by manually written patterns.
   */
  @Override
  public List<List<Mention>> extractPredictedMentions(Annotation doc, int maxID, Dictionaries dict) {
//    this.maxID = _maxID;
    List<List<Mention>> predictedMentions = new ArrayList<>();
    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {

      List<Mention> mentions = new ArrayList<>();
      predictedMentions.add(mentions);
      Set<IntPair> mentionSpanSet = Generics.newHashSet();
      Set<IntPair> namedEntitySpanSet = Generics.newHashSet();

      extractPremarkedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNamedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNPorPRP(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractEnumerations(s, mentions, mentionSpanSet, namedEntitySpanSet);
      findHead(s, mentions);
      setBarePlural(mentions);
      removeSpuriousMentions(s, mentions, dict);
    }

    // assign mention IDs
    if(assignIds) assignMentionIDs(predictedMentions, maxID);

    return predictedMentions;
  }

  protected static void assignMentionIDs(List<List<Mention>> predictedMentions, int maxID) {
    for(List<Mention> mentions : predictedMentions) {
      for(Mention m : mentions) {
        m.mentionID = (++maxID);
      }
    }
  }

  protected static void setBarePlural(List<Mention> mentions) {
    for (Mention m : mentions) {
      String pos = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(m.originalSpan.size()==1 && pos.equals("NNS")) m.generic = true;
    }
  }

  protected static void extractPremarkedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    int beginIndex = -1;
    for(CoreLabel w : sent) {
      MultiTokenTag t = w.get(CoreAnnotations.MentionTokenAnnotation.class);
      if (t != null) {
        // Part of a mention
        if (t.isStart()) {
          // Start of mention
          beginIndex = w.get(CoreAnnotations.IndexAnnotation.class) - 1;
        }
        if (t.isEnd()) {
          // end of mention
          int endIndex = w.get(CoreAnnotations.IndexAnnotation.class);
          if (beginIndex >= 0) {
            IntPair mSpan = new IntPair(beginIndex, endIndex);
            int dummyMentionId = -1;
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, dependency, new ArrayList<>(sent.subList(beginIndex, endIndex)));
            mentions.add(m);
            mentionSpanSet.add(mSpan);
            beginIndex = -1;
          } else {
            SieveCoreferenceSystem.logger.warning("Start of marked mention not found in sentence: "
                    + t + " at tokenIndex=" + (w.get(CoreAnnotations.IndexAnnotation.class)-1)+ " for "
                    + s.get(CoreAnnotations.TextAnnotation.class));
          }
        }
      }
    }
  }

  protected static void extractNamedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    String preNE = "O";
    int beginIndex = -1;
    for(CoreLabel w : sent) {
      String nerString = w.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if(!nerString.equals(preNE)) {
        int endIndex = w.get(CoreAnnotations.IndexAnnotation.class) - 1;
        if(!preNE.matches("O|QUANTITY|CARDINAL|PERCENT|DATE|DURATION|TIME|SET")){
          if(w.get(CoreAnnotations.TextAnnotation.class).equals("'s")) endIndex++;
          IntPair mSpan = new IntPair(beginIndex, endIndex);
          // Need to check if beginIndex < endIndex because, for
          // example, there could be a 's mislabeled by the NER and
          // attached to the previous NER by the earlier heuristic
          if(beginIndex < endIndex && !mentionSpanSet.contains(mSpan)) {
            int dummyMentionId = -1;
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, dependency, new ArrayList<>(sent.subList(beginIndex, endIndex)));
            mentions.add(m);
            mentionSpanSet.add(mSpan);
            namedEntitySpanSet.add(mSpan);
          }
        }
        beginIndex = endIndex;
        preNE = nerString;
      }
    }
    // NE at the end of sentence
    if(!preNE.matches("O|QUANTITY|CARDINAL|PERCENT|DATE|DURATION|TIME|SET")) {
      IntPair mSpan = new IntPair(beginIndex, sent.size());
      if(!mentionSpanSet.contains(mSpan)) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, beginIndex, sent.size(), dependency, new ArrayList<>(sent.subList(beginIndex, sent.size())));
        mentions.add(m);
        mentionSpanSet.add(mSpan);
        namedEntitySpanSet.add(mSpan);
      }
    }
  }

  private static final TregexPattern npOrPrpMentionPattern = TregexPattern.compile("/^(?:NP|PRP)/");

  protected static void extractNPorPRP(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    tree.indexLeaves();
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

    TregexPattern tgrepPattern = npOrPrpMentionPattern;
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    while (matcher.find()) {
      Tree t = matcher.getMatch();
      List<Tree> mLeaves = t.getLeaves();
      int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      if (",".equals(sent.get(endIdx-1).word())) { endIdx--; } // try not to have span that ends with ,
      IntPair mSpan = new IntPair(beginIdx, endIdx);
      if(!mentionSpanSet.contains(mSpan) && !insideNE(mSpan, namedEntitySpanSet)) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, beginIdx, endIdx, dependency, new ArrayList<>(sent.subList(beginIdx, endIdx)), t);
        mentions.add(m);
        mentionSpanSet.add(mSpan);
      }
    }
  }
  /** Extract enumerations (A, B, and C) */
  private static final TregexPattern enumerationsMentionPattern = TregexPattern.compile("NP < (/^(?:NP|NNP|NML)/=m1 $.. (/^CC|,/ $.. /^(?:NP|NNP|NML)/=m2))");

  protected static void extractEnumerations(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

    TregexPattern tgrepPattern = enumerationsMentionPattern;
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    Map<IntPair, Tree> spanToMentionSubTree = Generics.newHashMap();
    while (matcher.find()) {
      matcher.getMatch();
      Tree m1 = matcher.getNode("m1");
      Tree m2 = matcher.getNode("m2");

      List<Tree> mLeaves = m1.getLeaves();
      int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      spanToMentionSubTree.put(new IntPair(beginIdx, endIdx), m1);

      mLeaves = m2.getLeaves();
      beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      spanToMentionSubTree.put(new IntPair(beginIdx, endIdx), m2);
    }

    for(IntPair mSpan : spanToMentionSubTree.keySet()){
      if(!mentionSpanSet.contains(mSpan) && !insideNE(mSpan, namedEntitySpanSet)) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, mSpan.get(0), mSpan.get(1), dependency,
                new ArrayList<>(sent.subList(mSpan.get(0), mSpan.get(1))), spanToMentionSubTree.get(mSpan));
        mentions.add(m);
        mentionSpanSet.add(mSpan);
      }
    }
  }

  /** Check whether a mention is inside of a named entity */
  private static boolean insideNE(IntPair mSpan, Set<IntPair> namedEntitySpanSet) {
    for (IntPair span : namedEntitySpanSet){
      if(span.get(0) <= mSpan.get(0) && mSpan.get(1) <= span.get(1)) return true;
    }
    return false;
  }

  protected void findHead(CoreMap s, List<Mention> mentions) {
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    tree.indexSpans(0);
    for (Mention m : mentions){
      Tree head = findSyntacticHead(m, tree, sent);
      m.headIndex = ((CoreLabel) head.label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      m.headWord = sent.get(m.headIndex);
      m.headString = m.headWord.get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH);
      int start = m.headIndex - m.startIndex;
      if (start < 0 || start >= m.originalSpan.size()) {
        SieveCoreferenceSystem.logger.warning("Invalid index for head " + start + "=" + m.headIndex + "-" + m.startIndex
                + ": originalSpan=[" + StringUtils.joinWords(m.originalSpan, " ") + "], head=" + m.headWord);
        SieveCoreferenceSystem.logger.warning("Setting head string to entire mention");
        m.headIndex = m.startIndex;
        m.headWord = m.originalSpan.size() > 0 ? m.originalSpan.get(0) : sent.get(m.startIndex);
        m.headString = m.originalSpan.toString();
      }
    }
  }

  protected Tree findSyntacticHead(Mention m, Tree root, List<CoreLabel> tokens) {
    // mention ends with 's
    int endIdx = m.endIndex;
    if (m.originalSpan.size() > 0) {
      String lastWord = m.originalSpan.get(m.originalSpan.size()-1).get(CoreAnnotations.TextAnnotation.class);
        if((lastWord.equals("'s") || lastWord.equals("'"))
            && m.originalSpan.size() != 1 ) endIdx--;
    }

    Tree exactMatch = findTreeWithSpan(root, m.startIndex, endIdx);
    //
    // found an exact match
    //
    if (exactMatch != null) {
      return safeHead(exactMatch, endIdx);
    }

    // no exact match found
    // in this case, we parse the actual extent of the mention, embedded in a sentence
    // context, so as to make the parser work better :-)
    if (allowReparsing) {
      int approximateness = 0;
      List<CoreLabel> extentTokens = new ArrayList<>();
      extentTokens.add(initCoreLabel("It"));
      extentTokens.add(initCoreLabel("was"));
      final int ADDED_WORDS = 2;
      for (int i = m.startIndex; i < endIdx; i++) {
        // Add everything except separated dashes! The separated dashes mess with the parser too badly.
        CoreLabel label = tokens.get(i);
        if ( ! "-".equals(label.word())) {
          // necessary to copy tokens in case the parser does things like
          // put new indices on the tokens
          extentTokens.add((CoreLabel) label.labelFactory().newLabel(label));
        } else {
          approximateness++;
        }
      }
      extentTokens.add(initCoreLabel("."));

      // constrain the parse to the part we're interested in.
      // Starting from ADDED_WORDS comes from skipping "It was".
      // -1 to exclude the period.
      // We now let it be any kind of nominal constituent, since there
      // are VP and S ones
      ParserConstraint constraint = new ParserConstraint(ADDED_WORDS, extentTokens.size() - 1, Pattern.compile(".*"));
      List<ParserConstraint> constraints = Collections.singletonList(constraint);
      Tree tree = parse(extentTokens, constraints);
      convertToCoreLabels(tree);  // now unnecessary, as parser uses CoreLabels?
      tree.indexSpans(m.startIndex - ADDED_WORDS);  // remember it has ADDED_WORDS extra words at the beginning
      Tree subtree = findPartialSpan(tree, m.startIndex);
      // There was a possible problem that with a crazy parse, extentHead could be one of the added words, not a real word!
      // Now we make sure in findPartialSpan that it can't be before the real start, and in safeHead, we disallow something
      // passed the right end (that is, just that final period).
      Tree extentHead = safeHead(subtree, endIdx);
      assert(extentHead != null);
      // extentHead is a child in the local extent parse tree. we need to find the corresponding node in the main tree
      // Because we deleted dashes, it's index will be >= the index in the extent parse tree
      CoreLabel l = (CoreLabel) extentHead.label();
      Tree realHead = funkyFindLeafWithApproximateSpan(root, l.value(), l.get(CoreAnnotations.BeginIndexAnnotation.class), approximateness);
      assert(realHead != null);
      return realHead;
    }

    // If reparsing wasn't allowed, try to find a span in the tree
    // which happens to have the head
    Tree wordMatch = findTreeWithSmallestSpan(root, m.startIndex, endIdx);
    if (wordMatch != null) {
      Tree head = safeHead(wordMatch, endIdx);
      if (head != null) {
        int index = ((CoreLabel) head.label()).get(CoreAnnotations.IndexAnnotation.class)-1;
        if (index >= m.startIndex && index < endIdx) {
          return head;
        }
      }
    }

    // If that didn't work, guess that it's the last word

    int lastNounIdx = endIdx-1;
    for(int i=m.startIndex ; i < m.endIndex ; i++) {
      if(tokens.get(i).tag().startsWith("N")) lastNounIdx = i;
      else if(tokens.get(i).tag().startsWith("W")) break;
    }

    List<Tree> leaves = root.getLeaves();
    Tree endLeaf = leaves.get(lastNounIdx);
    return endLeaf;
  }

  /** Find the tree that covers the portion of interest. */
  private static Tree findPartialSpan(final Tree root, final int start) {
    CoreLabel label = (CoreLabel) root.label();
    int startIndex = label.get(CoreAnnotations.BeginIndexAnnotation.class);
    if (startIndex == start) {
      return root;
    }
    for (Tree kid : root.children()) {
      CoreLabel kidLabel = (CoreLabel) kid.label();
      int kidStart = kidLabel.get(CoreAnnotations.BeginIndexAnnotation.class);
      int kidEnd = kidLabel.get(CoreAnnotations.EndIndexAnnotation.class);
      if (kidStart <= start && kidEnd > start) {
        return findPartialSpan(kid, start);
      }
    }
    throw new RuntimeException("Shouldn't happen: " + start + " " + root);
  }

  private static Tree funkyFindLeafWithApproximateSpan(Tree root, String token, int index, int approximateness) {
    // log.info("Searching " + root + "\n  for " + token + " at position " + index + " (plus up to " + approximateness + ")");
    List<Tree> leaves = root.getLeaves();
    for (Tree leaf : leaves) {
      CoreLabel label = CoreLabel.class.cast(leaf.label());
      Integer indexInteger = label.get(CoreAnnotations.IndexAnnotation.class);
      if (indexInteger == null) continue;
      int ind = indexInteger - 1;
      if (token.equals(leaf.value()) && ind >= index && ind <= index + approximateness) {
        return leaf;
      }
    }
    // this shouldn't happen
    //    throw new RuntimeException("RuleBasedCorefMentionFinder: ERROR: Failed to find head token");
    SieveCoreferenceSystem.logger.warning("RuleBasedCorefMentionFinder: Failed to find head token:\n" +
                       "Tree is: " + root + "\n" +
                       "token = |" + token + "|" + index + "|, approx=" + approximateness);
    for (Tree leaf : leaves) {
      if (token.equals(leaf.value())) {
        //log.info("Found something: returning " + leaf);
        return leaf;
      }
    }
    int fallback = Math.max(0, leaves.size() - 2);
    SieveCoreferenceSystem.logger.warning("RuleBasedCorefMentionFinder: Last resort: returning as head: " + leaves.get(fallback));
    return leaves.get(fallback); // last except for the added period.
  }

  private static CoreLabel initCoreLabel(String token) {
    CoreLabel label = new CoreLabel();
    label.set(CoreAnnotations.TextAnnotation.class, token);
    label.set(CoreAnnotations.ValueAnnotation.class, token);
    return label;
  }

  private Tree parse(List<CoreLabel> tokens) {
    return parse(tokens, null);
  }

  private Tree parse(List<CoreLabel> tokens,
                     List<ParserConstraint> constraints) {
    CoreMap sent = new Annotation("");
    sent.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sent.set(ParserAnnotations.ConstraintAnnotation.class, constraints);
    Annotation doc = new Annotation("");
    List<CoreMap> sents = new ArrayList<>(1);
    sents.add(sent);
    doc.set(CoreAnnotations.SentencesAnnotation.class, sents);
    getParser().annotate(doc);
    sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    return sents.get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
  }

  private Annotator getParser() {
    if(parserProcessor == null){
      Annotator parser = StanfordCoreNLP.getExistingAnnotator("parse");
      if (parser == null) {
        Properties emptyProperties = new Properties();
        parser = new ParserAnnotator("coref.parse.md", emptyProperties);
      }
      if (parser == null) {
        // TODO: these assertions rule out the possibility of alternately named parse/pos annotators
        throw new AssertionError("Failed to get parser - this should not be possible");
      }
      if (parser.requires().contains(CoreAnnotations.PartOfSpeechAnnotation.class)) {
        Annotator tagger = StanfordCoreNLP.getExistingAnnotator("pos");
        if (tagger == null) {
          throw new AssertionError("Parser required tagger, but failed to find the pos annotator");
        }
        List<Annotator> annotators = Generics.newArrayList();
        annotators.add(tagger);
        annotators.add(parser);        
        parserProcessor = new AnnotationPipeline(annotators);
      } else {
        parserProcessor = parser;
      }
    }
    return parserProcessor;
  }

  // This probably isn't needed now; everything is always a core label. But no-op.
  private static void convertToCoreLabels(Tree tree) {
    Label l = tree.label();
    if (! (l instanceof CoreLabel)) {
      CoreLabel cl = new CoreLabel();
      cl.setValue(l.value());
      tree.setLabel(cl);
    }

    for (Tree kid : tree.children()) {
      convertToCoreLabels(kid);
    }
  }

  private Tree safeHead(Tree top, int endIndex) {
    // The trees passed in do not have the CoordinationTransformer
    // applied, but that just means the SemanticHeadFinder results are
    // slightly worse.
    Tree head = top.headTerminal(headFinder);
    // One obscure failure case is that the added period becomes the head. Disallow this.
    if (head != null) {
      Integer headIndexInteger = ((CoreLabel) head.label()).get(CoreAnnotations.IndexAnnotation.class);
      if (headIndexInteger != null) {
        int headIndex = headIndexInteger - 1;
        if (headIndex < endIndex) {
          return head;
        }
      }
    }
    // if no head found return the right-most leaf
    List<Tree> leaves = top.getLeaves();
    int candidate = leaves.size() - 1;
    while (candidate >= 0) {
      head = leaves.get(candidate);
      Integer headIndexInteger = ((CoreLabel) head.label()).get(CoreAnnotations.IndexAnnotation.class);
      if (headIndexInteger != null) {
        int headIndex = headIndexInteger - 1;
        if (headIndex < endIndex) {
          return head;
        }
      }
      candidate--;
    }
    // fallback: return top
    return top;
  }

  static Tree findTreeWithSmallestSpan(Tree tree, int start, int end) {
    List<Tree> leaves = tree.getLeaves();
    Tree startLeaf = leaves.get(start);
    Tree endLeaf = leaves.get(end - 1);
    return Trees.getLowestCommonAncestor(Arrays.asList(startLeaf, endLeaf), tree);
  }

  private static Tree findTreeWithSpan(Tree tree, int start, int end) {
    CoreLabel l = (CoreLabel) tree.label();
    if (l != null && l.containsKey(CoreAnnotations.BeginIndexAnnotation.class) && l.containsKey(CoreAnnotations.EndIndexAnnotation.class)) {
      int myStart = l.get(CoreAnnotations.BeginIndexAnnotation.class);
      int myEnd = l.get(CoreAnnotations.EndIndexAnnotation.class);
      if (start == myStart && end == myEnd){
        // found perfect match
        return tree;
      } else if (end < myStart) {
        return null;
      } else if (start >= myEnd) {
        return null;
      }
    }

    // otherwise, check inside children - a match is possible
    for (Tree kid : tree.children()) {
      if (kid == null) continue;
      Tree ret = findTreeWithSpan(kid, start, end);
      // found matching child
      if (ret != null) return ret;
    }

    // no match
    return null;
  }

  /** Filter out all spurious mentions */
  protected static void removeSpuriousMentions(CoreMap s, List<Mention> mentions, Dictionaries dict) {
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Set<Mention> remove = Generics.newHashSet();


    for(Mention m : mentions){
      String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      String headNE = m.headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      // pleonastic it
      if(isPleonastic(m, tree)) remove.add(m);

      // non word such as 'hmm'
      if(dict.nonWords.contains(m.headString)) remove.add(m);

      // quantRule : not starts with 'any', 'all' etc
      if (m.originalSpan.size() > 0 && dict.quantifiers.contains(m.originalSpan.get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH))) remove.add(m);

      // partitiveRule
      if (partitiveRule(m, sent, dict)) remove.add(m);

      // bareNPRule
      if (headPOS.equals("NN") && !dict.temporals.contains(m.headString)
          && (m.originalSpan.size()==1 || m.originalSpan.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("JJ"))) {
        remove.add(m);
      }

      // remove generic rule
      //  if(m.generic==true) remove.add(m);

      if (m.headString.equals("%")) remove.add(m);
      if (headNE.equals("PERCENT") || headNE.equals("MONEY")) remove.add(m);

      // adjective form of nations
      if (dict.isAdjectivalDemonym(m.spanToString())) remove.add(m);

      // stop list (e.g., U.S., there)
      if (inStopList(m)) remove.add(m);
    }

    // nested mention with shared headword (except apposition, enumeration): pick larger one
    for (Mention m1 : mentions){
      for (Mention m2 : mentions){
        if (m1==m2 || remove.contains(m1) || remove.contains(m2)) continue;
        if (m1.sentNum==m2.sentNum && m1.headWord==m2.headWord && m2.insideIn(m1)) {
          if (m2.endIndex < sent.size() && (sent.get(m2.endIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals(",")
              || sent.get(m2.endIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CC"))) {
            continue;
          }
          remove.add(m2);
        }
      }
    }
    mentions.removeAll(remove);
  }

  private static boolean inStopList(Mention m) {
    String mentionSpan = m.spanToString().toLowerCase(Locale.ENGLISH);
    if (mentionSpan.equals("u.s.") || mentionSpan.equals("u.k.")
        || mentionSpan.equals("u.s.s.r")) return true;
    if (mentionSpan.equals("there") || mentionSpan.startsWith("etc.")
        || mentionSpan.equals("ltd.")) return true;
    if (mentionSpan.startsWith("'s ")) return true;
    if (mentionSpan.endsWith("etc.")) return true;

    return false;
  }

  private static boolean partitiveRule(Mention m, List<CoreLabel> sent, Dictionaries dict) {
    return m.startIndex >= 2
            && sent.get(m.startIndex - 1).get(CoreAnnotations.TextAnnotation.class).equalsIgnoreCase("of")
            && dict.parts.contains(sent.get(m.startIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH));
  }

  /** Check whether pleonastic 'it'. E.g., It is possible that ... */
  private static final TregexPattern[] pleonasticPatterns = getPleonasticPatterns();
  private static boolean isPleonastic(Mention m, Tree tree) {
    if ( ! m.spanToString().equalsIgnoreCase("it")) return false;
    for (TregexPattern p : pleonasticPatterns) {
      if (checkPleonastic(m, tree, p)) {
        // SieveCoreferenceSystem.logger.fine("RuleBasedCorefMentionFinder: matched pleonastic pattern '" + p + "' for " + tree);
        return true;
      }
    }
    return false;
  }

  private static TregexPattern[] getPleonasticPatterns() {
    final String[] patterns = {
            // cdm 2013: I spent a while on these patterns. I fixed a syntax error in five patterns ($.. split with space), so it now shouldn't exception in checkPleonastic. This gave 0.02% on CoNLL11 dev
            // I tried some more precise patterns but they didn't help. Indeed, they tended to hurt vs. the higher recall patterns.

            //"NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (VP < (VBN $.. /S|SBAR/))))", // overmatches
            // "@NP < (PRP=m1 < it|IT|It) $.. (@VP < (/^V.*/ < /^(?i:is|was|be|becomes|become|became)$/ $.. (@VP < (VBN < expected|hoped $.. @SBAR))))",  // this one seems more accurate, but ...
            "@NP < (PRP=m1 < it|IT|It) $.. (@VP < (/^V.*/ < /^(?i:is|was|be|becomes|become|became)$/ $.. (@VP < (VBN $.. @S|SBAR))))",  // in practice, go with this one (best results)

            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (ADJP $.. (/S|SBAR/))))",
            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (ADJP < (/S|SBAR/))))",
            // "@NP < (PRP=m1 < it|IT|It) $.. (@VP < (/^V.*/ < /^(?i:is|was|be|becomes|become|became)$/ $.. (@ADJP < (/^(?:JJ|VB)/ < /^(?i:(?:hard|tough|easi)(?:er|est)?|(?:im|un)?(?:possible|interesting|worthwhile|likely|surprising|certain)|disappointing|pointless|easy|fine|okay)$/) [ < @S|SBAR | $.. (@S|SBAR !< (IN !< for|For|FOR|that|That|THAT)) ] )))", // does worse than above 2 on CoNLL11 dev

            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (NP < /S|SBAR/)))",
            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (NP $.. ADVP $.. /S|SBAR/)))",
            // "@NP < (PRP=m1 < it|IT|It) $.. (@VP < (/^V.*/ < /^(?i:is|was|be|becomes|become|became)$/ $.. (@NP $.. @ADVP $.. @SBAR)))", // cleft examples, generalized to not need ADVP; but gave worse CoNLL12 dev numbers....

            // these next 5 had buggy space in "$ ..", which I fixed
            "NP < (PRP=m1) $.. (VP < (MD $.. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (VP < (VBN $.. /S|SBAR/))))))",

            "NP < (PRP=m1) $.. (VP < (MD $.. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (ADJP $.. (/S|SBAR/))))))", // extraposed. OK 1/2 correct; need non-adverbial case
            "NP < (PRP=m1) $.. (VP < (MD $.. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (ADJP < (/S|SBAR/))))))", // OK: 3/3 good matches on dev; but 3/4 wrong on WSJ
            // certain can be either but relatively likely pleonastic with it ... be
            // "@NP < (PRP=m1 < it|IT|It) $.. (@VP < (MD $.. (@VP < ((/^V.*/ < /^(?:be|become)/) $.. (@ADJP < (/^JJ/ < /^(?i:(?:hard|tough|easi)(?:er|est)?|(?:im|un)?(?:possible|interesting|worthwhile|likely|surprising|certain)|disappointing|pointless|easy|fine|okay))$/) [ < @S|SBAR | $.. (@S|SBAR !< (IN !< for|For|FOR|that|That|THAT)) ] )))))", // GOOD REPLACEMENT ; 2nd clause is for extraposed ones

            "NP < (PRP=m1) $.. (VP < (MD $.. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (NP < /S|SBAR/)))))",
            "NP < (PRP=m1) $.. (VP < (MD $.. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (NP $.. ADVP $.. /S|SBAR/)))))",

            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:seems|appears|means|follows)/) $.. /S|SBAR/))",

            "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:turns|turned)/) $.. PRT $.. /S|SBAR/))"
    };

    TregexPattern[] tgrepPatterns = new TregexPattern[patterns.length];
    for (int i = 0; i < tgrepPatterns.length; i++) {
      tgrepPatterns[i] = TregexPattern.compile(patterns[i]);
    }
    return tgrepPatterns;
  }

  private static boolean checkPleonastic(Mention m, Tree tree, TregexPattern tgrepPattern) {
    try {
      TregexMatcher matcher = tgrepPattern.matcher(tree);
      while (matcher.find()) {
        Tree np1 = matcher.getNode("m1");
        if (((CoreLabel)np1.label()).get(CoreAnnotations.BeginIndexAnnotation.class)+1 == m.headWord.get(CoreAnnotations.IndexAnnotation.class)) {
          return true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

}
