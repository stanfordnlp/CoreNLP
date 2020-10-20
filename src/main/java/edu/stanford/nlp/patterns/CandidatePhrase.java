package edu.stanford.nlp.patterns;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Sonal Gupta on 11/7/14.
 */
public class CandidatePhrase implements Serializable, Comparable  {

  private final String phrase;
  private String phraseLemma;
  private Counter<String> features;
  private final int hashCode;

  private static final long serialVersionUID = 42L;

  private static final ConcurrentHashMap<String, CandidatePhrase> candidatePhraseMap = new ConcurrentHashMap<>();

  // static void setCandidatePhraseMap(ConcurrentHashMap<String, CandidatePhrase> candmap){
  //  candidatePhraseMap = candmap;
  // }

  public static CandidatePhrase createOrGet(String phrase){
    phrase = phrase.trim();
     if(candidatePhraseMap.containsKey(phrase)){
       return candidatePhraseMap.get(phrase);
     } else {
       CandidatePhrase p=  new CandidatePhrase(phrase);
       candidatePhraseMap.put(phrase, p);
       return p;
     }
  }

  public static CandidatePhrase createOrGet(String phrase, String phraseLemma){
    phrase = phrase.trim();
    if(candidatePhraseMap.containsKey(phrase)){
      CandidatePhrase p = candidatePhraseMap.get(phrase);
      p.phraseLemma = phraseLemma;
      return p;
    } else {
      CandidatePhrase p=  new CandidatePhrase(phrase, phraseLemma);
      candidatePhraseMap.put(phrase, p);
      return p;
    }
  }

  public static CandidatePhrase createOrGet(String phrase, String phraseLemma, Counter<String> features){
    phrase = phrase.trim();
    if(candidatePhraseMap.containsKey(phrase)){
      CandidatePhrase p = candidatePhraseMap.get(phrase);
      p.phraseLemma = phraseLemma;

      //If features are non-empty, add to the current set
      if(features != null && features.size() > 0){
        if(p.features == null) {
          p.features = new ClassicCounter<>();
        }
        p.features.addAll(features);
      }

      return p;
    }
    else{
      CandidatePhrase p=  new CandidatePhrase(phrase, phraseLemma, features);
      candidatePhraseMap.put(phrase, p);
      return p;
    }
  }



  private CandidatePhrase(String phrase, String lemma) {
    this(phrase, lemma, null);
  }

  private CandidatePhrase(String phrase, String lemma, Counter<String> features){

    if(phrase.isEmpty())
      new Exception("Creating empty candidatePhrase").printStackTrace(System.out);

    this.phrase = phrase;
    this.phraseLemma = lemma;
    this.features = features;
    this.hashCode = phrase.hashCode();
  }

  private CandidatePhrase(String w) {
    this(w, null, null);
  }

  public String getPhrase(){
    return phrase;
  }

  public String getPhraseLemma(){
    return phraseLemma;
  }

  public double getFeatureValue(String feat) {
    return features.getCount(feat);
  }

  @Override
  public String toString(){
    return phrase;
  }

  @Override
  public boolean equals(Object o){
    if(! (o instanceof CandidatePhrase))
      return false;

    return this.hashCode == o.hashCode();
  }

  @Override
  public int compareTo(Object o){
    if(! (o instanceof CandidatePhrase))
      return -1;
    else
      return ((CandidatePhrase)o).getPhrase().compareTo(this.getPhrase());
  }


  @Override
  public int hashCode(){
    return hashCode;
  }

  public static List<CandidatePhrase> convertStringPhrases(Collection<String> str){
    List<CandidatePhrase> phs = new ArrayList<>();
    for(String s: str){
      phs.add(CandidatePhrase.createOrGet(s));
    }
    return phs;
  }

  public static List<String> convertToString(Collection<CandidatePhrase> words) {
    List<String> phs = new ArrayList<>();
    for(CandidatePhrase ph: words){
      phs.add(ph.getPhrase());
    }
    return phs;
  }

  public Counter<String> getFeatures() {
    return features;
  }

  public void addFeature(String s, double v) {
    if(features == null){
      features = new ClassicCounter<>();
    }
    features.setCount(s, v);
  }

  public void addFeatures(Collection<String> feat) {
    if(features == null){
      features = new ClassicCounter<>();
    }
    Counters.addInPlace(features, feat);
  }

  public void setPhraseLemma(String phraseLemma) {
    this.phraseLemma = phraseLemma;
  }

  public static void deletePhrase(CandidatePhrase p) {
    candidatePhraseMap.remove(p);
  }
}
