package edu.stanford.nlp.wordseg;

import edu.stanford.nlp.fsm.DFSA;
import edu.stanford.nlp.fsm.DFSAState;
import edu.stanford.nlp.fsm.DFSATransition;
import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.WordSegmenter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.io.*;
import java.util.regex.Pattern;

/**
 * Lexicon-based semgenter. Uses dynamic programming to find a word
 * segmentation that satisfies the following two preferences:
 * (1) minimize the number of out-of-vocabulary (OOV) words;
 * (2) if there are multiple segmentations with the same number
 * of OOV words, then select the one that minimizes the number
 * of segments. Note that {@link edu.stanford.nlp.parser.lexparser.MaxMatchSegmenter}
 * contains a greedy version of this algorithm.
 *
 * <p>Note that the output segmentation may need to postprocessing for the segmentation
 * of non-Chinese characters (e.g., punctuation, foreign names).
 *
 * @author Michel Galley
 */
public class MaxMatchSegmenter implements WordSegmenter {

  private static final boolean DEBUG = false;

  private Set<String> words = Generics.newHashSet();
  private int len=-1;
  private int edgesNb=0;
  private static final int maxLength = 10;
  private List<DFSAState<Word, Integer>> states;
  private DFSA<Word, Integer> lattice=null;
  private Sighan2005DocumentReaderAndWriter writer = new Sighan2005DocumentReaderAndWriter();
  public enum MatchHeuristic { MINWORDS, MAXWORDS, MAXLEN }

  private static final Pattern chineseStartChars = Pattern.compile("^[\u4E00-\u9FFF]");
  private static final Pattern chineseEndChars = Pattern.compile("[\u4E00-\u9FFF]$");
  private static final Pattern chineseChars = Pattern.compile("[\u4E00-\u9FFF]");

  private static final Pattern excludeChars = Pattern.compile("[0-9\uff10-\uff19" +
        "\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4E5D\u5341" +
        "\u96F6\u3007\u767E\u5343\u4E07\u4ebf\u5169\u25cb\u25ef\u3021-\u3029\u3038-\u303A" +
        "-#$%&'*+/@_\uff0d\uff03\uff04\uff05\uff06\uff07\uff0a\uff0b\uff0f\uff20\uff3f]");

  @Override
  public void initializeTraining(double numTrees) {}

  @Override
  public void train(Collection<Tree> trees) {
    for (Tree tree : trees) {
      train(tree);
    }
  }

  @Override
  public void train(Tree tree) {
    train(tree.taggedYield());
  }

  @Override
  public void train(List<TaggedWord> sentence) {
    for (TaggedWord word : sentence) {
      if (word.word().length() <= maxLength) {
        addStringToLexicon(word.word());
      }
    }
  }

  @Override
  public void finishTraining() {}

  public void loadSegmenter(String filename) {
    addLexicon(filename);
  }

  public List<HasWord> segment(String s) {
    buildSegmentationLattice(s);
    ArrayList<Word> sent = maxMatchSegmentation();
    printlnErr("raw output: "+Sentence.listToString(sent));
    ArrayList<Word> postProcessedSent = postProcessSentence(sent);
    printlnErr("processed output: "+Sentence.listToString(postProcessedSent));
    String postSentString = ChineseStringUtils.postProcessingAnswerCTB(postProcessedSent.toString(),false,false);
    printlnErr("Sighan2005 output: "+postSentString);
    String[] postSentArray = postSentString.split("\\s+");
    ArrayList<Word> postSent = new ArrayList<Word>();
    for(String w : postSentArray) {
      postSent.add(new Word(w));
    }
    return new ArrayList<HasWord>(postSent);
  }

  /**
   * Add a word to the lexicon, unless it contains some non-Chinese character.
   */
  public void addStringToLexicon(String str) {
    if(str.equals("")) {
      System.err.println("WARNING: blank line in lexicon");
    } else if(str.contains(" ")) {
      System.err.println("WARNING: word with space in lexicon");
    } else {
      if(excludeChar(str)) {
        printlnErr("skipping word: "+str);
        return;
      }
      printlnErr("adding word: "+str);
      words.add(str);
    }
  }

