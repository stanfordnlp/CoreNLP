// BisequenceCRFClassifier -- a probabilistic (CRF) sequence model that comprises of two sequences,
// mainly used for NER tagging of bilingual sentence aligned data.
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

import edu.stanford.nlp.ie.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.maxent.Convert;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.concurrent.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.Quadruple;
import edu.stanford.nlp.util.WelshPowellGraphColoring;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.ArrayUtils;

/**
 * This model comprises internally of two CRFClassifiers, one for each sequence in the bisequences. 
 * In the most typical usage, the two sequences represent two sentences in different languages (e.g., CH and EN).
 * It contains a prior sequence model (BisequenceAlignmentPrior) and uses Gibbs sampling to perform inference
 *
 * @author Mengqiu Wang
 */
public class BisequenceCRFClassifier<IN extends CoreMap> {

  // crf classifier on EN side
  CRFClassifier crfEn;
  // crf classifier on CH side
  CRFClassifier crfCh;

  AtomicInteger threadCompletionCounter = new AtomicInteger(0);

  // the two classifiers are expected to have the same classIndex
  Index<String> classIndexEn, classIndexCh;
  Index<String> tagIndexEn, tagIndexCh;
  Pair<double[][], double[][]> entityMatricesEn, entityMatricesCh;
  SeqClassifierFlags flags;
  Map<String, Map<String, Double>> softPriorMapCh, softPriorMapEn;


  // comparator for sorting alignment edges into descending order
  Comparator<Triple<Integer, Integer, Double>> alignmentComparator = new Comparator<Triple<Integer, Integer, Double>>() {
    public int compare(Triple<Integer, Integer, Double> s, Triple<Integer, Integer, Double> s2) {
      double x = s2.third() - s.third();
      return (x == 0.0) ? 0 : (x > 0.0) ? 1 : -1;
    }
  };

  /**
   * Name of default serialized classifier resource to look for in a jar file.
   */
  public static final String DEFAULT_CLASSIFIER = "edu/stanford/nlp/models/ner/bisequence.crf.ser.gz";
  private static final boolean VERBOSE = false;


  public BisequenceCRFClassifier(CRFClassifier<IN> crfEn, CRFClassifier<IN> crfCh, SeqClassifierFlags flags) {
    this.crfEn = crfEn;
    this.crfCh = crfCh;
    this.flags = flags;
  }

