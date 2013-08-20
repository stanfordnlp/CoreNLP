package edu.stanford.nlp.parser.lexparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

public class PetrovLexicon implements Lexicon {

  /**
   * 
   */
  private static final long serialVersionUID = 5672415342334265614L;

  static final String UNKNOWN_WORD = "UNK";

  Index<String> tagIndex;

  Index<String> wordIndex;

  ClassicCounter<Integer> wordCounter;

  ClassicCounter<Integer> tagCounter;

  ClassicCounter<Integer> unseenTagCounter;

  ClassicCounter<IntTaggedWord> tagAndWordCounter;

  ClassicCounter<IntTaggedWord> unseenTagAndSignatureCounter;

  int smoothInUnknownsThreshold = 10;
  double smooth = 0.1;

  List[] rulesWithWord;

  public boolean isKnown(int word) {
    return wordCounter.getCount(word)>0.0;
  }

  public boolean isKnown(String word) {
    return wordIndex.contains(word) && isKnown(wordIndex.indexOf(word));
  }

  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
    if (isKnown(word)) {
      List<IntTaggedWord> rules = new ArrayList<IntTaggedWord>();

      return rules.iterator();
    } else {
      return null;
    }
  }

  public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc, String featureSpec) {
    if (isKnown(word)) {
      List<IntTaggedWord> rules = new ArrayList<IntTaggedWord>();

      return rules.iterator();
    } else {
      return null;
    }
  }

  protected void initRulesWithWord() {
  }


  /** Returns the number of rules (tag rewrites as word) in the Lexicon.
   *  This method assumes that the lexicon has been initialized.
   */
  public int numRules() {
    if (rulesWithWord == null) {
      initRulesWithWord();
    }
    int accumulated = 0;
    for (List<IntTaggedWord> lis : rulesWithWord) {
      accumulated += lis.size();
    }
    return accumulated;
  }


  @Override
  public void initializeTraining(double numTrees) {
    throw new UnsupportedOperationException();    
  }

  @Override
  public void train(Collection<Tree> trees) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(Collection<Tree> trees, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(Tree tree, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementTreesRead(double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(List<TaggedWord> sentence, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void trainUnannotated(List<TaggedWord> sentence, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void finishTraining() {
    throw new UnsupportedOperationException();
  }

  /**
   * Computes an estimate of log P(word | tag).
   */
  public float score(IntTaggedWord iTW, int loc, String wordStr, String featureSpec) {
    int word = iTW.word();
    int tag = iTW.tag();
    double wc = wordCounter.getCount(word);
    double totalSeen = tagCounter.totalCount();
    double totalUnseen = unseenTagCounter.totalCount();
    if (wc>0.0) { // seen
      double probTagGivenWord = Double.NEGATIVE_INFINITY;
      double twc = tagAndWordCounter.getCount(iTW);
      if (wc>smoothInUnknownsThreshold) {
        probTagGivenWord = twc / wc; // prob tag given word
      } else {
        double probTagGivenUnseen = unseenTagCounter.getCount(tag) / totalUnseen;
        probTagGivenWord = (twc + (smooth*probTagGivenUnseen)) / (wc + smooth);
      }
      double tc = tagCounter.getCount(tag);
      double probTag = tc / totalSeen;
      double probWord = wc / totalSeen;
      return (float) (probTagGivenWord * probWord / probTag);
    } else { // unseen
      // we have to get the string and look at it
      // TODO: will it be in the index?
      int signature = getSignatureIndex(wordStr, loc);
      double sc = wordCounter.getCount(signature);
      IntTaggedWord siTW = new IntTaggedWord(signature, tag);
      double tsc = unseenTagAndSignatureCounter.getCount(siTW);
      double probTagGivenUnseen = unseenTagCounter.getCount(tag) / totalUnseen;
      double probTagGivenWord = (tsc + (smooth*probTagGivenUnseen)) / (sc + smooth);
      double tc = unseenTagCounter.getCount(tag);
      double probTag = tc / totalUnseen;
      double probWord = wc / totalUnseen;
      return (float) (probTagGivenWord * probWord / probTag);
    }
  }

  public void writeData(Writer w) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void readData(BufferedReader in) throws IOException {
    Timing t = new Timing();
    t.start();
    System.err.println("Loading in PetrovLexicon from file...");
    int numSeenWords = 0;
    int numUnseenWords = 0;
    wordCounter = new ClassicCounter<Integer>();
    tagCounter = new ClassicCounter<Integer>();
    unseenTagCounter = new ClassicCounter<Integer>();
    tagAndWordCounter = new ClassicCounter<IntTaggedWord>();
    unseenTagAndSignatureCounter = new ClassicCounter<IntTaggedWord>();
    String line = in.readLine();
    // the next ints are flags we'll use to keep track of where in the file we
    // are
    int WC = 0;
    int TC = 1;
    int UTC = 2;
    int TWC = 3;
    int UTSC = 4;
    int status = -1; // next line has section header
    while (line != null) {
      if (line.startsWith("-------")) { // next line must have new section
                                        // header
        status = -1;
      } else if (status == -1) { // this line must have the section header
        if (line.startsWith("WORD-COUNTER"))
          status = WC;
        else if (line.startsWith("TAG-COUNTER"))
          status = TC;
        else if (line.startsWith("UNSEEN-TAG-COUNTER"))
          status = UTC;
        else if (line.startsWith("TAG-AND-WORD-COUNTER"))
          status = TWC;
        else if (line.startsWith("UNSEEN-TAG-AND-SIGNATURE-COUNTER"))
          status = UTSC;
        else
          throw new RuntimeException("Unrecognized header: " + line);
      } else if (status == WC) { // this line has a word count in it
        int space = line.indexOf(' ');
        String wordString = new String(line.substring(0, space));
        if (wordString.startsWith("UNK")) {
          numUnseenWords++;
        } else {
          numSeenWords++;
        }
        int word = wordIndex.indexOf(wordString, true);
        double count = Double.parseDouble(line.substring(space + 1));
        wordCounter.setCount(word, count);
      } else if (status == TC) { // this line has a tag count in it
        int space = line.indexOf(' ');
        int tag = tagIndex.indexOf(new String(line.substring(0, space)), true);
        double count = Double.parseDouble(line.substring(space + 1));
        tagCounter.setCount(tag, count);
      } else if (status == UTC) { // this line has an unseen tag count in it
        int space = line.indexOf(' ');
        int tag = tagIndex.indexOf(new String(line.substring(0, space)), true);
        double count = Double.parseDouble(line.substring(space + 1));
        unseenTagCounter.setCount(tag, count);
      } else if (status == TWC) { // this line has a tag and word count in it
        int space = line.indexOf(' ');
        int bracket = line.indexOf('[');
        String baseTag = line.substring(0, space);
        String word = new String(line.substring(space + 1, bracket - 1));
        String[] fields = line.substring(bracket + 1, line.length() - 1).split(" ,");
        for (int i = 0; i < fields.length; i++) {
          String tag = baseTag + "_" + i;
          IntTaggedWord itw = new IntTaggedWord(word, tag, wordIndex, tagIndex);
          double count = Double.parseDouble(fields[i]);
          tagAndWordCounter.setCount(itw, count);
        }
      } else if (status == UTSC) { // this line has a unseen tag and signature
                                    // count
        int space = line.indexOf(' ');
        int bracket = line.indexOf('[');
        String baseTag = new String(line.substring(0, space));
        String word = new String(line.substring(space + 1, bracket - 1));
        String[] fields = line.substring(bracket + 1, line.length() - 1).split(" ,");
        for (int i = 0; i < fields.length; i++) {
          String tag = baseTag + "_" + i;
          IntTaggedWord itw = new IntTaggedWord(word, tag, wordIndex, tagIndex);
          double count = Double.parseDouble(fields[i]);
          unseenTagAndSignatureCounter.setCount(itw, count);
        }
      }
      line = in.readLine();
    }
    // print out some debugging stuff about what was loaded in
    t.stop("Done loading.");
    System.err.println("numSeenWords: " + numSeenWords);
    System.err.println("numUnseenWords: " + numUnseenWords);
    System.err.println("wordCounter: " + wordCounter.size() + " keys and " + wordCounter.totalCount() + " total count.");
    System.err.println("tagCounter: " + tagCounter.size() + " keys and " + tagCounter.totalCount() + " total count.");
    System.err.println("unseenTagCounter: " + unseenTagCounter.size() + " keys and " + unseenTagCounter.totalCount() + " total count.");
    System.err.println("tagAndWordCounter: " + tagAndWordCounter.size() + " keys and " + tagAndWordCounter.totalCount() + " total count.");
    System.err.println("unseenTagAndSignatureCounter: " + unseenTagAndSignatureCounter.size() + " keys and " + unseenTagAndSignatureCounter.totalCount() + " total count.");
  }

  public int getSignatureIndex(String word, int loc) {
    String signatureString = getSignature(word, loc);
    // TODO: this was once indexOf(word), but that makes no sense
    int signature = wordIndex.indexOf(signatureString, true);
    return signature;
  }

  public String getSignature(String word, int loc) {
    StringBuilder sb = new StringBuilder();
    int wlen = word.length();
    int numCaps = 0;
    boolean hasDigit = false;
    boolean hasDash = false;
    boolean hasLower = false;
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else if (ch == '-') {
        hasDash = true;
      } else if (Character.isLetter(ch)) {
        if (Character.isLowerCase(ch)) {
          hasLower = true;
        } else if (Character.isTitleCase(ch)) {
          hasLower = true;
          numCaps++;
        } else {
          numCaps++;
        }
      }
    }
    char ch0 = word.charAt(0);
    String lowered = word.toLowerCase();
    if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
      if (loc == 0 && numCaps == 1) {
        sb.append("-INITC");
        if (isKnown(lowered)) {
          sb.append("-KNOWNLC");
        }
      } else {
        sb.append("-CAPS");
      }
    } else if (!Character.isLetter(ch0) && numCaps > 0) {
      sb.append("-CAPS");
    } else if (hasLower) { // (Character.isLowerCase(ch0)) {
      sb.append("-LC");
    }
    if (hasDigit) {
      sb.append("-NUM");
    }
    if (hasDash) {
      sb.append("-DASH");
    }
    if (lowered.endsWith("s") && wlen >= 3) {
      // here length 3, so you don't miss out on ones like 80s
      char ch2 = lowered.charAt(wlen - 2);
      // not -ess suffixes or greek/latin -us, -is
      if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
        sb.append("-s");
      }
    } else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
      // don't do for very short words;
      // Implement common discriminating suffixes
      if (lowered.endsWith("ed")) {
        sb.append("-ed");
      } else if (lowered.endsWith("ing")) {
        sb.append("-ing");
      } else if (lowered.endsWith("ion")) {
        sb.append("-ion");
      } else if (lowered.endsWith("er")) {
        sb.append("-er");
      } else if (lowered.endsWith("est")) {
        sb.append("-est");
      } else if (lowered.endsWith("ly")) {
        sb.append("-ly");
      } else if (lowered.endsWith("ity")) {
        sb.append("-ity");
      } else if (lowered.endsWith("y")) {
        sb.append("-y");
      } else if (lowered.endsWith("al")) {
        sb.append("-al");
      }
    }
    return sb.toString();
  }

  public PetrovLexicon(Index<String> wordIndex, Index<String> tagIndex) {
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    wordCounter = new ClassicCounter<Integer>();
    tagCounter = new ClassicCounter<Integer>();
    unseenTagCounter = new ClassicCounter<Integer>();
    tagAndWordCounter = new ClassicCounter<IntTaggedWord>();
    unseenTagAndSignatureCounter = new ClassicCounter<IntTaggedWord>();
  }

  public UnknownWordModel getUnknownWordModel() {
    throw new UnsupportedOperationException();
  }

  public void setUnknownWordModel(UnknownWordModel uwm) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
    
    
  }



}
