package edu.stanford.nlp.ie.hmm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ie.pnp.PnpClassifier;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;

/**
 * Pulls named entities from text using a char n-gram model and an HMM.
 * Run as a standalone app and pass in a properties file with the following
 * properties:
 * <ul>
 * <li><tt>trainFile</tt> - filename of training corpus (required)
 * <li><tt>testFile</tt> - filename of test corpus (required)
 * <li><tt>targetListFile</tt> - filename of target list (one target per line with <tt>targetField<space>example</tt>; optional)
 * <li><tt>maxNGramLength</tt> - largest char n-gram to consider (default: 6)
 * <li><tt>ignoreContext</tt> - whether to ignore prior text when starting a new target (default: false)
 * </ul>
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 */
public class HMMNamedEntityRecognizer<L> {
  // the ner2corpus.pl script puts this word between adjacent targets of the
  // same type so corpus can tell they're separate (we then take them out)
  public static final String ADJACENT = "ADJACENT_FIELDS";

  // whether to totally separate target fields from their context text
  public final boolean ignoreContext;

  /**
   * Creates a new instance of HMMNamedEntityRecognizer
   */
  public HMMNamedEntityRecognizer(Properties props) throws IOException {
    String[] targetFields = props.getProperty("targetFields").split(" ");
    Corpus<L, Word> trainDocs = new Corpus<L, Word>(targetFields);
    List<File> sources = new ArrayList<File>();
    sources.add(new File(props.getProperty("trainFile")));
    IteratorFromReaderFactory/*<Document<L, Word, Word>>*/ iter = CoNLL03DocumentIterator.factory(targetFields);
    trainDocs.load(sources, (IteratorFromReaderFactory<Datum<L, Word>>)iter);

    // initializes properties for PnpClassifiers based on maxNGramLength
    Properties pnpcProperties = CharSequenceEmitMap.getPnpClassifierProperties(Integer.parseInt(props.getProperty("maxNGramLength", "-1")));
    int nlength = Integer.parseInt(pnpcProperties.getProperty("cn"));

    // reads whether to ignore context for target fields
    ignoreContext = Boolean.parseBoolean(props.getProperty("ignoreContext", "false"));
    System.err.println("ignoreContext: " + ignoreContext);

    // reads in extra examples from the target list (if it's provided)
    Map<String, List<String>> listedExamplesByTargetField = new HashMap<String, List<String>>();
    String targetListFilename = props.getProperty("targetListFile");
    if (targetListFilename != null) {
      try {
        for(String line : ObjectBank.getLineIterator(new File(targetListFilename))) {
          // line looks liks: LOC New Jersey
          String[] fieldAndExample = line.split(" ", 2);
          String field = fieldAndExample[0];
          String example = fieldAndExample[1];
          List<String> listedExamples = listedExamplesByTargetField.get(field);
          if (listedExamples == null) {
            listedExamples = new ArrayList<String>();
          }
          listedExamples.add(example);
          listedExamplesByTargetField.put(field, listedExamples);
        }
        System.err.println("Listed example counts:");
        for (String targetField : listedExamplesByTargetField.keySet()) {
          List<String> listedExamples = listedExamplesByTargetField.get(targetField);
          System.err.println(targetField + ": " + listedExamples.size());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // computes how often each state type transitions to each other state type
    // index is: bg, target fields, start, end
    // count[i][j] = num times type j followed type i
    int[][] typeTransitionCounts = new int[targetFields.length + 3][targetFields.length + 3];
    for (int i = 0; i < typeTransitionCounts.length; i++) {
      Arrays.fill(typeTransitionCounts[i], 0);
    }
    boolean adjacentFields = false; // was last word a marker
    for (int i = 0; i < trainDocs.size(); i++) {
      int lastType = targetFields.length + 1; // start type
      TypedTaggedDocument<L> ttd = (TypedTaggedDocument<L>) trainDocs.get(i);
      int[] typeSequence = ttd.getTypeSequence();
      for (int j = 0; j < typeSequence.length; j++) {
        if (ttd.get(j).word().equals(ADJACENT)) {
          // found marker separating adjacent types
          adjacentFields = true;
          continue;
        }

        int curType = typeSequence[j];
        if (adjacentFields || curType != lastType) {
          typeTransitionCounts[lastType][curType]++;
        }
        lastType = curType;
        adjacentFields = false;
      }
      typeTransitionCounts[lastType][targetFields.length + 2]++; // end type
    }

    // trains PnpClassifiers on each target type (including background)
    String[] targetFieldsPlusBG = trainDocs.getTargetFields();
    PnpClassifier[] pnpcs = new PnpClassifier[targetFieldsPlusBG.length];
    for (int i = 0; i < targetFieldsPlusBG.length; i++) {
      System.err.println("Working on target: " + targetFieldsPlusBG[i]);
      pnpcs[i] = new PnpClassifier(pnpcProperties);
      Corpus<L, Word> targetDocs = new Corpus<L, Word>(trainDocs);
      for (int j = 0; j < targetDocs.size(); j++) {
        Document<L, Word, Word> doc = (Document<L, Word, Word>) targetDocs.get(j);
        //System.err.println(doc);
        String[] targetInstances = getTargetInstances(doc, i, ignoreContext ? 0 : nlength - 1, pnpcs[i].startSymbol);
        for (int k = 0; k < targetInstances.length; k++) {
          pnpcs[i].addCounts(targetInstances[k], ignoreContext);
        }
      }
      // feeds in listed examples (if any)
      List<String> listedExamples = listedExamplesByTargetField.get(targetFieldsPlusBG[i]);
      if (listedExamples != null) {
        for (int j = 0; j < listedExamples.size(); j++) {
          pnpcs[i].addCounts(listedExamples.get(j), true);
        }
      }

      // estimates parameters
      System.err.println(" - tuning parameters");
      pnpcs[i].tuneParameters();
    }

    // number of states in each target chain including "done" state
    int numTargetStates = nlength + 1;

    // start and finish state, then numTarget states for each target field and bg
    int numStates = 2 + targetFieldsPlusBG.length * numTargetStates;
    double[][] transitions = new double[numStates][numStates];
    for (int i = 0; i < transitions.length; i++) {
      Arrays.fill(transitions[i], 0.0);
    }

    // assigns types to each state
    int[] stateTypes = new int[numStates];
    stateTypes[0] = State.FINISHTYPE;
    stateTypes[1] = State.STARTTYPE;
    for (int i = 0; i < targetFieldsPlusBG.length; i++) {
      for (int j = 0; j < numTargetStates; j++) {
        stateTypes[2 + i * numTargetStates + j] = i;
      }
    }

    // initializes transition structure
    transitions[State.FINISHIDX][State.FINISHIDX] = 1.0; // end state loop
    for (int i = 0; i < targetFieldsPlusBG.length; i++) {
      int firstState = 2 + i * numTargetStates;
      int lastState = 2 + (i + 1) * numTargetStates - 1; // last state of this type
      for (int j = 0; j < numTargetStates - 1; j++) {
        int stateIndex = 2 + i * numTargetStates + j; // cur state
        transitions[stateIndex][stateIndex + 1] = 0.5; // go to next state
        transitions[stateIndex][lastState] = 0.5; // jump to end
        if (stateIndex == lastState - 1) {
          transitions[stateIndex][stateIndex] = 0.5; // loop in next-to-last state
        }
      }
      for (int j = 0; j < targetFieldsPlusBG.length; j++) {
        // end state -> other start states based on empirical counts
        int otherFirstState = 2 + j * numTargetStates; // first state of type j
        transitions[lastState][otherFirstState] = typeTransitionCounts[i][j];
      }
      // start state -> first state based on empirical counts
      transitions[State.STARTIDX][firstState] = typeTransitionCounts[targetFieldsPlusBG.length][i];
      // end state -> finish state based on empirical counts
      transitions[lastState][State.FINISHIDX] = typeTransitionCounts[i][targetFieldsPlusBG.length + 1];
    }

    // assigns the same pnpc to each of that type's emit states
    Structure structure = new Structure(transitions, stateTypes);
    State[] states = structure.getStates();
    for (int i = 0; i < targetFieldsPlusBG.length; i++) {
      for (int j = 0; j < numTargetStates; j++) {
        // states have increasing history and "done" state is special
        int charPosition = j == nlength ? -1 : j;
        states[2 + i * numTargetStates + j].emit = new CharSequenceEmitMap(pnpcs[i], charPosition, ignoreContext);
      }
    }

    // initializes transitions within target chains (all "free" 1.0's)
    // have to do this after making states because they'd get normalized
    for (int i = 0; i < targetFieldsPlusBG.length; i++) {
      //int firstState = 2 + i * numTargetStates;
      int lastState = 2 + (i + 1) * numTargetStates - 1; // last state of this type
      for (int j = 0; j < numTargetStates - 1; j++) {
        int stateIndex = 2 + i * numTargetStates + j; // cur sttae
        states[stateIndex].transition[stateIndex + 1] = 1.0; // free to go to next state
        states[stateIndex].transition[lastState] = 1.0; // free to jump to end
        if (stateIndex == lastState - 1) {
          states[stateIndex].transition[stateIndex] = 1.0; // free to loop in next-to-last state
        }
      }
    }

    HMM hmm = new HMM(states, HMM.REGULAR_HMM);
    hmm.setTargetFields(targetFieldsPlusBG);
    hmm.printProbs();

    // NER-style answer file to add your answers to
    String answerFilename = props.getProperty("answerFile");
    BufferedReader br = null; // for answer file
    if (answerFilename != null) {
      try {
        br = new BufferedReader(new FileReader(answerFilename));
      } catch (FileNotFoundException e) {
        throw(e);
      }
    }

    Corpus<L, TypedTaggedWord> testDocs = new Corpus<L, TypedTaggedWord>(targetFields);
    sources = new ArrayList<File>();
    sources.add(new File(props.getProperty("testFile")));
    IteratorFromReaderFactory/*<Document<L, TypedTaggedWord, TypedTaggedWord>>*/ iter1 = CoNLL03DocumentIterator.factory(targetFields);
    testDocs.load(sources, (IteratorFromReaderFactory<Datum<L, TypedTaggedWord>>)iter1);
    System.err.println("Testing on " + testDocs.size() + " docs...");
    if (answerFilename != null) {
      System.err.println("Writing NER-style output...");
    }
    Corpus<L, Word> testNGrams = new Corpus<L, Word>(targetFields);
    for (int i = 0; i < testDocs.size(); i++) {
      Document<L, TypedTaggedWord, TypedTaggedWord> doc = (Document<L, TypedTaggedWord, TypedTaggedWord>) testDocs.get(i);
      Document<L, Word, Word> ngrams = new TypedTaggedDocument<L>(targetFields); // tiled ngrams
      StringBuffer textSoFar = new StringBuffer();
      // pad with start symbols
      for (int j = 0; j < nlength - 1; j++) {
        textSoFar.append(pnpcs[0].startSymbol);
      }
      // add all ngrams in doc
      for (int j = 0; j < doc.size(); j++) {
        TypedTaggedWord ttw = doc.get(j);
        String word = ttw.word();
        if (word.equals(ADJACENT)) {
          continue; // skip markers
        }
        word += ' '; // need space after each word
        int type = ttw.type();
        for (int k = 0; k < word.length(); k++) {
          textSoFar.append(word.charAt(k));
          String ngram = textSoFar.substring(textSoFar.length() - nlength);
          ngrams.add(new TypedTaggedWord(ngram, type));
        }
      }
      testNGrams.add(ngrams); // add current doc to test corpus

      if (answerFilename != null) {
        String line;
        while ((line = br.readLine()).length() == 0) {
          System.out.println(); // blank lines
        }
        System.out.println(line + " O"); // DOCSTART (we have to call it bg)
        int[] stateSequence = hmm.viterbiSequence(ngrams);
        int[] typeSequence = hmm.getLabelsForSequence(stateSequence);
        //HMMTester.printTypeSequence(((TypedTaggedDocument)ngrams).getTypeSequence(),0);
        //HMMTester.printTypeSequence(typeSequence,0);
        //HMMTester.printTypeSequence(stateSequence,0);
        int[] wordLengths = getWordLengths(doc);
        int typeSequenceIndex = 0;
        int highestStateLastWord = 0;
        int typeLastWord = 0;
        for (int j = 0; j < doc.size(); j++) {
          while ((line = br.readLine()).length() == 0) {
            System.out.println(); // blank lines
          }
          System.out.print(line); // real line

          int state = stateSequence[typeSequenceIndex + 1]; // +1 for start state
          int type = typeSequence[typeSequenceIndex];
          boolean startedTarget = (type == typeLastWord && state < highestStateLastWord);
          typeLastWord = type;
          //System.err.print(doc.get(j)+" -> "+type);
          //if(startedTarget) System.err.print("!!");
          //System.err.print(" [");
          //System.out.print(doc.get(j)+" - ");
          System.out.print(' ');
          if (type == 0) {
            System.out.println("O");
          } else {
            System.out.println((startedTarget ? 'B' : 'I') + "-" + targetFieldsPlusBG[type]);
          }
          for (int k = 0; k < wordLengths[j] + 1; k++) // +1 for space
          {
            state = stateSequence[typeSequenceIndex + 1];
            //System.err.print(" "+state);
            highestStateLastWord = state;
            typeSequenceIndex++;
          }
          //System.err.println(" ]");
        }
      }
    }

    if (answerFilename == null) {
      new HMMTester(hmm).test(testNGrams, props, false, false, true);
    }

    /* // prints out type transition matrix
    DecimalFormat df=new DecimalFormat("000 ");
    System.err.print("BKG ");
    for(int i=0;i<targetFields.length;i++)
        System.err.print(targetFields[i]+" ");
    System.err.println("STA FIN");
    for(int i=0;i<typeTransitionCounts.length;i++)
    {
        for(int j=0;j<typeTransitionCounts.length;j++)
            System.err.print(df.format(typeTransitionCounts[i][j]));
        System.err.println();
    }
     */
  }

  /**
   * Returns the length of each word in the given doc.
   */
  private <T extends Word> int[] getWordLengths(Document<L, T, T> doc) {
    int[] wordLengths = new int[doc.size()];
    for (int i = 0; i < doc.size(); i++) {
      wordLengths[i] = doc.get(i).word().length();
    }
    return (wordLengths);
  }

  /**
   * Returns the complete list of Strings tagged with the given target type
   * in the given Document. Each target string is padded with numLeftContextChars
   * chars before the start of the actual target text. The document is padded
   * with startSymbol on the left for initial context.
   */
  private String[] getTargetInstances(Document<L, Word, Word> doc, int targetType, int numLeftContextChars, char startSymbol) {
    List<String> targetInstances = new ArrayList<String>();
    boolean inTargetWord = false;
    boolean startedTarget; // is current word first one in the target
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < numLeftContextChars; i++) {
      sb.append(startSymbol);
    }
    for (int i = 0; i < doc.size(); i++) {
      TypedTaggedWord ttw = (TypedTaggedWord) doc.get(i);
      int type = ttw.type();
      String word = ttw.word();
      startedTarget = false;
      if (type == targetType) {
        if (!inTargetWord) {
          // start target sequence (keep just enough context)
          // the +1 is because we'll add the space before the target word (unless this is the first word)
          sb.delete(0, sb.length() - numLeftContextChars + (i > 0 ? 1 : 0));
          inTargetWord = true;
          startedTarget = true;
        }
      } else if (inTargetWord) {
        // done with current target
        targetInstances.add(sb.toString());
        inTargetWord = false;
      }

      if (!word.equals(ADJACENT)) {
        // add to context (skips markers)
        if (i > 0) {
          sb.append(startedTarget ? startSymbol : ' ');
        }
        sb.append(word);
      }
    }
    if (inTargetWord) {
      targetInstances.add(sb.toString());
    }

    return targetInstances.toArray(new String[0]);
  }

  /**
   * Runs the named entity recognizer using the cmd line properties.
   * <pre>Usage: java edu.stanford.nlp.ie.hmm.HMMNamedEntityRecognizer propertiesFilename</pre>.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("java edu.stanford.nlp.ie.hmm.HMMNamedEntityRecognizer propertiesFilename");
      System.exit(1);
    }

    Properties props = new Properties();
    props.load(new FileInputStream(args[0]));
    new HMMNamedEntityRecognizer(props);
  }
}
