package edu.stanford.nlp.hcoref.sieve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.CorefPrinter;
import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.Rules;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Dictionaries.Animacy;
import edu.stanford.nlp.hcoref.data.Dictionaries.Gender;
import edu.stanford.nlp.hcoref.data.Dictionaries.MentionType;
import edu.stanford.nlp.hcoref.data.Dictionaries.Number;
import edu.stanford.nlp.hcoref.data.Dictionaries.Person;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Document.DocType;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.hcoref.rf.RandomForest;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

public class RFSieve extends Sieve {
  
  private static final long serialVersionUID = -4090017054885920527L;
  
  // for RF sieve
  public RandomForest rf;
  
  /** the probability threshold for merging two mentions */
  public double thresMerge;
  
  // constructor for RF sieve
  public RFSieve(RandomForest rf, Properties props, String sievename) {
    super(props, sievename);
    this.rf = rf;
    this.props = props;
    this.classifierType = ClassifierType.RF;
  }

  public void findCoreferentAntecedent(Mention m, int mIdx, Document document, Dictionaries dict, Properties props, StringBuilder sbLog) throws Exception {
    int sentIdx = m.sentNum;

    Counter<Integer> probs = new ClassicCounter<Integer>();  
    
    int mentionDist = 0;
    for(int sentDist=0 ; sentDist <= Math.min(this.maxSentDist, sentIdx) ; sentDist++) {
      List<Mention> candidates = getOrderedAntecedents(m, sentIdx-sentDist, mIdx, document.predictedMentions, dict);
      

      for(Mention candidate : candidates) {
        if(skipForAnalysis(candidate, m, props)) continue;
        if(candidate == m) continue;
        if(!aType.contains(candidate.mentionType)) continue;
        if(m.mentionType == MentionType.PRONOMINAL) {
          if(!matchedMentionType(m, mTypeStr)) continue;
          if(!matchedMentionType(candidate, aTypeStr)) continue;
        }
        
        if(sentDist==0 && m.appearEarlierThan(candidate)) continue;   // ignore cataphora
        mentionDist++;

        RVFDatum<Boolean, String> datum = extractDatum(m, candidate, document, mentionDist, dict, props, sievename);
        
        double probTrue = 0;
        if(this.classifierType == ClassifierType.RF) {
          probTrue = this.rf.probabilityOfTrue(datum);
        }
        
        probs.setCount(candidate.mentionID, probTrue);
      }
    }
    
    if(CorefProperties.debug(props)) {
      sbLog.append(CorefPrinter.printErrorLog(m, document, probs, mIdx, dict, this));
    }
    
    if(probs.size() > 0 && Counters.max(probs) > this.thresMerge) {
      // merge highest prob candidate
      int antID = Counters.argmax(probs);
      
      Sieve.merge(document, m.mentionID, antID);
    }
  }
  public static RVFDatum<Boolean, String> extractDatum(Mention m, Mention candidate, 
      Document document, int mentionDist, Dictionaries dict, Properties props, String sievename) {
    try {
      
      boolean label = (document.goldMentions==null)? false : document.isCoref(m, candidate);
      Counter<String> features = new ClassicCounter<String>();
      CorefCluster mC = document.corefClusters.get(m.corefClusterID);
      CorefCluster aC = document.corefClusters.get(candidate.corefClusterID);
      
      CoreLabel mFirst = m.sentenceWords.get(m.startIndex);
      CoreLabel mLast = m.sentenceWords.get(m.endIndex-1);
      CoreLabel mPreceding = (m.startIndex>0)? m.sentenceWords.get(m.startIndex-1) : null;
      CoreLabel mFollowing = (m.endIndex < m.sentenceWords.size())? m.sentenceWords.get(m.endIndex) : null;
      
      CoreLabel aFirst = candidate.sentenceWords.get(candidate.startIndex);
      CoreLabel aLast = candidate.sentenceWords.get(candidate.endIndex-1);
      CoreLabel aPreceding = (candidate.startIndex>0)? candidate.sentenceWords.get(candidate.startIndex-1) : null;
      CoreLabel aFollowing = (candidate.endIndex < candidate.sentenceWords.size())? candidate.sentenceWords.get(candidate.endIndex) : null;
      
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    basic features: distance, doctype, mention length, roles ////////////
      ////////////////////////////////////////////////////////////////////////////////
      if(CorefProperties.useBasicFeatures(props, sievename)) {
        int sentDist = m.sentNum - candidate.sentNum;
        features.incrementCount("SENTDIST", sentDist);
        features.incrementCount("MENTIONDIST", mentionDist);
        
        int minSentDist = sentDist;
        for(Mention a : aC.corefMentions) {
          minSentDist = Math.min(minSentDist, Math.abs(m.sentNum - a.sentNum));
        }
        features.incrementCount("MINSENTDIST", minSentDist);

        // When they are in the same sentence, divides a sentence into clauses and add such feature
        if(CorefProperties.useConstituencyTree(props)) {
          if(m.sentNum == candidate.sentNum) {
            int clauseCount = 0;
            Tree tree = m.contextParseTree;
            Tree current = m.mentionSubTree;
            
            while(true){
              current = current.ancestor(1, tree);
              if(current.label().value().startsWith("S")) {
                clauseCount++;
              }
              if(current.dominates(candidate.mentionSubTree)) break;
              if(current.label().value().equals("ROOT") || current.ancestor(1, tree)==null) break;
            }
            features.incrementCount("CLAUSECOUNT", clauseCount);
          }
        }
        
        if(document.docType == DocType.CONVERSATION) features.incrementCount("B-DOCTYPE-"+document.docType);
        if(m.headWord.get(SpeakerAnnotation.class).equalsIgnoreCase("PER0")) {
          features.incrementCount("B-SPEAKER-PER0");
        }
        
        if(document.docInfo!=null && document.docInfo.containsKey("DOC_ID")) {
          features.incrementCount("B-DOCSOURCE-"+document.docInfo.get("DOC_ID").split("/")[1]);
        }
        
        features.incrementCount("M-LENGTH", m.originalSpan.size());
        features.incrementCount("A-LENGTH", candidate.originalSpan.size());
        if(m.originalSpan.size() < candidate.originalSpan.size()) features.incrementCount("B-A-ISLONGER");
        features.incrementCount("A-SIZE", aC.getCorefMentions().size());
        features.incrementCount("M-SIZE", mC.getCorefMentions().size());

        String antRole = "A-NOROLE";
        String mRole = "M-NOROLE";
        
        if(m.isSubject) mRole = "M-SUBJ";
        if(m.isDirectObject) mRole = "M-DOBJ";
        if(m.isIndirectObject) mRole = "M-IOBJ";
        if(m.isPrepositionObject) mRole = "M-POBJ";
        
        if(candidate.isSubject) antRole = "A-SUBJ";
        if(candidate.isDirectObject) antRole = "A-DOBJ";
        if(candidate.isIndirectObject) antRole = "A-IOBJ";
        if(candidate.isPrepositionObject) antRole = "A-POBJ";
        
        features.incrementCount("B-"+mRole);
        features.incrementCount("B-"+antRole);
        features.incrementCount("B-"+antRole+"-"+mRole);

        if(CorefProperties.combineObjectRoles(props, sievename)) {
          // combine all objects
          if(m.isDirectObject || m.isIndirectObject || m.isPrepositionObject
              || candidate.isDirectObject || candidate.isIndirectObject || candidate.isPrepositionObject) {
            if(m.isDirectObject || m.isIndirectObject || m.isPrepositionObject) {
              mRole = "M-OBJ";
              features.incrementCount("B-M-OBJ");
            }
            if(candidate.isDirectObject || candidate.isIndirectObject || candidate.isPrepositionObject) {
              antRole = "A-OBJ";
              features.incrementCount("B-A-OBJ");
            }
            features.incrementCount("B-"+antRole+"-"+mRole);
          }
        }
        
        if(mFirst.word().toLowerCase().matches("a|an")) {
          features.incrementCount("B-M-START-WITH-INDEFINITE");
        }
        if(aFirst.word().toLowerCase().matches("a|an")) {
          features.incrementCount("B-A-START-WITH-INDEFINITE");
        }
        if(mFirst.word().equalsIgnoreCase("the")) {
          features.incrementCount("B-M-START-WITH-DEFINITE");
        }
        if(aFirst.word().equalsIgnoreCase("the")) {
          features.incrementCount("B-A-START-WITH-DEFINITE");
        }
        
        if(dict.indefinitePronouns.contains(m.lowercaseNormalizedSpanString())) {
          features.incrementCount("B-M-INDEFINITE-PRONOUN");
        }
        if(dict.indefinitePronouns.contains(candidate.lowercaseNormalizedSpanString())) {
          features.incrementCount("B-A-INDEFINITE-PRONOUN");
        }
        if(dict.indefinitePronouns.contains(mFirst.word().toLowerCase())) {
          features.incrementCount("B-M-INDEFINITE-ADJ");
        }
        if(dict.indefinitePronouns.contains(aFirst.word().toLowerCase())){
          features.incrementCount("B-A-INDEFINITE-ADJ");
        }
        if(dict.reflexivePronouns.contains(m.headString)) {
          features.incrementCount("B-M-REFLEXIVE");
        }
        if(dict.reflexivePronouns.contains(candidate.headString)) {
          features.incrementCount("B-A-REFLEXIVE");
        }
        
        if(m.headIndex == m.endIndex-1) features.incrementCount("B-M-HEADEND");
        if(m.headIndex < m.endIndex-1) {
          CoreLabel headnext = m.sentenceWords.get(m.headIndex+1);
          if(headnext.word().matches("that|,") || headnext.tag().startsWith("W")) {
            features.incrementCount("B-M-HASPOSTPHRASE");
            if(mFirst.tag().equals("DT") && mFirst.word().toLowerCase().matches("the|this|these|those")) features.incrementCount("B-M-THE-HASPOSTPHRASE");
            else if(mFirst.word().toLowerCase().matches("a|an")) features.incrementCount("B-M-INDEFINITE-HASPOSTPHRASE");
          }
        }

        // shape feature from Bjorkelund & Kuhn
        StringBuilder sb = new StringBuilder();
        List<Mention> sortedMentions = new ArrayList<Mention>(aC.corefMentions.size());
        sortedMentions.addAll(aC.corefMentions);
        Collections.sort(sortedMentions, new CorefChain.MentionComparator());
        for(Mention a : sortedMentions) {
          sb.append(a.mentionType).append("-");
        }
        features.incrementCount("B-A-SHAPE-"+sb.toString());
        
        sb = new StringBuilder();
        sortedMentions = new ArrayList<Mention>(mC.corefMentions.size());
        sortedMentions.addAll(mC.corefMentions);
        Collections.sort(sortedMentions, new CorefChain.MentionComparator());
        for(Mention men : sortedMentions) {
          sb.append(men.mentionType).append("-");
        }
        features.incrementCount("B-M-SHAPE-"+sb.toString());
        
        if(CorefProperties.useConstituencyTree(props)) {
          sb = new StringBuilder();
          Tree mTree = m.contextParseTree;
          Tree mHead = mTree.getLeaves().get(m.headIndex).ancestor(1, mTree);
          for(Tree node : mTree.pathNodeToNode(mHead, mTree)){
            sb.append(node.value()).append("-");
            if(node.value().equals("S")) break;
          }
          features.incrementCount("B-M-SYNPATH-"+sb.toString());

          sb = new StringBuilder();
          Tree aTree = candidate.contextParseTree;
          Tree aHead = aTree.getLeaves().get(candidate.headIndex).ancestor(1, aTree);
          for(Tree node : aTree.pathNodeToNode(aHead, aTree)){
            sb.append(node.value()).append("-");
            if(node.value().equals("S")) break;
          }
          features.incrementCount("B-A-SYNPATH-"+sb.toString());
        }
        
        
        features.incrementCount("A-FIRSTAPPEAR", aC.representative.sentNum);
        features.incrementCount("M-FIRSTAPPEAR", mC.representative.sentNum);
        int docSize = document.predictedMentions.size();   // document size in # of sentences
        features.incrementCount("A-FIRSTAPPEAR-NORMALIZED", aC.representative.sentNum/docSize);
        features.incrementCount("M-FIRSTAPPEAR-NORMALIZED", mC.representative.sentNum/docSize);
      }
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    mention detection features                               ////////////
      ////////////////////////////////////////////////////////////////////////////////
      if(CorefProperties.useMentionDetectionFeatures(props, sievename)) {
        // bare plurals
        if(m.originalSpan.size()==1 && m.headWord.tag().equals("NNS")) features.incrementCount("B-M-BAREPLURAL");
        if(candidate.originalSpan.size()==1 && candidate.headWord.tag().equals("NNS")) features.incrementCount("B-A-BAREPLURAL");
        
        // pleonastic it
        if(RuleBasedCorefMentionFinder.isPleonastic(m, m.contextParseTree) 
            || RuleBasedCorefMentionFinder.isPleonastic(candidate, candidate.contextParseTree)) {
          features.incrementCount("B-PLEONASTICIT");
        }
        
        // quantRule
        if(dict.quantifiers.contains(mFirst.word().toLowerCase(Locale.ENGLISH))) features.incrementCount("B-M-QUANTIFIER");
        if(dict.quantifiers.contains(aFirst.word().toLowerCase(Locale.ENGLISH))) features.incrementCount("B-A-QUANTIFIER");
        
        // starts with negation 
        if(mFirst.word().toLowerCase(Locale.ENGLISH).matches("none|no|nothing|not")
            || aFirst.word().toLowerCase(Locale.ENGLISH).matches("none|no|nothing|not")) {
          features.incrementCount("B-NEGATIVE-START");
        }
        
        // parititive rule
        if(RuleBasedCorefMentionFinder.partitiveRule(m, m.sentenceWords, dict)) features.incrementCount("B-M-PARTITIVE");
        if(RuleBasedCorefMentionFinder.partitiveRule(candidate, candidate.sentenceWords, dict)) features.incrementCount("B-A-PARTITIVE");
        
        // %
        if(m.headString.equals("%")) features.incrementCount("B-M-HEAD%");
        if(candidate.headString.equals("%")) features.incrementCount("B-A-HEAD%");
        
        // adjective form of nations
        if(dict.isAdjectivalDemonym(m.spanToString())) features.incrementCount("B-M-ADJ-DEMONYM");
        if(dict.isAdjectivalDemonym(candidate.spanToString())) features.incrementCount("B-A-ADJ-DEMONYM");
        
        // ends with "etc."
        if(m.lowercaseNormalizedSpanString().endsWith("etc.")) features.incrementCount("B-M-ETC-END");
        if(candidate.lowercaseNormalizedSpanString().endsWith("etc.")) features.incrementCount("B-A-ETC-END");
        
      }

      ////////////////////////////////////////////////////////////////////////////////
      ///////    attributes, attributes agree                             ////////////
      ////////////////////////////////////////////////////////////////////////////////
      features.incrementCount("B-M-NUMBER-"+m.number);
      features.incrementCount("B-A-NUMBER-"+candidate.number);
      features.incrementCount("B-M-GENDER-"+m.gender);
      features.incrementCount("B-A-GENDER-"+candidate.gender);
      features.incrementCount("B-M-ANIMACY-"+m.animacy);
      features.incrementCount("B-A-ANIMACY-"+candidate.animacy);
      features.incrementCount("B-M-PERSON-"+m.person);
      features.incrementCount("B-A-PERSON-"+candidate.person);
      features.incrementCount("B-M-NETYPE-"+m.nerString);
      features.incrementCount("B-A-NETYPE-"+candidate.nerString);
      
      features.incrementCount("B-BOTH-NUMBER-"+candidate.number+"-"+m.number);
      features.incrementCount("B-BOTH-GENDER-"+candidate.gender+"-"+m.gender);
      features.incrementCount("B-BOTH-ANIMACY-"+candidate.animacy+"-"+m.animacy);
      features.incrementCount("B-BOTH-PERSON-"+candidate.person+"-"+m.person);
      features.incrementCount("B-BOTH-NETYPE-"+candidate.nerString+"-"+m.nerString);
      
  
      Set<Number> mcNumber = Generics.newHashSet();
      for(Number n : mC.numbers) {
        features.incrementCount("B-MC-NUMBER-"+n);
        mcNumber.add(n);
      }
      if(mcNumber.size()==1) {
        features.incrementCount("B-MC-CLUSTERNUMBER-"+mcNumber.iterator().next());
      } else {
        mcNumber.remove(Number.UNKNOWN);
        if(mcNumber.size() == 1) features.incrementCount("B-MC-CLUSTERNUMBER-"+mcNumber.iterator().next());
        else features.incrementCount("B-MC-CLUSTERNUMBER-CONFLICT");
      }
      
      Set<Gender> mcGender = Generics.newHashSet();
      for(Gender g : mC.genders) {
        features.incrementCount("B-MC-GENDER-"+g);
        mcGender.add(g);
      }
      if(mcGender.size()==1) {
        features.incrementCount("B-MC-CLUSTERGENDER-"+mcGender.iterator().next());
      } else {
        mcGender.remove(Gender.UNKNOWN);
        if(mcGender.size() == 1) features.incrementCount("B-MC-CLUSTERGENDER-"+mcGender.iterator().next());
        else features.incrementCount("B-MC-CLUSTERGENDER-CONFLICT");
      }
      
      Set<Animacy> mcAnimacy = Generics.newHashSet();
      for(Animacy a : mC.animacies) {
        features.incrementCount("B-MC-ANIMACY-"+a);
        mcAnimacy.add(a);
      }
      if(mcAnimacy.size()==1) {
        features.incrementCount("B-MC-CLUSTERANIMACY-"+mcAnimacy.iterator().next());
      } else {
        mcAnimacy.remove(Animacy.UNKNOWN);
        if(mcAnimacy.size() == 1) features.incrementCount("B-MC-CLUSTERANIMACY-"+mcAnimacy.iterator().next());
        else features.incrementCount("B-MC-CLUSTERANIMACY-CONFLICT");
      }
      
      Set<String> mcNER = Generics.newHashSet();
      for(String t : mC.nerStrings) {
        features.incrementCount("B-MC-NETYPE-"+t);
        mcNER.add(t);
      }
      if(mcNER.size()==1) {
        features.incrementCount("B-MC-CLUSTERNETYPE-"+mcNER.iterator().next());
      } else {
        mcNER.remove("O");
        if(mcNER.size() == 1) features.incrementCount("B-MC-CLUSTERNETYPE-"+mcNER.iterator().next());
        else features.incrementCount("B-MC-CLUSTERNETYPE-CONFLICT");
      }
      
      Set<Number> acNumber = Generics.newHashSet();
      for(Number n : aC.numbers) {
        features.incrementCount("B-AC-NUMBER-"+n);
        acNumber.add(n);
      }
      if(acNumber.size()==1) {
        features.incrementCount("B-AC-CLUSTERNUMBER-"+acNumber.iterator().next());
      } else {
        acNumber.remove(Number.UNKNOWN);
        if(acNumber.size() == 1) features.incrementCount("B-AC-CLUSTERNUMBER-"+acNumber.iterator().next());
        else features.incrementCount("B-AC-CLUSTERNUMBER-CONFLICT");
      }
      
      Set<Gender> acGender = Generics.newHashSet();
      for(Gender g : aC.genders) {
        features.incrementCount("B-AC-GENDER-"+g);
        acGender.add(g);
      }
      if(acGender.size()==1) {
        features.incrementCount("B-AC-CLUSTERGENDER-"+acGender.iterator().next());
      } else {
        acGender.remove(Gender.UNKNOWN);
        if(acGender.size() == 1) features.incrementCount("B-AC-CLUSTERGENDER-"+acGender.iterator().next());
        else features.incrementCount("B-AC-CLUSTERGENDER-CONFLICT");
      }
      
      Set<Animacy> acAnimacy = Generics.newHashSet();
      for(Animacy a : aC.animacies) {
        features.incrementCount("B-AC-ANIMACY-"+a);
        acAnimacy.add(a);
      }
      if(acAnimacy.size()==1) {
        features.incrementCount("B-AC-CLUSTERANIMACY-"+acAnimacy.iterator().next());
      } else {
        acAnimacy.remove(Animacy.UNKNOWN);
        if(acAnimacy.size() == 1) features.incrementCount("B-AC-CLUSTERANIMACY-"+acAnimacy.iterator().next());
        else features.incrementCount("B-AC-CLUSTERANIMACY-CONFLICT");
      }
      
      Set<String> acNER = Generics.newHashSet();
      for(String t : aC.nerStrings) {
        features.incrementCount("B-AC-NETYPE-"+t);
        acNER.add(t);
      }
      if(acNER.size()==1) {
        features.incrementCount("B-AC-CLUSTERNETYPE-"+acNER.iterator().next());
      } else {
        acNER.remove("O");
        if(acNER.size() == 1) features.incrementCount("B-AC-CLUSTERNETYPE-"+acNER.iterator().next());
        else features.incrementCount("B-AC-CLUSTERNETYPE-CONFLICT");
      }
      
      
      if(m.numbersAgree(candidate)) features.incrementCount("B-NUMBER-AGREE");
      if(m.gendersAgree(candidate)) features.incrementCount("B-GENDER-AGREE");
      if(m.animaciesAgree(candidate)) features.incrementCount("B-ANIMACY-AGREE");
      if(Rules.entityAttributesAgree(mC, aC)) features.incrementCount("B-ATTRIBUTES-AGREE");
      if(Rules.entityPersonDisagree(document, m, candidate, dict)) features.incrementCount("B-PERSON-DISAGREE");
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    dcoref rules                                             ////////////
      ////////////////////////////////////////////////////////////////////////////////
      if(CorefProperties.useDcorefRules(props, sievename)) {
        if(Rules.entityIWithinI(m, candidate, dict)) features.incrementCount("B-i-within-i");
        if(Rules.antecedentIsMentionSpeaker(document, m, candidate, dict)) features.incrementCount("B-ANT-IS-SPEAKER");
        if(Rules.entitySameSpeaker(document, m, candidate)) features.incrementCount("B-SAME-SPEAKER");
        if(Rules.entitySubjectObject(m, candidate)) features.incrementCount("B-SUBJ-OBJ");
        for(Mention a : aC.corefMentions) {
          if(Rules.entitySubjectObject(m, a)) features.incrementCount("B-CLUSTER-SUBJ-OBJ");
        }
        
        if(Rules.entityPersonDisagree(document, m, candidate, dict) 
            && Rules.entitySameSpeaker(document, m, candidate)) features.incrementCount("B-PERSON-DISAGREE-SAME-SPEAKER");
        
        if(Rules.entityIWithinI(mC, aC, dict)) features.incrementCount("B-ENTITY-IWITHINI");
        if(Rules.antecedentMatchesMentionSpeakerAnnotation(m, candidate, document)) features.incrementCount("B-ANT-IS-SPEAKER-OF-MENTION");
        
        Set<MentionType> mType = CorefProperties.getMentionType(props, sievename);
        if(mType.contains(MentionType.PROPER) || mType.contains(MentionType.NOMINAL)) {
          if(m.headString.equals(candidate.headString)) features.incrementCount("B-HEADMATCH");
          if(Rules.entityHeadsAgree(mC, aC, m, candidate, dict)) features.incrementCount("B-HEADSAGREE");
          if(Rules.entityExactStringMatch(mC, aC, dict, document.roleSet)) features.incrementCount("B-EXACTSTRINGMATCH");
          if(Rules.entityHaveExtraProperNoun(m, candidate, new HashSet<String>())) features.incrementCount("B-HAVE-EXTRA-PROPER-NOUN");
          if(Rules.entityBothHaveProper(mC, aC)) features.incrementCount("B-BOTH-HAVE-PROPER");
          if(Rules.entityHaveDifferentLocation(m, candidate, dict)) features.incrementCount("B-HAVE-DIFF-LOC");
          if(Rules.entityHaveIncompatibleModifier(mC, aC)) features.incrementCount("B-HAVE-INCOMPATIBLE-MODIFIER");
          if(Rules.entityIsAcronym(document, mC, aC)) features.incrementCount("B-IS-ACRONYM");
          if(Rules.entityIsApposition(mC, aC, m, candidate)) features.incrementCount("B-IS-APPOSITION");
          if(Rules.entityIsPredicateNominatives(mC, aC, m, candidate)) features.incrementCount("B-IS-PREDICATE-NOMINATIVES");
          if(Rules.entityIsRoleAppositive(mC, aC, m, candidate, dict)) features.incrementCount("B-IS-ROLE-APPOSITIVE");
          if(Rules.entityNumberInLaterMention(m, candidate)) features.incrementCount("B-NUMBER-IN-LATER");
          if(Rules.entityRelaxedExactStringMatch(mC, aC, m, candidate, dict, document.roleSet)) features.incrementCount("B-RELAXED-EXACT-STRING-MATCH");
          if(Rules.entityRelaxedHeadsAgreeBetweenMentions(mC, aC, m, candidate)) features.incrementCount("B-RELAXED-HEAD-AGREE");
          if(Rules.entitySameProperHeadLastWord(m, candidate)) features.incrementCount("B-SAME-PROPER-HEAD");
          if(Rules.entitySameProperHeadLastWord(mC, aC, m, candidate)) features.incrementCount("B-CLUSTER-SAME-PROPER-HEAD");
          if(Rules.entityWordsIncluded(mC, aC, m, candidate)) features.incrementCount("B-WORD-INCLUSION");
        }
        if(mType.contains(MentionType.LIST)) {
          features.incrementCount("NUM-LIST-", numEntitiesInList(m));
          if(m.spanToString().contains("two") || m.spanToString().contains("2") || m.spanToString().contains("both")) features.incrementCount("LIST-M-TWO");
          if(m.spanToString().contains("three") || m.spanToString().contains("3")) features.incrementCount("LIST-M-THREE");
          if(candidate.spanToString().contains("two") 
              || candidate.spanToString().contains("2") 
              || candidate.spanToString().contains("both")) {
            features.incrementCount("B-LIST-A-TWO");
          }
          if(candidate.spanToString().contains("three") 
              || candidate.spanToString().contains("3")) {
            features.incrementCount("B-LIST-A-THREE");
          }
        }
        
        if(mType.contains(MentionType.PRONOMINAL)) {
          if(dict.firstPersonPronouns.contains(m.headString)) features.incrementCount("B-M-I");
          if(dict.secondPersonPronouns.contains(m.headString)) features.incrementCount("B-M-YOU");
          if(dict.thirdPersonPronouns.contains(m.headString)) features.incrementCount("B-M-3RDPERSON");
          if(dict.possessivePronouns.contains(m.headString)) features.incrementCount("B-M-POSSESSIVE");
          if(dict.neutralPronouns.contains(m.headString)) features.incrementCount("B-M-NEUTRAL");
          if(dict.malePronouns.contains(m.headString)) features.incrementCount("B-M-MALE");
          if(dict.femalePronouns.contains(m.headString)) features.incrementCount("B-M-FEMALE");
          
          if(dict.firstPersonPronouns.contains(candidate.headString)) features.incrementCount("B-A-I");
          if(dict.secondPersonPronouns.contains(candidate.headString)) features.incrementCount("B-A-YOU");
          if(dict.thirdPersonPronouns.contains(candidate.headString)) features.incrementCount("B-A-3RDPERSON");
          if(dict.possessivePronouns.contains(candidate.headString)) features.incrementCount("B-A-POSSESSIVE");
          if(dict.neutralPronouns.contains(candidate.headString)) features.incrementCount("B-A-NEUTRAL");
          if(dict.malePronouns.contains(candidate.headString)) features.incrementCount("B-A-MALE");
          if(dict.femalePronouns.contains(candidate.headString)) features.incrementCount("B-A-FEMALE");
          
          features.incrementCount("B-M-GENERIC-"+m.generic);
          features.incrementCount("B-A-GENERIC-"+candidate.generic);

          if(CorefPrinter.dcorefPronounSieve.skipThisMention(document, m, mC, dict)) {
            features.incrementCount("B-SKIPTHISMENTION-true");
          }
          
          if(m.spanToString().equalsIgnoreCase("you") && mFollowing!=null && mFollowing.word().equalsIgnoreCase("know")) {
            features.incrementCount("B-YOUKNOW-PRECEDING-POS-" + ((mPreceding==null)? "NULL" : mPreceding.tag()) );
            features.incrementCount("B-YOUKNOW-PRECEDING-WORD-"+ ((mPreceding==null)? "NULL" : mPreceding.word().toLowerCase()) );
            CoreLabel nextword = (m.endIndex+1 < m.sentenceWords.size())? m.sentenceWords.get(m.endIndex+1) : null;
            features.incrementCount("B-YOUKNOW-FOLLOWING-POS-" + ((nextword==null)? "NULL" : nextword.tag()) );
            features.incrementCount("B-YOUKNOW-FOLLOWING-WORD-"+ ((nextword==null)? "NULL" : nextword.word().toLowerCase()) );
          }
          if(candidate.spanToString().equalsIgnoreCase("you") && aFollowing!=null && aFollowing.word().equalsIgnoreCase("know")) {
            features.incrementCount("B-YOUKNOW-PRECEDING-POS-" + ((aPreceding==null)? "NULL" : aPreceding.tag()) );
            features.incrementCount("B-YOUKNOW-PRECEDING-WORD-"+ ((aPreceding==null)? "NULL" : aPreceding.word().toLowerCase()) );
            CoreLabel nextword = (candidate.endIndex+1 < candidate.sentenceWords.size())? candidate.sentenceWords.get(candidate.endIndex+1) : null;
            features.incrementCount("B-YOUKNOW-FOLLOWING-POS-" + ((nextword==null)? "NULL" : nextword.tag()) );
            features.incrementCount("B-YOUKNOW-FOLLOWING-WORD-"+ ((nextword==null)? "NULL" : nextword.word().toLowerCase()) );
          }
        }
        
        // discourse match features
        if(m.person==Person.YOU && document.docType==DocType.ARTICLE && m.headWord.get(CoreAnnotations.SpeakerAnnotation.class).equals("PER0")) {
          features.incrementCount("B-DISCOURSE-M-YOU-GENERIC?");
        }
        if(candidate.generic && candidate.person==Person.YOU) features.incrementCount("B-DISCOURSE-A-YOU-GENERIC?");
        
        String mString = m.lowercaseNormalizedSpanString();
        String antString = candidate.lowercaseNormalizedSpanString();
        
        // I-I
        if(m.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString)
            && candidate.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString)
            && Rules.entitySameSpeaker(document, m, candidate)) {
          features.incrementCount("B-DISCOURSE-I-I-SAMESPEAKER");
        }
        
        // (speaker - I)
        if ((m.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString))
                && Rules.antecedentIsMentionSpeaker(document, m, candidate, dict)) {
          features.incrementCount("B-DISCOURSE-SPEAKER-I");
        }
        
