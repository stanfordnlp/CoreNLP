// CRFClassifier -- a probabilistic (CRF) sequence model, mainly used for NER.
// Copyright (c) 2002-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu

package edu.stanford.nlp.ie.crf; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.util.*;

import java.io.*;

/**
 * Subclass of CRFClassifier for modeling noisy label

 * @author Mengqiu Wang
 */
public class CRFClassifierNoisyLabel<IN extends CoreMap> extends CRFClassifier<IN>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CRFClassifierNoisyLabel.class);

  protected double[][] errorMatrix;

  public CRFClassifierNoisyLabel(SeqClassifierFlags flags) {
    super(flags);
  }

  static double[][] readErrorMatrix(String fileName, Index<String> tagIndex, boolean useLogProb) {
    int numTags = tagIndex.size();
    int matrixSize = numTags;

    String[] matrixLines = new String[matrixSize];
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
      String line = null;
      int lineCount = 0;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        matrixLines[lineCount] = line;
        lineCount++;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }

    double[][] matrix = parseMatrix(matrixLines, tagIndex, matrixSize, false, useLogProb);

    log.info("Error Matrix P(Observed|Truth): ");
    log.info(ArrayUtils.toString(matrix));

    return matrix;
  }

  protected CRFLogConditionalObjectiveFunction getObjectiveFunction(int[][][][] data, int[][] labels) {
    if (errorMatrix == null) {
      if (flags.errorMatrix != null ) {
        if (tagIndex == null) {
          loadTagIndex();
        }
        errorMatrix = readErrorMatrix(flags.errorMatrix, tagIndex, true);
      }
    }
    return new CRFLogConditionalObjectiveFunctionNoisyLabel(data, labels, windowSize, classIndex,
      labelIndices, map, flags.priorType, flags.backgroundSymbol, flags.sigma, null, flags.multiThreadGrad, errorMatrix);
  }
} // end class CRFClassifier
