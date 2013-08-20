package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Class for HMM structure learning.
 * <p/>
 * The usage of HMMLearner is learning the HMM structure from training samples<p>
 * The demo file demo.txt is come from the example in Stolcke's paper "Best-first Model Merging
 * fro Hidden Markov Model Induction". It has two training samples: string "a b" and string "a b a b".
 * Our learning algorithm will create a most specific model having six target states and learn a general
 * HMM with only two target states left.<p>
 * The command for demo is:<p>
 * java edu.stanford.nlp.ie.hmm.HMMLearner -v demo demo.txt<p>
 * The initial model is initial_0.hmm, the final model is stored as final.hmm, and all intermediate models
 * are saved as inter_0_*.hmm. User can use HMMGrapher to view these hmm structures. The command for using
 * HMMGrapher is: <p>
 * java edu.stanford.nlp.ie.hmm.HMMGrapher &lt;HMMFile&gt;<p>
 *
 * @author Haoyi Wang, Zhen Yin
 * @see CountedEmitMap
 */

public class HMMLearner {

  private String targetField;

  private String targetFile;

  private Corpus trainSet;

  private Corpus data;

  private boolean verbose;

  private double emissionPriorParas[];

  private double emissionPriorSum;


  private int time;

  /**
   * The constructor for HMMLearner
   * <p/>
   * an empty method
   */

  public HMMLearner() {
  }


  /**
   * The constructor for HMMLearner
   *
   * @param field TargetField
   * @param file  Training file
   * @param v     Verbose or not
   */

  public HMMLearner(String field, String file, boolean v) {
    targetField = field;
    targetFile = file;
    verbose = v;
  }


  /**
   * In case the input is not specified, this method will be called to prompt error message
   *
   * @param extraMessage Error message
   */

  private static void dieUsage(String extraMessage) {
    System.err.println("Usage: java HMMLearner [-v] targetField targetFile");
    if (extraMessage != null) {
      System.err.println();
      System.err.println(extraMessage);
    }
    System.exit(1);
  }


  /**
   * Main method for HMMLearner class.<p>
   * Command line format "java edu.stanford.nlp.ie.hmm.HMMLearner [-v] TargetField TargetFile"
   * TargetField is tagged field in the traning file, like "title", TargetFile is the training file.
   * The option -v is used to generate intermediate hmm results and more informations.
   */

  public static void main(String[] args) {
    if (args.length == 0) {
      dieUsage(null);
    }
    int arg = 0;
    boolean verbose = false;
    if (args[arg].equals("-v")) {
      verbose = true;
      arg++;
    }

    if (args.length < arg + 2) {
      dieUsage("must specify target field and file");
    }

    String targetField = args[arg++];
    String targetFile = args[arg];

    //Create a HMMLearner
    HMMLearner hmml = new HMMLearner(targetField, targetFile, verbose);

    //Learn the HMM
    hmml.buildModel();
  }


  /**
   * Incrementally learning the HMM and save the final model as "final.hmm"
   */

