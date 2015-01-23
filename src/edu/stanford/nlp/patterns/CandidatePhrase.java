package edu.stanford.nlp.patterns;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by sonalg on 11/7/14.
 */
public class CandidatePhrase implements Serializable {
  //TODO: would computing hash code and storing it make this faster to access?
  final String phrase;
  final String phraseLemma;
  Counter<String> features;

  static ConcurrentHashMap<String, CandidatePhrase> candidatePhraseMap = new ConcurrentHashMap<String, CandidatePhrase>();

  static public CandidatePhrase createOrGet(String phrase){
     if(candidatePhraseMap.containsKey(phrase)){
       return candidatePhraseMap.get(phrase);
     }
    else
       return new CandidatePhrase(phrase);
  }

  public CandidatePhrase(String phrase, String lemma){
    this(phrase, lemma, null);
  }

  public CandidatePhrase(String phrase, String lemma, Counter<String> features){
    this.phrase = phrase;
    this.phraseLemma = lemma;
    this.features = features;
    candidatePhraseMap.put(phrase, this);
  }

  public CandidatePhrase(String w) {
    this(w, null);
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
    return ((CandidatePhrase)o).getPhrase().equals(this.getPhrase());
  }

  @Override
  public int hashCode(){
    return phrase.hashCode();
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
      features = new ClassicCounter<String>();
    }
    features.setCount(s, v);
  }

  public void addFeatures(Collection<String> feat) {
    if(features == null){
      features = new ClassicCounter<String>();
    }
    Counters.addInPlace(features, feat);
  }
}
