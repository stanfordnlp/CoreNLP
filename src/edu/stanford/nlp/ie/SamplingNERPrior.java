package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;


/**
 * @author Jenny Finkel
 */
public class SamplingNERPrior extends EntityCachingAbstractSequencePrior<CoreLabel> {

  static final String ORG = "ORGANIZATION";
  static final String PER = "PERSON";
  static final String LOC = "LOCATION";
  static final String MISC = "MISC";

  public SamplingNERPrior(String backgroundSymbol, Index<String> classIndex, List<CoreLabel> doc) {
    super(backgroundSymbol, classIndex, doc);
  }

//   double penalty = 1.7;

//   public double getLogScore(int[] sequence) {
//     double p = 0.0;
//     for (int i = 0; i < entities.length; i++) {
//       Entity entity = entities[i];
//       if ((i == 0 || entities[i-1] != entity) && entity != null) {
//         int[] other = entities[i].otherOccurrences;
//         for (int j = 0; j < other.length; j++) {
//           Entity otherEntity = entities[other[j]];
//           if (otherEntity == null) {
//             p -= 1.0 * penalty;
//           } else if (otherEntity.type != entity.type) {
//             p -= 1.0 * penalty;
//           } else if (entity.words.size() + (other[j] - otherEntity.startPosition) > otherEntity.words.size()) {
//             p -= 1.0 * penalty;
//           }
//         }
//       }
//     }
//     return p;
//   }

  double penalty1 = 2.0;
  double penalty2 = 0.0;
  double penalty3 = 2.0;
  double penalty4 = 1.0;
  double penalty5 = 2.0;
  double penalty6 = 5.0;
  double penalty7 = 0.0;
  double penalty8 = 1.0;
  double penalty9 = 1.0;
  double penalty10 = 0.0;
  double penalty11 = 0.50;

  // private static Pattern yearPattern = Pattern.compile("(?:19|20)[0-9][0-9](?:-(?:19|20)?[0-9][0-9])?");

  public double scoreOf(int[] sequence) {
    double p = 0.0;
    for (int i = 0; i < entities.length; i++) {
      Entity entity = entities[i];
      //System.err.println(entity);
      if ((i == 0 || entities[i-1] != entity) && entity != null) {
        //System.err.println(1);
        int length = entity.words.size();
        String tag1 = classIndex.get(entity.type);

        int[] other = entities[i].otherOccurrences;
        for (int j = 0; j < other.length; j++) {

          // singleton + other instance null?
          if (entity.words.size() > 0 && entities[other[j]] == null) {
            p -= penalty8;
            continue;
          }


          Entity otherEntity = entities[other[j]]; 
          if (otherEntity == null) {
            //            p -= penalty7;
            continue;
          }
          int oLength = otherEntity.words.size();
          String tag2 = classIndex.get(otherEntity.type);
          // exact match??
          boolean exact = false;
          int[] oOther = otherEntity.otherOccurrences;
          for (int k = 0; k < oOther.length; k++) {
            if (oOther[k] == i) {
              exact = true;
              break;
            }
          }

          if (exact) {
            // entity not complete
            if (length != oLength) {
              //              System.err.println(entity.toString(classIndex)+"\n"+otherEntity.toString(classIndex)+"\n");

              // well do we want it to get longer or shorter?  
              if (tag1.equals(tag2)) { // ||
                //                  (tag1.equals(ORG) && tag2.equals(LOC)) ||
                //                  (tag1=2.equals(LOC) && tag1.equals(ORG))) {
                // longer
                p -= Math.abs(oLength - length) * penalty1;
                //              } else {
              } else if (!(tag1.equals(ORG) && tag2.equals(LOC)) &&
                         !(tag2.equals(LOC) && tag1.equals(ORG))) {
                // shorter
                p -= (oLength + length) * penalty1;
              }
            } 
            if (tag1.equals(tag2)) {
              // do nothing, we're happy
            } else if ((tag1.equals(ORG) && tag2.equals(LOC)) ||
                       (tag1.equals(LOC) && tag2.equals(ORG))) {
              // do nothing, we're happy
              p -= length * penalty11;
            } else {
              p -= length * penalty3;
            }
          } else {
            if (tag1.equals(tag2)) {
              // do nothing, we're happy
            } else if (tag1.equals(LOC) && tag2.equals(ORG)) {
              //p -= length * penalty4;
              // still happy
            } else if ((tag1.equals(MISC) && tag2.equals(ORG)) ||
                (tag1.equals(LOC) && tag2.equals(MISC))) {
              // only slightly unhappy
              p -= length * penalty4;
            } else if (tag1.equals(MISC) && tag2.equals(LOC)) {
              // mildly perturbed
              p -= length * penalty5;
            } else {
              // downright miserable
              p -= length * penalty6;
            }
          }
        }
      }
    }
    return p;
  }

}
