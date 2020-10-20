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

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.util.*;

import java.util.*;

/**
 * Subclass of CRFClassifier that performs dropout feature-noising training.
 *
 * @author Mengqiu Wang
 */
public class CRFClassifierWithDropout<IN extends CoreMap> extends CRFClassifier<IN>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CRFClassifierWithDropout.class);

  private List<List<IN>> unsupDocs;

  public CRFClassifierWithDropout(SeqClassifierFlags flags) {
    super(flags);
  }


  @Override
  protected Collection<List<IN>> loadAuxiliaryData(Collection<List<IN>> docs, DocumentReaderAndWriter<IN> readerAndWriter) {
    if (flags.unsupDropoutFile != null) {
      log.info("Reading unsupervised dropout data from file: " + flags.unsupDropoutFile);
      Timing timer = new Timing();
      timer.start();
      unsupDocs = new ArrayList<>();
      ObjectBank<List<IN>> unsupObjBank = makeObjectBankFromFile(flags.unsupDropoutFile, readerAndWriter);
      for (List<IN> doc : unsupObjBank) {
        for (IN tok: doc) {
          tok.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);
          tok.set(CoreAnnotations.GoldAnswerAnnotation.class, flags.backgroundSymbol);
        }
        unsupDocs.add(doc);
      }
      long elapsedMs = timer.stop();
      log.info("Time to read: : " + Timing.toSecondsString(elapsedMs) + " seconds");
    }
    if (unsupDocs != null && flags.doFeatureDiscovery) {
      List<List<IN>> totalDocs = new ArrayList<>();
      totalDocs.addAll(docs);
      totalDocs.addAll(unsupDocs);
      return totalDocs;
    } else
      return docs;
  }

  @Override
  protected CRFLogConditionalObjectiveFunction getObjectiveFunction(int[][][][] data, int[][] labels) {
    int[][][][] unsupDropoutData = null;
    if (unsupDocs != null) {
      Timing timer = new Timing();
      timer.start();
      List<Triple<int[][][], int[], double[][][]>> unsupDataAndLabels = documentsToDataAndLabelsList(unsupDocs);
      unsupDropoutData = new int[unsupDataAndLabels.size()][][][];
      for (int q=0; q<unsupDropoutData.length; q++)
        unsupDropoutData[q] = unsupDataAndLabels.get(q).first();
      long elapsedMs = timer.stop();
      log.info("Time to read unsupervised dropout data: " + Timing.toSecondsString(elapsedMs) + " seconds, read " + unsupDropoutData.length + " files");
    }

    return new CRFLogConditionalObjectiveFunctionWithDropout(data, labels, windowSize, classIndex,
      labelIndices, map, flags.priorType, flags.backgroundSymbol, flags.sigma, null, flags.dropoutRate, flags.dropoutScale, flags.multiThreadGrad, flags.dropoutApprox, flags.unsupDropoutScale, unsupDropoutData);
  }

} // end class CRFClassifierWithDropout
