package edu.stanford.nlp.parser.lexparser;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.GeneralizedCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/**
 * @author Galen Andrew
 */
public class ChineseCharacterBasedLexicon implements Lexicon {

  private final double lengthPenalty;
  // penaltyType should be set as follows:
  // 0: no length penalty
  // 1: quadratic length penalty
  // 2: penalty for continuation chars only
  private final int penaltyType;

  private Map<List,Distribution<Symbol>> charDistributions;
  private Set<Symbol> knownChars;

  private Distribution<String> POSDistribution;

  private final boolean useUnknownCharacterModel;

  private static final int CONTEXT_LENGTH = 2;

  private final Index<String> wordIndex;
  private final Index<String> tagIndex;

  public ChineseCharacterBasedLexicon(ChineseTreebankParserParams params,
                                      Index<String> wordIndex,
                                      Index<String> tagIndex) {
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    this.lengthPenalty = params.lengthPenalty;
    this.penaltyType = params.penaltyType;
    this.useUnknownCharacterModel = params.useUnknownCharacterModel;
  }

  // We need to make two passes over the data, whereas the calling
  // routines only pass in the sentences or trees once, so we keep all
  // the sentences and then process them at the end
  private transient List<List<TaggedWord>> trainingSentences;

  @Override
  public void initializeTraining(double numTrees) {
    trainingSentences = new ArrayList<>();
  }

  /**
   * Train this lexicon on the given set of trees.
   */
  @Override
  public void train(Collection<Tree> trees) {
    for (Tree tree : trees) {
      train(tree, 1.0);
    }
  }

  /**
   * Train this lexicon on the given set of trees.
   */
  @Override
  public void train(Collection<Tree> trees, double weight) {
    for (Tree tree : trees) {
      train(tree, weight);
    }
  }

  /**
   * TODO: make this method do something with the weight
   */
  @Override
  public void train(Tree tree, double weight) {
    trainingSentences.add(tree.taggedYield());
  }

  @Override
  public void trainUnannotated(List<TaggedWord> sentence, double weight) {
    // TODO: for now we just punt on these
    throw new UnsupportedOperationException("This version of the parser does not support non-tree training data");
  }

  @Override
  public void incrementTreesRead(double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    throw new UnsupportedOperationException();
  }


  @Override
  public void train(List<TaggedWord> sentence, double weight) {
    trainingSentences.add(sentence);
  }

