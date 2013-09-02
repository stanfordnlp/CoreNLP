/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.tagger.maxent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * As of 23 January 2008, I have no idea what this class if for.  It is never referenced anywhere other than its own
 * main method. - rafferty
 *
 */
public class ErrorTags {
  private final TTags tags = new TTags("english");
  private final int[][] cMatrix;
  private final int[] sums;
  private boolean[] nonzeros;

  private ErrorTags(String fileoutput, String fileTags) {
    tags.read(fileTags);
    cMatrix = new int[tags.getSize()][tags.getSize()];
    sums = new int[tags.getSize()];
    try {

      BufferedReader inF = new BufferedReader(new FileReader(fileoutput));
      for (String sent;(sent = inF.readLine()) != null; ) {
        StringTokenizer st = new StringTokenizer(sent);
        String lastTag = ".";
        boolean lastWrong = true;
        while (st.hasMoreElements()) {
          String wordTag = st.nextToken();
          if (wordTag.startsWith("|")) {
            lastWrong = true;
            String tag = wordTag.substring(1);
            int correct = tags.getIndex(tag);
            int wrong = tags.getIndex(lastTag);
            if ((correct == -1) || (wrong == -1)) {
              System.out.println(tag + " " + lastTag);
              lastTag = tag;
              continue;
            }
            cMatrix[correct][wrong]++;
            sums[correct]++;
            lastTag = tag;
          } else {
            // the previous thing was correct if not lastWrong and read the next thing
            try {
              if (!lastWrong) {
                int corr1 = tags.getIndex(lastTag);
                cMatrix[corr1][corr1]++;
                sums[corr1]++;
              }
              lastTag = wordTag.substring(wordTag.lastIndexOf('\\') + 1);
              lastWrong = false;
            } catch (Exception e) {
              // keep going
            }
          }// else

        }// while

      } // while
      inF.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private void print() {

    print_non_zeros();
    int len = tags.getSize();
    System.out.print("\t");
    for (int k = 0; k < len; k++) {
      if (nonzeros[k]) {
        System.out.print(tags.getTag(k) + "\t");
      }
    }
    System.out.println();

    for (int i = 0; i < len; i++) {
      if (nonzeros[i]) {
        System.out.print(tags.getTag(i) + "\t");
        for (int j = 0; j < len; j++) {
          if (nonzeros[j]) {
            float x = (int) (cMatrix[i][j] * 1000 / (float) sums[i]) / (float) 10.0;
            System.out.print(x + "%" + "\t ");
          }
        }
        System.out.println();
      }
    }
  }


  private void print_non_zeros() {
    // true if there is a smth
    //greater than 1 there
    int numNonzeros = 0;
    int len = tags.getSize();
    nonzeros = new boolean[tags.getSize()];
    for (int i = 0; i < len; i++) {
      for (int j = 0; j < len; j++) {
        if (cMatrix[i][j] > 20) {
          nonzeros[i] = true;
          numNonzeros++;
          break;
        } // if
      } // for j
    }//
    System.out.println(" Number of non zeros " + numNonzeros);

  }


  public static void main(String[] args) {

    ErrorTags eT = new ErrorTags(args[0], args[1]);
    eT.print();

  }


}