  public void buildModel() {
    //Read the training set
    trainSet = new Corpus(targetFile, targetField);
    trainSet.retainOnlyTarget(targetField);

    //The accumulate training samples
    data = new Corpus(targetField);

    ArrayList stateList = new ArrayList();

    //Initialize the start and end states
    State endState = new State(State.FINISHTYPE, null, 2);
    State startState = new State(State.STARTTYPE, null, 2);

    endState.transition[State.FINISHIDX] = 1.0;
    stateList.add(endState);

    stateList.add(startState);

    State[] states = (State[]) stateList.toArray(new State[0]);
    HMM hmm = new HMM(states, HMM.TARGET_HMM);
    hmm.setTargetFields(data.getTargetFields());

    ObjectOutputStream oos;

    try {
      int batchNum = 3;
      int batchTimes = ((trainSet.size() / batchNum) == 0) ? 1 : trainSet.size() / batchNum;

      for (time = 0; time < batchTimes; time++) {
        int startDocNum = time * batchNum;
        int endDocNum = ((startDocNum + batchNum) < trainSet.size()) ? startDocNum + batchNum : trainSet.size();
        for (int docIndex = startDocNum; docIndex < endDocNum; docIndex++) {
          //Add new samples and build the specific model for the new training samples
          hmm = addNewDoc(hmm, (Document) trainSet.get(docIndex));
        }

        if (verbose) {
          //Record the initial hmm
          oos = new ObjectOutputStream(new FileOutputStream("initial_" + time + ".hmm"));
          oos.writeObject(hmm);
          oos.close();
        }

        //Fill the alpha array for emissions
        ClassicCounter v = new ClassicCounter(data.getVocab());
        emissionPriorParas = new double[v.size()];
        Iterator iter = v.keySet().iterator();
        int pos = 0;
        while (iter.hasNext()) {

          // adds each count from new vocab, merging as needed
          Object key = iter.next();
          double count = v.getCount(key);
          emissionPriorParas[pos++] = count;
        }

        emissionPriorSum = 0.0;
        for (int i = 0; i < emissionPriorParas.length; i++) {
          emissionPriorSum += emissionPriorParas[i];
        }

        //Learn a general model
        hmm = learnModel(hmm);

        if (verbose) {
          oos = new ObjectOutputStream(new FileOutputStream("final_" + time + ".hmm"));
          oos.writeObject(hmm);
          oos.close();
        }
      }

      oos = new ObjectOutputStream(new FileOutputStream("final.hmm"));
      oos.writeObject(hmm);
      oos.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Add a sample into the model
   *
   * @return new hmm model
   */

  private HMM addNewDoc(HMM baseHmm, Document newDoc) {
    State[] baseStates = baseHmm.getStates();

    //Get the size of the new model
    int numStates = baseStates.length + newDoc.size();

    ArrayList stateList = new ArrayList(numStates);

    data.add(newDoc);

    //Make the target chain and adjust the transition from the start state

    double numTargetChains = data.size();
    double initProb = 1.0 / numTargetChains;
    double oldPercent = (numTargetChains - 1) / numTargetChains;

    //Transfer the old transitions for existing states
    for (int i = 0; i < baseStates.length; i++) {
      double[] t = new double[numStates];
      for (int j = 0; j < baseStates.length; j++) {
        t[j] = baseStates[i].transition[j];
        if (i == State.STARTIDX) {
          t[j] = t[j] * oldPercent;
        }
      }
      baseStates[i].transition = t;
      stateList.add(baseStates[i]);
    }

    //Add the target chain for sample
    int stateNum = baseStates.length;
    String[] vocab = new String[1];
    int[] counts = new int[1];
    ((State) stateList.get(State.STARTIDX)).transition[stateNum] = initProb;

    for (int i = 0, dsize = newDoc.size(); i < dsize; i++) {
      TypedTaggedWord ttw = (TypedTaggedWord) newDoc.get(i);
      String word = ttw.word();
      //the initial target state only emit one word
      vocab[0] = word;
      counts[0] = 1;
      EmitMap initailEmitMap = new CountedEmitMap(vocab, counts);
      State target = new State(Structure.TARGET_TYPE, initailEmitMap, numStates);
      stateList.add(target);
      stateNum++;

      if (i < dsize - 1) {
        //Goto the next state;
        target.transition[stateNum] = 1.0;
      } else {
        //end of the chain, go to the end state
        target.transition[State.FINISHIDX] = 1.0;
      }
    }

    State[] states = (State[]) stateList.toArray(new State[0]);

    //Set the new HMM which is specific
    HMM hmm = new HMM(states, HMM.TARGET_HMM);

    hmm.setTargetFields(data.getTargetFields());

    hmm.setVocab(new ClassicCounter(data.getVocab()));

    return hmm;
  }


  /**
   * Learn the HMM structure from the most specific model.<p>
   * Merge the states if the posterior can be increased
   *
   * @param baseHmm Input specific model
   * @return Learned general model
   */

  public HMM learnModel(HMM baseHmm) {

    State[] baseStates = baseHmm.getStates();
    int iteration = 0;

    //Initial posterior for model, logP(M|X) ~ logP(M) + logP(X|M)

    double bestLogPosterior = logPriorProb(baseHmm) + baseHmm.logLikelihood(data, false);

    HMM bestHmm = baseHmm;

    boolean change = true;

    //Keep merging until no better model can be created or only one target state left

    while (baseStates.length > 3 && change) {

      change = false;
      iteration++;

      //Try all possible merge and fins the best one

      for (int firstIndex = 2; firstIndex < baseStates.length; firstIndex++) {

        for (int secondIndex = firstIndex + 1; secondIndex < baseStates.length; secondIndex++) {

          ArrayList currentStateList = new ArrayList(baseStates.length);

          for (int m = 0; m < baseStates.length; m++) {
            currentStateList.add(copyState(baseStates[m]));
          }

          State[] currentStates = mergeTwoStates(currentStateList, firstIndex, secondIndex);

          HMM currentHmm = new HMM(currentStates, HMM.TARGET_HMM);

          currentHmm.setTargetFields(data.getTargetFields());

          currentHmm.setVocab(new ClassicCounter(data.getVocab()));

          //Get the posterior for the current HMM

          double currentLogPosterior = logPriorProb(currentHmm) + currentHmm.logLikelihood(data, false);

          if (currentLogPosterior > bestLogPosterior) {
            bestLogPosterior = currentLogPosterior;
            bestHmm = currentHmm;
            change = true;
            if (verbose) {
              try {
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("inter_" + time + "_" + currentStates.length + ".hmm"));
                oos.writeObject(bestHmm);
                oos.close();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          }

        }

      }

      baseStates = bestHmm.getStates();

      if (verbose) {
        System.err.println("Posterior for the best model is: " + bestLogPosterior);
      }
    }

    return bestHmm;

  }


  /**
   * copy the state from the source state
   */

  private State copyState(State s) {

    double[] transition = new double[s.transition.length];

    for (int i = 0; i < transition.length; i++) {
      transition[i] = s.transition[i];
    }

    if (s.type < 0) {
      return (new State(s.type, null, transition));
    } else {
      return (new State(s.type, new CountedEmitMap((CountedEmitMap) s.emit), transition));
    }
  }


  /**
   * Merge two states to get a new HMM structure
   *
   * @param stateList   States in the old HMM
   * @param firstIndex  The first state's index
   * @param secondIndex The second state's index
   * @return The new state array with one state less
   */

  public State[] mergeTwoStates(ArrayList stateList, int firstIndex, int secondIndex) {

    State first = (State) stateList.get(firstIndex);
    State second = (State) stateList.get(secondIndex);

    //Merge the emission distribution

    double ratio = ((CountedEmitMap) first.emit).merge((CountedEmitMap) second.emit);

    double[] weights = new double[2];

    //Get the weights for transition distribution merge

    weights[0] = 1.0 / (1.0 + ratio);

    weights[1] = 1.0 - weights[0];

    //Move the transitions TO the second state, add them to the first state

    for (int i = 0; i < stateList.size(); i++) {

      if (i != firstIndex && i != secondIndex && ((State) stateList.get(i)).transition[secondIndex] > 0.0) {

        ((State) stateList.get(i)).transition[firstIndex] += ((State) stateList.get(i)).transition[secondIndex];

      }

    }

    //Merge the transitions FROM the first and second states

    for (int i = 0; i < stateList.size(); i++) {

      if (i != firstIndex && i != secondIndex &&

              (first.transition[i] > 0.0 || second.transition[i] > 0.0)) {

        first.transition[i] = weights[0] * first.transition[i] + weights[1] * second.transition[i];

      }
    }

    //Merge the self loops and transitions between these two states

    first.transition[firstIndex] = weights[0] * (first.transition[firstIndex] + first.transition[secondIndex])

            + weights[1] * (second.transition[secondIndex] + second.transition[firstIndex]);

    //Adjuist other states' transitions

    stateList.remove(secondIndex);

    for (int i = 0; i < stateList.size(); i++) {
      State s = (State) stateList.get(i);
      double[] t = new double[stateList.size()];
      for (int j = 0; j < t.length; j++) {
        if (j < secondIndex) {
          t[j] = s.transition[j];
        } else {
          t[j] = s.transition[j + 1];
        }
      }
      s.transition = t;
    }

    State[] states = (State[]) stateList.toArray(new State[0]);
    return states;
  }


  /**
   * Calculate the log of prior probability for a HMM
   */

  private double logPriorProb(HMM hmm) {

    State[] states = hmm.getStates();
    int stateSize = states.length;
    ClassicCounter vocab = new ClassicCounter(data.getVocab());
    int vocabSize = vocab.size();

    double[] transitionPriorParas = new double[stateSize];
    double[] transitionCount = new double[stateSize];
    double[] emissionCount = new double[vocabSize];

    double logPrior = 0.0;

    //Assign the prior parameters for transitions
    for (int i = 0; i < stateSize; i++) {
      transitionPriorParas[i] = 1.0;
    }

    //Traverse all states in the model, accumulate their structure prior and parameter prior

    for (int i = 0; i < stateSize; i++) {
      State s = states[i];
      double numVisits;
      double numTransitions = 0.0;
      double numEmissions = 0.0;
      double statePrior = 0.0;
      double betaTran = 1.0, betaEmit = 1.0;
      double accumTran = stateSize * 1.0;
      double accumEmit = emissionPriorSum;

      if (s.type < 0) {
        numVisits = data.size();
      } else {
        numVisits = ((CountedEmitMap) s.emit).getCount();
      }

      //Get the count + prior for transistions

      for (int j = 0; j < stateSize; j++) {
        if (s.transition[j] == 0.0) {
          transitionCount[j] = transitionPriorParas[j];
        } else {
          transitionCount[j] = transitionPriorParas[j] + numVisits * s.transition[j];
          numTransitions += 1.0;
          //Calculate the parameter prior for transition by the special feature of Beta function

          for (int k = 1; k <= (int) (numVisits * s.transition[j]); k++) {

            betaTran *= (transitionPriorParas[j] + k - 1) / (++accumTran - 1);
          }
        }
      }

      //Get the count + prior for emissions
      if (s.type < 0) {
        //no emissions for start and finish state

        for (int j = 0; j < vocabSize; j++) {
          emissionCount[j] = emissionPriorParas[j];
        }
      } else {
        ClassicCounter globalVocab = new ClassicCounter(data.getVocab());
        ClassicCounter localVocab = ((CountedEmitMap) s.emit).getVocab();
        Iterator iter = globalVocab.keySet().iterator();
        int pos = 0;

        while (iter.hasNext()) {
          // adds count for each word type in the local vocabulary
          Object key = iter.next();
          double count = localVocab.getCount(key);
          if (count != 0.0) {
            emissionCount[pos] = emissionPriorParas[pos] + count;
            numEmissions += 1.0;
            // Calculate the parameter prior for emission by the
            // special feature of Beta function
            for (int k = 1; k <= (int) count; k++) {
              betaEmit *= (emissionPriorParas[pos] + k - 1) / (++accumEmit - 1);
            }
          } else {
            emissionCount[pos] = emissionPriorParas[pos];
          }
          pos++;
        }
      }
      //Calculate Log of the structure prior
      statePrior += (-1.0) * numTransitions * Math.log(stateSize + 1.0);
      statePrior += (-1.0) * numEmissions * Math.log(vocabSize + 1.0);
      //Calculate Log of the parameter prior
      if (betaTran > 1.2E-321) {
        statePrior += Math.log(betaTran);
      }
      if (betaEmit > 1.2E-321) {
        statePrior += Math.log(betaEmit);
      }
      logPrior += statePrior;
    }
    return logPrior;
  }

}
