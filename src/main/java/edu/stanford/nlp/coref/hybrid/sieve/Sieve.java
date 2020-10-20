package edu.stanford.nlp.coref.hybrid.sieve;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Dictionaries.Person;
import edu.stanford.nlp.coref.hybrid.HybridCorefPrinter;
import edu.stanford.nlp.coref.hybrid.HybridCorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

public abstract class Sieve implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Sieve.class);

  private static final long serialVersionUID = 3986463332365306868L;

  public enum ClassifierType {RULE, RF, ORACLE}

  public ClassifierType classifierType = null;

  protected Locale lang;

  public final String sievename;

  /** the maximum sentence distance for linking two mentions */
  public int maxSentDist = -1;

  /** type of mention we want to resolve. e.g., if mType is PRONOMINAL, we only resolve pronoun mentions */
  public final Set<MentionType> mType;

  /** type of mention we want to compare to. e.g., if aType is PROPER, the resolution can be done only with PROPER antecedent  */
  public final Set<MentionType> aType;

  public final Set<String> mTypeStr;
  public final Set<String> aTypeStr;

  public Properties props = null;

  public Sieve() {
    this.lang = Locale.ENGLISH;
    this.sievename = this.getClass().getSimpleName();
    this.aType = new HashSet<>(Arrays.asList(MentionType.values()));
    this.mType = new HashSet<>(Arrays.asList(MentionType.values()));
    this.maxSentDist = 1000;
    this.mTypeStr = Generics.newHashSet();
    this.aTypeStr = Generics.newHashSet();
  }

  public Sieve(Properties props){
    this.lang = HybridCorefProperties.getLanguage(props);
    this.sievename = this.getClass().getSimpleName();
    this.aType = HybridCorefProperties.getAntecedentType(props, sievename);
    this.mType = HybridCorefProperties.getMentionType(props, sievename);
    this.maxSentDist = HybridCorefProperties.getMaxSentDistForSieve(props, sievename);
    this.mTypeStr = HybridCorefProperties.getMentionTypeStr(props, sievename);
    this.aTypeStr = HybridCorefProperties.getAntecedentTypeStr(props, sievename);
  }

  public Sieve(Properties props, String sievename) {
    this.lang = HybridCorefProperties.getLanguage(props);
    this.sievename = sievename;
    this.aType = HybridCorefProperties.getAntecedentType(props, sievename);
    this.mType = HybridCorefProperties.getMentionType(props, sievename);
    this.maxSentDist = HybridCorefProperties.getMaxSentDistForSieve(props, sievename);
    this.mTypeStr = HybridCorefProperties.getMentionTypeStr(props, sievename);
    this.aTypeStr = HybridCorefProperties.getAntecedentTypeStr(props, sievename);
  }

  public String resolveMention(Document document, Dictionaries dict, Properties props) throws Exception {
    StringBuilder sbLog = new StringBuilder();

    if(HybridCorefProperties.debug(props)) {
      sbLog.append("=======================================================");
      sbLog.append(HybridCorefPrinter.printRawDoc(document, true, true));
    }

    for(List<Mention> mentionsInSent : document.predictedMentions) {
      for(int mIdx = 0 ; mIdx < mentionsInSent.size() ; mIdx++) {
        Mention m = mentionsInSent.get(mIdx);
        if(skipMentionType(m, props)) continue;
        findCoreferentAntecedent(m, mIdx, document, dict, props, sbLog);
      }
    }
    return sbLog.toString();
  }

  public abstract void findCoreferentAntecedent(Mention m, int mIdx, Document document, Dictionaries dict, Properties props, StringBuilder sbLog) throws Exception;


  // load sieve (from file or make a deterministic sieve)
  public static Sieve loadSieve(Properties props, String sievename) throws Exception {
    // log.info("Loading sieve: "+sievename+" ...");
    switch(HybridCorefProperties.getClassifierType(props, sievename)) {
      case RULE:
        DeterministicCorefSieve sieve = (DeterministicCorefSieve) Class.forName("edu.stanford.nlp.coref.hybrid.sieve."+sievename).getConstructor().newInstance();
        sieve.props = props;
        sieve.lang = HybridCorefProperties.getLanguage(props);
        return sieve;

      case RF:
        log.info("Loading sieve: " + sievename + " from " + HybridCorefProperties.getPathModel(props, sievename) + " ... ");
        RFSieve rfsieve = IOUtils.readObjectFromURLOrClasspathOrFileSystem(HybridCorefProperties.getPathModel(props, sievename));
        rfsieve.thresMerge = HybridCorefProperties.getMergeThreshold(props, sievename);
        log.info("done. Merging threshold: " + rfsieve.thresMerge);
        return rfsieve;

      case ORACLE:
        OracleSieve oracleSieve = new OracleSieve(props, sievename);
        oracleSieve.props = props;
        return oracleSieve;

      default:
        throw new RuntimeException("no sieve type specified");
    }
  }


  public static List<Sieve> loadSieves(Properties props) throws Exception {
    List<Sieve> sieves = new ArrayList<>();
    String sieveProp = HybridCorefProperties.getSieves(props);
    String currentSieveForTrain = HybridCorefProperties.getCurrentSieveForTrain(props);
    String[] sievenames = (currentSieveForTrain==null)?
        sieveProp.trim().split(",\\s*") : sieveProp.split(currentSieveForTrain)[0].trim().split(",\\s*");
    for(String sievename : sievenames) {
      Sieve sieve = loadSieve(props, sievename);
      sieves.add(sieve);
    }
    return sieves;
  }

  public static boolean hasThat(List<CoreLabel> words) {
    for(CoreLabel cl : words) {
      if(cl.word().equalsIgnoreCase("that") && cl.tag().equalsIgnoreCase("IN")) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasToVerb(List<CoreLabel> words) {
    for(int i=0 ; i<words.size()-1 ; i++) {
      if(words.get(i).tag().equals("TO") && words.get(i+1).tag().startsWith("V")) return true;
    }
    return false;
  }


  private boolean skipMentionType(Mention m, Properties props) {
    if(mType.contains(m.mentionType)) return false;
    return true;
  }

  public static void merge(Document document, int mID, int antID) {
    CorefCluster c1 = document.corefClusters.get(document.predictedMentionsByID.get(mID).corefClusterID);
    CorefCluster c2 = document.corefClusters.get(document.predictedMentionsByID.get(antID).corefClusterID);
    if(c1==c2) return;

    int removeID = c1.getClusterID();
    CorefCluster.mergeClusters(c2, c1);
    document.mergeIncompatibles(c2, c1);
    document.corefClusters.remove(removeID);
  }

  // check if two mentions are really coref in gold annotation
  public static boolean isReallyCoref(Document document, int mID, int antID) {
    if(!document.goldMentionsByID.containsKey(mID) || !document.goldMentionsByID.containsKey(antID)) {
      return false;
    }
    int mGoldClusterID = document.goldMentionsByID.get(mID).goldCorefClusterID;
    int aGoldClusterID = document.goldMentionsByID.get(antID).goldCorefClusterID;

    return (mGoldClusterID == aGoldClusterID);
  }

  protected static boolean skipForAnalysis(Mention ant, Mention m, Properties props) {
    if(!HybridCorefProperties.doAnalysis(props)) return false;
    String skipMentionType = HybridCorefProperties.getSkipMentionType(props);
    String skipAntType = HybridCorefProperties.getSkipAntecedentType(props);

    return matchedMentionType(ant, skipAntType) && matchedMentionType(m, skipMentionType);
  }
  protected static boolean matchedMentionType(Mention m, Set<String> types) {
    if(types.isEmpty()) return true;
    for(String type : types) {
      if(matchedMentionType(m, type)) return true;
    }
    return false;
  }
  protected static boolean matchedMentionType(Mention m, String type) {
    if(type==null) return false;
    if(type.equalsIgnoreCase("all") || type.equalsIgnoreCase(m.mentionType.toString())) return true;

    // check pronoun specific type
    if(type.equalsIgnoreCase("he") && m.isPronominal() && m.person == Person.HE) return true;
    if(type.equalsIgnoreCase("she") && m.isPronominal() && m.person == Person.SHE) return true;
    if(type.equalsIgnoreCase("you") && m.isPronominal() && m.person == Person.YOU) return true;
    if(type.equalsIgnoreCase("I") && m.isPronominal() && m.person == Person.I) return true;
    if(type.equalsIgnoreCase("it") && m.isPronominal() && m.person == Person.IT) return true;
    if(type.equalsIgnoreCase("they") && m.isPronominal() && m.person == Person.THEY) return true;
    if(type.equalsIgnoreCase("we") && m.isPronominal() && m.person == Person.WE) return true;

    // check named entity type
    if(type.toLowerCase().startsWith("ne:")) {
      if(type.toLowerCase().substring(3).startsWith(m.nerString.toLowerCase().substring(0, Math.min(3, m.nerString.length())))) return true;
    }

    return false;
  }

  public static List<Mention> getOrderedAntecedents(
      Mention m,
      int antecedentSentence,
      int mPosition,
      List<List<Mention>> orderedMentionsBySentence,
      Dictionaries dict) {
    List<Mention> orderedAntecedents = new ArrayList<>();
    // ordering antecedents
    if (antecedentSentence == m.sentNum) {   // same sentence
      orderedAntecedents.addAll(orderedMentionsBySentence.get(m.sentNum).subList(0, mPosition));

      if(dict.relativePronouns.contains(m.spanToString())) Collections.reverse(orderedAntecedents);
      else {
        orderedAntecedents = sortMentionsByClause(orderedAntecedents, m);
      }

    } else {    // previous sentence
      orderedAntecedents.addAll(orderedMentionsBySentence.get(antecedentSentence));
    }
    return orderedAntecedents;
  }

  /** Divides a sentence into clauses and sort the antecedents for pronoun matching  */
  private static List<Mention> sortMentionsByClause(List<Mention> l, Mention m1) {
    List<Mention> sorted = new ArrayList<>();
    Tree tree = m1.contextParseTree;
    Tree current = m1.mentionSubTree;
    if(tree==null || current==null) return l;
    while(true){
      current = current.ancestor(1, tree);
      String curLabel = current.label().value();
      if("TOP".equals(curLabel) || curLabel.startsWith("S") || curLabel.equals("NP")){
//      if(current.label().value().startsWith("S")){
        for(Mention m : l){
          if(!sorted.contains(m) && current.dominates(m.mentionSubTree)) sorted.add(m);
        }
      }
      if(current.ancestor(1, tree)==null) break;
    }
    return sorted;
  }
}
