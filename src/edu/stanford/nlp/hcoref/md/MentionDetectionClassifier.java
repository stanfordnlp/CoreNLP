package edu.stanford.nlp.hcoref.md;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.rf.RandomForest;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;

public class MentionDetectionClassifier implements Serializable {

  private static final long serialVersionUID = -4100580709477023158L;

  public RandomForest rf;
  
  public MentionDetectionClassifier(RandomForest rf) {
    this.rf = rf;
  }

  public static Counter<String> extractFeatures(Mention p, Set<Mention> shares, Set<String> neStrings, Dictionaries dict, Properties props) {
    Counter<String> features = new ClassicCounter<String>();
    
    String span = p.lowercaseNormalizedSpanString();
    String ner = p.headWord.ner();
    int sIdx = p.startIndex;
    int eIdx = p.endIndex;
    List<CoreLabel> sent = p.sentenceWords;
    CoreLabel preWord = (sIdx==0)? null : sent.get(sIdx-1);
    CoreLabel nextWord = (eIdx == sent.size())? null : sent.get(eIdx);
    CoreLabel firstWord = p.originalSpan.get(0);
    CoreLabel lastWord = p.originalSpan.get(p.originalSpan.size()-1);
    
    
    features.incrementCount("B-NETYPE-"+ner);
    if(neStrings.contains(span)) {
      features.incrementCount("B-NE-STRING-EXIST");
      if( ( preWord==null || !preWord.ner().equals(ner) ) && ( nextWord==null || !nextWord.ner().equals(ner) ) ) {
        features.incrementCount("B-NE-FULLSPAN");
      }
    }
    if(preWord!=null) features.incrementCount("B-PRECEDINGWORD-"+preWord.word());
    if(nextWord!=null) features.incrementCount("B-FOLLOWINGWORD-"+nextWord.word());
    
    if(preWord!=null) features.incrementCount("B-PRECEDINGPOS-"+preWord.tag());
    if(nextWord!=null) features.incrementCount("B-FOLLOWINGPOS-"+nextWord.tag());
    
    features.incrementCount("B-FIRSTWORD-"+firstWord.word());
    features.incrementCount("B-FIRSTPOS-"+firstWord.tag());
    
    features.incrementCount("B-LASTWORD-"+lastWord.word());
    features.incrementCount("B-LASTWORD-"+lastWord.tag());
    
    for(Mention s : shares) {
      if(s==p) continue;
      if(s.insideIn(p)) {
        features.incrementCount("B-BIGGER-THAN-ANOTHER");
        break;
      }
    }
    for(Mention s : shares) {
      if(s==p) continue;
      if(p.insideIn(s)) {
        features.incrementCount("B-SMALLER-THAN-ANOTHER");
        break;
      }
    }
    
//    if(Boolean.parseBoolean(props.getProperty("useSemantics", "false"))) {
//      // entire string in entity linking dict
//      features.incrementCount("VALDICT-MENTIONSTRING", dict.dictScore.getCount(p.lowercaseNormalizedSpanString()));
//    }
    
    return features;
  }
  
  public static MentionDetectionClassifier loadMentionDetectionClassifier(String filename) throws ClassNotFoundException, IOException {
    System.err.print("loading MentionDetectionClassifier ...");
    MentionDetectionClassifier mdc = IOUtils.readObjectFromFile(filename);
    System.err.println("done");
    return mdc;
  }
  
  public double probabilityOf(Mention p, Set<Mention> shares, Set<String> neStrings, Dictionaries dict, Properties props) {
    try {
      boolean dummyLabel = false;
      RVFDatum<Boolean, String> datum = new RVFDatum<Boolean, String>(extractFeatures(p, shares, neStrings, dict, props), dummyLabel);
      return rf.probabilityOfTrue(datum);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void classifyMentions(List<List<Mention>> predictedMentions, Dictionaries dict, Properties props) {
    Set<String> neStrings = Generics.newHashSet();
    for(int i=0 ; i < predictedMentions.size() ; i++) {
      for(Mention m : predictedMentions.get(i)) {
        String ne = m.headWord.ner();
        if(ne.equals("O")) continue;
        for(CoreLabel cl : m.originalSpan) {
          if(!cl.ner().equals(ne)) continue;
        }
        neStrings.add(m.lowercaseNormalizedSpanString());
      }
    }
    
    for(int i=0 ; i<predictedMentions.size() ; i++) {
      List<Mention> predicts = predictedMentions.get(i);
      
      Map<Integer, Set<Mention>> headPositions = Generics.newHashMap();
      for(Mention p : predicts) {
        if(!headPositions.containsKey(p.headIndex)) headPositions.put(p.headIndex, Generics.newHashSet());
        headPositions.get(p.headIndex).add(p);
      }
      
      Set<Mention> remove = Generics.newHashSet();
      for(int hPos : headPositions.keySet()) {
        Set<Mention> shares = headPositions.get(hPos);
        if(shares.size() > 1) {
          Counter<Mention> probs = new ClassicCounter<Mention>();
          for(Mention p : shares) {
            double trueProb = probabilityOf(p, shares, neStrings, dict, props);
            probs.incrementCount(p, trueProb);
          }
          
          // add to remove
          Mention keep = Counters.argmax(probs);
          probs.remove(keep);
          remove.addAll(probs.keySet());
        }
      }
      for(Mention r : remove) {
        predicts.remove(r);
      }
    }
  }
  
}
