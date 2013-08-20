package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;

import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.List;

/**
 * This has some common code for EmitMaps.
 *
 * @author Jim McFadden
 */
public abstract class AbstractEmitMap implements EmitMap {

  /**
   * Default implementation does nothing and returns 0.
   */
  public double tuneParameters(ClassicCounter<String> expectedEmissions, HMM hmm) {
    return (0);
  }

  public void printEmissions(PrintWriter out, boolean onlyCommon) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(6);
    nf.setMinimumFractionDigits(6);

    ClassicCounter c = getCounter();
    List keys = Counters.toSortedList(c);
    int pairsSize = keys.size();

    if (onlyCommon && pairsSize > 30) {
      out.println("Favorite words");
      out.println("--------------");
    } else {
      out.println("All words");
      out.println("---------");
    }

    if (onlyCommon) {
      for (int j = 0; j < 10; j++) {
        boolean printedSomething = false;
        for (int k = 0; k < 30; k += 10) {
          // check that the pairs List is big enough!
          if (j + k < pairsSize) {
            printedSomething = true;
            String key = (String) keys.get(j + k);
            double value = c.getCount(key);
            String piece = StringUtils.pad(key, 12) + " " + nf.format(value);
            out.print(piece);
          }
          if (printedSomething) {
            if (k == 20) {
              out.println();
            } else {
              out.print("    ");
            }
          }
        } // for k
      } // for j
    } else {
      for (int j = 0; j < pairsSize; j++) {

        String key = (String) keys.get(j);
        double value = c.getCount(key);
        String piece = StringUtils.pad(key, 15) + " " + nf.format(value);
        if (value < 0.00001) {
          piece += " (" + value + ")";
        }
        out.println(piece);
      } // for j
    }
    printUnseenEmissions(out, nf);
  }

}
