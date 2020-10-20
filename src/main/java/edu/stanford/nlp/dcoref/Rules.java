package edu.stanford.nlp.dcoref;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.dcoref.Dictionaries.Animacy;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.dcoref.Dictionaries.Person;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.math.NumberMatchingRegex;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Sets;


/**
 * Rules for coref system (mention detection, entity coref, event coref)
 * The name of the method for mention detection starts with detection,
 * for entity coref starts with entity, and for event coref starts with event.
 *
 * @author heeyoung, recasens
 */
public class Rules {

  private static final boolean DEBUG = true;

  public static boolean entityBothHaveProper(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent) {
    boolean mentionClusterHaveProper = false;
    boolean potentialAntecedentHaveProper = false;

    for (Mention m : mentionCluster.corefMentions) {
      if (m.mentionType==MentionType.PROPER) {
        mentionClusterHaveProper = true;
        break;
      }
    }
    for (Mention a : potentialAntecedent.corefMentions) {
      if (a.mentionType==MentionType.PROPER) {
        potentialAntecedentHaveProper = true;
        break;
      }
    }
    return (mentionClusterHaveProper && potentialAntecedentHaveProper);
  }
  public static boolean entitySameProperHeadLastWord(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent, Mention mention, Mention ant) {
    for (Mention m : mentionCluster.getCorefMentions()){
      for (Mention a : potentialAntecedent.getCorefMentions()) {
        if (entitySameProperHeadLastWord(m, a)) return true;
      }
    }
    return false;
  }

  public static boolean entityAlias(CorefCluster mentionCluster, CorefCluster potentialAntecedent,
      Semantics semantics, Dictionaries dict) throws Exception {

    Mention mention = mentionCluster.getRepresentativeMention();
    Mention antecedent = potentialAntecedent.getRepresentativeMention();
    if(mention.mentionType!=MentionType.PROPER
        || antecedent.mentionType!=MentionType.PROPER) return false;

    Method meth = semantics.wordnet.getClass().getMethod("alias", new Class[]{Mention.class, Mention.class});
    if((Boolean) meth.invoke(semantics.wordnet, new Object[]{mention, antecedent})) {
      return true;
    }
    return false;
  }
  public static boolean entityIWithinI(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent, Dictionaries dict) {
    for(Mention m : mentionCluster.getCorefMentions()) {
      for(Mention a : potentialAntecedent.getCorefMentions()) {
        if(entityIWithinI(m, a, dict)) return true;
      }
    }
    return false;
  }
  public static boolean entityPersonDisagree(Document document, CorefCluster mentionCluster, CorefCluster potentialAntecedent, Dictionaries dict){
    boolean disagree = false;
    for(Mention m : mentionCluster.getCorefMentions()) {
      for(Mention ant : potentialAntecedent.getCorefMentions()) {
        if(entityPersonDisagree(document, m, ant, dict)) {
          disagree = true;
          break;
        }
      }
    }
    if(disagree) return true;
    else return false;
  }

  private static final List<String> entityWordsToExclude =
          Arrays.asList(new String[]{ "the","this", "mr.", "miss", "mrs.", "dr.", "ms.", "inc.", "ltd.", "corp.", "'s"});
  /** Word inclusion except stop words  */
  public static boolean entityWordsIncluded(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention mention, Mention ant) {
    Set<String> wordsExceptStopWords = Generics.newHashSet(mentionCluster.words);
    wordsExceptStopWords.removeAll(entityWordsToExclude);
    wordsExceptStopWords.remove(mention.headString.toLowerCase());
    if(potentialAntecedent.words.containsAll(wordsExceptStopWords)) return true;
    else return false;
  }