  /**
   * Read in the alignment links from the specified alignment file
   */
  private List<List<Triple<Integer, Integer, Double>>> readAlignments(String alignmentFile) {
    String line = null;
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(alignmentFile)))); 
      List<List<Triple<Integer, Integer, Double>>> alignments = new ArrayList<List<Triple<Integer, Integer, Double>>>();
      while( (line = br.readLine()) != null) {
        Set<Triple<Integer, Integer, Double>> sent = new HashSet<Triple<Integer, Integer, Double>>();

        line = line.trim();
        if (line.length() > 0) {
          String[] parts = line.split(" ");
          for (String part: parts) {
            String[] subparts = part.trim().split("-");
            int chIndex = Integer.parseInt(subparts[0]);
            int enIndex = Integer.parseInt(subparts[1]);
            String leftover = subparts[2];
            if (subparts.length > 3)
              for (int j = 3; j < subparts.length; j++)
                leftover += "-"+subparts[j];
            double prob = Double.parseDouble(leftover);
            if (prob >= flags.alignmentPruneThreshold) {
              Triple<Integer, Integer, Double> edge = new Triple(enIndex, chIndex, prob);
              sent.add(edge);
            }
          }
        }

        alignments.add(new ArrayList<Triple<Integer, Integer, Double>>(sent));
      }

      return alignments;
    } catch (Exception ex) {
      System.err.println("Reading alignments error in line:\n" + line);
      ex.printStackTrace();
      return null;
    }
  }

  /**
   * This method prunes the edges of nodes whose degree exceeds chromatic size
   * The pruning takes effect in the set of edges represented by parameter sent.
   * CURRENTLY NOT USED
   */
  private void pruneAlignments(Map<Integer, List<Triple<Integer, Integer, Double>>> edges, Set<Triple<Integer, Integer, Double>> sent, int chromaticSize) {
    for (Map.Entry<Integer, List<Triple<Integer, Integer, Double>>> entry : edges.entrySet()) {
      List<Triple<Integer, Integer, Double>> listOfE = entry.getValue();
      if (listOfE.size() > chromaticSize) {
        Collections.sort(listOfE, alignmentComparator);
        for (int i = chromaticSize; i < listOfE.size(); i++) {
          sent.remove(listOfE.get(i));
        }
      }
    }
  }

  public void classifyAndWriteAnswers() throws IOException {
    String testFileEn = flags.bisequenceTestFileEn;
    String testFileCh = flags.bisequenceTestFileCh;
    String testAlignmentFile = flags.bisequenceTestAlignmentFile;
    String testOutEn = flags.bisequenceTestOutputEn;
    String testOutCh = flags.bisequenceTestOutputCh;

    DocumentReaderAndWriter<IN> readerAndWriter = null;
    try {
      readerAndWriter = ((DocumentReaderAndWriter<IN>)
                         Class.forName(flags.readerAndWriter).newInstance());
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error loading flags.readerAndWriter: '%s'", flags.readerAndWriter), e);
    }
    readerAndWriter.init(flags);

    ObjectBank<List<IN>> documentsOBEn = crfEn.makeObjectBankFromFile(testFileEn, readerAndWriter);
    ObjectBank<List<IN>> documentsOBCh = crfCh.makeObjectBankFromFile(testFileCh, readerAndWriter);
    List<List<IN>> documentsEn = new ArrayList<List<IN>>();
    List<List<IN>> documentsCh = new ArrayList<List<IN>>();
    Iterator<List<IN>> documentsChOBItr = documentsOBCh.iterator();
    for (List<IN> enDoc: documentsOBEn) {
      documentsEn.add(enDoc);
      documentsCh.add(documentsChOBItr.next());
    }

    if (documentsEn.size() != documentsCh.size()) {
      throw new RuntimeException("documentsEnSize ("+documentsEn.size()+") != documentsChSize ("+documentsCh.size());
    }

    List<List<Triple<Integer, Integer, Double>>> alignments = readAlignments(testAlignmentFile);
    // partition sequence into subsequences for chromatic sampling
    Timing timer = new Timing();

    Iterator<List<IN>> documentsChItr = documentsCh.iterator();
    Iterator<List<Triple<Integer, Integer, Double>>> alignmentItr = alignments.iterator();
    List<List<List<Integer>>> partitions = new ArrayList<List<List<Integer>>>();
    if (flags.useChromaticSampling) {
      System.err.println("\nStart graph coloring");
      timer.start();
      for (List<IN> enDoc: documentsEn) {
        List<IN> chDoc = documentsChItr.next();
        List<Triple<Integer, Integer, Double>> alignment = alignmentItr.next();
        Map<Integer, Set<Integer>> graph = alignmentToGraph(alignment, enDoc.size(), chDoc.size());
        List<List<Integer>> partition = WelshPowellGraphColoring.colorGraph(graph);
        // for (List<Integer> p : partition)
        //   System.err.print(p.size() + " ");
        // System.err.println();
        partitions.add(partition);
      }
      long elapsedMs = timer.stop();
      System.err.println("Graph coloring took: " + Timing.toSecondsString(elapsedMs) + " seconds");
    }

    documentsChItr = documentsCh.iterator();
    alignmentItr = alignments.iterator();
    Iterator<List<List<Integer>>> partitionItr = partitions.iterator();

    PrintWriter enPW = new PrintWriter(new FileOutputStream(testOutEn));
    PrintWriter chPW = new PrintWriter(new FileOutputStream(testOutCh));

    if (flags.bisequencePriorType == BisequenceAlignmentPrior.softPrior) {
      if (flags.bisequenceAlignmentPriorPenaltyEn != null)
        softPriorMapEn = BisequenceAlignmentPrior.loadSoftPriorMap(flags.bisequenceAlignmentPriorPenaltyEn, false);
      if (flags.bisequenceAlignmentPriorPenaltyCh != null)
        softPriorMapCh = BisequenceAlignmentPrior.loadSoftPriorMap(flags.bisequenceAlignmentPriorPenaltyCh, true);
    }

    if (flags.useBilingualNERPrior) {
      tagIndexEn = new HashIndex<String>();
      for (String tag: classIndexEn.objectsList()) {
        String[] parts = tag.split("-");
        if (parts.length > 1)
          tagIndexEn.add(parts[parts.length-1]);
      }
      tagIndexEn.add(flags.backgroundSymbol);
      System.err.println("tagIndexEn: " + tagIndexEn.toString());
      entityMatricesEn = BisequenceEmpiricalNERPrior.readEntityMatrices(flags.entityMatrixEn, tagIndexEn);
      tagIndexCh = new HashIndex<String>();
      for (String tag: classIndexCh.objectsList()) {
        String[] parts = tag.split("-");
        if (parts.length > 1)
          tagIndexCh.add(parts[parts.length-1]);
      }
      tagIndexCh.add(flags.backgroundSymbol);
      System.err.println("tagIndexCh: " + tagIndexCh.toString());
      entityMatricesCh = BisequenceEmpiricalNERPrior.readEntityMatrices(flags.entityMatrixCh, tagIndexCh);
    }

    System.err.println("\nStart sampling");
    timer.start();
    ThreadsafeProcessor<Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>, Integer> threadProcessor = 
        new ThreadsafeProcessor<Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>, Integer>() {
      @Override
      public Integer process(Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>> quad) {
        List<IN> enDoc = quad.first();
        List<IN> chDoc = quad.second();
        List<Triple<Integer, Integer, Double>> alignment = quad.third();
        List<List<Integer>> partition = quad.fourth();
        try {
          classifyGibbs(enDoc, chDoc, alignment, partition);
        } catch (Exception e) {
          System.err.println("Error running testGibbs inference!");
          e.printStackTrace();
          return -1;
        }
        
        int completedNo = threadCompletionCounter.incrementAndGet();
        System.err.println("\n" + completedNo + " examples completed");
        return completedNo;
      }
      @Override
      public ThreadsafeProcessor<Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>, Integer> newInstance() {
        return this;
      }
    };
    MulticoreWrapper<Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>, Integer> wrapper = null;
    if (flags.multiThreadGibbs > 0) {
      wrapper = new MulticoreWrapper<Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>, Integer>(flags.multiThreadGibbs, threadProcessor); 
    }
      
    List<Integer> results = new ArrayList<Integer>();
    for (List<IN> enDoc: documentsEn) {
      List<IN> chDoc = documentsChItr.next();
      List<Triple<Integer, Integer, Double>> alignment = alignmentItr.next();
      List<List<Integer>> partition = null;
      if (flags.useChromaticSampling) {
        partition = partitionItr.next();
      }
      Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>> quad = 
        new Quadruple<List<IN>, List<IN>, List<Triple<Integer, Integer, Double>>, List<List<Integer>>>(enDoc, chDoc, alignment, partition);
      if (flags.multiThreadGibbs > 0) {
        wrapper.put(quad);
        while (wrapper.peek()) {
          results.add(wrapper.poll());
        }
      } else {
        results.add(threadProcessor.process(quad));
      }
    }
    if (flags.multiThreadGibbs > 0) {
      wrapper.join();
      while (wrapper.peek()) {
        results.add(wrapper.poll());
      }
    }

    // write answers
    documentsChItr = documentsCh.iterator();
    for (List<IN> enDoc: documentsEn) {
      List<IN> chDoc = documentsChItr.next();
      crfEn.writeAnswers(enDoc, enPW, readerAndWriter);
      crfCh.writeAnswers(chDoc, chPW, readerAndWriter);
    }
    long elapsedMs = timer.stop();
    if (results.size() == documentsEn.size() && results.indexOf(-1) == -1)
      System.err.println("\nSampling process completed for " + results.size() + " docs, took: " + Timing.toSecondsString(elapsedMs) + " seconds");
    else
      System.err.println("\nError in Sampling process, completed " + results.size() + " docs, indexOf(-1) == " + results.indexOf(-1));

  }

  private Map<Integer, Set<Integer>> alignmentToGraph(List<Triple<Integer, Integer, Double>> alignment, int enDocSize, int chDocSize) {
    Map<Integer, Set<Integer>> graph = new HashMap<Integer, Set<Integer>>();
    // convering alignments
    for(Triple<Integer, Integer, Double> edge: alignment) {
      Integer node1 = edge.first();
      Integer node2 = edge.second() + enDocSize;

      if (!graph.containsKey(node1)) {
        graph.put(node1, new HashSet<Integer>());
      }
      if (!graph.containsKey(node2)) {
        graph.put(node2, new HashSet<Integer>());
      }
      graph.get(node1).add(node2);
      graph.get(node2).add(node1);
    }
    for (int i = 0; i < enDocSize; i++) {
      if (!graph.containsKey(i)) {
        graph.put(i, new HashSet<Integer>());
      }
      if (i > 0)
        graph.get(i).add(i-1);
      if (i < enDocSize-1)
        graph.get(i).add(i+1);
    }
    int totalSize = enDocSize + chDocSize;
    for (int i = enDocSize; i < totalSize; i++) {
      if (!graph.containsKey(i)) {
        graph.put(i, new HashSet<Integer>());
      }
      if (i > enDocSize)
        graph.get(i).add(i-1);
      if (i < totalSize-1)
        graph.get(i).add(i+1);
    }

    return graph;
  }

  public void classifyGibbs(List<IN> enDoc, List<IN> chDoc, List<Triple<Integer, Integer, Double>> alignment, List<List<Integer>> partition) 
      throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException,
      InstantiationException, IllegalAccessException, InvocationTargetException {
    // System.err.println("Testing using Gibbs sampling.");
    Triple<int[][][], int[], double[][][]> pEn = crfEn.documentToDataAndLabels(enDoc);
    Triple<int[][][], int[], double[][][]> pCh = crfCh.documentToDataAndLabels(chDoc);
    CRFCliqueTree cliqueTreeEn = crfEn.getCliqueTree(pEn); 
    CRFCliqueTree cliqueTreeCh = crfCh.getCliqueTree(pCh); 
    int enDocSize = enDoc.size();
    int chDocSize = chDoc.size();

    SequenceModel crfModel = new SequentialSequenceModel(new SequenceModel[]{cliqueTreeEn, cliqueTreeCh});
    int[] listenerLengths = new int[]{cliqueTreeEn.length(), cliqueTreeCh.length()};
    SequenceListener crfListener = new SequentialSequenceListener(new SequenceListener[]{cliqueTreeEn, cliqueTreeCh}, listenerLengths);


    BisequenceAlignmentPrior biPrior = new BisequenceAlignmentPrior<IN>(flags.backgroundSymbol,
      classIndexEn, classIndexCh, alignment, enDocSize, chDocSize, flags, softPriorMapEn, softPriorMapCh);
    SequenceModel biPriorModel = biPrior;
    SequenceListener biPriorListener = biPrior;

    List<SequenceModel> models = new ArrayList<SequenceModel>();
    List<SequenceListener> listeners = new ArrayList<SequenceListener>();
    models.add(crfModel);
    models.add(biPriorModel);
    listeners.add(crfListener);
    listeners.add(biPriorListener);

    EmpiricalNERPriorBIO<IN> priorEn = null;
    EmpiricalNERPriorBIO<IN> priorCh = null;
    if (flags.useBilingualNERPrior) {
      if (tagIndexEn == null) {
        tagIndexEn = new HashIndex<String>();
        for (String tag: classIndexEn.objectsList()) {
          String[] parts = tag.split("-");
          if (parts.length > 1)
            tagIndexEn.add(parts[parts.length-1]);
        }
        tagIndexEn.add(flags.backgroundSymbol);
      }
      if (entityMatricesEn == null)
        entityMatricesEn = BisequenceEmpiricalNERPrior.readEntityMatrices(flags.entityMatrixEn, tagIndexEn);
      if (tagIndexCh == null) {
        tagIndexCh = new HashIndex<String>();
        for (String tag: classIndexCh.objectsList()) {
          String[] parts = tag.split("-");
          if (parts.length > 1)
            tagIndexCh.add(parts[parts.length-1]);
        }
        tagIndexCh.add(flags.backgroundSymbol);
      }
      if (entityMatricesCh == null)
        entityMatricesCh = BisequenceEmpiricalNERPrior.readEntityMatrices(flags.entityMatrixCh, tagIndexCh);

      priorEn = new EmpiricalNERPriorBIO<IN>(flags.backgroundSymbol, classIndexEn, tagIndexEn, enDoc, entityMatricesEn, flags);
      priorCh = new EmpiricalNERPriorBIO<IN>(flags.backgroundSymbol, classIndexCh, tagIndexCh, chDoc, entityMatricesCh, flags);
      SequenceModel nerModel= new SequentialSequenceModel(new SequenceModel[]{priorEn, priorCh});
      SequenceListener nerListener = new SequentialSequenceListener(new SequenceListener[]{priorEn, priorCh}, listenerLengths);

      models.add(nerModel);
      listeners.add(nerListener);
    }

    int numOfModels = models.size();
    double[] weights = new double[numOfModels];
    Arrays.fill(weights, 1.0);
    SequenceModel model = new FactoredSequenceModel(models.toArray(new SequenceModel[numOfModels]), weights);
    SequenceListener listener = new FactoredSequenceListener(listeners.toArray(new SequenceListener[numOfModels]));

    int samplingStyle = 0;
    if (flags.useChromaticSampling) {
      samplingStyle = 2;
    } else if (flags.useSequentialScanSampling) {
      samplingStyle = 1;
    }

    SequenceGibbsSampler sampler = new SequenceGibbsSampler(0, 0, listener, samplingStyle, 
      flags.maxAllowedChromaticSize, partition, flags.samplingSpeedUpThreshold, priorEn, priorCh);

    int[] sequence = new int[model.length()];
    if (flags.initViterbi) {
      CRFClassifier.TestSequenceModel testSequenceModelEn = new CRFClassifier.TestSequenceModel(cliqueTreeEn);
      CRFClassifier.TestSequenceModel testSequenceModelCh = new CRFClassifier.TestSequenceModel(cliqueTreeCh);
      ExactBestSequenceFinder tagInference = new ExactBestSequenceFinder();
      int[] bestSequenceEn = tagInference.bestSequence(testSequenceModelEn);
      int[] bestSequenceCh = tagInference.bestSequence(testSequenceModelCh);
      System.arraycopy(bestSequenceEn, crfEn.windowSize()- 1, sequence, 0, cliqueTreeEn.length());
      System.arraycopy(bestSequenceCh, crfCh.windowSize()- 1, sequence, cliqueTreeEn.length(), cliqueTreeCh.length());
    } else {
      int[] initialSequence = SequenceGibbsSampler.getRandomSequence(model);
      System.arraycopy(initialSequence, 0, sequence, 0, sequence.length);
    }

    sampler.verbose = 0;
    if (flags.annealingType.equalsIgnoreCase("linear")) {
      sequence = sampler.findBestUsingAnnealing(model, CoolingSchedule.getLinearSchedule(1.0, flags.numSamples),
          sequence);
    } else if (flags.annealingType.equalsIgnoreCase("exp") || flags.annealingType.equalsIgnoreCase("exponential")) {
      sequence = sampler.findBestUsingAnnealing(model, CoolingSchedule.getExponentialSchedule(1.0, flags.annealingRate,
          flags.numSamples), sequence);
    } else {
      throw new RuntimeException("No annealing type specified");
    }

    // System.err.println(ArrayMath.toString(sequence));
    for (int j = 0; j < enDocSize; j++) {
      IN wi = enDoc.get(j);
      if (wi == null) throw new RuntimeException("");
      if (classIndexEn == null) throw new RuntimeException("");
      wi.set(AnswerAnnotation.class, classIndexEn.get(sequence[j]));
    }
    for (int j = 0; j < chDocSize; j++) {
      IN wi = chDoc.get(j);
      if (wi == null) throw new RuntimeException("");
      if (classIndexCh == null) throw new RuntimeException("");
      wi.set(AnswerAnnotation.class, classIndexCh.get(sequence[enDocSize + j]));
    }
  }

  /** The main method. See the class documentation. */
  public static void main(String[] args) throws Exception {
    StringUtils.printErrInvocationString("BisequenceCRFClassifier", args);

    Properties props = StringUtils.argsToProperties(args);
    SeqClassifierFlags flags = new SeqClassifierFlags(props);

    String loadPathEn = flags.loadBisequenceClassifierEn;
    String loadPathCh = flags.loadBisequenceClassifierCh;
    String[] argsEn = new String[]{"-prop", flags.bisequenceClassifierPropEn};
    String[] argsCh = new String[]{"-prop", flags.bisequenceClassifierPropCh};

    Properties propsEn = StringUtils.argsToProperties(argsEn);
    Properties propsCh = StringUtils.argsToProperties(argsCh);

    CRFClassifier<CoreLabel> crfEn = new CRFClassifier<CoreLabel>(propsEn);
    if (loadPathEn != null) {
      crfEn.loadClassifierNoExceptions(loadPathEn, props);
    } 

    CRFClassifier<CoreLabel> crfCh = new CRFClassifier<CoreLabel>(propsCh);
    if (loadPathCh != null) {
      crfCh.loadClassifierNoExceptions(loadPathCh, props);
    }

    if (crfEn.classIndex.objectsList().size() != (crfCh.classIndex.objectsList().size()))
      throw new RuntimeException("classIndex of crfEn and crfCh size not equal\n" + "crfEn's classIndex: " + crfEn.classIndex.toString() + "\ncrfCh's classIndex: " + crfCh.classIndex.toString()); 

    System.err.println("crfEn's classIndex: " + crfEn.classIndex.toString() + "\ncrfCh's classIndex: " + crfCh.classIndex.toString()); 

    BisequenceCRFClassifier<CoreLabel> biCRF = new BisequenceCRFClassifier<CoreLabel>(crfEn, crfCh, flags);
    biCRF.classIndexEn = crfEn.classIndex;
    biCRF.classIndexCh = crfCh.classIndex;
    biCRF.classifyAndWriteAnswers();

    System.err.println("biCRF completed on " + new Date());
  } // end main
} // end class CRFClassifier
