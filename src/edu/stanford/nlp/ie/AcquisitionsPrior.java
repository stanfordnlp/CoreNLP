package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Jenny Finkel
 */

public class AcquisitionsPrior<IN extends CoreMap> extends EntityCachingAbstractSequencePrior<IN> {

  double penalty = 4.0;
  double penalty1 = 3.0;
  double penalty2 = 4.0;

  public AcquisitionsPrior(String backgroundSymbol, Index<String> classIndex, List<IN> doc) {
    super(backgroundSymbol, classIndex, doc);
  }

  public double scoreOf(int[] sequence) {

    Set<String> purchasers = Generics.newHashSet();
    Set<String> purchabrs = Generics.newHashSet();
    Set<String> sellers = Generics.newHashSet();
    Set<String> sellerabrs = Generics.newHashSet();
    Set<String> acquireds = Generics.newHashSet();
    Set<String> acqabrs = Generics.newHashSet();

    List<Entity> purchasersL = new ArrayList<Entity>();
    List<Entity> purchabrsL = new  ArrayList<Entity>();
    List<Entity> sellersL = new  ArrayList<Entity>();
    List<Entity> sellerabrsL = new  ArrayList<Entity>();
    List<Entity> acquiredsL = new  ArrayList<Entity>();
    List<Entity> acqabrsL = new  ArrayList<Entity>();

    double p = 0.0;
    for (int i = 0; i < entities.length; i++) {
      Entity entity = entities[i];
      if ((i == 0 || entities[i-1] != entity) && entity != null) {

        String type = classIndex.get(entity.type);
        String phrase = StringUtils.join(entity.words, " ").toLowerCase();
        if (type.equals("purchaser")) {
          purchasers.add(phrase);
          purchasersL.add(entity);
        } else if (type.equals("purchabr")) {
          purchabrs.add(phrase);
          purchabrsL.add(entity);
        } else if (type.equals("seller")) {
          sellers.add(phrase);
          sellersL.add(entity);
        } else if (type.equals("sellerabr")) {
          sellerabrs.add(phrase);
          sellerabrsL.add(entity);
        } else if (type.equals("acquired")) {
          acquireds.add(phrase);
          acquiredsL.add(entity);
        } else if (type.equals("acqabr")) {
          acqabrs.add(phrase);
          acqabrsL.add(entity);
        } else {
          System.err.println("unknown entity type: "+type);
          System.exit(0);
        }
      }
    }
    
    for (Entity purchaser : purchasersL) {
      if (purchasers.size() > 1) {
        p -= purchaser.words.size() * penalty;
      }
      String s = StringUtils.join(purchaser.words, "").toLowerCase();
      boolean match = false;
      for (Entity purchabr : purchabrsL) {
        String s1 = StringUtils.join(purchabr.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s.indexOf(s1) >= 0) {
          match = true;
          break;
        }
      }
      if (!match && purchabrs.size() > 0) {
        p -= purchaser.words.size() * penalty;
      }
    }

    for (Entity seller : sellersL) {
      if (sellers.size() > 1) {
        p -= seller.words.size() * penalty;
      }
      String s = StringUtils.join(seller.words, "").toLowerCase();
      boolean match = false;
      for (Entity sellerabr : sellerabrsL) {
        String s1 = StringUtils.join(sellerabr.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s.indexOf(s1) >= 0) {
          match = true;
          break;
        }
      }
      if (!match && sellerabrs.size() > 0) {
        p -= seller.words.size() * penalty;
      }
    }
    
    for (Entity acquired : acquiredsL) {
      if (acquireds.size() > 1) {
        p -= acquired.words.size() * penalty;
      }
      String s = StringUtils.join(acquired.words, "").toLowerCase();
      boolean match = false;
      for (Entity acqabr : acqabrsL) {
        String s1 = StringUtils.join(acqabr.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s.indexOf(s1) >= 0) {
          match = true;
          break;
        }
      }
      if (!match && acqabrs.size() > 0) {
        p -= acquired.words.size() * penalty;
      }
    }

    
    for (Entity purchabr : purchabrsL) {
      //p -= purchabr.words.size() * penalty;
      String s = StringUtils.join(purchabr.words, "").toLowerCase();
      boolean match = false;
      for (Entity purchaser : purchasersL) {
        String s1 = StringUtils.join(purchaser.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (!match) {
        p -= purchabr.words.size() * penalty2;
      }
      
      match = false;
      for (Entity acquired : acquiredsL) {
        String s1 = StringUtils.join(acquired.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      for (Entity seller : sellersL) {
        String s1 = StringUtils.join(seller.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (match) {
        p -= purchabr.words.size() * penalty1;
      }
    }

    for (Entity sellerabr : sellerabrsL) {
      //p -= sellerabr.words.size() * penalty;
      String s = StringUtils.join(sellerabr.words, "").toLowerCase();
      boolean match = false;
      for (Entity seller : sellersL) {
        String s1 = StringUtils.join(seller.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (!match) {
        p -= sellerabr.words.size() * penalty2;
      }
      
      
      match = false;
      for (Entity acquired : acquiredsL) {
        String s1 = StringUtils.join(acquired.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      for (Entity purchaser : purchasersL) {
        String s1 = StringUtils.join(purchaser.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (match) {
        p -= sellerabr.words.size() * penalty1;
      }
    }


    for (Entity acqabr : acqabrsL) {
      //p -= acqabr.words.size() * penalty;
      String s = StringUtils.join(acqabr.words, "").toLowerCase();
      boolean match = false;
      for (Entity acquired : acquiredsL) {
        String s1 = StringUtils.join(acquired.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s1.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (!match) {
        p -= acqabr.words.size() * penalty2;
      }
      
      match = false;
      for (Entity seller : sellersL) {
        String s1 = StringUtils.join(seller.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          //System.err.println(acqabr.toString(classIndex)+"\n"+seller.toString(classIndex)+"\n");
          match = true;
            break;
        }
      }
      for (Entity purchaser : purchasersL) {
        String s1 = StringUtils.join(purchaser.words, "").toLowerCase();
        //int dist = StringUtils.longestCommonSubstring(s, s1);          
        //if (dist > s.length() - 2) {
        if (s1.indexOf(s) >= 0) {
          match = true;
          break;
        }
      }
      if (match) {
        p -= acqabr.words.size() * penalty1;
      }
    }

    return p;
  }
  
}