  @Override
  public void finishTraining() {
    Timing.tick("Counting characters...");
    ClassicCounter<Symbol> charCounter = new ClassicCounter<>();

    // first find all chars that occur only once
    for (List<TaggedWord> labels : trainingSentences) {
      for (TaggedWord label : labels) {
        String word = label.word();
        if (word.equals(BOUNDARY)) {
          continue;
        }
        for (int j = 0, length = word.length(); j < length; j++) {
          Symbol sym = Symbol.cannonicalSymbol(word.charAt(j));
          charCounter.incrementCount(sym);
        }
        charCounter.incrementCount(Symbol.END_WORD);
      }
    }

    Set<Symbol> singletons = Counters.keysBelow(charCounter, 1.5);
    knownChars = Generics.newHashSet(charCounter.keySet());

    Timing.tick("Counting nGrams...");
    GeneralizedCounter[] POSspecificCharNGrams = new GeneralizedCounter[CONTEXT_LENGTH + 1];
    for (int i = 0; i <= CONTEXT_LENGTH; i++) {
      POSspecificCharNGrams[i] = new GeneralizedCounter(i + 2);
    }

    ClassicCounter<String> POSCounter = new ClassicCounter<>();
    List<Serializable> context = new ArrayList<>(CONTEXT_LENGTH + 1);
    for (List<TaggedWord> words : trainingSentences) {
      for (TaggedWord taggedWord : words) {
        String word = taggedWord.word();
        String tag = taggedWord.tag();
        tagIndex.add(tag);
        if (word.equals(BOUNDARY)) {
          continue;
        }
        POSCounter.incrementCount(tag);
        for (int i = 0, size = word.length(); i <= size; i++) {
          Symbol sym;
          Symbol unknownCharClass = null;
          context.clear();
          context.add(tag);
          if (i < size) {
            char thisCh = word.charAt(i);
            sym = Symbol.cannonicalSymbol(thisCh);
            if (singletons.contains(sym)) {
              unknownCharClass = unknownCharClass(sym);
              charCounter.incrementCount(unknownCharClass);
            }
          } else {
            sym = Symbol.END_WORD;
          }
          POSspecificCharNGrams[0].incrementCount(context, sym); // POS-specific 1-gram
          if (unknownCharClass != null) {
            POSspecificCharNGrams[0].incrementCount(context, unknownCharClass); // for unknown ch model
          }

          // context is constructed incrementally:
          // tag prevChar prevPrevChar
          // this could be made faster using .sublist like in score
          for (int j = 1; j <= CONTEXT_LENGTH; j++) { // poly grams
            if (i - j < 0) {
              context.add(Symbol.BEGIN_WORD);
              POSspecificCharNGrams[j].incrementCount(context, sym);
              if (unknownCharClass != null) {
                POSspecificCharNGrams[j].incrementCount(context, unknownCharClass); // for unknown ch model
              }
              break;
            } else {
              Symbol prev = Symbol.cannonicalSymbol(word.charAt(i - j));
              if (singletons.contains(prev)) {
                context.add(unknownCharClass(prev));
              } else {
                context.add(prev);
              }
              POSspecificCharNGrams[j].incrementCount(context, sym);
              if (unknownCharClass != null) {
                POSspecificCharNGrams[j].incrementCount(context, unknownCharClass); // for unknown ch model
              }
            }
          }
        }
      }
    }

    POSDistribution = Distribution.getDistribution(POSCounter);
    Timing.tick("Creating character prior distribution...");

    charDistributions = Generics.newHashMap();
    //    charDistributions = Generics.newHashMap();  // 1.5
    //    charCounter.incrementCount(Symbol.UNKNOWN, singletons.size());
    int numberOfKeys = charCounter.size() + singletons.size();
    Distribution<Symbol> prior = Distribution.goodTuringSmoothedCounter(charCounter, numberOfKeys);
    charDistributions.put(Collections.EMPTY_LIST, prior);

    for (int i = 0; i <= CONTEXT_LENGTH; i++) {
      Set<Map.Entry<List<Serializable>, ClassicCounter<Symbol>>> counterEntries = POSspecificCharNGrams[i].lowestLevelCounterEntrySet();
      Timing.tick("Creating " + counterEntries.size() + " character " + (i + 1) + "-gram distributions...");
      for (Map.Entry<List<Serializable>, ClassicCounter<Symbol>> entry : counterEntries) {
        context = entry.getKey();
        ClassicCounter<Symbol> c = entry.getValue();
        Distribution<Symbol> thisPrior = charDistributions.get(context.subList(0, context.size() - 1));
        double priorWeight = thisPrior.getNumberOfKeys() / 200.0;
        Distribution<Symbol> newDist = Distribution.dynamicCounterWithDirichletPrior(c, thisPrior, priorWeight);
        charDistributions.put(context, newDist);
      }
    }
  }

  public Distribution<String> getPOSDistribution() {
    return POSDistribution;
  }

  public static boolean isForeign(String s) {
    for (int i = 0; i < s.length(); i++) {
      int num = Character.getNumericValue(s.charAt(i));
      if (num < 10 || num > 35) {
        return false;
      }
    }
    return true;
  }

  private Symbol unknownCharClass(Symbol ch) {
    if (useUnknownCharacterModel) {
      return new Symbol(Character.toString(RadicalMap.getRadical(ch.getCh()))).intern();
    } else {
      return Symbol.UNKNOWN;
    }
  }

  @Override
  public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
    String tag = tagIndex.get(iTW.tag);
    assert !word.equals(BOUNDARY);
    char[] chars = word.toCharArray();
    List<Serializable> charList = new ArrayList<>(chars.length + CONTEXT_LENGTH + 1); // this starts of storing Symbol's and then starts storing String's. Clean this up someday!

