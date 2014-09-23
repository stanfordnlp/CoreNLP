package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.ie.pascal.AcronymModel;
import edu.stanford.nlp.ling.CoreAnnotations;


import java.util.*;

/**
 * @author Jenny Finkel
 */

public class SeminarsPrior<IN extends CoreMap> extends EntityCachingAbstractSequencePrior<IN> {

  //double penalty = 4.0;
  double penalty = 2.3;
  //double penalty1 = 3.0;
  //double penalty2 = 4.0;

  public SeminarsPrior(String backgroundSymbol, Index<String> classIndex, List<IN> doc) {
    super(backgroundSymbol, classIndex, doc);
    init(doc);
  }

  private void init(List<IN> doc) {

    interned = new String[doc.size()];
    int i = 0;
    for (IN wi : doc) {
      interned[i++] = wi.get(CoreAnnotations.TextAnnotation.class).toLowerCase().intern();
    }

  }

  private String[] interned;

  public double scoreOf(int[] sequence) {

    Set<String> speakers = Generics.newHashSet();
    Set<String> locations = Generics.newHashSet();
    Set<String> stimes = Generics.newHashSet();
    Set<String> etimes = Generics.newHashSet();

    List<Entity> speakersL = new ArrayList<Entity>();
    List<Entity> locationsL = new  ArrayList<Entity>();
    List<Entity> stimesL = new  ArrayList<Entity>();
    List<Entity> etimesL = new  ArrayList<Entity>();

    double p = 0.0;
    for (int i = 0; i < entities.length; i++) {
      Entity entity = entities[i];
      if ((i == 0 || entities[i-1] != entity) && entity != null) {

        String type = classIndex.get(entity.type);
        String phrase = StringUtils.join(entity.words, " ").toLowerCase();
        if (type.equalsIgnoreCase("SPEAKER")) {
          speakers.add(phrase);
          speakersL.add(entity);
        } else if (type.equalsIgnoreCase("LOCATION")) {
          locations.add(phrase);
          locationsL.add(entity);
        } else if (type.equals("STIME")) {
          stimes.add(phrase);
          stimesL.add(entity);
        } else if (type.equals("ETIME")) {
          etimes.add(phrase);
          etimesL.add(entity);
        } else {
          System.err.println("unknown entity type: "+type);
          System.exit(0);
        }
      }
    }
    
    for (Entity stimeE : stimesL) {
      if (stimes.size() == 1) { break; }
      String stime = StringUtils.join(stimeE.words, " ");
      String time = "";
      for (char c : stime.toCharArray()) {
        if (c >= '0' && c <= '9') {
          time += c;
        }
      }
      if (time.length() == 1 || time.length() == 2) { time = time+"00"; }
      boolean match = false;
      for (String stime1 : stimes) {
        String time1 = "";
        for (char c : stime1.toCharArray()) {
          if (c >= '0' && c <= '9') {
            time1 += c;
          }
        }
        if (time1.length() == 1 || time1.length() == 2) { time1 = time1+"00"; }
        if (!time.equals(time1)) {
          p -= stimeE.words.size() * penalty;
          //System.err.println(time+" ("+s+") "+time1+" ("+s1+") "+stimes);
        }
      }
    }


    for (Entity etimeE : etimesL) {
      if (etimes.size() == 1) { break; }
      String etime = StringUtils.join(etimeE.words, " ");
      String time = "";
      for (char c : etime.toCharArray()) {
        if (c >= '0' && c <= '9') {
          time += c;
        }
      }
      if (time.length() == 1 || time.length() == 2) { time = time+"00"; }
      boolean match = false;
      for (String etime1 : etimes) {
        String time1 = "";
        for (char c : etime1.toCharArray()) {
          if (c >= '0' && c <= '9') {
            time1 += c;
          }
        }
        if (time1.length() == 1 || time1.length() == 2) { time1 = time1+"00"; }
        if (!time.equals(time1)) {
          p -= etimeE.words.size() * penalty;
          //System.err.println(time+" ("+s+") "+time1+" ("+s1+") "+etimes);
        }
      }
    }

//     for (Entity locationE : locationsL) {
//       String location = StringUtils.join(locationE.words, " ");
//       for (String location1 : locations) {
//         String s1 = location;
//         String s2 = location1;
//         if (s2.length() > s1.length()) {
//           String tmp = s2;
//           s2 = s1;
//           s1 = tmp;
//         }       
//         Pair<String,String> pair = new Pair(s1, s2);
//         Boolean b = aliasLocCache.get(pair);
//         if (b == null) {
//           double d = acronymModel.HearstSimilarity(s1, s2);
//           b = (d >= 0.7);
//           aliasLocCache.put(pair, b);
//         }
//         if (!b) {
//           p -= locationE.words.size() * penalty;
//         }
//       }
//     }

    int speakerIndex = classIndex.indexOf("SPEAKER");

    for (Entity speakerE : speakersL) {     
      //String lastName = speakerE.words.get(speakerE.words.size()-1);
      String lastName = interned[speakerE.startPosition+speakerE.words.size()-1];
      
      for (int i = 0; i < interned.length; i++) {
        String w = interned[i];
        if (w == lastName) {
          if (sequence[i] != speakerIndex) {
            p -= penalty;
          }
        }
      }
    }

    return p;
  }
  
  private static Map<Pair<String, String>, Boolean> aliasLocCache = Generics.newHashMap();

  private static AcronymModel acronymModel;

  static {
    try {
      acronymModel = new AcronymModel();
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

}
