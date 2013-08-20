package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.Words;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.process.BadPunctuationTokenizationFixer;
import edu.stanford.nlp.process.ListProcessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** This class holds all of the information about a single instance from
 *  a training file.
 *
 *  @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class Instance implements Serializable {

  private static final long serialVersionUID = -8249377200360700646L;

  public static final String DISTRIBUTION = "DISTRIBUTION";
  public static final String UNASSIGNED = "UNASSIGNED";
  public static TokenizerFactory tokenizerFactory = PTBTokenizer.factory();
  public static ListProcessor<Word, List<Word>> wordToSentenceProc = new WordToSentenceProcessor<Word>();
  private BadPunctuationTokenizationFixer tokFixer = new BadPunctuationTokenizationFixer();
  // this means there is a distributions over sense, in subcatDist
  protected static NumberFormat formatter = new DecimalFormat("0.000");

  // raw data
  public List<Word> allWords;
  public List<Word> sentence; // holds the sentence surrounding the target word, for parsing
  public String word; // the word to be disambiguated/subcategorized
  public int index;  // the index that the word is located at
  public Tree tree;  // parse of the instance (if read from a tree)

  // higher level data
  public String lexicalElement = null;
  public String instanceID = null;
  public String[] sense; // labeled senses of instance.  may be "DISTRIBUTION"
  public Distribution<String> senseDist; // the distribution over senses, if unmarked
  public Subcategory subcat; // subcat of instance.
  public Distribution<Subcategory> subcatDist; // the distribution over subcats, if unmarked
  ClassicCounter<Subcategory> logSequenceGivenSubcat; // probability of sequence with this subcat

  private final Map<String, Index<String>> senseIndices = 
    new HashMap<String, Index<String>>();

  private void extractTarget() {
    String dummyWord = "xtrainx";
    index = getIndexOfWordWithChar(allWords, '_');
    List<List<Word>> sentences = wordToSentenceProc.process(allWords);
    for (Iterator<List<Word>> sentI = sentences.iterator(); sentI.hasNext();) {
      List<Word> s = sentI.next();
      for (Iterator<Word> wordI = s.iterator(); wordI.hasNext();) {
        Word w = wordI.next();
        String wordStr = w.word();
        int ind = wordStr.indexOf('_');
        if (ind > 0) {
          word = wordStr.substring(0, ind) + "^";
          w.setWord(word); // get rid of the underscore and sense in the
          sentence = removeDummyWords(s, dummyWord);
          //          System.out.println("sentence: " + sentence);
          return;
        }
      }
    }
    throw new RuntimeException("no target word found in: " + allWords);
  }

  private static int getIndexOfWordWithChar(List<Word> s, char c) {
    int i = 0;
    for (Iterator<Word> wordI = s.iterator(); wordI.hasNext();) {
      Word w = wordI.next();
      String wordStr = w.word();
      int index = wordStr.lastIndexOf(c);
      if (index > 0) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static ArrayList<Word> removeDummyWords(List<Word> s, String dummyWord) {
    ArrayList<Word> result = new ArrayList<Word>();
    for (Iterator<Word> wordI = s.iterator(); wordI.hasNext();) {
      Word word = wordI.next();
      if (!word.word().equals(dummyWord)) {
        result.add(word);
      }
    }
    return result;
  }

  private String extractSenses(String s) {
    int beginSense = s.indexOf("_");
    String temp = s.substring(beginSense);
    int endSense = temp.indexOf(" ");
    String senseString = temp.substring(0, endSense);
    List<String> senses = new ArrayList<String>();
    while (senseString.length() > 0) {
      int paren = senseString.lastIndexOf('(');
      String newSense = senseString.substring(paren + 1, senseString.length() - 1);
      senses.add(newSense);
      senseString = senseString.substring(0, paren - 1);
      number(lexicalElement, newSense);
    }
    sense = senses.toArray(new String[]{});
    // the 1 is stupid hack to keep the tokenizer from separating the underscore from the word
    return s.substring(0, beginSense + 1) + "1" + temp.substring(endSense);
  }
  /*
    public static int numSenses(String lexicalElement) {
      return Numberer.getGlobalNumberer(lexicalElement).total();
    }

    public static String getStringSense(String lexicalElement, int n) {
      return (String)Numberer.object(lexicalElement, n);
    }

    public static int getSenseIndex(String lexicalElement, String s) {
      return Numberer.number(lexicalElement, s);
    }

    public static Numberer getSenseNumberer(String lexicalElement) {
      return Numberer.getGlobalNumberer(lexicalElement);
    }
  */

  private void number(String lexicalElement, String newSense) {
    Index<String> index = senseIndices.get(lexicalElement);
    if (index == null) {
      index = new HashIndex<String>();
      senseIndices.put(lexicalElement, index);
    }
    index.indexOf(newSense, true);
  }

  /**
   * Returns a new Instance from a sentence that may be marked for sense.
   */
  public Instance(String instanceString, SubcatProbabilityMetric subcatParser) {
    lexicalElement = instanceString.substring(0, instanceString.indexOf(" "));
    instanceString = instanceString.substring(instanceString.indexOf(" ") + 1);
    instanceID = instanceString.substring(0, instanceString.indexOf(" "));
    instanceString = instanceString.substring(instanceString.indexOf(" ") + 1);
    instanceString = tokFixer.apply(instanceString);
    instanceString = extractSenses(instanceString);
    allWords = (PTBTokenizer.newPTBTokenizer(new StringReader(instanceString))).tokenize();
    extractTarget();
    if (sentence == null) {
      throw new RuntimeException();
    }
    if (word == null) {
      throw new RuntimeException();
    }
    if (index == -1) {
      throw new RuntimeException("Error: instance has no word marked with carat: " + sentence);
    }
    //    System.out.println("INSTANCE CONSTRUCTOR");
    //    System.out.println("allWords: " + allWords);
    //    System.out.println("sentence: " + sentence);
    //    System.out.println("word: " + word);
    //    System.out.println("sense: " + Arrays.asList(sense));
    subcat = Subcategory.UNASSIGNED;
    if (subcatParser != null) {
      try {
        logSequenceGivenSubcat = subcatParser.getLogSubcatProbs(Words.toStrings(sentence));
      } catch (Exception e) {
        logSequenceGivenSubcat = new ClassicCounter<Subcategory>(); // this is effectively the uniform distribution in log space
      }
      //      System.out.println("logSequenceGivenSubcat: " + logSequenceGivenSubcat);
    }
  }

  /**
   * Returns a new Instance from a parsed sentence.
   *
   */
  public Instance(Tree tree, SubcatProbabilityMetric subcatParser) {
    sentence = tree.yieldWords();
    allWords = sentence; // here they are the same
    this.word = null;
    for (int i = 0; i < sentence.size(); i++) {
      Word next = sentence.get(i);
      if (next.word().indexOf("^") >= 0) {
        index = i;
        word = next.word();
        break;
      }
    }
    TgrepMatcher.addParents(tree);
    subcat = Subcategory.getSubcategory(tree, word); // always returns some subcat
    sense = new String[]{UNASSIGNED};
    if (subcatParser != null) {
      try {
        logSequenceGivenSubcat = subcatParser.getLogSubcatProbs(Words.toStrings(sentence));
      } catch (Exception e) {
        logSequenceGivenSubcat = new ClassicCounter<Subcategory>(); // this is effectively the uniform distribution in log space
      }
    }
    this.tree = tree;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Instance)) {
      return false;
    }

    final Instance instance = (Instance) o;

    if (index != instance.index) {
      return false;
    }
    if (!sentence.equals(instance.sentence)) {
      return false;
    }
    if (!word.equals(instance.word)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = sentence.hashCode();
    result = 29 * result + word.hashCode();
    result = 29 * result + index;
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("word: ").append(word);
    sb.append("\nallWords: ").append(allWords);
    sb.append("\nsentence: ").append(sentence);
    sb.append("\ntree: ").append(tree);
    sb.append("\nsense: ").append(sense[0].equals(DISTRIBUTION) ? senseDist.toString(formatter) : Arrays.asList(sense).toString());
    sb.append("\nsubcat: ").append(subcat.equals(Subcategory.DISTRIBUTION) ? subcatDist.toString(formatter) : subcat.toString());
    sb.append("\nsequenceGivenSubcat: ").append(Counters.toString(logSequenceGivenSubcat, formatter));
    sb.append("\n");
    return sb.toString();
  }

  public String getLexicalElement() {
    return lexicalElement;
  }

  public String getInstanceID() {
    return instanceID;
  }

  public static void main(String[] args) {
    // test resding in sense marked instances:
    List<String> files = new ArrayList<String>();
    File currentDir = new File(args[0]);
    String[] filenames = currentDir.list();
    System.out.println("loading data for files: ");
    for (int i = 0; i < filenames.length; i++) {
      String filename = filenames[i];
      if (filename.endsWith(".v.train") || filename.endsWith(".v.test")) {
        files.add(args[0] + '\\' + filename);
        System.out.print(filename + " ");
      }
    }
    List<Instance> list = new ArrayList<Instance>();
    for (int i = 0; i < files.size(); i++) {
      String filename = files.get(i);
      System.out.println("Reading and processing sense instances from " + filename);
      String line;
      BufferedReader senseInstanceFile = null;
      try {
        senseInstanceFile = new BufferedReader(new FileReader(filename));
        int numTotal = 0;
        while ((line = senseInstanceFile.readLine()) != null) {
          try {
            Instance instance = new Instance(line, null); // TODO pass parser
            list.add(instance);
            numTotal++;
          } catch (Exception e) {
            throw new RuntimeException(e); // shouldn't throw exceptions
            // do nothing
          }
        }
        System.out.println("Got " + numTotal + " sense marked data instances");
      } catch (Exception e) {
        System.out.println("AARRGH!");
        System.exit(1);
      }
      //      if (numTotal == 5) break; // TODO remove
    }
  }
}