    // charList is constructed backward
    // END_WORD char[length-1] char[length-2] ... char[0] BEGIN_WORD BEGIN_WORD
    charList.add(Symbol.END_WORD);
    for (int i = chars.length - 1; i >= 0; i--) {
      Symbol ch = Symbol.cannonicalSymbol(chars[i]);
      if (knownChars.contains(ch)) {
        charList.add(ch);
      } else {
        charList.add(unknownCharClass(ch));
      }
    }
    for (int i = 0; i < CONTEXT_LENGTH; i++) {
      charList.add(Symbol.BEGIN_WORD);
    }

    double score = 0.0;
    for (int i = 0, size = charList.size(); i < size - CONTEXT_LENGTH; i++) {
      Symbol nextChar = (Symbol) charList.get(i);
      charList.set(i, tag);
      double charScore = getBackedOffDist(charList.subList(i, i + CONTEXT_LENGTH + 1)).probabilityOf(nextChar);
      score += Math.log(charScore);
    }

    switch (penaltyType) {
      case 0:
        break;

      case 1:
        score -= (chars.length * (chars.length + 1)) * (lengthPenalty / 2);
        break;

      case 2:
        score -= (chars.length - 1) * lengthPenalty;
        break;
    }
    return (float) score;
  }


  // this is where we do backing off for unseen contexts
  // (backing off for rarely seen contexts is done implicitly
  // because the distributions are smoothed)
  private Distribution<Symbol> getBackedOffDist(List<Serializable> context) {
    // context contains [tag prevChar prevPrevChar]
    for (int i = CONTEXT_LENGTH + 1; i >= 0; i--) {
      List<Serializable> l = context.subList(0, i);
      if (charDistributions.containsKey(l)) {
        return charDistributions.get(l);
      }
    }
    throw new RuntimeException("OOPS... no prior distribution...?");
  }

  /**
   * Samples from the distribution over words with this POS according to the lexicon.
   *
   * @param tag the POS of the word to sample
   * @return a sampled word
   */
  public String sampleFrom(String tag) {
    StringBuilder buf = new StringBuilder();
    List<Serializable> context = new ArrayList<>(CONTEXT_LENGTH + 1);

    // context must contain [tag prevChar prevPrevChar]
    context.add(tag);
    for (int i = 0; i < CONTEXT_LENGTH; i++) {
      context.add(Symbol.BEGIN_WORD);
    }
    Distribution<Symbol> d = getBackedOffDist(context);
    Symbol gen = d.sampleFrom();
    genLoop:
    while (gen != Symbol.END_WORD) {
      buf.append(gen.getCh());
      switch (penaltyType) {
        case 1:
          if (Math.random() > Math.pow(lengthPenalty, buf.length())) {
            break genLoop;
          }
          break;
        case 2:
          if (Math.random() > lengthPenalty) {
            break genLoop;
          }
          break;
      }
      for (int i = 1; i < CONTEXT_LENGTH; i++) {
        context.set(i + 1, context.get(i));
      }
      context.set(1, gen);
      d = getBackedOffDist(context);
      gen = d.sampleFrom();
    }

    return buf.toString();
  }

  /**
   * Samples over words regardless of POS: first samples POS, then samples
   * word according to that POS
   *
   * @return a sampled word
   */
  public String sampleFrom() {
    String POS = POSDistribution.sampleFrom();
    return sampleFrom(POS);
  }

  // don't think this should be used, but just in case...
  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
    throw new UnsupportedOperationException("ChineseCharacterBasedLexicon has no rule iterator!");
  }

  // don't think this should be used, but just in case...
  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc, String featureSpec) {
    throw new UnsupportedOperationException("ChineseCharacterBasedLexicon has no rule iterator!");
  }

  /** Returns the number of rules (tag rewrites as word) in the Lexicon.
   *  This method isn't yet implemented in this class.
   *  It currently just returns 0, which may or may not be helpful.
   */
  @Override
  public int numRules() {
    return 0;
  }

  private Distribution<Integer> getWordLengthDistribution() {
    int samples = 0;
    ClassicCounter<Integer> c = new ClassicCounter<>();
    while (samples++ < 10000) {
      String s = sampleFrom();
      c.incrementCount(Integer.valueOf(s.length()));
      if (samples % 1000 == 0) {
        System.out.print(".");
      }
    }
    System.out.println();
    Distribution<Integer> genWordLengthDist = Distribution.getDistribution(c);
    return genWordLengthDist;
  }

  @Override
  public void readData(BufferedReader in) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeData(Writer w) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isKnown(int word) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isKnown(String word) {
    throw new UnsupportedOperationException();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> tagSet(Function<String,String> basicCategoryFunction) {
    Set<String> tagSet = new HashSet<>();
    for (String tag : tagIndex.objectsList()) {
      tagSet.add(basicCategoryFunction.apply(tag));
    }
    return tagSet;
  }


  static class Symbol implements Serializable {
    private static final int UNKNOWN_TYPE = 0;
    private static final int DIGIT_TYPE = 1;
    private static final int LETTER_TYPE = 2;
    private static final int BEGIN_WORD_TYPE = 3;
    private static final int END_WORD_TYPE = 4;
    private static final int CHAR_TYPE = 5;
    private static final int UNK_CLASS_TYPE = 6;

    private char ch;
    private String unkClass;

    int type;

    public static final Symbol UNKNOWN = new Symbol(UNKNOWN_TYPE);
    public static final Symbol DIGIT = new Symbol(DIGIT_TYPE);
    public static final Symbol LETTER = new Symbol(LETTER_TYPE);
    public static final Symbol BEGIN_WORD = new Symbol(BEGIN_WORD_TYPE);
    public static final Symbol END_WORD = new Symbol(END_WORD_TYPE);

    public static final Interner<Symbol> interner = new Interner<>();

    public Symbol(char ch) {
      type = CHAR_TYPE;
      this.ch = ch;
    }

    public Symbol(String unkClass) {
      type = UNK_CLASS_TYPE;
      this.unkClass = unkClass;
    }

    public Symbol(int type) {
      assert type != CHAR_TYPE;
      this.type = type;
    }

    public static Symbol cannonicalSymbol(char ch) {
      if (Character.isDigit(ch)) {
        return DIGIT; //{ Digits.add(new Character(ch)); return DIGIT; }
      }

      if (Character.getNumericValue(ch) >= 10 && Character.getNumericValue(ch) <= 35) {
        return LETTER; //{ Letters.add(new Character(ch)); return LETTER; }
      }

      return new Symbol(ch);
    }

    public char getCh() {
      if (type == CHAR_TYPE) {
        return ch;
      } else {
        return '*';
      }
    }

    public Symbol intern() {
      return interner.intern(this);
    }

    @Override
    public String toString() {
      if (type == CHAR_TYPE) {
        return "[u" + (int) ch + "]";
      } else if (type == UNK_CLASS_TYPE) {
        return "UNK:" + unkClass;
      } else {
        return Integer.toString(type);
      }
    }

    protected Object readResolve() throws ObjectStreamException {
      switch (type) {
        case CHAR_TYPE:
          return intern();
        case UNK_CLASS_TYPE:
          return intern();
        case UNKNOWN_TYPE:
          return UNKNOWN;
        case DIGIT_TYPE:
          return DIGIT;
        case LETTER_TYPE:
          return LETTER;
        case BEGIN_WORD_TYPE:
          return BEGIN_WORD;
        case END_WORD_TYPE:
          return END_WORD;
        default: // impossible...
          throw new InvalidObjectException("ILLEGAL VALUE IN SERIALIZED SYMBOL");
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Symbol)) {
        return false;
      }

      final Symbol symbol = (Symbol) o;

      if (ch != symbol.ch) {
        return false;
      }
      if (type != symbol.type) {
        return false;
      }
      if (unkClass != null ? !unkClass.equals(symbol.unkClass) : symbol.unkClass != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = ch;
      result = 29 * result + (unkClass != null ? unkClass.hashCode() : 0);
      result = 29 * result + type;
      return result;
    }

    private static final long serialVersionUID = 8925032621317022510L;

  } // end class Symbol

  private static final long serialVersionUID = -5357655683145854069L;

  @Override
  public UnknownWordModel getUnknownWordModel() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setUnknownWordModel(UnknownWordModel uwm) {
    // TODO Auto-generated method stub

  }

  @Override
  public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
    train(trees);
  }

} // end class ChineseCharacterBasedLexicon
