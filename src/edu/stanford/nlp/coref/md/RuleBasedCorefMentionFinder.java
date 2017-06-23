
package edu.stanford.nlp.coref.md;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;

public class RuleBasedCorefMentionFinder extends CorefMentionFinder {

  public RuleBasedCorefMentionFinder(HeadFinder headFinder, Properties props) {
    this(true, headFinder, CorefProperties.getLanguage(props));
  }

  public RuleBasedCorefMentionFinder(boolean allowReparsing, HeadFinder headFinder, Locale lang) {
    this.headFinder = headFinder;
    this.allowReparsing = allowReparsing;
    this.lang = lang;
  }

  /** When mention boundaries are given */
  public List<List<Mention>> filterPredictedMentions(List<List<Mention>> allGoldMentions, Annotation doc, Dictionaries dict, Properties props){
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
    }
    removeSpuriousMentions(doc, predictedMentions, dict, CorefProperties.removeNestedMentions(props), lang);
    return predictedMentions;
  }

  /** Main method of mention detection.
   *  Extract all NP, PRP or NE, and filter out by manually written patterns.
   */
  @Override
  public List<List<Mention>> findMentions(Annotation doc, Dictionaries dict, Properties props) {
    List<List<Mention>> predictedMentions = new ArrayList<>();
    Set<String> neStrings = Generics.newHashSet();
    List<Set<IntPair>> mentionSpanSetList = Generics.newArrayList();
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    // extract premarked mentions, NP/PRP, named entity, enumerations
    for (CoreMap s : sentences) {
      List<Mention> mentions = new ArrayList<>();
      predictedMentions.add(mentions);
      Set<IntPair> mentionSpanSet = Generics.newHashSet();
      Set<IntPair> namedEntitySpanSet = Generics.newHashSet();

      extractPremarkedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNamedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNPorPRP(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractEnumerations(s, mentions, mentionSpanSet, namedEntitySpanSet);

      addNamedEntityStrings(s, neStrings, namedEntitySpanSet);
      mentionSpanSetList.add(mentionSpanSet);
    }

    if (CorefProperties.liberalMD(props)) {
      extractNamedEntityModifiers(sentences, mentionSpanSetList, predictedMentions, neStrings);
    }

    // find head
    for (int i=0, sz = sentences.size(); i < sz; i++) {
      findHead(sentences.get(i), predictedMentions.get(i));
      setBarePlural(predictedMentions.get(i));
    }

    // mention selection based on document-wise info
    if (lang == Locale.ENGLISH && !CorefProperties.liberalMD(props)) {
      removeSpuriousMentionsEn(doc, predictedMentions, dict);
    } else if (lang == Locale.CHINESE) {
      if (CorefProperties.liberalMD(props)) {
        removeSpuriousMentionsZhSimple(doc, predictedMentions, dict);
      } else {
        removeSpuriousMentionsZh(doc, predictedMentions, dict,
            CorefProperties.removeNestedMentions(props));
      }
    }

    return predictedMentions;
  }

  protected static void setBarePlural(List<Mention> mentions) {
    for (Mention m : mentions) {
      String pos = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(m.originalSpan.size()==1 && pos.equals("NNS")) m.generic = true;
    }
  }

  public void extractNPorPRP(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
    tree.indexLeaves();
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhancedDependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    if (enhancedDependency == null) {
      enhancedDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    }

    TregexPattern tgrepPattern = npOrPrpMentionPattern;
    TregexMatcher matcher = tgrepPattern.matcher(tree);
    while (matcher.find()) {
      Tree t = matcher.getMatch();
      List<Tree> mLeaves = t.getLeaves();
      int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
      int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
      //if (",".equals(sent.get(endIdx-1).word())) { endIdx--; } // try not to have span that ends with ,
      IntPair mSpan = new IntPair(beginIdx, endIdx);
      if(!mentionSpanSet.contains(mSpan) && ( lang==Locale.CHINESE || !insideNE(mSpan, namedEntitySpanSet)) ) {
//      if(!mentionSpanSet.contains(mSpan) && (!insideNE(mSpan, namedEntitySpanSet) || t.value().startsWith("PRP")) ) {
        int dummyMentionId = -1;
        Mention m = new Mention(dummyMentionId, beginIdx, endIdx, sent, basicDependency, enhancedDependency, new ArrayList<>(sent.subList(beginIdx, endIdx)), t);
        mentions.add(m);
        mentionSpanSet.add(mSpan);

//        if(m.originalSpan.size() > 1) {
//          boolean isNE = true;
//          for(CoreLabel cl : m.originalSpan) {
//            if(!cl.tag().startsWith("NNP")) isNE = false;
//          }
//          if(isNE) {
//            namedEntitySpanSet.add(mSpan);
//          }
//        }
      }
    }
  }
  protected static void extractNamedEntityMentions(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basicDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhancedDependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    if (enhancedDependency == null) {
      enhancedDependency = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    }
    String preNE = "O";
    int beginIndex = -1;
    for(CoreLabel w : sent) {
      String nerString = w.ner();
      if(!nerString.equals(preNE)) {
        int endIndex = w.get(CoreAnnotations.IndexAnnotation.class) - 1;
        if(!preNE.matches("O|QUANTITY|CARDINAL|PERCENT|DATE|DURATION|TIME|SET")){
          if(w.get(CoreAnnotations.TextAnnotation.class).equals("'s") && w.tag().equals("POS")) {
              endIndex++;
          }
          IntPair mSpan = new IntPair(beginIndex, endIndex);
          // Need to check if beginIndex < endIndex because, for
          // example, there could be a 's mislabeled by the NER and
          // attached to the previous NER by the earlier heuristic
          if(beginIndex < endIndex && !mentionSpanSet.contains(mSpan)) {
            int dummyMentionId = -1;
            Mention m = new Mention(dummyMentionId, beginIndex, endIndex, sent, basicDependency, enhancedDependency, new ArrayList<>(sent.subList(beginIndex, endIndex)));
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
        Mention m = new Mention(dummyMentionId, beginIndex, sent.size(), sent, basicDependency, enhancedDependency, new ArrayList<>(sent.subList(beginIndex, sent.size())));
        mentions.add(m);
        mentionSpanSet.add(mSpan);
        namedEntitySpanSet.add(mSpan);
      }
    }
  }

  private static void removeSpuriousMentionsZhSimple(Annotation doc,
      List<List<Mention>> predictedMentions, Dictionaries dict) {
    for(int i=0 ; i < predictedMentions.size() ; i++) {
      List<Mention> mentions = predictedMentions.get(i);
      Set<Mention> remove = Generics.newHashSet();
      for(Mention m : mentions){
        if (m.originalSpan.size()==1 && m.headWord.tag().equals("CD")) {
          remove.add(m);
        }
        if (m.spanToString().contains("ｑｕｏｔ")) {
          remove.add(m);
        }
      }
      mentions.removeAll(remove);
    }
  }

  /** Filter out all spurious mentions
   */
  @Override
  public void removeSpuriousMentionsEn(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict) {

    Set<String> standAlones = new HashSet<>();
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    for(int i=0 ; i < predictedMentions.size() ; i++) {
      CoreMap s = sentences.get(i);
      List<Mention> mentions = predictedMentions.get(i);

      Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
      List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
      Set<Mention> remove = Generics.newHashSet();

      for(Mention m : mentions){
        String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String headNE = m.headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        // pleonastic it
        if(isPleonastic(m, tree)) {
          remove.add(m);
        }

        // non word such as 'hmm'
        if(dict.nonWords.contains(m.headString)) remove.add(m);

        // quantRule : not starts with 'any', 'all' etc
        if (m.originalSpan.size() > 0) {
          String firstWord = m.originalSpan.get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH);
          if(firstWord.matches("none|no|nothing|not")) {
            remove.add(m);
          }
//          if(dict.quantifiers.contains(firstWord)) remove.add(m);
        }

        // partitiveRule
        if (partitiveRule(m, sent, dict)) {
          remove.add(m);
        }

        // bareNPRule
        if (headPOS.equals("NN") && !dict.temporals.contains(m.headString)
            && (m.originalSpan.size()==1 || m.originalSpan.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("JJ"))) {
          remove.add(m);
        }

        // remove generic rule
//          if(m.generic==true) remove.add(m);

        if (m.headString.equals("%")) {
          remove.add(m);
        }
        if (headNE.equals("PERCENT") || headNE.equals("MONEY")) {
          remove.add(m);
        }

        // adjective form of nations
        // the [American] policy -> not mention
        // speak in [Japanese] -> mention
        // check if the mention is noun and the next word is not noun
        if (dict.isAdjectivalDemonym(m.spanToString())) {
          remove.add(m);
        }

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
  }
}
