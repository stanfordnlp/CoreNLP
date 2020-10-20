package edu.stanford.nlp.coref.hybrid.sieve;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;

public class OracleSieve extends Sieve  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(OracleSieve.class);

  private static final long serialVersionUID = 3510248899162246138L;

  public OracleSieve(Properties props, String sievename) {
    super(props, sievename);
    this.classifierType = ClassifierType.ORACLE;
  }

  @Override
  public void findCoreferentAntecedent(Mention m, int mIdx, Document document, Dictionaries dict, Properties props, StringBuilder sbLog) throws Exception {
    for(int distance=0 ; distance <= m.sentNum ; distance++) {
      List<Mention> candidates = document.predictedMentions.get(m.sentNum-distance);

      for(Mention candidate : candidates) {
        if(!matchedMentionType(candidate, aTypeStr) || !matchedMentionType(m, mTypeStr)) continue;
//        if(!options.mType.contains(m.mentionType) || !options.aType.contains(candidate.mentionType)) continue;
        if(candidate == m) continue;
        if(distance==0 && m.appearEarlierThan(candidate)) continue;   // ignore cataphora

        if(Sieve.isReallyCoref(document, m.mentionID, candidate.mentionID)) {
          if(m.mentionType==MentionType.LIST) {
            log.info("LIST MATCHING MENTION : "+m.spanToString()+"\tANT: "+candidate.spanToString());
          }
          Sieve.merge(document, m.mentionID, candidate.mentionID);
          return;
        }
      }
    }
  }
}
