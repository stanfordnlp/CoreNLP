package edu.stanford.nlp.coref.md;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.ParserAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Interface for finding coref mentions in a document.
 *
 * @author Angel Chang
 */
public abstract class CorefMentionFinder  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CorefMentionFinder.class);

  protected Locale lang;

  protected HeadFinder headFinder;
  protected Annotator parserProcessor;
  protected boolean allowReparsing;

  protected static final TregexPattern npOrPrpMentionPattern = TregexPattern.compile("/^(?:NP|PN|PRP)/");
  private static final boolean VERBOSE = false;

  /** Get all the predicted mentions for a document.
   *
   * @param doc The syntactically annotated document
   * @param dict Dictionaries for coref.
   * @return For each of the List of sentences in the document, a List of Mention objects
   */
  public abstract List<List<Mention>> findMentions(Annotation doc, Dictionaries dict, Properties props);


  protected static void extractPremarkedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhancedDependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    if (enhancedDependency == null) {
      enhancedDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    }
    int beginIndex = -1;
    for (CoreLabel w : sent) {
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
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, sent, basicDependency, enhancedDependency, new ArrayList<>(sent.subList(beginIndex, endIndex)));
            mentions.add(m);
            mentionSpanSet.add(mSpan);
            beginIndex = -1;
          } else {
            Redwood.log("Start of marked mention not found in sentence: "
                    + t + " at tokenIndex=" + (w.get(CoreAnnotations.IndexAnnotation.class)-1)+ " for "
                    + s.get(CoreAnnotations.TextAnnotation.class));
          }
        }
      }
    }
  }


  /** Extract enumerations (A, B, and C) */
  protected static final TregexPattern enumerationsMentionPattern = TregexPattern.compile("NP < (/^(?:NP|NNP|NML)/=m1 $.. (/^CC|,/ $.. /^(?:NP|NNP|NML)/=m2))");

  protected static void extractEnumerations(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhancedDependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    if (enhancedDependency == null) {
      enhancedDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    }

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

    for (Map.Entry<IntPair, Tree> spanMention : spanToMentionSubTree.entrySet()) {
      IntPair span = spanMention.getKey();
      if (!mentionSpanSet.contains(span) && !insideNE(span, namedEntitySpanSet)) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, span.get(0), span.get(1), sent, basicDependency, enhancedDependency,
                new ArrayList<>(sent.subList(span.get(0), span.get(1))), spanMention.getValue());
        mentions.add(m);
        mentionSpanSet.add(span);
      }
    }
  }

  /** Check whether a mention is inside of a named entity */
  protected static boolean insideNE(IntPair mSpan, Set<IntPair> namedEntitySpanSet) {
    for (IntPair span : namedEntitySpanSet){
      if(span.get(0) <= mSpan.get(0) && mSpan.get(1) <= span.get(1)) return true;
    }
    return false;
  }


  public static boolean inStopList(Mention m) {
    String mentionSpan = m.spanToString().toLowerCase(Locale.ENGLISH);
    if (mentionSpan.equals("u.s.") || mentionSpan.equals("u.k.")
        || mentionSpan.equals("u.s.s.r")) return true;
    if (mentionSpan.equals("there") || mentionSpan.startsWith("etc.")
        || mentionSpan.equals("ltd.")) return true;
    if (mentionSpan.startsWith("'s ")) return true;
//    if (mentionSpan.endsWith("etc.")) return true;

    return false;
  }

  protected void removeSpuriousMentions(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict, boolean removeNested, Locale lang) {
    if(lang == Locale.ENGLISH) removeSpuriousMentionsEn(doc, predictedMentions, dict);
    else if (lang == Locale.CHINESE) removeSpuriousMentionsZh(doc, predictedMentions, dict, removeNested);
  }

  protected void removeSpuriousMentionsEn(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict) {
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    for(int i=0 ; i < predictedMentions.size() ; i++) {
      CoreMap s = sentences.get(i);
      List<Mention> mentions = predictedMentions.get(i);

      List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
      Set<Mention> remove = Generics.newHashSet();

      for(Mention m : mentions){
        String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);

        // non word such as 'hmm'
        if(dict.nonWords.contains(m.headString)) remove.add(m);

        // adjective form of nations
        // the [American] policy -> not mention
        // speak in [Japanese] -> mention
        // check if the mention is noun and the next word is not noun
        if (dict.isAdjectivalDemonym(m.spanToString())) {
          if(!headPOS.startsWith("N")
              || (m.endIndex < sent.size() && sent.get(m.endIndex).tag().startsWith("N")) ) {
            remove.add(m);
          }
        }

        // stop list (e.g., U.S., there)
        if (inStopList(m)) remove.add(m);
      }
      mentions.removeAll(remove);
    }
  }

  protected void removeSpuriousMentionsZh(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict, boolean removeNested) {
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    // this goes through each sentence -- predictedMentions has a list for each sentence
    for (int i=0, sz = predictedMentions.size(); i < sz ; i++) {
      List<Mention> mentions = predictedMentions.get(i);
      List<CoreLabel> sent = sentences.get(i).get(CoreAnnotations.TokensAnnotation.class);
      Set<Mention> remove = Generics.newHashSet();

      for (Mention m : mentions) {
        if (m.headWord.ner().matches("PERCENT|MONEY|QUANTITY|CARDINAL")) {
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING number NER: " + m.spanToString());
        } else if (m.originalSpan.size()==1 && m.headWord.tag().equals("CD")) {
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING number: " + m.spanToString());
        } else if (dict.removeWords.contains(m.spanToString())) {
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING removeWord: " + m.spanToString());
        } else if (mentionContainsRemoveChars(m, dict.removeChars)) {
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING removeChars: " + m.spanToString());
        } else if (m.headWord.tag().equals("PU")) {
          // punctuation-only mentions
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING Punctuation only mention: " + m.spanToString());
        } else if (mentionIsDemonym(m, dict.countries)) {
          // demonyms -- this seems to be a no-op on devset. Maybe not working?
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING Removed demonym: " + m.spanToString());
        } else if (m.spanToString().equals("问题") && m.startIndex > 0 &&
            sent.get(m.startIndex - 1).word().endsWith("没")) {
          // 没 问题 - this is maybe okay but having 问题 on removeWords was dangerous
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING Removed meiyou: " + m.spanToString());
        } else if (mentionIsRangren(m, sent)) {
            remove.add(m);
            if (VERBOSE) log.info("MENTION FILTERING Removed rangren: " + m.spanToString());
        } else if (m.spanToString().equals("你") && m.startIndex < sent.size() - 1 &&
            sent.get(m.startIndex + 1).word().startsWith("知道")) {
          // 你 知道
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING Removed nizhidao: " + m.spanToString());
        // The words that used to be in this case are now handled more generallyin removeCharsZh
        // } else if (m.spanToString().contains("什么") || m.spanToString().contains("多少")) {
        //   remove.add(m);
        //   if (VERBOSE) log.info("MENTION FILTERING Removed many/few mention ending: " + m.spanToString());
        } else if (m.spanToString().endsWith("的")) {
          remove.add(m);
          if (VERBOSE) log.info("MENTION FILTERING Removed de ending mention: " + m.spanToString());
        // omit this case, it decreases performance. A few useful interrogative pronouns are now in the removeChars list
        // } else if (mentionIsInterrogativePronoun(m, dict.interrogativePronouns)) {
        //     remove.add(m);
        //     if (VERBOSE) log.info("MENTION FILTERING Removed interrogative pronoun: " + m.spanToString());
        }

        // 的 handling
//        if(m.startIndex>0 && sent.get(m.startIndex-1).word().equals("的")) {
//          // remove.add(m);
//          Tree t = sentences.get(i).get(TreeAnnotation.class);
//          Tree mTree = m.mentionSubTree;
//          if(mTree==null) continue;
//          for(Tree p : t.pathNodeToNode(mTree, t)) {
//            if(mTree==p) continue;
//            if(p.value().equals("NP")) {
//              remove.add(m);
//            }
//          }
//        }

      } // for each mention

      // nested mention with shared headword (except apposition, enumeration): pick larger one
      if (removeNested) {
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
      }

      mentions.removeAll(remove);
    } // for each sentence
  }

  private static boolean mentionContainsRemoveChars(Mention m, Set<String> removeChars) {
    String spanString = m.spanToString();
    for (String ch : removeChars) {
      if (spanString.contains(ch)) {
        return true;
      }
    }
    return false;
  }

  private static boolean mentionIsDemonym(Mention m, Set<String> countries) {
    String lastWord = m.originalSpan.get(m.originalSpan.size()-1).word();
    return lastWord.length() > 0 && m.spanToString().endsWith("人") &&
        countries.contains(lastWord.substring(0, lastWord.length()-1));
  }

  private static boolean mentionIsRangren(Mention m, List<CoreLabel> sent) {
    if (m.spanToString().equals("人") && m.startIndex > 0) {
      String priorWord = sent.get(m.startIndex - 1).word();
      // cdm [2016]: This test matches everything because of the 3rd clause! That can't be right!
      if (priorWord.endsWith("让") || priorWord.endsWith("令") || priorWord.endsWith("")) {
        return true;
      }
    }
    return false;
  }

  private static boolean mentionIsInterrogativePronoun(Mention m, Set<String> interrogatives) {
    // handling interrogative pronouns
    for (CoreLabel cl : m.originalSpan) {
      // if (dict.interrogativePronouns.contains(m.spanToString())) remove.add(m);
      if (interrogatives.contains(cl.word())) {
        return true;
      }
    }
    return false;
  }

  // extract mentions which have same string as another stand-alone mention
  protected static void extractNamedEntityModifiers(List<CoreMap> sentences, List<Set<IntPair>> mentionSpanSetList, List<List<Mention>> predictedMentions, Set<String> neStrings) {
    for (int i=0, sz = sentences.size(); i < sz ; i++ ) {
      List<Mention> mentions = predictedMentions.get(i);
      CoreMap sent = sentences.get(i);
      List<CoreLabel> tokens = sent.get(TokensAnnotation.class);
      Set<IntPair> mentionSpanSet = mentionSpanSetList.get(i);

      for (int j=0, tSize=tokens.size(); j < tSize; j++) {
        for (String ne : neStrings) {
          int len = ne.split(" ").length;
          if (j+len > tokens.size()) continue;

          StringBuilder sb = new StringBuilder();
          for(int k=0 ; k < len ; k++) {
            sb.append(tokens.get(k+j).word()).append(" ");
          }
          String phrase = sb.toString().trim();
          int beginIndex = j;
          int endIndex = j+len;

          // include "'s" if it belongs to this named entity
          if( endIndex < tokens.size() && tokens.get(endIndex).word().equals("'s") && tokens.get(endIndex).tag().equals("POS")) {
            Tree tree = sent.get(TreeAnnotation.class);
            Tree sToken = tree.getLeaves().get(beginIndex);
            Tree eToken = tree.getLeaves().get(endIndex);
            Tree join = tree.joinNode(sToken, eToken);
            Tree sJoin = join.getLeaves().get(0);
            Tree eJoin = join.getLeaves().get(join.getLeaves().size()-1);
            if(sToken == sJoin && eToken == eJoin) {
              endIndex++;
            }
          }

          // include DT if it belongs to this named entity
          if( beginIndex > 0 && tokens.get(beginIndex-1).tag().equals("DT")) {
            Tree tree = sent.get(TreeAnnotation.class);
            Tree sToken = tree.getLeaves().get(beginIndex-1);
            Tree eToken = tree.getLeaves().get(endIndex-1);
            Tree join = tree.joinNode(sToken, eToken);
            Tree sJoin = join.getLeaves().get(0);
            Tree eJoin = join.getLeaves().get(join.getLeaves().size()-1);
            if(sToken == sJoin && eToken == eJoin) {
              beginIndex--;
            }
          }

          IntPair span = new IntPair(beginIndex, endIndex);
          if(phrase.equalsIgnoreCase(ne) && !mentionSpanSet.contains(span)) {
            int dummyMentionId = -1;
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, tokens,
                sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
                sent.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class) != null
                    ? sent.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)
                    : sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
                    new ArrayList<>(tokens.subList(beginIndex, endIndex)));
            mentions.add(m);
            mentionSpanSet.add(span);
          }
        }
      }
    }
  }

  protected static void addNamedEntityStrings(CoreMap s, Set<String> neStrings, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> tokens = s.get(TokensAnnotation.class);
    for(IntPair p : namedEntitySpanSet) {
      StringBuilder sb = new StringBuilder();
      for(int idx=p.get(0) ; idx < p.get(1) ; idx++) {
        sb.append(tokens.get(idx).word()).append(" ");
      }
      String str = sb.toString().trim();
      if(str.endsWith(" 's")) {
        str = str.substring(0, str.length()-3);
      }
      neStrings.add(str);
    }
  }


  // temporary for debug
  protected static void addGoldMentions(List<CoreMap> sentences,
      List<Set<IntPair>> mentionSpanSetList,
      List<List<Mention>> predictedMentions, List<List<Mention>> allGoldMentions) {
    for (int i=0, sz = sentences.size(); i < sz; i++) {
      List<Mention> mentions = predictedMentions.get(i);
      CoreMap sent = sentences.get(i);
      List<CoreLabel> tokens = sent.get(TokensAnnotation.class);
      Set<IntPair> mentionSpanSet = mentionSpanSetList.get(i);
      List<Mention> golds = allGoldMentions.get(i);

      for (Mention g : golds) {
        IntPair pair = new IntPair(g.startIndex, g.endIndex);
        if(!mentionSpanSet.contains(pair)) {
          int dummyMentionId = -1;
          Mention m = new Mention(dummyMentionId, g.startIndex, g.endIndex, tokens,
              sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
              sent.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class) != null
                 ? sent.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)
                 : sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
                  new ArrayList<>(tokens.subList(g.startIndex, g.endIndex)));
          mentions.add(m);
          mentionSpanSet.add(pair);
        }
      }
    }

  }

  public void findHead(CoreMap s, List<Mention> mentions) {
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    tree.indexSpans(0);
    for (Mention m : mentions){

      if (lang == Locale.CHINESE) {
        findHeadChinese(sent, m);
      } else {
        CoreLabel head = (CoreLabel) findSyntacticHead(m, tree, sent).label();
        m.headIndex = head.get(CoreAnnotations.IndexAnnotation.class)-1;
        m.headWord = sent.get(m.headIndex);
        m.headString = m.headWord.get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH);
      }

      int start = m.headIndex - m.startIndex;
      if (start < 0 || start >= m.originalSpan.size()) {
        Redwood.log("Invalid index for head " + start + "=" + m.headIndex + "-" + m.startIndex
                + ": originalSpan=[" + StringUtils.joinWords(m.originalSpan, " ") + "], head=" + m.headWord);
        Redwood.log("Setting head string to entire mention");
        m.headIndex = m.startIndex;
        m.headWord = m.originalSpan.size() > 0 ? m.originalSpan.get(0) : sent.get(m.startIndex);
        m.headString = m.originalSpan.toString();
      }
    }
  }

  protected static void findHeadChinese(List<CoreLabel> sent, Mention m) {
    int headPos = m.endIndex - 1;
    // Skip trailing punctuations
    while (headPos > m.startIndex && sent.get(headPos).tag().equals("PU")) {
      headPos--;
    }
    // If we got right to the end without finding non punctuation, reset to end again
    if (headPos == m.startIndex && sent.get(headPos).tag().equals("PU")) {
      headPos = m.endIndex - 1;
    }
    if (sent.get(headPos).originalText().equals("自己") && m.endIndex != m.startIndex && headPos > m.startIndex) {
      if (!sent.get(headPos-1).tag().equals("PU"))
        headPos--;
    }
    m.headIndex = headPos;
    m.headWord = sent.get(headPos);
    m.headString = m.headWord.get(CoreAnnotations.TextAnnotation.class);
  }

  public Tree findSyntacticHead(Mention m, Tree root, List<CoreLabel> tokens) {
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
      extentTokens.add(initCoreLabel("It", "PRP"));
      extentTokens.add(initCoreLabel("was", "VBD"));
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
      extentTokens.add(initCoreLabel(".", "."));

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
    Redwood.log("RuleBasedCorefMentionFinder: Failed to find head token:\n" +
        "Tree is: " + root + "\n" +
        "token = |" + token + "|" + index + "|, approx=" + approximateness);
    for (Tree leaf : leaves) {
      if (token.equals(leaf.value())) {
        // log.info("Found it at position " + ind + "; returning " + leaf);
        return leaf;
      }
    }
    int fallback = Math.max(0, leaves.size() - 2);
    Redwood.log("RuleBasedCorefMentionFinder: Last resort: returning as head: " + leaves.get(fallback));
    return leaves.get(fallback); // last except for the added period.
  }

  private static CoreLabel initCoreLabel(String token, String posTag) {
    CoreLabel label = new CoreLabel();
    label.set(CoreAnnotations.TextAnnotation.class, token);
    label.set(CoreAnnotations.ValueAnnotation.class, token);
    label.set(CoreAnnotations.PartOfSpeechAnnotation.class, posTag);
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
      parserProcessor = StanfordCoreNLP.getExistingAnnotator("parse");
      if (parserProcessor == null) {
        Properties emptyProperties = new Properties();
        parserProcessor = new ParserAnnotator("coref.parse.md", emptyProperties);
      }
      assert(parserProcessor != null);
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

  public static boolean partitiveRule(Mention m, List<CoreLabel> sent, Dictionaries dict) {
    return m.startIndex >= 2
            && sent.get(m.startIndex - 1).get(CoreAnnotations.TextAnnotation.class).equalsIgnoreCase("of")
            && dict.parts.contains(sent.get(m.startIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH));
  }

  /** Check whether pleonastic 'it'. E.g., It is possible that ... */
  private static final TregexPattern[] pleonasticPatterns = getPleonasticPatterns();
  public static boolean isPleonastic(Mention m, Tree tree) {
    if ( ! m.spanToString().equalsIgnoreCase("it")) return false;
    for (TregexPattern p : pleonasticPatterns) {
      if (checkPleonastic(m, tree, p)) {
        // SieveCoreferenceSystem.logger.fine("RuleBasedCorefMentionFinder: matched pleonastic pattern '" + p + "' for " + tree);
        return true;
      }
    }
    return false;
  }
  public static boolean isPleonasticDebug(Mention m, Tree tree, StringBuilder sbLog) {
    if ( ! m.spanToString().equalsIgnoreCase("it")) return false;
    boolean isPleonastic = false;
    int patternIdx = -1;
    int matchedPattern = -1;

    for (TregexPattern p : pleonasticPatterns) {
      patternIdx++;
      if (checkPleonastic(m, tree, p)) {
        // SieveCoreferenceSystem.logger.fine("RuleBasedCorefMentionFinder: matched pleonastic pattern '" + p + "' for " + tree);
        isPleonastic = true;
        matchedPattern = patternIdx;
      }
    }
    sbLog.append("PLEONASTIC IT: mention ID: "+m.mentionID +"\thastwin: "+m.hasTwin+"\tpleonastic it? "+isPleonastic+"\tcorrect? "+(m.hasTwin!=isPleonastic)+"\tmatched pattern: "+matchedPattern+"\n");
    sbLog.append(m.contextParseTree.pennString()).append("\n");
    sbLog.append("PLEONASTIC IT END\n");

    return isPleonastic;
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
