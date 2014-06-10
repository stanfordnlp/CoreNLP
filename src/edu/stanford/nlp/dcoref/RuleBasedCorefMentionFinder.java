package edu.stanford.nlp.dcoref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.ParserConstraint;
import edu.stanford.nlp.parser.lexparser.ParserAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.StringUtils;

public class RuleBasedCorefMentionFinder implements CorefMentionFinder {

  protected boolean assignIds = true;
  protected int maxID = -1;
  private final HeadFinder headFinder;
  protected Annotator parserProcessor;

  public RuleBasedCorefMentionFinder() {
    SieveCoreferenceSystem.logger.fine("Using SEMANTIC HEAD FINDER!!!!!!!!!!!!!!!!!!!");
    headFinder = new SemanticHeadFinder();
  }

  /** When mention boundaries are given */
  public List<List<Mention>> filterPredictedMentions(List<List<Mention>> allGoldMentions, Annotation doc, Dictionaries dict){
    List<List<Mention>> predictedMentions = new ArrayList<List<Mention>>();

    for(int i = 0 ; i < allGoldMentions.size(); i++){
      CoreMap s = doc.get(CoreAnnotations.SentencesAnnotation.class).get(i);
      List<Mention> goldMentions = allGoldMentions.get(i);
      List<Mention> mentions = new ArrayList<Mention>();
      predictedMentions.add(mentions);
      mentions.addAll(goldMentions);
      findHead(s, mentions);

      // todo [cdm 2013]: This block seems to do nothing - the two sets are never used
      Set<IntPair> mentionSpanSet = new HashSet<IntPair>();
      Set<IntPair> namedEntitySpanSet = new HashSet<IntPair>();
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
  public List<List<Mention>> extractPredictedMentions(Annotation doc, int _maxID, Dictionaries dict){
    this.maxID = _maxID;
    List<List<Mention>> predictedMentions = new ArrayList<List<Mention>>();
    for(CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {

      List<Mention> mentions = new ArrayList<Mention>();
      predictedMentions.add(mentions);
      Set<IntPair> mentionSpanSet = new HashSet<IntPair>();
      Set<IntPair> namedEntitySpanSet = new HashSet<IntPair>();

      extractNamedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNPorPRP(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractEnumerations(s, mentions, mentionSpanSet, namedEntitySpanSet);
      findHead(s, mentions);
      setBarePlural(mentions);
      removeSpuriousMentions(s, mentions, dict);
    }
    return predictedMentions;
  }

  protected static void setBarePlural(List<Mention> mentions) {
    for (Mention m : mentions) {
      String pos = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(m.originalSpan.size()==1 && pos.equals("NNS")) m.generic = true;
    }
  }

  protected void extractNamedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    String preNE = "O";
    int beginIndex = -1;
    for(CoreLabel w : sent) {
      String nerString = w.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if(!nerString.equals(preNE)) {
        int endIndex = w.get(CoreAnnotations.IndexAnnotation.class) - 1;
        if(!preNE.equals("O") && !preNE.equals("QUANTITY") && !preNE.equals("CARDINAL") && !preNE.equals("PERCENT")) {
          if(w.get(CoreAnnotations.TextAnnotation.class).equals("'s")) endIndex++;
          IntPair mSpan = new IntPair(beginIndex, endIndex);
          // Need to check if beginIndex < endIndex because, for
          // example, there could be a 's mislabeled by the NER and
          // attached to the previous NER by the earlier heuristic
          if(beginIndex < endIndex && !mentionSpanSet.contains(mSpan)) {
            int mentionId = assignIds? ++maxID:-1;
            Mention m = new Mention(mentionId, beginIndex, endIndex, dependency, new ArrayList<CoreLabel>(sent.subList(beginIndex, endIndex)));
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
    if(!preNE.equals("O") && !preNE.equals("QUANTITY") && !preNE.equals("CARDINAL") && !preNE.equals("PERCENT")) {
      IntPair mSpan = new IntPair(beginIndex, sent.size());
      if(!mentionSpanSet.contains(mSpan)) {
        int mentionId = assignIds? ++maxID:-1;
        Mention m = new Mention(mentionId, beginIndex, sent.size(), dependency, new ArrayList<CoreLabel>(sent.subList(beginIndex, sent.size())));
        mentions.add(m);
        mentionSpanSet.add(mSpan);
        namedEntitySpanSet.add(mSpan);
      }
    }
  }

  protected void extractNPorPRP(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    tree.indexLeaves();
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);

    final String mentionPattern = "/^(?:NP|PRP)/";
    TregexPattern tgrepPattern = TregexPattern.compile(mentionPattern);
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    while (matcher.find()) {
      Tree t = matcher.getMatch();
      List<Tree> mLeaves = t.getLeaves();
      int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      IntPair mSpan = new IntPair(beginIdx, endIdx);
      if(!mentionSpanSet.contains(mSpan) && !insideNE(mSpan, namedEntitySpanSet)) {
        int mentionID = assignIds? ++maxID:-1;
        Mention m = new Mention(mentionID, beginIdx, endIdx, dependency, new ArrayList<CoreLabel>(sent.subList(beginIdx, endIdx)), t);
        mentions.add(m);
        mentionSpanSet.add(mSpan);
      }
    }
  }
  /** Extract enumerations (A, B, and C) */
  protected void extractEnumerations(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet){
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    SemanticGraph dependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);

    final String mentionPattern = "NP < (/^(?:NP|NNP|NML)/=m1 $.. (/^CC|,/ $.. /^(?:NP|NNP|NML)/=m2))";
    TregexPattern tgrepPattern = TregexPattern.compile(mentionPattern);
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    Map<IntPair, Tree> spanToMentionSubTree = new HashMap<IntPair, Tree>();
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
        int mentionID = assignIds? ++maxID:-1;
        Mention m = new Mention(mentionID, mSpan.get(0), mSpan.get(1), dependency,
                                new ArrayList<CoreLabel>(sent.subList(mSpan.get(0), mSpan.get(1))), spanToMentionSubTree.get(mSpan));
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
      m.headString = m.headWord.get(CoreAnnotations.TextAnnotation.class).toLowerCase();
      int start = m.headIndex - m.startIndex;
      if (start < 0 || start >= m.originalSpan.size()) {
        SieveCoreferenceSystem.logger.warning("Invalid index for head " + start + "=" + m.headIndex + "-" + m.startIndex
                + ": originalSpan=[" + StringUtils.joinWords(m.originalSpan, " ") + "], head=" + m.headWord);
        SieveCoreferenceSystem.logger.warning("Setting head string to entire mention");
        m.headIndex = m.startIndex;
        m.headWord = m.originalSpan.get(0);
        m.headString = m.originalSpan.toString();
      }
    }
  }

  protected Tree findSyntacticHead(Mention m, Tree root, List<CoreLabel> tokens) {
    // mention ends with 's
    int endIdx = m.endIndex;
    String lastWord = m.originalSpan.get(m.originalSpan.size()-1).get(CoreAnnotations.TextAnnotation.class);
    if((lastWord.equals("'s") || lastWord.equals("'"))
        && m.originalSpan.size() != 1 ) endIdx--;

    Tree exactMatch = findTreeWithSpan(root, m.startIndex, endIdx);
    //
    // found an exact match
    //
    if (exactMatch != null) {
      return safeHead(exactMatch);
    }

    // no exact match found
    // in this case, we parse the actual extent of the mention, embedded in a sentence
    // context, so as to make the parser work better :-)

    int approximateness = 0;
    List<CoreLabel> extentTokens = new ArrayList<CoreLabel>();
    extentTokens.add(initCoreLabel("It"));
    extentTokens.add(initCoreLabel("was"));
    final int ADDED_WORDS = 2;
    for (int i = m.startIndex; i < endIdx; i++) {
      // Add everything except separated dashes! The separated dashes mess with the parser too badly.
      CoreLabel label = tokens.get(i);
      if ( ! "-".equals(label.word())) {
        extentTokens.add(tokens.get(i));
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
    convertToCoreLabels(tree);
    tree.indexSpans(m.startIndex - ADDED_WORDS);  // remember it has ADDED_WORDS extra words at the beginning
    Tree subtree = findPartialSpan(tree, m.startIndex);
    Tree extentHead = safeHead(subtree);
    assert(extentHead != null);
    // extentHead is a child in the local extent parse tree. we need to find the corresponding node in the main tree
    // Because we deleted dashes, it's index will be >= the index in the extent parse tree
    CoreLabel l = (CoreLabel) extentHead.label();
    Tree realHead = funkyFindLeafWithApproximateSpan(root, l.value(), l.get(CoreAnnotations.BeginIndexAnnotation.class), approximateness);
    assert(realHead != null);
    return realHead;
  }
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
    List<Tree> leaves = root.getLeaves();
    for (Tree leaf : leaves) {
      CoreLabel label = CoreLabel.class.cast(leaf.label());
      int ind = label.get(CoreAnnotations.IndexAnnotation.class) - 1;
      if (token.equals(leaf.value()) && ind >= index && ind <= index + approximateness) {
        return leaf;
      }
    }
    // this shouldn't happen
    //    throw new RuntimeException("RuleBasedCorefMentionFinder: ERROR: Failed to find head token");
    System.err.println("RuleBasedCorefMentionFinder: ERROR: Failed to find head token");
    return leaves.get(leaves.size() - 1);
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
    List<CoreMap> sents = new ArrayList<CoreMap>();
    sents.add(sent);
    doc.set(CoreAnnotations.SentencesAnnotation.class, sents);
    getParser().annotate(doc);
    sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    return sents.get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
  }
  private Annotator getParser() {
    if(parserProcessor == null){
      parserProcessor = StanfordCoreNLP.getExistingAnnotator("parse");
      assert(parserProcessor != null);
    }
    return parserProcessor;
  }
  private static void convertToCoreLabels(Tree tree) {
    Label l = tree.label();
    if(! (l instanceof CoreLabel)){
      CoreLabel cl = new CoreLabel();
      cl.setValue(l.value());
      tree.setLabel(cl);
    }

    for (Tree kid : tree.children()) {
      convertToCoreLabels(kid);
    }
  }
  private Tree safeHead(Tree top) {
    Tree head = top.headTerminal(headFinder);
    if (head != null) return head;
    // if no head found return the right-most leaf
    List<Tree> leaves = top.getLeaves();
    if(leaves.size() > 0) return leaves.get(leaves.size() - 1);
    // fallback: return top
    return top;
  }
  private static Tree findTreeWithSpan(Tree tree, int start, int end) {
    CoreLabel l = (CoreLabel) tree.label();
    if (l != null && l.has(CoreAnnotations.BeginIndexAnnotation.class) && l.has(CoreAnnotations.EndIndexAnnotation.class)) {
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
    Set<Mention> remove = new HashSet<Mention>();


    for(Mention m : mentions){
      String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      String headNE = m.headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      // pleonastic it
      if(isPleonastic(m, tree)) remove.add(m);

      // non word such as 'hmm'
      if(dict.nonWords.contains(m.headString)) remove.add(m);

      // quantRule : not starts with 'any', 'all' etc
      if(dict.quantifiers.contains(m.originalSpan.get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase())) remove.add(m);

      // partitiveRule
      if(partitiveRule(m, sent, dict)) remove.add(m);

      // bareNPRule
      if(headPOS.equals("NN") && !dict.temporals.contains(m.headString)
          && (m.originalSpan.size()==1 || m.originalSpan.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("JJ"))) {
        remove.add(m);
      }

      // remove generic rule
      //  if(m.generic==true) remove.add(m);

      if(m.headString.equals("%")) remove.add(m);
      if(headNE.equals("PERCENT") || headNE.equals("MONEY")) remove.add(m);

      // adjective form of nations
      if(dict.adjectiveNation.contains(m.spanToString().toLowerCase())) remove.add(m);

      // stop list (e.g., U.S., there)
      if(inStopList(m)) remove.add(m);
    }

    // nested mention with shared headword (except apposition, enumeration): pick larger one
    for(Mention m1 : mentions){
      for(Mention m2 : mentions){
        if(m1==m2 || remove.contains(m1) || remove.contains(m2)) continue;
        if(m1.sentNum==m2.sentNum && m1.headWord==m2.headWord && m2.insideIn(m1)) {
          if(m2.endIndex < sent.size() && (sent.get(m2.endIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals(",")
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
    String mentionSpan = m.spanToString().toLowerCase();
    if(mentionSpan.equals("u.s.") || mentionSpan.equals("u.k.")
        || mentionSpan.equals("u.s.s.r")) return true;
    if(mentionSpan.equals("there") || mentionSpan.startsWith("etc.")
        || mentionSpan.equals("ltd.")) return true;
    if(mentionSpan.startsWith("'s ")) return true;
    if(mentionSpan.endsWith("etc.")) return true;

    return false;
  }

  private static boolean partitiveRule(Mention m, List<CoreLabel> sent, Dictionaries dict) {
    return m.startIndex >= 2
            && sent.get(m.startIndex - 1).get(CoreAnnotations.TextAnnotation.class).equalsIgnoreCase("of")
            && dict.parts.contains(sent.get(m.startIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase());
  }

  /** Check whether pleonastic 'it'. E.g., It is possible that ... */
  private static boolean isPleonastic(Mention m, Tree tree) {
    if(!m.spanToString().equalsIgnoreCase("it")) return false;
    final String[] patterns = {
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (VP < (VBN $.. /S|SBAR/))))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (ADJP $.. (/S|SBAR/))))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (ADJP < (/S|SBAR/))))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (NP < /S|SBAR/)))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:is|was|become|became)/) $.. (NP $.. ADVP $.. /S|SBAR/)))",
        "NP < (PRP=m1) $.. (VP < (MD $ .. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (VP < (VBN $.. /S|SBAR/))))))",
        "NP < (PRP=m1) $.. (VP < (MD $ .. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (ADJP $.. (/S|SBAR/))))))",
        "NP < (PRP=m1) $.. (VP < (MD $ .. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (ADJP < (/S|SBAR/))))))",
        "NP < (PRP=m1) $.. (VP < (MD $ .. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (NP < /S|SBAR/)))))",
        "NP < (PRP=m1) $.. (VP < (MD $ .. (VP < ((/^V.*/ < /^(?:be|become)/) $.. (NP $.. ADVP $.. /S|SBAR/)))))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:seems|appears|means|follows)/) $.. /S|SBAR/))",
        "NP < (PRP=m1) $.. (VP < ((/^V.*/ < /^(?:turns|turned)/) $.. PRT $.. /S|SBAR/))"
    };

    for(String p : patterns){
      if(checkPleonastic(m, tree, p)) return true;
    }
    return false;
  }

  private static boolean checkPleonastic(Mention m, Tree tree, String pattern) {
    try {
      TregexPattern tgrepPattern = TregexPattern.compile(pattern);
      TregexMatcher matcher = tgrepPattern.matcher(tree);
      while (matcher.find()) {
        Tree np1 = matcher.getNode("m1");
        if(((CoreLabel)np1.label()).get(CoreAnnotations.BeginIndexAnnotation.class)+1 == m.headWord.get(CoreAnnotations.IndexAnnotation.class)) {
          return true;
        }
      }
    } catch (Exception e) {
      return false;
    }
    return false;
  }
}