  /**
   * Read lexicon from a one-column text file.
   */
  public void addLexicon(String filename) {
    try {
      BufferedReader lexiconReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
      String lexiconLine;
      while ((lexiconLine = lexiconReader.readLine()) != null) {
        addStringToLexicon(lexiconLine);
      }
    } catch (FileNotFoundException e) {
      System.err.println("Lexicon not found: "+ filename);
      System.exit(-1);
    } catch (IOException e) {
      System.err.println("IO error while reading: "+ filename);
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds a lattice of all possible segmentations using only words
   * present in the lexicon. This function must be run prior to
   * running maxMatchSegmentation.
   */
  public void buildSegmentationLattice(String s) {
    edgesNb = 0;
    len = s.length();
    // Initialize word lattice:
    states = new ArrayList<DFSAState<Word, Integer>>();
    lattice = new DFSA<Word, Integer>("wordLattice");
    for (int i=0; i<=s.length(); ++i)
      states.add(new DFSAState<Word, Integer>(i, lattice));
    // Set start and accepting state:
    lattice.setInitialState(states.get(0));
    states.get(len).setAccepting(true);
    // Find all instances of lexicon words in input string:
    for (int start=0; start<len; ++start) {
      for (int end=len; end>start; --end) {
        String str = s.substring(start, end);
        assert(str.length() > 0);
        boolean isOneChar = (start+1 == end);
        boolean isInDict = words.contains(str);
        if (isInDict || isOneChar) {
          double cost = isInDict ? 1 : 100;
          DFSATransition<Word, Integer> trans =
            new DFSATransition<Word, Integer>(null, states.get(start), states.get(end), new Word(str), null, cost);
          //System.err.println("start="+start+" end="+end+" word="+str);
          states.get(start).addTransition(trans);
          ++edgesNb;
        }
      }
    }
  }

  /**
   *  Returs the lexicon-based segmentation that minimizes the number of words.
   * @return Segmented sentence.
   */
  public ArrayList<Word> maxMatchSegmentation() {
    return segmentWords(MatchHeuristic.MINWORDS);
  }

  /**
   * Returns the lexicon-based segmentation following heuristic h.
   * Note that buildSegmentationLattice must be run first.
   * Two heuristics are currently available -- MINWORDS and MAXWORDS --
   * to respectively minimize and maximize the number of segment
   * (where each segment is a lexicon word, if possible).
   *
   * @param h Heuristic to use for segmentation.
   * @return Segmented sentence.
   * @throws UnsupportedOperationException
   * @see #buildSegmentationLattice
   */
  public ArrayList<Word> segmentWords(MatchHeuristic h) throws UnsupportedOperationException {
    if(lattice==null || len < 0)
      throw new UnsupportedOperationException("segmentWords must be run first");
    List<Word> segmentedWords = new ArrayList<Word>();
    // Init dynamic programming:
    double costs[] = new double[len+1];
    List<DFSATransition<Word, Integer>> bptrs = new ArrayList<DFSATransition<Word, Integer>>();
    for (int i = 0; i < len + 1; ++i) {
      bptrs.add(null);
    }
    costs[0]=0.0;
    for (int i=1; i<=len; ++i)
       costs[i] = Double.MAX_VALUE;
    // DP:
    for (int start=0; start<len; ++start) {
      DFSAState<Word, Integer> fromState = states.get(start);
      Collection<DFSATransition<Word, Integer>> trs = fromState.transitions();
      for (DFSATransition<Word, Integer> tr : trs) {
        DFSAState<Word, Integer> toState = tr.getTarget();
        double lcost = tr.score();
        int end = toState.stateID();
        //System.err.println("start="+start+" end="+end+" word="+tr.getInput());
        if (h == MatchHeuristic.MINWORDS) {
          // Minimize number of words:
          if (costs[start]+1 < costs[end]) {
            costs[end] = costs[start]+lcost;
            bptrs.set(end, tr);
            //System.err.println("start="+start+" end="+end+" word="+tr.getInput());
          }
        } else if (h == MatchHeuristic.MAXWORDS) {
          // Maximze number of words:
          if (costs[start]+1 < costs[end]) {
            costs[end] = costs[start]-lcost;
            bptrs.set(end, tr);
          }
        } else {
          throw new UnsupportedOperationException("unimplemented heuristic");
        }
      }
    }
    // Extract min-cost path:
    int i=len;
    while (i>0) {
      DFSATransition<Word, Integer> tr = bptrs.get(i);
      DFSAState<Word, Integer> fromState = tr.getSource();
      Word word = tr.getInput();
      if (!word.word().equals(" "))
        segmentedWords.add(0, word);
      i = fromState.stateID();
    }
    if(DEBUG) {
      // Print lattice density ([1,+inf[) : if equal to 1, it means
      // there is only one segmentation using words of the lexicon.
      double density = edgesNb*1.0/segmentedWords.size();
      System.err.println("latticeDensity: "+density+" cost: "+costs[len]);
    }
    return new ArrayList<Word>(segmentedWords);
  }

  /**
   * Returns a lexicon-based segmentation. At each position x in the input string,
   * it attempts to find largest value y, so that [x,y] is part of the lexicon.
   * Then, it tried to match more input from position y+1. This greedy algorithm
   * (taken from edu.stanford.nlp.lexparser.MaxMatchSegmenter) has no theoretical
   * guarantee, and it would be wise to use segmentWords instead.
   *
   * @param s Input (unsegmented) string.
   * @return Segmented sentence.
   */
  public ArrayList<Word> greedilySegmentWords(String s) {
    List<Word> segmentedWords = new ArrayList<Word>();
    int length = s.length();
    int start = 0;
    while (start < length) {
      int end = Math.min(length, start + maxLength);
      while (end > start + 1) {
        String nextWord = s.substring(start, end);
        if (words.contains(nextWord)) {
          segmentedWords.add(new Word(nextWord));
          break;
        }
        end--;
      }
      if (end == start + 1) {
        // character does not start any word in our dictionary
        segmentedWords.add(new Word(new String(new char[] {s.charAt(start)} )));
        start++;
      } else {
        start = end;
      }
    }
    return new ArrayList<Word>(segmentedWords);
  }

  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);
    System.err.println(props.toString());
    MaxMatchSegmenter seg = new MaxMatchSegmenter();
    String lexiconFile = props.getProperty("lexicon");
    if(lexiconFile != null) {
      seg.addLexicon(lexiconFile);
    } else {
      System.err.println("Error: no lexicon file!");
      System.exit(1);
    }

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    int lineNb = 0;
    for(;;) {
      ++lineNb;
      System.err.println("line: "+lineNb);
      try {
        String line = br.readLine();
        if(line == null)
          break;
        if(props.getProperty("greedy") != null) {
          ArrayList<Word> sentence = seg.greedilySegmentWords(line);
          System.out.println(Sentence.listToString(sentence));
        } else if(props.getProperty("maxwords") != null) {
          seg.buildSegmentationLattice(line);
          System.out.println
            (Sentence.listToString(seg.segmentWords(MatchHeuristic.MAXWORDS)));
        } else {
          seg.buildSegmentationLattice(line);
          System.out.println(Sentence.listToString(seg.maxMatchSegmentation()));
        }
      }
      catch (IOException e) {
        break;
      }
    }
  }

