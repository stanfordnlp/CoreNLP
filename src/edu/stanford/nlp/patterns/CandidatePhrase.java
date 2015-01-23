package edu.stanford.nlp.patterns;

import edu.stanford.nlp.stats.Counter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Created by sonalg on 11/7/14.
 */
public class CandidatePhrase {
  //TODO: would computing hash code and storing it make this faster to access?
  final String phrase;
  final String phraseLemma;
  Counter<String> features;

  public CandidatePhrase(String phrase, String lemma){
    this(phrase, lemma, null);
  }
  public CandidatePhrase(String phrase, String lemma, Counter<String> features){
    this.phrase = phrase;
    this.phraseLemma = lemma;
    this.features = features;
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
      phs.add(new CandidatePhrase(s));
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
}
