package edu.stanford.nlp.coref.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.coref.CorefRules;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Document.DocType;
import edu.stanford.nlp.coref.data.Dictionaries.Number;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UtteranceAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.math.NumberMatchingRegex;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Coref document preprocessor.
 * @author Heeyoung Lee
 * @author Kevin Clark
 */
public class DocumentPreprocessor  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(DocumentPreprocessor.class);

  private DocumentPreprocessor() {}

  /**
   * Fill missing information in document including mention ID, mention attributes, syntactic relation, etc.
   *
   * @throws Exception
   */
  public static void preprocess(Document doc, Dictionaries dict, LogisticClassifier<String, String> singletonPredictor, HeadFinder headFinder) throws Exception {
    // assign mention IDs, find twin mentions, fill mention positions, sentNum, headpositions
    initializeMentions(doc, dict, singletonPredictor, headFinder);

    // mention reordering
    mentionReordering(doc, headFinder);

    // find syntactic information
    fillSyntacticInfo(doc);

    // process discourse (speaker info etc)
    setParagraphAnnotation(doc);
    processDiscourse(doc, dict);

    // initialize cluster info
    initializeClusters(doc);

    // extract gold clusters if we have
    if(doc.goldMentions!=null) {
      extractGoldClusters(doc);
      int foundGoldCount = 0;
      for(Mention g : doc.goldMentionsByID.values()) {
        if(g.hasTwin) foundGoldCount++;
      }
      Redwood.log("debug-md", "# of found gold mentions: "+ foundGoldCount +
          " / # of gold mentions: "+ doc.goldMentionsByID.size());

    }

    // assign mention numbers
    assignMentionNumbers(doc);
  }

  /** Extract gold coref cluster information. */
  public static void extractGoldClusters(Document doc){
    doc.goldCorefClusters = Generics.newHashMap();
    for (List<Mention> mentions : doc.goldMentions) {
      for (Mention m : mentions) {
        int id = m.goldCorefClusterID;
        if (id == -1) {
          throw new RuntimeException("No gold info");
        }
        CorefCluster c = doc.goldCorefClusters.get(id);
        if (c == null) {
          c = new CorefCluster(id);
          doc.goldCorefClusters.put(id, c);
        }
        c.corefMentions.add(m);
      }
    }
  }

  private static void assignMentionNumbers(Document document) {
    List<Mention> mentionsList = CorefUtils.getSortedMentions(document);
    for (int i = 0; i < mentionsList.size(); i++) {
      mentionsList.get(i).mentionNum = i;
    }
  }


  private static void mentionReordering(Document doc, HeadFinder headFinder) throws Exception {
    List<List<Mention>> mentions = doc.predictedMentions;
    List<CoreMap> sentences = doc.annotation.get(SentencesAnnotation.class);

    for (int i=0 ; i<sentences.size() ; i++) {
      List<Mention> mentionsInSent = mentions.get(i);
      mentions.set(i, mentionReorderingBySpan(mentionsInSent));
    }
  }

  protected static int getHeadIndex(Tree t, HeadFinder headFinder) {
    // The trees passed in do not have the CoordinationTransformer
    // applied, but that just means the SemanticHeadFinder results are
    // slightly worse.
    Tree ht = t.headTerminal(headFinder);
    if(ht==null) return -1;  // temporary: a key which is matched to nothing
    CoreLabel l = (CoreLabel) ht.label();
    return l.get(CoreAnnotations.IndexAnnotation.class);
  }

  private static List<Mention> mentionReorderingBySpan(List<Mention> mentionsInSent) {
    TreeSet<Mention> ordering = new TreeSet<>(new Comparator<Mention>() {
      @Override
      public int compare(Mention m1, Mention m2) {
        return (m1.appearEarlierThan(m2)) ? -1 : (m2.appearEarlierThan(m1)) ? 1 : 0;
      }
    });
    ordering.addAll(mentionsInSent);
    List<Mention> orderedMentions = Generics.newArrayList(ordering);
    return orderedMentions;
  }

  private static void fillSyntacticInfo(Document doc) {

    List<List<Mention>> mentions = doc.predictedMentions;
    List<CoreMap> sentences = doc.annotation.get(SentencesAnnotation.class);

    for (int i=0 ; i<sentences.size() ; i++) {
      List<Mention> mentionsInSent = mentions.get(i);
      findSyntacticRelationsFromDependency(mentionsInSent);
    }
  }

  /** assign mention IDs, find twin mentions, fill mention positions, initialize coref clusters, etc
   * @throws Exception */
  private static void initializeMentions(Document doc, Dictionaries dict, LogisticClassifier<String, String> singletonPredictor, HeadFinder headFinder) throws Exception {
    boolean hasGold = (doc.goldMentions != null);
    assignMentionIDs(doc);
    if(hasGold) findTwinMentions(doc, true);
    fillMentionInfo(doc, dict, singletonPredictor, headFinder);
    doc.allPositions = Generics.newHashMap(doc.positions);    // allPositions retain all mentions even after postprocessing
  }

  private static void assignMentionIDs(Document doc) {
    boolean hasGold = (doc.goldMentions != null);
    int maxID = 0;
    if(hasGold) {
      for (List<Mention> golds : doc.goldMentions) {
        for (Mention g : golds) {
          g.mentionID = maxID++;
        }
      }
    }
    for (List<Mention> predicted : doc.predictedMentions) {
      for (Mention p : predicted) {
        p.mentionID = maxID++;
      }
    }
  }

  /** Mark twin mentions in gold and predicted mentions */
  protected static void findTwinMentions(Document doc, boolean strict){
    if(strict) findTwinMentionsStrict(doc);
    else findTwinMentionsRelaxed(doc);
  }

  /** Mark twin mentions: All mention boundaries should be matched */
  private static void findTwinMentionsStrict(Document doc){
    for(int sentNum = 0; sentNum < doc.goldMentions.size(); sentNum++) {
      List<Mention> golds = doc.goldMentions.get(sentNum);
      List<Mention> predicts = doc.predictedMentions.get(sentNum);

      // For CoNLL training there are some documents with gold mentions with the same position offsets
      // See /scr/nlp/data/conll-2011/v2/data/train/data/english/annotations/nw/wsj/09/wsj_0990.v2_auto_conll
      //  (Packwood - Roth)
      CollectionValuedMap<IntPair, Mention> goldMentionPositions = new CollectionValuedMap<>();
      for(Mention g : golds) {
        IntPair ip = new IntPair(g.startIndex, g.endIndex);
        if (goldMentionPositions.containsKey(ip)) {
          StringBuilder existingMentions = new StringBuilder();
          for (Mention eg: goldMentionPositions.get(ip)) {
            if (existingMentions.length() > 0) {
              existingMentions.append(",");
            }
            existingMentions.append(eg.mentionID);
          }
          Redwood.log("debug-preprocessor", "WARNING: gold mentions with the same offsets: " + ip
                  + " mentions=" + g.mentionID + "," + existingMentions + ", " + g.spanToString());
        }
        //assert(!goldMentionPositions.containsKey(ip));
        goldMentionPositions.add(new IntPair(g.startIndex, g.endIndex), g);
      }
      for(Mention p : predicts) {
        IntPair pos = new IntPair(p.startIndex, p.endIndex);
        if(goldMentionPositions.containsKey(pos)) {
          Collection<Mention> cm = goldMentionPositions.get(pos);
          int minId = Integer.MAX_VALUE;
          Mention g = null;
          for (Mention m : cm) {
            if (m.mentionID < minId) {
              g = m;
              minId = m.mentionID;
            }
          }
          if (cm.size() == 1) {
            goldMentionPositions.remove(pos);
          } else {
            cm.remove(g);
          }
          p.mentionID = g.mentionID;
          p.hasTwin = true;
          g.hasTwin = true;
        }
      }
    }
  }

  /** Mark twin mentions: heads of the mentions are matched */
  private static void findTwinMentionsRelaxed(Document doc) {
    for(int sentNum = 0; sentNum < doc.goldMentions.size(); sentNum++) {
      List<Mention> golds = doc.goldMentions.get(sentNum);
      List<Mention> predicts = doc.predictedMentions.get(sentNum);

      Map<IntPair, Mention> goldMentionPositions = Generics.newHashMap();
      Map<Integer, LinkedList<Mention>> goldMentionHeadPositions = Generics.newHashMap();
      for(Mention g : golds) {
        goldMentionPositions.put(new IntPair(g.startIndex, g.endIndex), g);
        if(!goldMentionHeadPositions.containsKey(g.headIndex)) {
          goldMentionHeadPositions.put(g.headIndex, new LinkedList<>());
        }
        goldMentionHeadPositions.get(g.headIndex).add(g);
      }

      List<Mention> remains = new ArrayList<>();
      for (Mention p : predicts) {
        IntPair pos = new IntPair(p.startIndex, p.endIndex);
        if(goldMentionPositions.containsKey(pos)) {
          Mention g = goldMentionPositions.get(pos);
          p.mentionID = g.mentionID;
          p.hasTwin = true;
          g.hasTwin = true;
          goldMentionHeadPositions.get(g.headIndex).remove(g);
          if(goldMentionHeadPositions.get(g.headIndex).isEmpty()) {
            goldMentionHeadPositions.remove(g.headIndex);
          }
        }
        else remains.add(p);
      }
      for (Mention r : remains){
        if(goldMentionHeadPositions.containsKey(r.headIndex)) {
          Mention g = goldMentionHeadPositions.get(r.headIndex).poll();
          r.mentionID = g.mentionID;
          r.hasTwin = true;
          g.hasTwin = true;
          if(goldMentionHeadPositions.get(g.headIndex).isEmpty()) {
            goldMentionHeadPositions.remove(g.headIndex);
          }
        }
      }
    }
  }

  /** initialize several variables for mentions
   * @throws Exception
   */
  private static void fillMentionInfo(Document doc, Dictionaries dict,
      LogisticClassifier<String, String> singletonPredictor, HeadFinder headFinder) throws Exception {
    List<CoreMap> sentences = doc.annotation.get(SentencesAnnotation.class);

    for(int i = 0; i < doc.predictedMentions.size(); i ++){
      CoreMap sentence = sentences.get(i);
      for(int j = 0; j < doc.predictedMentions.get(i).size(); j ++){
        Mention m = doc.predictedMentions.get(i).get(j);
        doc.predictedMentionsByID.put(m.mentionID, m);      // mentionsByID

        IntTuple pos = new IntTuple(2);
        pos.set(0, i);
        pos.set(1, j);
        doc.positions.put(m, pos);        // positions
        m.sentNum = i;                    // sentNum

        IntTuple headPosition = new IntTuple(2);
        headPosition.set(0, i);
        headPosition.set(1, m.headIndex);
        doc.mentionheadPositions.put(headPosition, m);    // headPositions

        m.contextParseTree = sentence.get(TreeAnnotation.class);
//        m.sentenceWords = sentence.get(TokensAnnotation.class);
        m.basicDependency = sentence.get(BasicDependenciesAnnotation.class);
        m.enhancedDependency = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        if (m.enhancedDependency == null) {
          m.enhancedDependency = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        }

        // mentionSubTree (highest NP that has the same head) if constituency tree available
        if (m.contextParseTree != null) {
          Tree headTree = m.contextParseTree.getLeaves().get(m.headIndex);
          if (headTree == null) { throw new RuntimeException("Missing head tree for a mention!"); }
          Tree t = headTree;
          while ((t = t.parent(m.contextParseTree)) != null) {
            if (t.headTerminal(headFinder) == headTree && t.value().equals("NP")) {
              m.mentionSubTree = t;
            } else if(m.mentionSubTree != null){
              break;
            }
          }
          if (m.mentionSubTree == null) {
            m.mentionSubTree = headTree;
          }
        }

        m.process(dict, null, singletonPredictor);
      }
    }


    boolean hasGold = (doc.goldMentions != null);
    if(hasGold) {
      doc.goldMentionsByID = Generics.newHashMap();
      int sentNum = 0;
      for(List<Mention> golds : doc.goldMentions) {
        for(Mention g : golds) {
          doc.goldMentionsByID.put(g.mentionID, g);
          g.sentNum = sentNum;
        }
        sentNum++;
      }
    }
  }

  private static void findSyntacticRelationsFromDependency(List<Mention> orderedMentions) {
    if(orderedMentions.size()==0) return;
    markListMemberRelation(orderedMentions);
    SemanticGraph dependency = orderedMentions.get(0).enhancedDependency;

    // apposition
    Set<Pair<Integer, Integer>> appos = Generics.newHashSet();
    List<SemanticGraphEdge> appositions = dependency.findAllRelns(UniversalEnglishGrammaticalRelations.APPOSITIONAL_MODIFIER);
    for(SemanticGraphEdge edge : appositions) {
      int sIdx = edge.getSource().index()-1;
      int tIdx = edge.getTarget().index()-1;
      appos.add(Pair.makePair(sIdx, tIdx));
    }
    markMentionRelation(orderedMentions, appos, "APPOSITION");

    // predicate nominatives
    Set<Pair<Integer, Integer>> preNomi = Generics.newHashSet();
    List<SemanticGraphEdge> copula = dependency.findAllRelns(UniversalEnglishGrammaticalRelations.COPULA);
    for(SemanticGraphEdge edge : copula) {
      IndexedWord source = edge.getSource();
      IndexedWord target = dependency.getChildWithReln(source, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT);
      if(target==null) target = dependency.getChildWithReln(source, UniversalEnglishGrammaticalRelations.CLAUSAL_SUBJECT);
      // TODO
      if(target == null) continue;

      // to handle relative clause: e.g., Tim who is a student,
      if(target.tag().startsWith("W")) {
        IndexedWord parent = dependency.getParent(source);
        if(parent!=null && dependency.reln(parent, source).equals(UniversalEnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER)) {
          target = parent;
        }
      }
      int sIdx = source.index()-1;
      int tIdx = target.index()-1;
      preNomi.add(Pair.makePair(tIdx, sIdx));
    }
    markMentionRelation(orderedMentions, preNomi, "PREDICATE_NOMINATIVE");


    // relative pronouns  TODO
    Set<Pair<Integer, Integer>> relativePronounPairs = Generics.newHashSet();
    markMentionRelation(orderedMentions, relativePronounPairs, "RELATIVE_PRONOUN");
  }

  private static void initializeClusters(Document doc) {
    for (List<Mention> predicted : doc.predictedMentions) {
      for (Mention p : predicted) {
        doc.corefClusters.put(p.mentionID, new CorefCluster(p.mentionID, Generics.newHashSet(Arrays.asList(p))));
        p.corefClusterID = p.mentionID;
      }
    }
    boolean hasGold = (doc.goldMentions != null);
    if(hasGold) {
      for(List<Mention> golds : doc.goldMentions) {
        for(Mention g : golds) {
          doc.goldMentionsByID.put(g.mentionID, g);
        }
      }
    }
  }

  /** Find document type: Conversation or article  */
  private static DocType findDocType(Document doc) {
    boolean speakerChange = false;

    for(CoreMap sent : doc.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for(CoreLabel w : sent.get(CoreAnnotations.TokensAnnotation.class)) {
        int utterIndex = w.get(CoreAnnotations.UtteranceAnnotation.class);
        if(utterIndex!=0) speakerChange = true;
        if(speakerChange && utterIndex==0) return DocType.ARTICLE;
        if(doc.maxUtter < utterIndex) doc.maxUtter = utterIndex;
      }
    }
    if(!speakerChange) return DocType.ARTICLE;
    return DocType.CONVERSATION;  // in conversation, utter index keep increasing.
  }

  /** Set paragraph index */
  private static void setParagraphAnnotation(Document doc) {
    int paragraphIndex = 0;
    int previousOffset = -10;
    for(CoreMap sent : doc.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for(CoreLabel w : sent.get(CoreAnnotations.TokensAnnotation.class)) {
        if(w.containsKey(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
          if(w.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) > previousOffset+2) paragraphIndex++;
          w.set(CoreAnnotations.ParagraphAnnotation.class, paragraphIndex);
          previousOffset = w.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        } else {
          w.set(CoreAnnotations.ParagraphAnnotation.class, -1);
        }
      }
    }
    for(List<Mention> l : doc.predictedMentions) {
      for(Mention m : l){
        m.paragraph = m.headWord.get(CoreAnnotations.ParagraphAnnotation.class);
      }
    }
    doc.numParagraph = paragraphIndex;
  }

  /** Process discourse information */
  protected static void processDiscourse(Document doc, Dictionaries dict) {
    Boolean useMarkedDiscourse =
        doc.annotation.get(CoreAnnotations.UseMarkedDiscourseAnnotation.class);
    if (useMarkedDiscourse == null || !useMarkedDiscourse) {
      for (CoreLabel l : doc.annotation.get(CoreAnnotations.TokensAnnotation.class)) {
        l.remove(CoreAnnotations.SpeakerAnnotation.class);
        l.remove(CoreAnnotations.UtteranceAnnotation.class);
      }
    }

    setUtteranceAndSpeakerAnnotation(doc);
//    markQuotations(this.annotation.get(CoreAnnotations.SentencesAnnotation.class), false);

    // mention utter setting
    for(Mention m : doc.predictedMentionsByID.values()) {
      m.utter = m.headWord.get(CoreAnnotations.UtteranceAnnotation.class);
    }

    doc.docType = findDocType(doc);
    findSpeakers(doc, dict);

    boolean debug = false;
    if(debug) {
      for(CoreMap sent : doc.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        for(CoreLabel cl : sent.get(TokensAnnotation.class)) {
          log.info("   "+cl.word()+"-"+cl.get(UtteranceAnnotation.class)+"-"+cl.get(SpeakerAnnotation.class));
        }
      }


      for(Integer utter : doc.speakers.keySet()) {
        String speakerID = doc.speakers.get(utter);
        log.info("utterance: "+utter);
        log.info("speakers value: " + speakerID);
        log.info("mention for it: "+
            ( (NumberMatchingRegex.isDecimalInteger(speakerID))?
                doc.predictedMentionsByID.get(Integer.parseInt(doc.speakers.get(utter)))
                : "no mention for this speaker yet") );
      }
      log.info("AA SPEAKERS: "+ doc.speakers);
    }

    // build 'speakerInfo' from 'speakers'
    for(Integer utter : doc.speakers.keySet()) {
      String speaker = doc.speakers.get(utter);
      SpeakerInfo speakerInfo = doc.speakerInfoMap.get(speaker);
      if (speakerInfo == null) {
        doc.speakerInfoMap.put(speaker, speakerInfo = new SpeakerInfo(speaker));
      }
    }
    if(debug){
      log.info("BB SPEAKER INFO MAP: "+doc.speakerInfoMap);
    }

    // mention -> to its speakerID: m.headWord.get(SpeakerAnnotation.class)
    // speakerID -> more info: speakerInfoMap.get(speakerID)
    // if exists, set(mentionID, its speakerID pair): speakerPairs

    // for speakerInfo with real speaker name, find corresponding mention by strict/loose matching
    Map<String, Integer> speakerConversion = Generics.newHashMap();
    for(String speaker : doc.speakerInfoMap.keySet()) {
      SpeakerInfo speakerInfo = doc.speakerInfoMap.get(speaker);
      if (speakerInfo.hasRealSpeakerName()) {   // do only for real name speaker, not mention ID
        boolean found = false;
        for(Mention m : doc.predictedMentionsByID.values()) {
          if (CorefRules.mentionMatchesSpeaker(m, speakerInfo, true)) {
            speakerConversion.put(speaker, m.mentionID);
            found = true;
            break;
          }
        }
        if(!found) {
          for(Mention m : doc.predictedMentionsByID.values()) {
            if (CorefRules.mentionMatchesSpeaker(m, speakerInfo, false)) {
              speakerConversion.put(speaker, m.mentionID);
              break;
            }
          }
        }
      }
    }

    if(debug) log.info("CC speaker conversion: " + speakerConversion);

    // convert real name speaker to speaker mention id
    for(Integer utter : doc.speakers.keySet()) {
      String speaker = doc.speakers.get(utter);
      if(speakerConversion.containsKey(speaker)) {
        int speakerID = speakerConversion.get(speaker);
        doc.speakers.put(utter, Integer.toString(speakerID));
      }
    }
    for(String speaker : speakerConversion.keySet()) {
      doc.speakerInfoMap.put( Integer.toString(speakerConversion.get(speaker)), doc.speakerInfoMap.get(speaker));
      doc.speakerInfoMap.remove(speaker);
    }

    // fix SpeakerAnnotation
    for(CoreLabel cl : doc.annotation.get(TokensAnnotation.class)) {
      int utter = cl.get(UtteranceAnnotation.class);
      if(doc.speakers.containsKey(utter)) {
        cl.set(CoreAnnotations.SpeakerAnnotation.class, doc.speakers.get(utter));
      }
    }

    // find speakerPairs
    for(Mention m : doc.predictedMentionsByID.values()) {
      String speaker = m.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
      if(debug) log.info("DD: "+speaker);
      // if this is not a CoNLL doc, don't treat a number username as a speakerMentionID
      // conllDoc == null indicates not a CoNLL doc
      if (doc.conllDoc != null) {
        if (NumberMatchingRegex.isDecimalInteger(speaker)) {
          int speakerMentionID = Integer.parseInt(speaker);
          doc.speakerPairs.add(new Pair<>(m.mentionID, speakerMentionID));
        }
      }
    }

    if(debug) {
      log.info("==========================================================================");
      for(Integer utter : doc.speakers.keySet()) {
        String speakerID = doc.speakers.get(utter);
        log.info("utterance: "+utter);
        log.info("speakers value: " + speakerID);
        log.info("mention for it: "+
            ( (NumberMatchingRegex.isDecimalInteger(speakerID))?
                doc.predictedMentionsByID.get(Integer.parseInt(doc.speakers.get(utter)))
                : "no mention for this speaker yet") );
      }
      log.info(doc.speakers);
    }
  }

  private static void setUtteranceAndSpeakerAnnotation(Document doc) {
    doc.speakerInfoGiven = false;
    int utterance = 0;
    int outsideQuoteUtterance = 0;   // the utterance of outside of quotation
    boolean insideQuotation = false;
    List<CoreLabel> tokens = doc.annotation.get(CoreAnnotations.TokensAnnotation.class);
    String preSpeaker = (tokens.size() > 0)? tokens.get(0).get(CoreAnnotations.SpeakerAnnotation.class) : null;

    for (CoreLabel l : tokens) {
      String curSpeaker = l.get(CoreAnnotations.SpeakerAnnotation.class);
      String w = l.get(CoreAnnotations.TextAnnotation.class);

      if (curSpeaker!=null && !curSpeaker.equals("-")) doc.speakerInfoGiven = true;

      boolean speakerChange = doc.speakerInfoGiven && curSpeaker!=null && !curSpeaker.equals(preSpeaker);
      boolean quoteStart = w.equals("``") || (!insideQuotation && w.equals("\""));
      boolean quoteEnd = w.equals("''") || (insideQuotation && w.equals("\""));

      if(speakerChange) {
        if(quoteStart) {
          utterance = doc.maxUtter + 1;
          outsideQuoteUtterance = utterance+1;
        } else {
          utterance = doc.maxUtter + 1;
          outsideQuoteUtterance = utterance;
        }
        preSpeaker = curSpeaker;
      } else {
        if(quoteStart) {
          utterance = doc.maxUtter + 1;
        }
      }
      if(quoteEnd) {
        utterance = outsideQuoteUtterance;
        insideQuotation = false;
      }
      if(doc.maxUtter < utterance) doc.maxUtter = utterance;

      l.set(CoreAnnotations.UtteranceAnnotation.class, utterance);
      if(quoteStart) l.set(CoreAnnotations.UtteranceAnnotation.class, outsideQuoteUtterance);   // quote start got outside utterance idx

      boolean noSpeakerInfo = !l.containsKey(CoreAnnotations.SpeakerAnnotation.class)
      || l.get(CoreAnnotations.SpeakerAnnotation.class).equals("")
      || l.get(CoreAnnotations.SpeakerAnnotation.class).startsWith("PER");

      if(noSpeakerInfo || insideQuotation){
        l.set(CoreAnnotations.SpeakerAnnotation.class, "PER"+utterance);
      }
      if(quoteStart) insideQuotation = true;
    }
  }

  /** Speaker extraction */
  private static void findSpeakers(Document doc, Dictionaries dict) {
    Boolean useMarkedDiscourseBoolean = doc.annotation.get(CoreAnnotations.UseMarkedDiscourseAnnotation.class);
    boolean useMarkedDiscourse = (useMarkedDiscourseBoolean != null)? useMarkedDiscourseBoolean: false;

    if(!useMarkedDiscourse) {
      if(doc.docType==DocType.CONVERSATION) findSpeakersInConversation(doc, dict);
      else if (doc.docType==DocType.ARTICLE) findSpeakersInArticle(doc, dict);
    }

    for(CoreMap sent : doc.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for(CoreLabel w : sent.get(CoreAnnotations.TokensAnnotation.class)) {
        int utterIndex = w.get(CoreAnnotations.UtteranceAnnotation.class);
        if(!doc.speakers.containsKey(utterIndex)) {
          doc.speakers.put(utterIndex, w.get(CoreAnnotations.SpeakerAnnotation.class));
        }
      }
    }
  }

  private static void findSpeakersInArticle(Document doc, Dictionaries dict) {
    List<CoreMap> sentences = doc.annotation.get(CoreAnnotations.SentencesAnnotation.class);
    IntPair beginQuotation = null;
    IntPair endQuotation = null;
    boolean insideQuotation = false;
    int utterNum = -1;

    for (int i = 0 ; i < sentences.size(); i++) {
      List<CoreLabel> sent = sentences.get(i).get(CoreAnnotations.TokensAnnotation.class);
      for(int j = 0 ; j < sent.size() ; j++) {
        int utterIndex = sent.get(j).get(CoreAnnotations.UtteranceAnnotation.class);

        if(utterIndex != 0 && !insideQuotation) {
          utterNum = utterIndex;
          insideQuotation = true;
          beginQuotation = new IntPair(i,j);
        } else if (utterIndex == 0 && insideQuotation) {
          insideQuotation = false;
          endQuotation = new IntPair(i,j);
          findQuotationSpeaker(doc, utterNum, sentences, beginQuotation, endQuotation, dict);
        }
      }
    }
    if(insideQuotation) {
      endQuotation = new IntPair(sentences.size()-1, sentences.get(sentences.size()-1).get(CoreAnnotations.TokensAnnotation.class).size()-1);
      findQuotationSpeaker(doc, utterNum, sentences, beginQuotation, endQuotation, dict);
    }
  }

  private static void findQuotationSpeaker(Document doc, int utterNum, List<CoreMap> sentences,
      IntPair beginQuotation, IntPair endQuotation, Dictionaries dict) {

    if(findSpeaker(doc, utterNum, beginQuotation.get(0), sentences, 0, beginQuotation.get(1), dict))
      return ;

    if(findSpeaker(doc, utterNum, endQuotation.get(0), sentences, endQuotation.get(1),
        sentences.get(endQuotation.get(0)).get(CoreAnnotations.TokensAnnotation.class).size(), dict))
      return;

    if(beginQuotation.get(1) <= 1 && beginQuotation.get(0) > 0) {
      if(findSpeaker(doc, utterNum, beginQuotation.get(0)-1, sentences, 0,
          sentences.get(beginQuotation.get(0)-1).get(CoreAnnotations.TokensAnnotation.class).size(), dict))
        return;
    }

    if(endQuotation.get(1) >= sentences.get(endQuotation.get(0)).size()-2
        && sentences.size() > endQuotation.get(0)+1) {
      if(findSpeaker(doc, utterNum, endQuotation.get(0)+1, sentences, 0,
          sentences.get(endQuotation.get(0)+1).get(CoreAnnotations.TokensAnnotation.class).size(), dict))
        return;
    }
  }

  private static boolean findSpeaker(Document doc, int utterNum, int sentNum, List<CoreMap> sentences,
      int startIndex, int endIndex, Dictionaries dict) {
    List<CoreLabel> sent = sentences.get(sentNum).get(CoreAnnotations.TokensAnnotation.class);
    for(int i = startIndex ; i < endIndex ; i++) {
      CoreLabel cl = sent.get(i);
      if(cl.get(CoreAnnotations.UtteranceAnnotation.class)!=0) continue;
      String lemma = cl.lemma();
      String word = cl.word();
      if(dict.reportVerb.contains(lemma) && cl.tag().startsWith("V")) {
        // find subject
        SemanticGraph dependency = sentences.get(sentNum).get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        if (dependency == null) {
          dependency = sentences.get(sentNum).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        }
        IndexedWord w = dependency.getNodeByWordPattern(word);

        if (w != null) {
          if(findSubject(doc, dependency, w, sentNum, utterNum)) return true;
          for(IndexedWord p : dependency.getPathToRoot(w)) {
            if(!p.tag().startsWith("V") && !p.tag().startsWith("MD")) break;
            if(findSubject(doc, dependency, p, sentNum, utterNum)) return true;    // handling something like "was talking", "can tell"
          }
        } else {
          Redwood.log("debug-preprocessor", "Cannot find node in dependency for word " + word);
        }
      }
    }
    return false;
  }

  private static boolean findSubject(Document doc, SemanticGraph dependency, IndexedWord w, int sentNum, int utterNum) {
    for(Pair<GrammaticalRelation,IndexedWord> child : dependency.childPairs(w)){
      if(child.first().getShortName().equals("nsubj")) {
        String subjectString = child.second().word();
        int subjectIndex = child.second().index();  // start from 1
        IntTuple headPosition = new IntTuple(2);
        headPosition.set(0, sentNum);
        headPosition.set(1, subjectIndex-1);
        String speaker;
        if(doc.mentionheadPositions.containsKey(headPosition)) {
          speaker = Integer.toString(doc.mentionheadPositions.get(headPosition).mentionID);
        } else {
          speaker = subjectString;
        }
        doc.speakers.put(utterNum, speaker);
        return true;
      }
    }
    return false;
  }

  private static void findSpeakersInConversation(Document doc, Dictionaries dict) {
    for(List<Mention> l : doc.predictedMentions) {
      for(Mention m : l){
        if(m.predicateNominatives == null) continue;
        for (Mention a : m.predicateNominatives){
          if(a.spanToString().toLowerCase().equals("i")) {
            doc.speakers.put(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class), Integer.toString(m.mentionID));
          }
        }
      }
    }
    List<CoreMap> paragraph = new ArrayList<>();
    int paragraphUtterIndex = 0;
    String nextParagraphSpeaker = "";
    int paragraphOffset = 0;
    for(CoreMap sent : doc.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      paragraph.add(sent);
      int currentUtter = sent.get(CoreAnnotations.TokensAnnotation.class).get(0).get(CoreAnnotations.UtteranceAnnotation.class);
      if(paragraphUtterIndex!=currentUtter) {
        nextParagraphSpeaker = findParagraphSpeaker(doc, paragraph, paragraphUtterIndex, nextParagraphSpeaker, paragraphOffset, dict);
        paragraphUtterIndex = currentUtter;
        paragraphOffset += paragraph.size();
        paragraph = new ArrayList<>();
      }
    }
    findParagraphSpeaker(doc, paragraph, paragraphUtterIndex, nextParagraphSpeaker, paragraphOffset, dict);
  }

  private static String findParagraphSpeaker(Document doc, List<CoreMap> paragraph,
            int paragraphUtterIndex, String nextParagraphSpeaker, int paragraphOffset, Dictionaries dict) {
    if ( ! doc.speakers.containsKey(paragraphUtterIndex)) {
      if ( ! nextParagraphSpeaker.isEmpty()) {
        doc.speakers.put(paragraphUtterIndex, nextParagraphSpeaker);
      } else {  // find the speaker of this paragraph (John, nbc news)
        // cdm [Sept 2015] added this check to try to avoid crash
        if (paragraph.isEmpty()) {
          Redwood.log("debug-preprocessor", "Empty paragraph; skipping findParagraphSpeaker");
          return "";
        }
        CoreMap lastSent = paragraph.get(paragraph.size()-1);
        String speaker = "";
        boolean hasVerb = false;
        for(int i = 0 ; i < lastSent.get(CoreAnnotations.TokensAnnotation.class).size() ; i++){
          CoreLabel w = lastSent.get(CoreAnnotations.TokensAnnotation.class).get(i);
          String pos = w.get(CoreAnnotations.PartOfSpeechAnnotation.class);
          String ner = w.get(CoreAnnotations.NamedEntityTagAnnotation.class);
          if(pos.startsWith("V")) {
            hasVerb = true;
            break;
          }
          if(ner.startsWith("PER")) {
            IntTuple headPosition = new IntTuple(2);
            headPosition.set(0, paragraph.size()-1 + paragraphOffset);
            headPosition.set(1, i);
            if(doc.mentionheadPositions.containsKey(headPosition)) {
              speaker = Integer.toString(doc.mentionheadPositions.get(headPosition).mentionID);
            }
          }
        }
        if(!hasVerb && !speaker.equals("")) {
          doc.speakers.put(paragraphUtterIndex, speaker);
        }
      }
    }
    return findNextParagraphSpeaker(doc, paragraph, paragraphOffset, dict);
  }

  private static String findNextParagraphSpeaker(Document doc, List<CoreMap> paragraph, int paragraphOffset, Dictionaries dict) {
    if (paragraph.isEmpty()) {
      return "";
    }
    CoreMap lastSent = paragraph.get(paragraph.size()-1);
    String speaker = "";
    for(CoreLabel w : lastSent.get(CoreAnnotations.TokensAnnotation.class)) {
      if(w.get(CoreAnnotations.LemmaAnnotation.class).equals("report") || w.get(CoreAnnotations.LemmaAnnotation.class).equals("say")) {
        String word = w.get(CoreAnnotations.TextAnnotation.class);
        SemanticGraph dependency = lastSent.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        if (dependency == null) {
          dependency = lastSent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        }
        IndexedWord t = dependency.getNodeByWordPattern(word);

        for(Pair<GrammaticalRelation,IndexedWord> child : dependency.childPairs(t)){
          if(child.first().getShortName().equals("nsubj")) {
            int subjectIndex = child.second().index();  // start from 1
            IntTuple headPosition = new IntTuple(2);
            headPosition.set(0, paragraph.size()-1 + paragraphOffset);
            headPosition.set(1, subjectIndex-1);
            if(doc.mentionheadPositions.containsKey(headPosition)
                && doc.mentionheadPositions.get(headPosition).nerString.startsWith("PER")) {
              speaker = Integer.toString(doc.mentionheadPositions.get(headPosition).mentionID);
            }
          }
        }
      }
    }
    return speaker;
  }

  /** Check one mention is the speaker of the other mention */
  public static boolean isSpeaker(Mention m, Mention ant, Dictionaries dict) {

    if(!dict.firstPersonPronouns.contains(ant.spanToString().toLowerCase())
        || ant.number==Number.PLURAL || ant.sentNum!=m.sentNum) return false;

    int countQuotationMark = 0;
    for(int i = Math.min(m.headIndex, ant.headIndex)+1 ; i < Math.max(m.headIndex, ant.headIndex) ; i++) {
      String word = m.sentenceWords.get(i).get(CoreAnnotations.TextAnnotation.class);
      if(word.equals("``") || word.equals("''")) countQuotationMark++;
    }
    if(countQuotationMark!=1) return false;

    IndexedWord w = m.enhancedDependency.getNodeByWordPattern(m.sentenceWords.get(m.headIndex).get(CoreAnnotations.TextAnnotation.class));
    if(w== null) return false;

    for(Pair<GrammaticalRelation,IndexedWord> parent : m.enhancedDependency.parentPairs(w)){
      if(parent.first().getShortName().equals("nsubj")
          && dict.reportVerb.contains(parent.second().get(CoreAnnotations.LemmaAnnotation.class))) {
        return true;
      }
    }
    return false;
  }

  private static void markListMemberRelation(List<Mention> orderedMentions) {
    for(Mention m1 : orderedMentions){
      for(Mention m2 : orderedMentions){
        // Mark if m2 and m1 are in list relationship
        if (m1.isListMemberOf(m2)) {
          m2.addListMember(m1);
          m1.addBelongsToList(m2);
        } else if (m2.isListMemberOf(m1)) {
          m1.addListMember(m2);
          m2.addBelongsToList(m1);
        }
      }
    }
  }
  private static void markMentionRelation(List<Mention> orderedMentions, Set<Pair<Integer, Integer>> foundPairs, String flag) {
    for(Mention m1 : orderedMentions){
      for(Mention m2 : orderedMentions){
        if(m1==m2) continue;
        // Ignore if m2 and m1 are in list relationship
        if (m1.isListMemberOf(m2) || m2.isListMemberOf(m1) || m1.isMemberOfSameList(m2)) {
          //Redwood.log("debug-preprocessor", "Not checking '" + m1 + "' and '" + m2 + "' for " + flag + ": in list relationship");
          continue;
        }
        for(Pair<Integer, Integer> foundPair: foundPairs){
          if (foundPair.first() == m1.headIndex && foundPair.second() == m2.headIndex) {
            if(flag.equals("APPOSITION")) {
              if ( ! foundPair.first().equals(foundPair.second()) || m2.insideIn(m1)) {
                m2.addApposition(m1);
              }
            }
            else if(flag.equals("PREDICATE_NOMINATIVE")) {
              m2.addPredicateNominatives(m1);
            }
            else if(flag.equals("RELATIVE_PRONOUN")) m2.addRelativePronoun(m1);
            else throw new RuntimeException("check flag in markMentionRelation (dcoref/MentionExtractor.java)");
          }
        }
      }
    }
  }

//  private static final TregexPattern relativePronounPattern = TregexPattern.compile("NP < (NP=m1 $.. (SBAR < (WHNP < WP|WDT=m2)))");
//  private static void findRelativePronouns(Tree tree, Set<Pair<Integer, Integer>> relativePronounPairs) {
//    findTreePattern(tree, relativePronounPattern, relativePronounPairs);
//  }
}