  private static void printlnErr(String s) {
    EncodingPrintWriter.err.println(s, "UTF-8");
  }

  private static ArrayList<Word> postProcessSentence(ArrayList<Word> sent) {
    ArrayList<Word> newSent = new ArrayList<Word>();
    for(Word word : sent) {
      if(newSent.size() > 0) {
        String prevWord = newSent.get(newSent.size()-1).toString();
        String curWord = word.toString();
        String prevChar = prevWord.substring(prevWord.length()-1);
        String curChar = curWord.substring(0,1);
        if(!isChinese(prevChar) && !isChinese(curChar)) {
          Word mergedWord = new Word(prevWord+curWord);
          newSent.set(newSent.size()-1, mergedWord);
          //printlnErr("merged: "+mergedWord);
          //printlnErr("merged: "+mergedWord+" from: "+prevWord+" and: "+curWord);
          continue;
        }
      }
      newSent.add(word);
    }
    return new ArrayList<Word>(newSent);
  }

  private static boolean startsWithChinese(String str) { return chineseStartChars.matcher(str).matches(); }
  private static boolean endsWithChinese(String str) { return chineseEndChars.matcher(str).matches(); }
  private static boolean isChinese(String str) { return chineseChars.matcher(str).matches(); }
  private static boolean excludeChar(String str) { return excludeChars.matcher(str).matches(); }

  private static final long serialVersionUID   = 8263734344886904724L;
}

