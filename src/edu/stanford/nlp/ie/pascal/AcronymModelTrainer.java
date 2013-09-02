package edu.stanford.nlp.ie.pascal;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;
/**
 *    Used to train an {@link AcronymModel}.
 *
 *    @author Jamie Nicolson
 */

public class AcronymModelTrainer {

  public static final int NUM_SAMPLES = 10;
  public static final boolean SAMPLE_TEMPLATES = true;
  public static final String GOOD_ALIGNMENTS_FILE = "acnames012.out";

  public static void main(String [] args) throws Exception {
    AcronymModelTrainer trainer = new AcronymModelTrainer();
    trainer.train(args);
  }

  CRFClassifier classifier;

  public AcronymModelTrainer() throws Exception {
    goodAlignments = new HashSet<Alignment>();
    badAlignments = new HashSet<Alignment>();

    classifier = CRFClassifier.getClassifier("/u/nlp/data/iedata/Pascal2004/final/crf2.ser.gz");
  }

  AcronymModel.Feature[] features;
  // RVFDataset dataset;

  private void train(String[] inputFiles) throws IOException {
    constructAlignmentSets(inputFiles);

    features = new AcronymModel.Feature[] {
      new AcronymModel.LettersAligned(),
      new AcronymModel.BegWord(),
      new AcronymModel.EndWord(),
      new AcronymModel.AfterAligned(),
      new AcronymModel.AlignedPerWord(),
      new AcronymModel.WordsSkipped(),
      new AcronymModel.SyllableBoundary()
    };

    PrintStream out = new PrintStream(new FileOutputStream("x.dat"));
    System.out.println("goodAlignments has " + goodAlignments.size() +
      " elements");
    computeFeatureValues(goodAlignments, 1, out);
    computeFeatureValues(badAlignments, 0, out);
    out.close();
  }

  private void computeFeatureValues(HashSet<Alignment> alignments, int label,
    PrintStream out)
  {
    // int count = 0;
    for (Alignment alignment : alignments) {
      Double[] vals = new Double[features.length + 1];
      for (int f = 0; f < features.length; ++f) {
        vals[f] = new Double(features[f].value(alignment));
      }
      vals[features.length] = new Double(label);
      //System.out.println(a.toString( (count++) + ") ") );
      out.println(StringUtils.join(vals));
    }
  }


  /**
   * Builds badAlignments from the input files.
   */
  private void constructAlignmentSets(String[] inputFiles) throws IOException {
    readGoodAlignments(
      new BufferedReader(new FileReader(GOOD_ALIGNMENTS_FILE)));

    for (String filename : inputFiles) {
      ArrayList<CoreLabel> testList = CMMSampler.readWordInfos(filename);

      CMMSampler cmmSampler = new CMMSampler(testList, classifier,
              NUM_SAMPLES, SAMPLE_TEMPLATES);

      Counter<PascalTemplate> templateHash = cmmSampler.sampleDocuments();

      for (PascalTemplate pascalTemplate : templateHash.keySet()) {
        // pascalTemplate.print();

        String wsname = pascalTemplate.getValue("workshopname");
        String wsac = pascalTemplate.getValue("workshopacronym");
        String confname = pascalTemplate.getValue("conferencename");
        String confac = pascalTemplate.getValue("conferenceacronym");

        if (wsname != null && wsac != null) {
          insertAlignments(wsname, wsac);
        }
        if (confname != null && confac != null) {
          insertAlignments(confname, confac);
        }
      }
    }
  }

  private void insertAlignments(String name, String ac) {
    AlignmentFactory fact = new AlignmentFactory(name.toCharArray(),
      AcronymModel.stripAcronym(ac));

    int alignCount = 0;
    Iterator alignments = fact.getAlignments();
    int goodMatches = 0;
    while(alignments.hasNext()) {
      ++alignCount;
      Alignment a = (Alignment) alignments.next();
      if( ! goodAlignments.contains(a) ) {
        badAlignments.add(a);
      } else {
        ++goodMatches;
      }
    }
    System.out.println("There were " + alignCount + " alignments");
    System.out.println("There were " + goodMatches + " matches to a good alignment");
    if( goodMatches == 0 ) {
      System.out.println(name);
      System.out.println(ac);
    }
  }

  private HashSet<Alignment> goodAlignments;
  private HashSet<Alignment> badAlignments;

  private void readGoodAlignments(BufferedReader input) {
    while(true) {
      try {
        goodAlignments.add( new Alignment(input) );
      } catch(IOException e) {
        e.printStackTrace();
        break;
      }
    }
  }
}
