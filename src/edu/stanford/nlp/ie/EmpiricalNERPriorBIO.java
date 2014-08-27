package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

import java.util.List;


/**
 * @author Mengqiu Wang
 */

public class EmpiricalNERPriorBIO<IN extends CoreMap> extends EntityCachingAbstractSequencePriorBIO<IN> {

  private double[][] entityMatrix, subEntityMatrix;
  private SeqClassifierFlags flags;

  protected double p1 = Math.log(0.01);
  protected double p2 = Math.log(2.0);
  protected int ORGIndex, LOCIndex;

  public static boolean DEBUG = false;

  public EmpiricalNERPriorBIO(String backgroundSymbol, Index<String> classIndex, Index<String> tagIndex, List<IN> doc, Pair<double[][], double[][]> matrices, SeqClassifierFlags flags) {
    super(backgroundSymbol, classIndex, tagIndex, doc);
    entityMatrix = matrices.first();
    subEntityMatrix = matrices.second();
    this.flags = flags;
    ORGIndex = tagIndex.indexOf("ORG");
    LOCIndex = tagIndex.indexOf("LOC");
  }

  public double scoreOf(int[] sequence) {
    double p = 0.0;
    for (int i = 0; i < entities.length; i++) {
      EntityBIO entity = entities[i];
      if ((i == 0 || entities[i-1] != entity) && entity != null) {
        int length = entity.words.size();
        int tag1 = entity.type;
        // String tag1 = classIndex.get(entity.type);

        int[] other = entities[i].otherOccurrences;
        for (int j = 0; j < other.length; j++) {

          EntityBIO otherEntity = null;
          for (int k = other[j]; k < other[j]+length && k < entities.length; k++) {
            otherEntity = entities[k]; 
            if (otherEntity != null) { 
              break;
            }
          }
          // singleton + other instance null?
          if (otherEntity == null) {
            continue;
          }

          int oLength = otherEntity.words.size();
          // String tag2 = classIndex.get(otherEntity.type);
          int tag2 = otherEntity.type;

          // exact match??
          boolean exact = false;
          int[] oOther = otherEntity.otherOccurrences;
          for (int k = 0; k < oOther.length; k++) {
            if (oOther[k] >= i && oOther[k] <= i+length-1) {
              exact = true;
              break;
            }
          }

          double factor = 0;
          if (exact) {
            if (DEBUG)
              System.err.print("Exact match of tag1=" + tagIndex.get(tag1) + ", tag2=" + tagIndex.get(tag2));
            // entity not complete
            if (length != oLength) {
              // if (DEBUG)
              //   System.err.println("Entity Not Complete");
              if (tag1 == tag2) {
                p += Math.abs(oLength - length) * p1;
              } else if (!(tag1 == ORGIndex && tag2 == LOCIndex ) &&
                         !(tag1 == LOCIndex && tag2 == ORGIndex)) {
                // shorter
                p += (oLength + length) * p1;
              }
            }
            factor = entityMatrix[tag1][tag2];
          } else {
            if (DEBUG)
              System.err.print("Sub  match of tag1=" + tagIndex.get(tag1) + ", tag2=" + tagIndex.get(tag2));
            factor = subEntityMatrix[tag1][tag2];
          }
          if (tag1 == tag2) {
            if (flags.matchNERIncentive) {
              factor = p2;
              // factor *= -1;
            } else 
              factor = 0;
          }
          if (DEBUG)
            System.err.println(" of factor=" + factor + ", p += " + (length * factor));
          p += length * factor;
        }
      }
    }
    return p;
  }
}