        // (I - speaker)
        if ((candidate.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString))
                && Rules.antecedentIsMentionSpeaker(document, candidate, m, dict)) {
          features.incrementCount("B-DISCOURSE-I-SPEAKER");
        }
        // Can be iffy if more than two speakers... but still should be okay most of the time
        if (dict.secondPersonPronouns.contains(mString)
            && dict.secondPersonPronouns.contains(antString)
            && Rules.entitySameSpeaker(document, m, candidate)) {
          features.incrementCount("B-DISCOURSE-BOTH-YOU");
        }
        // previous I - you or previous you - I in two person conversation
        if (((m.person==Person.I && candidate.person==Person.YOU
            || (m.person==Person.YOU && candidate.person==Person.I))
            && (m.headWord.get(CoreAnnotations.UtteranceAnnotation.class)-candidate.headWord.get(CoreAnnotations.UtteranceAnnotation.class) == 1)
            && document.docType==DocType.CONVERSATION)) {
          features.incrementCount("B-DISCOURSE-I-YOU");
        }
        if (dict.reflexivePronouns.contains(m.headString) && Rules.entitySubjectObject(m, candidate)){
          features.incrementCount("B-DISCOURSE-REFLEXIVE");
        }
        if(m.person==Person.I && candidate.person==Person.I && !Rules.entitySameSpeaker(document, m, candidate)) {
          features.incrementCount("B-DISCOURSE-I-I-DIFFSPEAKER");
        }
        if(m.person==Person.YOU && candidate.person==Person.YOU && !Rules.entitySameSpeaker(document, m, candidate)) {
          features.incrementCount("B-DISCOURSE-YOU-YOU-DIFFSPEAKER");
        }
        if(m.person==Person.WE && candidate.person==Person.WE && !Rules.entitySameSpeaker(document, m, candidate)) {
          features.incrementCount("B-DISCOURSE-WE-WE-DIFFSPEAKER");
        }
      }
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    POS features                                             ////////////
      ////////////////////////////////////////////////////////////////////////////////
      if(CorefProperties.usePOSFeatures(props, sievename)) {
        features.incrementCount("B-LEXICAL-M-HEADPOS-"+m.headWord.tag());
        features.incrementCount("B-LEXICAL-A-HEADPOS-"+candidate.headWord.tag());
        features.incrementCount("B-LEXICAL-M-FIRSTPOS-"+mFirst.tag());
        features.incrementCount("B-LEXICAL-A-FIRSTPOS-"+aFirst.tag());
        features.incrementCount("B-LEXICAL-M-LASTPOS-"+mLast.tag());
        features.incrementCount("B-LEXICAL-A-LASTPOS-"+aLast.tag());
        
        features.incrementCount("B-LEXICAL-M-PRECEDINGPOS-"+ ((mPreceding==null)? "NULL" : mPreceding.tag()) );
        features.incrementCount("B-LEXICAL-A-PRECEDINGPOS-"+ ((aPreceding==null)? "NULL" : aPreceding.tag()) );
        features.incrementCount("B-LEXICAL-M-FOLLOWINGPOS-"+ ((mFollowing==null)? "NULL" : mFollowing.tag()) );
        features.incrementCount("B-LEXICAL-A-FOLLOWINGPOS-"+ ((aFollowing==null)? "NULL" : aFollowing.tag()) );
      }
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    lexical features                                         ////////////
      ////////////////////////////////////////////////////////////////////////////////
      if(CorefProperties.useLexicalFeatures(props, sievename)) {

        features.incrementCount("B-LEXICAL-M-HEADWORD-"+m.headString.toLowerCase());
        features.incrementCount("B-LEXICAL-A-HEADWORD-"+candidate.headString.toLowerCase());
        features.incrementCount("B-LEXICAL-M-FIRSTWORD-"+mFirst.word().toLowerCase());
        features.incrementCount("B-LEXICAL-A-FIRSTWORD-"+aFirst.word().toLowerCase());
        features.incrementCount("B-LEXICAL-M-LASTWORD-"+mLast.word().toLowerCase());
        features.incrementCount("B-LEXICAL-A-LASTWORD-"+aLast.word().toLowerCase());
        
        features.incrementCount("B-LEXICAL-M-PRECEDINGWORD-"+ ((mPreceding==null)? "NULL" : mPreceding.word().toLowerCase()) );
        features.incrementCount("B-LEXICAL-A-PRECEDINGWORD-"+ ((aPreceding==null)? "NULL" : aPreceding.word().toLowerCase()) );
        features.incrementCount("B-LEXICAL-M-FOLLOWINGWORD-"+ ((mFollowing==null)? "NULL" : mFollowing.word().toLowerCase()) );
        features.incrementCount("B-LEXICAL-A-FOLLOWINGWORD-"+ ((aFollowing==null)? "NULL" : aFollowing.word().toLowerCase()) );
        
        //extra headword, modifiers lexical features
        for(String mHead : mC.heads) {
          if(!aC.heads.contains(mHead)) features.incrementCount("B-LEXICAL-MC-EXTRAHEAD-"+mHead);
        }
        for(String mWord : mC.words) {
          if(!aC.words.contains(mWord)) features.incrementCount("B-LEXICAL-MC-EXTRAWORD-"+mWord);
        }
      }
      
      ////////////////////////////////////////////////////////////////////////////////
      ///////    word vector features                                     ////////////
      ////////////////////////////////////////////////////////////////////////////////

      // cosine
      if(CorefProperties.useWordEmbedding(props, sievename)) {
        // dimension
        int dim = dict.vectors.entrySet().iterator().next().getValue().length;
        
        // distance between headword
        float[] mV = dict.vectors.get(m.headString.toLowerCase());
        float[] aV = dict.vectors.get(candidate.headString.toLowerCase());
        if(mV!=null && aV!=null) {
          features.incrementCount("WORDVECTOR-DIFF-HEADWORD", cosine(mV, aV));
        }
        
        mV = dict.vectors.get(mFirst.word().toLowerCase());
        aV = dict.vectors.get(aFirst.word().toLowerCase());
        if(mV!=null && aV!=null) {
          features.incrementCount("WORDVECTOR-DIFF-FIRSTWORD", cosine(mV, aV));
        }
        
        mV = dict.vectors.get(mLast.word().toLowerCase());
        aV = dict.vectors.get(aLast.word().toLowerCase());
        if(mV!=null && aV!=null) {
          features.incrementCount("WORDVECTOR-DIFF-LASTWORD", cosine(mV, aV));
        }
        
        if(mPreceding!=null && aPreceding!=null) {
          mV = dict.vectors.get(mPreceding.word().toLowerCase());
          aV = dict.vectors.get(aPreceding.word().toLowerCase());
          if(mV!=null && aV!=null) {
            features.incrementCount("WORDVECTOR-DIFF-PRECEDINGWORD", cosine(mV, aV));
          }
        }
        if(mFollowing!=null && aFollowing!=null) {
          mV = dict.vectors.get(mFollowing.word().toLowerCase());
          aV = dict.vectors.get(aFollowing.word().toLowerCase());
          if(mV!=null && aV!=null) {
            features.incrementCount("WORDVECTOR-DIFF-FOLLOWINGWORD", cosine(mV, aV));
          }
        }
        
        float[] aggreM = new float[dim];
        float[] aggreA = new float[dim];
        
        for(CoreLabel cl : m.originalSpan) {
          float[] v = dict.vectors.get(cl.word().toLowerCase());
          if(v==null) continue;
          ArrayMath.pairwiseAddInPlace(aggreM, v);
        }
        for(CoreLabel cl : candidate.originalSpan) {
          float[] v = dict.vectors.get(cl.word().toLowerCase());
          if(v==null) continue;
          ArrayMath.pairwiseAddInPlace(aggreA, v);
        }
        if(ArrayMath.L2Norm(aggreM)!=0 && ArrayMath.L2Norm(aggreA)!=0) {
          features.incrementCount("WORDVECTOR-AGGREGATE-DIFF", cosine(aggreM, aggreA));
        }
        
        int cnt = 0;
        double dist = 0;
        for(CoreLabel mcl : m.originalSpan) {
          for(CoreLabel acl : candidate.originalSpan) {
            mV = dict.vectors.get(mcl.word().toLowerCase());
            aV = dict.vectors.get(acl.word().toLowerCase());
            if(mV==null || aV==null) continue;
            cnt++;
            dist += cosine(mV, aV);
          }
        }
        features.incrementCount("WORDVECTOR-AVG-DIFF", dist/cnt);
      }
      
      return new RVFDatum<Boolean, String>(features, label);
    } catch (Exception e) {
      System.err.println("Datum Extraction failed in Sieve.java while processing document: "+document.docInfo.get("DOC_ID")+" part: "+document.docInfo.get("DOC_PART"));
      throw new RuntimeException(e);
    }
  }
  
  // assume the input vectors are normalized
  private static double cosine(float[] normalizedVector1, float[] normalizedVector2) { 
    double inner = ArrayMath.innerProduct(normalizedVector1, normalizedVector2);
    return inner;
  }
  private static int numEntitiesInList(Mention m) {
    int num = 0;
    for(int i=1 ; i < m.originalSpan.size() ; i++) {
      CoreLabel cl = m.originalSpan.get(i);
      if(cl.word().equals(",")) num++;
      if((cl.word().equalsIgnoreCase("and") || cl.word().equalsIgnoreCase("or"))
          && !m.originalSpan.get(i-1).word().equals(",")) num++;
    }
    
    return num;
  }
}