  /** Compatible modifier only  */
  public static boolean entityHaveIncompatibleModifier(CorefCluster mentionCluster, CorefCluster potentialAntecedent) {
    for(Mention m : mentionCluster.corefMentions){
      for(Mention ant : potentialAntecedent.corefMentions){
        if(entityHaveIncompatibleModifier(m, ant)) return true;
      }
    }
    return false;
  }
  public static boolean entityIsRoleAppositive(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2, Dictionaries dict) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    return m1.isRoleAppositive(m2, dict) || m2.isRoleAppositive(m1, dict);
  }
  public static boolean entityIsRelativePronoun(Mention m1, Mention m2) {
      return m1.isRelativePronoun(m2) || m2.isRelativePronoun(m1);
  }

  public static boolean entityIsAcronym(Document document, CorefCluster mentionCluster, CorefCluster potentialAntecedent) {
    int minId = Math.min(mentionCluster.clusterID, potentialAntecedent.clusterID);
    int maxId = Math.max(mentionCluster.clusterID, potentialAntecedent.clusterID);
    if(!document.acronymCache.contains(minId, maxId)) {
      boolean isAcronym = false;
      for(Mention m : mentionCluster.corefMentions){
        if(m.isPronominal()) continue;
        for(Mention ant : potentialAntecedent.corefMentions){
          if(isAcronym(m.originalSpan, ant.originalSpan)) isAcronym = true;
        }
      }
      document.acronymCache.put(minId, maxId, isAcronym);
    }
    return document.acronymCache.get(minId, maxId);
  }

  public static boolean isAcronym(List<CoreLabel> first, List<CoreLabel> second) {
    if (first.size() > 1 && second.size() > 1) {
      return false;
    }
    if (first.size() == 0 && second.size() == 0) { return false; }
    List<CoreLabel> longer;
    List<CoreLabel> shorter;

    if (first.size() == second.size()) {
      String firstWord = first.get(0).get(CoreAnnotations.TextAnnotation.class);
      String secondWord = second.get(0).get(CoreAnnotations.TextAnnotation.class);
      longer = (firstWord.length() > secondWord.length()) ? first : second;
      shorter = (firstWord.length() > secondWord.length()) ? second : first;
    } else {
      longer = (first.size() > 0 && first.size() > second.size()) ? first : second;
      shorter = (second.size() > 0 && first.size() > second.size()) ? second : first;
    }

    String acronym = shorter.size() > 0 ? shorter.get(0).get(CoreAnnotations.TextAnnotation.class) : "<UNK>";
    // This check is not strictly necessary, but it saves a chunk of
    // time iterating through the text of the longer mention
    for (int acronymPos = 0; acronymPos < acronym.length(); ++acronymPos) {
      if (acronym.charAt(acronymPos) < 'A' || acronym.charAt(acronymPos) > 'Z') {
        return false;
      }
    }
    int acronymPos = 0;
    for (CoreLabel aLonger1 : longer) {
      String word = aLonger1.get(CoreAnnotations.TextAnnotation.class);
      for (int charNum = 0; charNum < word.length(); ++charNum) {
        if (word.charAt(charNum) >= 'A' && word.charAt(charNum) <= 'Z') {
          // This triggers if there were more "acronym" characters in
          // the longer mention than in the shorter mention
          if (acronymPos >= acronym.length()) {
            return false;
          }
          if (acronym.charAt(acronymPos) != word.charAt(charNum)) {
            return false;
          }
          ++acronymPos;
        }
      }
    }
    if (acronymPos != acronym.length()) {
      return false;
    }
    for (CoreLabel aLonger : longer) {
      if (aLonger.get(CoreAnnotations.TextAnnotation.class).contains(acronym)) {
        return false;
      }
    }

    return true;
  }

  public static boolean entityIsPredicateNominatives(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    if ((m1.startIndex <= m2.startIndex && m1.endIndex >= m2.endIndex)
            || (m1.startIndex >= m2.startIndex && m1.endIndex <= m2.endIndex)) {
      return false;
    }
    return m1.isPredicateNominatives(m2) || m2.isPredicateNominatives(m1);
  }

  public static boolean entityIsApposition(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    if(m1.mentionType==MentionType.PROPER && m2.mentionType==MentionType.PROPER) return false;
    if(m1.nerString.equals("LOCATION")) return false;
    return m1.isApposition(m2) || m2.isApposition(m1);
  }

  public static boolean entityAttributesAgree(CorefCluster mentionCluster, CorefCluster potentialAntecedent){
    return entityAttributesAgree(mentionCluster, potentialAntecedent, false);
  }

  public static boolean entityAttributesAgree(CorefCluster mentionCluster, CorefCluster potentialAntecedent, boolean ignoreGender){

    boolean hasExtraAnt = false;
    boolean hasExtraThis = false;

    // number
    if(!mentionCluster.numbers.contains(Number.UNKNOWN)){
      for(Number n : potentialAntecedent.numbers){
        if(n!=Number.UNKNOWN && !mentionCluster.numbers.contains(n)) {
          hasExtraAnt = true;
          break;
        }
      }
    }
    if(!potentialAntecedent.numbers.contains(Number.UNKNOWN)){
      for(Number n : mentionCluster.numbers){
        if(n!=Number.UNKNOWN && !potentialAntecedent.numbers.contains(n)) {
          hasExtraThis = true;
          break;
        }
      }
    }

    if(hasExtraAnt && hasExtraThis) return false;

    // gender
    hasExtraAnt = false;
    hasExtraThis = false;

    if (!ignoreGender) {
      if(!mentionCluster.genders.contains(Gender.UNKNOWN)){
        for(Gender g : potentialAntecedent.genders){
          if(g!=Gender.UNKNOWN && !mentionCluster.genders.contains(g)) {
            hasExtraAnt = true;
            break;
          }
        }
      }
      if(!potentialAntecedent.genders.contains(Gender.UNKNOWN)){
        for(Gender g : mentionCluster.genders){
          if(g!=Gender.UNKNOWN && !potentialAntecedent.genders.contains(g)) {
            hasExtraThis = true;
            break;
          }
        }
      }
    }
    if(hasExtraAnt && hasExtraThis) return false;

    // animacy
    hasExtraAnt = false;
    hasExtraThis = false;

    if(!mentionCluster.animacies.contains(Animacy.UNKNOWN)){
      for(Animacy a : potentialAntecedent.animacies){
        if(a!=Animacy.UNKNOWN && !mentionCluster.animacies.contains(a)) {
          hasExtraAnt = true;
          break;
        }
      }
    }
    if(!potentialAntecedent.animacies.contains(Animacy.UNKNOWN)){
      for(Animacy a : mentionCluster.animacies){
        if(a!=Animacy.UNKNOWN && !potentialAntecedent.animacies.contains(a)) {
          hasExtraThis = true;
          break;
        }
      }
    }
    if(hasExtraAnt && hasExtraThis) return false;

    // NE type
    hasExtraAnt = false;
    hasExtraThis = false;

    if(!mentionCluster.nerStrings.contains("O") && !mentionCluster.nerStrings.contains("MISC")){
      for(String ne : potentialAntecedent.nerStrings){
        if(!ne.equals("O") && !ne.equals("MISC") && !mentionCluster.nerStrings.contains(ne)) {
          hasExtraAnt = true;
          break;
        }
      }
    }
    if(!potentialAntecedent.nerStrings.contains("O") && !potentialAntecedent.nerStrings.contains("MISC")){
      for(String ne : mentionCluster.nerStrings){
        if(!ne.equals("O") && !ne.equals("MISC") && !potentialAntecedent.nerStrings.contains(ne)) {
          hasExtraThis = true;
          break;
        }
      }
    }
    return ! (hasExtraAnt && hasExtraThis);
  }

  public static boolean entityRelaxedHeadsAgreeBetweenMentions(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m, Mention ant) {
    if(m.isPronominal() || ant.isPronominal()) return false;
    if(m.headsAgree(ant)) return true;
    return false;
  }

  public static boolean entityHeadsAgree(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m, Mention ant, Dictionaries dict) {
    boolean headAgree = false;
    if(m.isPronominal() || ant.isPronominal()
        || dict.allPronouns.contains(m.lowercaseNormalizedSpanString())
        || dict.allPronouns.contains(ant.lowercaseNormalizedSpanString())) return false;
    for(Mention a : potentialAntecedent.corefMentions){
      if(a.headString.equals(m.headString)) headAgree= true;
    }
    return headAgree;
  }

  public static boolean entityExactStringMatch(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Dictionaries dict, Set<Mention> roleSet){
    boolean matched = false;
    for(Mention m : mentionCluster.corefMentions){
      if(roleSet.contains(m)) return false;
      if(m.isPronominal()) {
        continue;
      }
      String mSpan = m.lowercaseNormalizedSpanString();
      if(dict.allPronouns.contains(mSpan)) {
        continue;
      }
      for(Mention ant : potentialAntecedent.corefMentions){
        if(ant.isPronominal()) {
          continue;
        }
        String antSpan = ant.lowercaseNormalizedSpanString();
        if(dict.allPronouns.contains(antSpan)) continue;
        if(mSpan.equals(antSpan)) matched = true;
        if(mSpan.equals(antSpan+" 's") || antSpan.equals(mSpan+" 's")) matched = true;
      }
    }
    return matched;
  }

  /**
   * Exact string match except phrase after head (only for proper noun):
   * For dealing with a error like {@literal "[Mr. Bickford] <- [Mr. Bickford , an 18-year mediation veteran] }"
   */
  public static boolean entityRelaxedExactStringMatch(
      CorefCluster mentionCluster,
      CorefCluster potentialAntecedent,
      Mention mention,
      Mention ant,
      Dictionaries dict,
      Set<Mention> roleSet){
    if(roleSet.contains(mention)) return false;
    if(mention.mentionType == MentionType.LIST || ant.mentionType == MentionType.LIST) return false;
    if(mention.isPronominal() || ant.isPronominal()
        || dict.allPronouns.contains(mention.lowercaseNormalizedSpanString())
        || dict.allPronouns.contains(ant.lowercaseNormalizedSpanString())) return false;
    String mentionSpan = mention.removePhraseAfterHead();
    String antSpan = ant.removePhraseAfterHead();
    if(mentionSpan.equals("") || antSpan.equals("")) return false;

    if(mentionSpan.equals(antSpan) || mentionSpan.equals(antSpan+" 's") || antSpan.equals(mentionSpan+" 's")){
      return true;
    }
    return false;
  }

  /** Check whether two mentions are in i-within-i relation (Chomsky, 1981) */
  public static boolean entityIWithinI(Mention m1, Mention m2, Dictionaries dict){
    // check for nesting: i-within-i
    if(!m1.isApposition(m2) && !m2.isApposition(m1)
        && !m1.isRelativePronoun(m2) && !m2.isRelativePronoun(m1)
        && !m1.isRoleAppositive(m2, dict) && !m2.isRoleAppositive(m1, dict)
    ){
      if(m1.includedIn(m2) || m2.includedIn(m1)){
        return true;
      }
    }
    return false;
  }


  /** Check whether later mention has incompatible modifier */
  public static boolean entityHaveIncompatibleModifier(Mention m, Mention ant) {
    if(!ant.headString.equalsIgnoreCase(m.headString)) return false;   // only apply to same head mentions
    boolean thisHasExtra = false;
    int lengthThis = m.originalSpan.size();
    int lengthM = ant.originalSpan.size();
    Set<String> thisWordSet = Generics.newHashSet();
    Set<String> antWordSet = Generics.newHashSet();
    Set<String> locationModifier = Generics.newHashSet(Arrays.asList("east", "west", "north", "south",
        "eastern", "western", "northern", "southern", "upper", "lower"));

    for (int i=0; i< lengthThis ; i++){
      String w1 = m.originalSpan.get(i).get(CoreAnnotations.TextAnnotation.class).toLowerCase();
      String pos1 = m.originalSpan.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if (!(pos1.startsWith("N") || pos1.startsWith("JJ") || pos1.equals("CD")
            || pos1.startsWith("V")) || w1.equalsIgnoreCase(m.headString)) {
        continue;
      }
      thisWordSet.add(w1);
    }
    for (int j=0 ; j < lengthM ; j++){
      String w2 = ant.originalSpan.get(j).get(CoreAnnotations.TextAnnotation.class).toLowerCase();
      antWordSet.add(w2);
    }
    for (String w : thisWordSet){
      if(!antWordSet.contains(w)) {
        thisHasExtra = true;
        break;
      }
    }
    boolean hasLocationModifier = false;
    for(String l : locationModifier){
      if(antWordSet.contains(l) && !thisWordSet.contains(l)) {
        hasLocationModifier = true;
        break;
      }
    }
    return (thisHasExtra || hasLocationModifier);
  }
  /** Check whether two mentions have different locations */
  private static final Set<String> locationModifier = Generics.newHashSet(Arrays.asList("east", "west", "north", "south",
          "eastern", "western", "northern", "southern", "northwestern", "southwestern", "northeastern",
          "southeastern", "upper", "lower"));
  public static boolean entityHaveDifferentLocation(Mention m, Mention a, Dictionaries dict) {

    // state and country cannot be coref
    if ((dict.statesAbbreviation.containsKey(a.spanToString()) || dict.statesAbbreviation.containsValue(a.spanToString()))
          && (m.headString.equalsIgnoreCase("country") || m.headString.equalsIgnoreCase("nation"))) {
      return true;
    }

    Set<String> locationM = Generics.newHashSet();
    Set<String> locationA = Generics.newHashSet();
    String mString = m.lowercaseNormalizedSpanString();
    String aString = a.lowercaseNormalizedSpanString();

    for (CoreLabel w : m.originalSpan){
      String text = w.get(CoreAnnotations.TextAnnotation.class);
      String lowercased = text.toLowerCase();
      if (locationModifier.contains(lowercased)) return true;
      if (w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("LOCATION")) {
        String loc = text;
        if(dict.statesAbbreviation.containsKey(loc)) loc = dict.statesAbbreviation.get(loc);
        locationM.add(lowercased);
      }
    }
    for (CoreLabel w : a.originalSpan){
      String text = w.get(CoreAnnotations.TextAnnotation.class);
      String lowercased = text.toLowerCase();
      if (locationModifier.contains(lowercased)) return true;
      if (w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("LOCATION")) {
        String loc = text;
        if(dict.statesAbbreviation.containsKey(loc)) loc = dict.statesAbbreviation.get(loc);
        locationA.add(lowercased);
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;
    for (String s : locationM) {
      if (!aString.contains(s)) {
        mHasExtra = true;
        break;
      }
    }
    for (String s : locationA) {
      if (!mString.contains(s)) {
        aHasExtra = true;
        break;
      }
    }
    if(mHasExtra && aHasExtra) {
      return true;
    }
    return false;
  }

  /** Check whether two mentions have the same proper head words */
  public static boolean entitySameProperHeadLastWord(Mention m, Mention a) {
    if(!m.headString.equalsIgnoreCase(a.headString)
        || !m.sentenceWords.get(m.headIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")
        || !a.sentenceWords.get(a.headIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
      return false;
    }
    if(!m.removePhraseAfterHead().toLowerCase().endsWith(m.headString)
        || !a.removePhraseAfterHead().toLowerCase().endsWith(a.headString)) {
      return false;
    }
    Set<String> mProperNouns = Generics.newHashSet();
    Set<String> aProperNouns = Generics.newHashSet();
    for (CoreLabel w : m.sentenceWords.subList(m.startIndex, m.headIndex)){
      if (w.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
        mProperNouns.add(w.get(CoreAnnotations.TextAnnotation.class));
      }
    }
    for (CoreLabel w : a.sentenceWords.subList(a.startIndex, a.headIndex)){
      if (w.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
        aProperNouns.add(w.get(CoreAnnotations.TextAnnotation.class));
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;
    for (String s : mProperNouns) {
      if (!aProperNouns.contains(s)) {
        mHasExtra = true;
        break;
      }
    }
    for (String s : aProperNouns) {
      if (!mProperNouns.contains(s)) {
        aHasExtra = true;
        break;
      }
    }
    if(mHasExtra && aHasExtra) return false;
    return true;
  }

  private static final Set<String> NUMBERS = Generics.newHashSet(Arrays.asList(new String[]{
          "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "hundred", "thousand", "million", "billion"}));
  /** Check whether there is a new number in later mention */
  public static boolean entityNumberInLaterMention(Mention mention, Mention ant) {
    Set<String> antecedentWords = Generics.newHashSet();
    for (CoreLabel w : ant.originalSpan){
      antecedentWords.add(w.get(CoreAnnotations.TextAnnotation.class));
    }
    for (CoreLabel w : mention.originalSpan) {
      String word = w.get(CoreAnnotations.TextAnnotation.class);
      // Note: this is locale specific for English and ascii numerals
      if (NumberMatchingRegex.isDouble(word)) {
        if (!antecedentWords.contains(word)) return true;
      } else {
        if (NUMBERS.contains(word.toLowerCase()) && !antecedentWords.contains(word)) return true;
      }
    }
    return false;
  }

  /** Have extra proper noun except strings involved in semantic match */
  public static boolean entityHaveExtraProperNoun(Mention m, Mention a, Set<String> exceptWords) {
    Set<String> mProper = Generics.newHashSet();
    Set<String> aProper = Generics.newHashSet();
    String mString = m.spanToString();
    String aString = a.spanToString();

    for (CoreLabel w : m.originalSpan){
      if (w.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
        mProper.add(w.get(CoreAnnotations.TextAnnotation.class));
      }
    }
    for (CoreLabel w : a.originalSpan){
      if (w.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
        aProper.add(w.get(CoreAnnotations.TextAnnotation.class));
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;


    for (String s : mProper) {
      if (!aString.contains(s) && !exceptWords.contains(s.toLowerCase())) {
        mHasExtra = true;
        break;
      }
    }
    for (String s : aProper) {
      if (!mString.contains(s) && !exceptWords.contains(s.toLowerCase())) {
        aHasExtra = true;
        break;
      }
    }

    if(mHasExtra && aHasExtra) {
      return true;
    }
    return false;
  }

  /** Is the speaker for mention the same entity as the ant entity? */
  public static boolean antecedentIsMentionSpeaker(Document document,
                                                   Mention mention, Mention ant, Dictionaries dict) {
    if(document.speakerPairs.contains(new Pair<>(mention.mentionID, ant.mentionID))) {
      return true;
    }

    if(antecedentMatchesMentionSpeakerAnnotation(mention, ant, document)) {
      return true;
    }
    return false;
  }

  /** Is the speaker for mention the same entity as the ant entity? */
  public static boolean antecedentMatchesMentionSpeakerAnnotation(Mention mention, Mention ant) {
    return antecedentMatchesMentionSpeakerAnnotation(mention, ant, null);
  }

  public static final Pattern WHITESPACE_PATTERN = Pattern.compile(" +");

  /**
   * The antecedent matches the speaker annotation found in the mention
   */
  public static boolean antecedentMatchesMentionSpeakerAnnotation(Mention mention, Mention ant, Document document) {
    if (mention.headWord == null) {
      return false;
    }
    String speaker = mention.headWord.get(CoreAnnotations.SpeakerAnnotation.class);

    if (speaker == null) {
      return false;
    }

    SpeakerInfo speakerInfo = (document != null)? document.getSpeakerInfo(speaker):null;
    if (speakerInfo != null) {
      return (mentionMatchesSpeaker(ant, speakerInfo, false));
    }

    // CAN'T get speaker info - take alternate path

    // We optimize a little here: if the name has no spaces, which is
    // the common case, then it is unnecessarily expensive to call
    // regex split
    if (speaker.indexOf(" ") >= 0) {
      // Perhaps we could optimize this, too, but that would be trickier
      for (String s : WHITESPACE_PATTERN.split(speaker)) {
        if (ant.headString.equalsIgnoreCase(s)) return true;
      }
    } else {
      if (ant.headString.equalsIgnoreCase(speaker)) return true;
    }
    return false;
  }

  public static boolean mentionMatchesSpeaker(Mention mention, SpeakerInfo speakerInfo, boolean strictMatch) {
    // Got info about this speaker
    if (mention.speakerInfo != null) {
      if (mention.speakerInfo == speakerInfo) return true;
    }
    if (speakerInfo.containsMention(mention)) return true;
    if (strictMatch) {
      String spkstr = SpeakerInfo.WHITESPACE_PATTERN.matcher(speakerInfo.getSpeakerName()).replaceAll("");
      String mstr = SpeakerInfo.WHITESPACE_PATTERN.matcher(mention.spanToString()).replaceAll("");
      if (spkstr.equalsIgnoreCase(mstr)) {
        speakerInfo.addMention(mention);
        return true;
      }
    } else {
      // speaker strings are pre-split
      for (String s : speakerInfo.getSpeakerNameStrings()) {
        if (mention.headString.equalsIgnoreCase(s)) {
          speakerInfo.addMention(mention);
          return true;
        }
      }
      if (speakerInfo.getSpeakerDesc() != null) {
        String spkDescStr = SpeakerInfo.WHITESPACE_PATTERN.matcher(speakerInfo.getSpeakerDesc()).replaceAll("");
        String mstr = SpeakerInfo.WHITESPACE_PATTERN.matcher(mention.spanToString()).replaceAll("");
        if (spkDescStr.equalsIgnoreCase(mstr)) return true;
      }
    }
    return false;
  }

  public static boolean entityPersonDisagree(Document document, Mention m, Mention ant, Dictionaries dict) {
    boolean sameSpeaker = entitySameSpeaker(document, m, ant);

    if(sameSpeaker && m.person!=ant.person) {
      if ((m.person == Person.IT && ant.person == Person.THEY)
           || (m.person == Person.THEY && ant.person == Person.IT) || (m.person == Person.THEY && ant.person == Person.THEY)) {
        return false;
      } else if (m.person != Person.UNKNOWN && ant.person != Person.UNKNOWN)
        return true;
    }
    if(sameSpeaker) {
      if(!ant.isPronominal()) {
        if(m.person==Person.I || m.person==Person.WE || m.person==Person.YOU) return true;
      } else if(!m.isPronominal()) {
        if(ant.person==Person.I || ant.person==Person.WE || ant.person==Person.YOU) return true;
      }
    }
    if(m.person==Person.YOU && m != ant && ant.appearEarlierThan(m)) {
      assert !m.appearEarlierThan(ant);
      int mUtter = m.headWord.get(CoreAnnotations.UtteranceAnnotation.class);
      if (document.speakers.containsKey(mUtter - 1)) {
        String previousSpeaker = document.speakers.get(mUtter - 1);
        int previousSpeakerCorefClusterID = getSpeakerClusterId(document, previousSpeaker);
        if (previousSpeakerCorefClusterID < 0) {
          return true;
        }
        if (ant.corefClusterID != previousSpeakerCorefClusterID && ant.person != Person.I) {
          return true;
        }
      } else {
        return true;
      }
    } else if (ant.person==Person.YOU && m != ant && m.appearEarlierThan(ant)) {
      assert !(ant.appearEarlierThan(m));
      int aUtter = ant.headWord.get(CoreAnnotations.UtteranceAnnotation.class);
      if (document.speakers.containsKey(aUtter - 1)) {
        String previousSpeaker = document.speakers.get(aUtter - 1);
        int previousSpeakerCorefClusterID = getSpeakerClusterId(document, previousSpeaker);
        if (previousSpeakerCorefClusterID < 0) {
          return true;
        }
        if (m.corefClusterID != previousSpeakerCorefClusterID && m.person != Person.I) {
          return true;
        }
      } else {
        return true;
      }
    }
    return false;
  }

  /** Do the mentions share the same speaker? */
  public static boolean entitySameSpeaker(Document document, Mention m, Mention ant) {
    String mSpeakerStr = m.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
    if (mSpeakerStr == null) {
      return false;
    }
    String antSpeakerStr = ant.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
    if (antSpeakerStr == null) {
      return false;
    }

    // Speakers are the same if the speaker strings are the same (most common case?)
    if (mSpeakerStr.equals(antSpeakerStr)) {
      return true;
    } else {
      // Speakers are also the same if they map to the same cluster id...
      int mSpeakerClusterID = getSpeakerClusterId(document, mSpeakerStr);
      int antSpeakerClusterID = getSpeakerClusterId(document, antSpeakerStr);
      if (mSpeakerClusterID >= 0 && antSpeakerClusterID >= 0) {
        return (mSpeakerClusterID == antSpeakerClusterID);
      } else {
        return false;
      }
    }
  }

  /**
   * Given the name of a speaker, returns the coref cluster id it belongs to (-1 if no cluster)
   * @param document The document to search in
   * @param speakerString The name to search for
   * @return cluster id
   */
  public static int getSpeakerClusterId(Document document, String speakerString) {
    int speakerClusterId = -1;
    // try looking up cluster id from speaker info
    SpeakerInfo speakerInfo = null;
    if (speakerString != null) {
      speakerInfo = document.getSpeakerInfo(speakerString);
      if (speakerInfo != null) {
        speakerClusterId = speakerInfo.getCorefClusterId();
      }
    }
    if (speakerClusterId < 0 && speakerString != null && NumberMatchingRegex.isDecimalInteger(speakerString)) {
      // speakerString is number so is mention id
      try {
        int speakerMentionId = Integer.parseInt(speakerString);
        Mention mention = document.allPredictedMentions.get(speakerMentionId);
        if (mention != null) {
          speakerClusterId = mention.corefClusterID;
          if (speakerInfo != null) speakerInfo.addMention(mention);
        }
      } catch (Exception e) {
      }
    }
    return speakerClusterId;
  }

  public static boolean entitySubjectObject(Mention m1, Mention m2) {
    if(m1.sentNum != m2.sentNum) return false;
    if(m1.dependingVerb==null || m2.dependingVerb ==null) return false;
    if (m1.dependingVerb == m2.dependingVerb
         && ((m1.isSubject && (m2.isDirectObject || m2.isIndirectObject || m2.isPrepositionObject))
              || (m2.isSubject && (m1.isDirectObject || m1.isIndirectObject || m1.isPrepositionObject)))) {
      return true;
    }
    return false;
  }

  // Return true if the two mentions are less than n mentions apart in the same sent
  public static boolean entityTokenDistance(Mention m1, Mention m2) {
    if( (m2.sentNum == m1.sentNum) && (m1.startIndex - m2.startIndex < 6) ) return true;
    return false;
  }

  // COREF_DICT strict: all the mention pairs between the two clusters must match in the dict
  public static boolean entityClusterAllCorefDictionary(CorefCluster menCluster, CorefCluster antCluster,
      Dictionaries dict, int dictColumn, int freq){
    boolean ret = false;
    for(Mention men : menCluster.getCorefMentions()){
      if(men.isPronominal()) continue;
      for(Mention ant : antCluster.getCorefMentions()){
        if(ant.isPronominal() || men.headWord.lemma().equals(ant.headWord.lemma())) continue;
        if(entityCorefDictionary(men, ant, dict, dictColumn, freq)){
          ret = true;
        } else {
          return false;
        }
      }
    }
    return ret;
  }

   // COREF_DICT pairwise: the two mentions match in the dict
   public static boolean entityCorefDictionary(Mention men, Mention ant, Dictionaries dict, int dictVersion, int freq){

     Pair<String, String> mention_pair = new Pair<>(
             men.getSplitPattern()[dictVersion - 1].toLowerCase(),
             ant.getSplitPattern()[dictVersion - 1].toLowerCase());

     int high_freq = -1;
     if(dictVersion == 1){
       high_freq = 75;
     } else if(dictVersion == 2){
       high_freq = 16;
     } else if(dictVersion == 3){
       high_freq = 16;
     } else if(dictVersion == 4){
       high_freq = 16;
     }

     if(dict.corefDict.get(dictVersion-1).getCount(mention_pair) > high_freq) return true;

     if(dict.corefDict.get(dictVersion-1).getCount(mention_pair) > freq){
         if(dict.corefDictPMI.getCount(mention_pair) > 0.18) return true;
         if(!dict.corefDictPMI.containsKey(mention_pair)) return true;
     }
     return false;
   }

   public static boolean contextIncompatible(Mention men, Mention ant, Dictionaries dict) {
     String antHead = ant.headWord.word();
     if ( (ant.mentionType == MentionType.PROPER)
           && ant.sentNum != men.sentNum
           && !isContextOverlapping(ant,men)
           && dict.NE_signatures.containsKey(antHead)) {
       IntCounter<String> ranks = Counters.toRankCounter(dict.NE_signatures.get(antHead));
       List<String> context;
       if (!men.getPremodifierContext().isEmpty()) {
         context = men.getPremodifierContext();
       } else {
         context = men.getContext();
       }
       if (!context.isEmpty()) {
         int highestRank = 100000;
         for (String w: context) {
           if (ranks.containsKey(w) && ranks.getIntCount(w) < highestRank) {
             highestRank = ranks.getIntCount(w);
           }
           // check in the other direction
           if (dict.NE_signatures.containsKey(w)) {
             IntCounter<String> reverseRanks = Counters.toRankCounter(dict.NE_signatures.get(w));
             if (reverseRanks.containsKey(antHead) && reverseRanks.getIntCount(antHead) < highestRank) {
               highestRank = reverseRanks.getIntCount(antHead);
             }
           }
         }
         if (highestRank > 10) return true;
       }
     }
     return false;
   }

   public static boolean sentenceContextIncompatible(Mention men, Mention ant, Dictionaries dict) {
     if ( (ant.mentionType != MentionType.PROPER)
          && (ant.sentNum != men.sentNum)
          && (men.mentionType != MentionType.PROPER)
          && !isContextOverlapping(ant,men)) {
       List<String> context1 = !ant.getPremodifierContext().isEmpty() ? ant.getPremodifierContext() : ant.getContext();
       List<String> context2 = !men.getPremodifierContext().isEmpty() ? men.getPremodifierContext() : men.getContext();
       if (!context1.isEmpty() && !context2.isEmpty()) {
         int highestRank = 100000;
         for (String w1: context1) {
           for (String w2: context2) {
             // check the forward direction
             if (dict.NE_signatures.containsKey(w1)) {
               IntCounter<String> ranks = Counters.toRankCounter(dict.NE_signatures.get(w1));
               if (ranks.containsKey(w2) && ranks.getIntCount(w2) < highestRank) {
                 highestRank = ranks.getIntCount(w2);
               }
             }
             // check in the other direction
             if (dict.NE_signatures.containsKey(w2)) {
               IntCounter<String> reverseRanks = Counters.toRankCounter(dict.NE_signatures.get(w2));
               if (reverseRanks.containsKey(w1) && reverseRanks.getIntCount(w1) < highestRank) {
                 highestRank = reverseRanks.getIntCount(w1);
               }
             }
           }
         }
         if (highestRank > 10) return true;
       }
     }
     return false;
   }

   private static boolean isContextOverlapping(Mention m1, Mention m2) {
     Set<String> context1 = Generics.newHashSet();
     Set<String> context2 = Generics.newHashSet();
     context1.addAll(m1.getContext());
     context2.addAll(m2.getContext());
     return Sets.intersects(context1, context2);
   }


}
