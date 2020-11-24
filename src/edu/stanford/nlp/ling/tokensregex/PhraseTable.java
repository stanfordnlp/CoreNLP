package edu.stanford.nlp.ling.tokensregex; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Table used to lookup multi-word phrases.
 * This class provides functions for looking up all instances of known phrases in a document in an efficient manner.
 *
 * Phrases can be added to the phrase table using
 * <ul>
 *   <li>readPhrases</li>
 *   <li>readPhrasesWithTagScores</li>
 *   <li>addPhrase</li>
 * </ul>
 *
 * You can lookup phrases in the table using
 * <ul>
 *   <li>get</li>
 *   <li>lookup</li>
 * </ul>
 *
 * You can find phrases occurring in a piece of text using
 * <ul>
 *   <li>findAllMatches</li>
 *   <li>findNonOverlappingPhrases</li>
 * </ul>
 * @author Angel Chang
 */
public class PhraseTable implements Serializable
{

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(PhraseTable.class);

  private static final String PHRASE_END = "";
  private static final long serialVersionUID = 1L;
  Map<String,Object> rootTree;

  public boolean normalize = true;
  public boolean caseInsensitive = false;
  public boolean ignorePunctuation = false;
  public boolean ignorePunctuationTokens = true;
  public Annotator tokenizer;  // tokenizing annotator

  int nPhrases = 0;
  int nStrings = 0;

  transient CacheMap<String,String> normalizedCache = new CacheMap<>(5000);

  public PhraseTable() {}

  public PhraseTable(int initSize) { rootTree = new HashMap<>(initSize); }

  public PhraseTable(boolean normalize, boolean caseInsensitive, boolean ignorePunctuation) {
    this.normalize = normalize;
    this.caseInsensitive = caseInsensitive;
    this.ignorePunctuation = ignorePunctuation;
  }

  public boolean isEmpty() {
    return (nPhrases == 0);
  }

  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  public Phrase get(Object key) {
    if (key instanceof String) {
      return lookup((String) key);
    } else if (key instanceof WordList) {
      return lookup((WordList) key);
    } else {
      return null;
    }
  }

  /**
   * Clears this table
   */
  public void clear()
  {
    rootTree = null;
    nPhrases = 0;
    nStrings = 0;
  }

  public void setNormalizationCacheSize(int cacheSize)
  {
    CacheMap<String,String> newNormalizedCache = new CacheMap<>(cacheSize);
    newNormalizedCache.putAll(normalizedCache);
    normalizedCache = newNormalizedCache;
  }

  /**
   * Input functions to read in phrases to the table
   */

  private static final Pattern tabPattern = Pattern.compile("\t");

  /**
   * Read in phrases from a file (assumed to be tab delimited)
   * @param filename - Name of file
   * @param checkTag - Indicates if there is a tag column (assumed to be 2nd column)
   *                   If false, treats entire line as the phrase
   * @throws IOException
   */
  public void readPhrases(String filename, boolean checkTag) throws IOException
  {
    readPhrases(filename, checkTag, tabPattern);
  }

  /**
   * Read in phrases from a file.  Column delimiters are matched using regex
   * @param filename - Name of file
   * @param checkTag - Indicates if there is a tag column (assumed to be 2nd column)
   *                   If false, treats entire line as the phrase
   * @param delimiterRegex - Regex for identifying column delimiter
   * @throws IOException
   */
  public void readPhrases(String filename, boolean checkTag, String delimiterRegex) throws IOException
  {
    readPhrases(filename, checkTag, Pattern.compile(delimiterRegex));
  }

  public void readPhrases(String filename, boolean checkTag, Pattern delimiterPattern) throws IOException
  {
    Timing timer = new Timing();
    timer.doing("Reading phrases: " + filename);
    BufferedReader br = IOUtils.readerFromString(filename);
    String line;
    while ((line = br.readLine()) != null) {
      if (checkTag) {
        String[] columns = delimiterPattern.split(line, 2);
        if (columns.length == 1) {
          addPhrase(columns[0]);
        } else {
          addPhrase(columns[0], columns[1]);
        }
      } else {
        addPhrase(line);
      }
    }
    br.close();
    timer.done();
  }

  /**
   * Read in phrases where there is each pattern has a score of being associated with a certain tag.
   * The file format is assumed to be
   *   phrase\ttag1 count\ttag2 count...
   * where the phrases and tags are delimited by tabs, and each tag and count is delimited by whitespaces
   * @param filename
   * @throws IOException
   */
  public void readPhrasesWithTagScores(String filename) throws IOException
  {
    readPhrasesWithTagScores(filename, tabPattern, whitespacePattern);
  }

  public void readPhrasesWithTagScores(String filename, String fieldDelimiterRegex,
                                    String countDelimiterRegex) throws IOException
  {
    readPhrasesWithTagScores(filename, Pattern.compile(fieldDelimiterRegex), Pattern.compile(countDelimiterRegex));
  }

  public void readPhrasesWithTagScores(String filename, Pattern fieldDelimiterPattern, Pattern countDelimiterPattern) throws IOException
  {
    Timing timer = new Timing();
    timer.doing("Reading phrases: " + filename);
    BufferedReader br = IOUtils.readerFromString(filename);
    String line;
    int lineno = 0;
    while ((line = br.readLine()) != null) {
      String[] columns = fieldDelimiterPattern.split(line);
      String phrase = columns[0];
      // Pick map factory to use depending on number of tags we have
      MapFactory<String,MutableDouble> mapFactory = (columns.length < 20)?
              MapFactory.<String,MutableDouble>arrayMapFactory(): MapFactory.<String,MutableDouble>linkedHashMapFactory();
      Counter<String> counts = new ClassicCounter<>(mapFactory);
      for (int i = 1; i < columns.length; i++) {
        String[] tagCount = countDelimiterPattern.split(columns[i], 2);
        if (tagCount.length == 2) {
          try {
            counts.setCount(tagCount[0], Double.parseDouble(tagCount[1]));
          } catch (NumberFormatException ex) {
            throw new RuntimeException("Error processing field " + i + ": '" + columns[i] +
                    "' from (" + filename + ":" + lineno + "): " + line, ex);
          }
        } else {
          throw new RuntimeException("Error processing field " + i + ": '" + columns[i] +
                  "' from + (" + filename + ":" + lineno + "): " + line);
        }
      }
      addPhrase(phrase, null, counts);
      lineno++;
    }
    br.close();
    timer.done();
  }

  public void readPhrases(String filename, int phraseColIndex, int tagColIndex) throws IOException
  {
    if (phraseColIndex < 0) {
      throw new IllegalArgumentException("Invalid phraseColIndex " + phraseColIndex);
    }
    Timing timer = new Timing();
    timer.doing("Reading phrases: " + filename);
    BufferedReader br = IOUtils.readerFromString(filename);
    String line;
    while ((line = br.readLine()) != null) {
      String[] columns = tabPattern.split(line);
      String phrase = columns[phraseColIndex];
      String tag = (tagColIndex >= 0)? columns[tagColIndex]: null;
      addPhrase(phrase, tag);
    }
    br.close();
    timer.done();
  }

  public static Phrase getLongestPhrase(List<Phrase> phrases)
  {
    Phrase longest = null;
    for (Phrase phrase:phrases) {
      if (longest == null || phrase.isLonger(longest)) {
        longest = phrase;
      }
    }
    return longest;
  }

  public String[] splitText(String phraseText)
  {
    String[] words;
    if (tokenizer != null) {
      Annotation annotation = new Annotation(phraseText);
      tokenizer.annotate(annotation);
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      words = new String[tokens.size()];
      for (int i = 0; i < tokens.size(); i++) {
        words[i] = tokens.get(i).word();
      }
    } else {
      phraseText = possPattern.matcher(phraseText).replaceAll(" 's$1");
      words = delimPattern.split(phraseText);
    }
    return words;
  }

  public WordList toWordList(String phraseText)
  {
    String[] words = splitText(phraseText);
    return new StringList(words);
  }

  public WordList toNormalizedWordList(String phraseText)
  {
    String[] words = splitText(phraseText);
    List<String> list = new ArrayList<>(words.length);
    for (String word:words) {
      word = getNormalizedForm(word);
      if (word.length() > 0) {
        list.add(word);
      }
    }
    return new StringList(list);
  }

  public void addPhrases(Collection<String> phraseTexts)
  {
    for (String phraseText:phraseTexts) {
      addPhrase(phraseText, null);
    }
  }

  public void addPhrases(Map<String,String> taggedPhraseTexts)
  {
    for (String phraseText:taggedPhraseTexts.keySet()) {
      addPhrase(phraseText, taggedPhraseTexts.get(phraseText));
    }
  }

  public boolean addPhrase(String phraseText)
  {
    return addPhrase(phraseText, null);
  }

  public boolean addPhrase(String phraseText, String tag)
  {
    return addPhrase(phraseText, tag, null);
  }

  public boolean addPhrase(String phraseText, String tag, Object phraseData)
  {
    WordList wordList = toNormalizedWordList(phraseText);
    return addPhrase(phraseText, tag, wordList, phraseData);
  }

  public boolean addPhrase(List<String> tokens)
  {
    return addPhrase(tokens, null);
  }

  public boolean addPhrase(List<String> tokens, String tag)
  {
    return addPhrase(tokens, tag, null);
  }

  public boolean addPhrase(List<String> tokens, String tag, Object phraseData)
  {
    WordList wordList = new StringList(tokens);
    return addPhrase(StringUtils.join(tokens, " "), tag, wordList, phraseData);
  }

  private int MAX_LIST_SIZE = 20;
  private synchronized boolean addPhrase(String phraseText, String tag, WordList wordList, Object phraseData)
  {
    if (rootTree == null) {
      rootTree = new HashMap<>();
    }
    return addPhrase(rootTree, phraseText, tag, wordList, phraseData, 0);
  }

  private synchronized void addPhrase(Map<String,Object> tree, Phrase phrase, int wordIndex)
  {
    String word = (phrase.wordList.size() <= wordIndex)? PHRASE_END:phrase.wordList.getWord(wordIndex);
    Object node = tree.get(word);
    if (node == null) {
      tree.put(word, phrase);
    } else if (node instanceof Phrase) {
      // create list with this phrase and other and put it here
      List<Object> list = new ArrayList<>(2);
      list.add(phrase);
      list.add(node);
      tree.put(word, list);
    } else if (node instanceof Map) {
      addPhrase((Map<String,Object>) node, phrase, wordIndex+1);
    } else if (node instanceof List) {
      ((List) node).add(phrase);
    } else {
      throw new RuntimeException("Unexpected class " + node.getClass() + " while adding word "
              + wordIndex + "(" + word + ") in phrase " + phrase.getText());
    }
  }

  private synchronized boolean addPhrase(Map<String,Object> tree,
                                         String phraseText, String tag, WordList wordList, Object phraseData, int wordIndex)
  {
    // Find place to insert this item
    boolean phraseAdded = false;  // True if this phrase was successfully added to the phrase table
    boolean newPhraseAdded = false;    // True if the phrase was a new phrase
    boolean oldPhraseNewFormAdded = false;      // True if the phrase already exists, and this was new form added to old phrase
    for (int i = wordIndex; i < wordList.size(); i++) {
      String word = Interner.globalIntern(wordList.getWord(i));
      Object node = tree.get(word);
      if (node == null) {
        // insert here
        Phrase phrase = new Phrase(wordList, phraseText, tag, phraseData);
        tree.put(word, phrase);
        phraseAdded = true;
        newPhraseAdded = true;
      } else if (node instanceof Phrase) {
        // check rest of the phrase matches
        Phrase oldphrase = (Phrase) node;
        int matchedTokenEnd = checkWordListMatch(
          oldphrase, wordList, 0, wordList.size(), i+1, true);
        if (matchedTokenEnd >= 0) {
          oldPhraseNewFormAdded = oldphrase.addForm(phraseText);
        } else {
          // create list with this phrase and other and put it here
          Phrase newphrase = new Phrase(wordList, phraseText, tag, phraseData);
          List<Phrase> list = new ArrayList<>(2);
          list.add(oldphrase);
          list.add(newphrase);
          tree.put(word, list);
          newPhraseAdded = true;
        }
        phraseAdded = true;
      } else if (node instanceof Map) {
        tree = (Map<String, Object>) node;
      } else if (node instanceof List) {
        // Search through list for matches to word (at this point, the table is small, so no Map)
        List lookupList = (List) node;
        int nMaps = 0;
        for (Object obj:lookupList) {
          if (obj instanceof Phrase) {
            // check rest of the phrase matches
            Phrase oldphrase = (Phrase) obj;
            int matchedTokenEnd = checkWordListMatch(
              oldphrase, wordList, 0, wordList.size(), i, true);
            if (matchedTokenEnd >= 0) {
              oldPhraseNewFormAdded = oldphrase.addForm(phraseText);
              phraseAdded = true;
              break;
            }
          } else if (obj instanceof Map) {
            if (nMaps == 1) {
              throw new RuntimeException("More than one map in list while adding word "
                      + i + "(" + word + ") in phrase " + phraseText);
            }
            tree = (Map<String, Object>) obj;
            nMaps++;
          } else  {
            throw new RuntimeException("Unexpected class in list " + obj.getClass() + " while adding word "
                    + i + "(" + word + ") in phrase " + phraseText);
          }
        }
        if (!phraseAdded && nMaps == 0) {
          // add to list
          Phrase newphrase = new Phrase(wordList, phraseText, tag, phraseData);
          lookupList.add(newphrase);
          newPhraseAdded = true;
          phraseAdded = true;
          if (lookupList.size() > MAX_LIST_SIZE) {
            // convert lookupList (should consist only of phrases) to map
            Map newMap = new HashMap<String,Object>(lookupList.size());
            for (Object obj:lookupList) {
              if (obj instanceof Phrase) {
                Phrase oldphrase = (Phrase) obj;
                addPhrase(newMap, oldphrase, i+1);
              } else  {
                throw new RuntimeException("Unexpected class in list " + obj.getClass() + " while converting list to map");
              }
            }
            tree.put(word,newMap);
          }
        }
      } else {
        throw new RuntimeException("Unexpected class in list " + node.getClass() + " while adding word "
                + i + "(" + word + ") in phrase " + phraseText);
      }
      if (phraseAdded) {
        break;
      }
    }
    if (!phraseAdded) {
      if (wordList.size() == 0) {
        log.warn(phraseText + " not added");
      } else {
        Phrase oldphrase = (Phrase) tree.get(PHRASE_END);
        if (oldphrase != null) {
          int matchedTokenEnd = checkWordListMatch(
                  oldphrase, wordList, 0, wordList.size(), wordList.size(), true);
          if (matchedTokenEnd >= 0) {
            oldPhraseNewFormAdded = oldphrase.addForm(phraseText);
          } else {
            // create list with this phrase and other and put it here
            Phrase newphrase = new Phrase(wordList, phraseText, tag, phraseData);
            List<Phrase> list = new ArrayList<>(2);
            list.add(oldphrase);
            list.add(newphrase);
            tree.put(PHRASE_END, list);
            newPhraseAdded = true;
          }
        } else {
          Phrase newphrase = new Phrase(wordList, phraseText, tag, phraseData);
          tree.put(PHRASE_END, newphrase);
          newPhraseAdded = true;
        }
      }
    }
    if (newPhraseAdded) {
      nPhrases++;
      nStrings++;
    } else {
      nStrings++;
    }
    return (newPhraseAdded || oldPhraseNewFormAdded);
  }

  public String getNormalizedForm(String word)
  {
    String normalized = normalizedCache.get(word);
    if (normalized == null) {
      normalized = createNormalizedForm(word);
      synchronized (this) {
        normalizedCache.put(word, normalized);
      }
    }
    return normalized;
  }

  private static final Pattern punctWhitespacePattern = Pattern.compile("\\s*(\\p{Punct})\\s*");
  private static final Pattern whitespacePattern = Pattern.compile("\\s+");
  private static final Pattern delimPattern = Pattern.compile("[\\s_-]+");
  private static final Pattern possPattern = Pattern.compile("'s(\\s+|$)");
  private String createNormalizedForm(String word)
  {
    if (normalize) {
      word = StringUtils.normalize(word);
    }
    if (caseInsensitive) {
      word = word.toLowerCase();
    }
    if (ignorePunctuation) {
      word = punctWhitespacePattern.matcher(word).replaceAll("");
    } else if (ignorePunctuationTokens) {
      if (punctWhitespacePattern.matcher(word).matches()) {
        word = "";
      }
    }
    word = whitespacePattern.matcher(word).replaceAll("");
    return word;
  }

  public Phrase lookup(String phrase)
  {
    return lookup(toWordList(phrase));
  }

  public Phrase lookupNormalized(String phrase)
  {
    return lookup(toNormalizedWordList(phrase));
  }

  public Phrase lookup(WordList wordList)
  {
    if (wordList == null || rootTree == null) return null;
    Map<String,Object> tree = rootTree;
    for (int i = 0; i < wordList.size(); i++) {
      String word = wordList.getWord(i);
      Object node = tree.get(word);
      if (node == null) {
        return null;
      } else if (node instanceof Phrase) {
        Phrase phrase = (Phrase) node;
        int matchedTokenEnd = checkWordListMatch(
          phrase, wordList, 0, wordList.size(), i, true);

        if (matchedTokenEnd >= 0) {
          return phrase;
        }
      } else if (node instanceof Map) {
        tree = (Map<String, Object>) node;
      } else if (node instanceof List) {
        // Search through list for matches to word (at this point, the table is small, so no Map)
        List lookupList = (List) node;
        int nMaps = 0;
        for (Object obj:lookupList) {
          if (obj instanceof Phrase) {
            // check rest of the phrase matches
            Phrase phrase = (Phrase) obj;
            int matchedTokenEnd = checkWordListMatch(
              phrase, wordList, 0, wordList.size(), i, true);

            if (matchedTokenEnd >= 0) {
              return phrase;
            }
          } else if (obj instanceof Map) {
            if (nMaps == 1) {
              throw new RuntimeException("More than one map in list while looking up word "
                      + i + "(" + word + ") in phrase " + wordList.toString());
            }
            tree = (Map<String, Object>) obj;
            nMaps++;
          } else  {
            throw new RuntimeException("Unexpected class in list " + obj.getClass() + " while looking up word "
                    + i + "(" + word + ") in phrase " + wordList.toString());
          }
        }
        if (nMaps == 0) {
          return null;
        }
      } else {
        throw new RuntimeException("Unexpected class in list " + node.getClass() + " while looking up word "
                + i + "(" + word + ") in phrase " + wordList.toString());
      }
    }
    Phrase phrase = (Phrase) tree.get(PHRASE_END);
    if (phrase != null) {
      int matchedTokenEnd = checkWordListMatch(
        phrase, wordList, 0, wordList.size(), wordList.size(), true);
      return (matchedTokenEnd >= 0)? phrase:null;
    } else {
      return null;
    }
  }

  /**
   * Given a segment of text, returns list of spans (PhraseMatch) that corresponds
   *  to a phrase in the table
   * @param text Input text to search over
   * @return List of all matched spans
   */
  public List<PhraseMatch> findAllMatches(String text)
  {
    WordList tokens = toNormalizedWordList(text);
    return findAllMatches(tokens, 0, tokens.size(), false);
  }

  /**
   * Given a list of tokens, returns list of spans (PhraseMatch) that corresponds
   *  to a phrase in the table
   * @param tokens List of tokens to search over
   * @return List of all matched spans
   */
  public List<PhraseMatch> findAllMatches(WordList tokens)
  {
    return findAllMatches(tokens, 0, tokens.size(), true);
  }

  /**
   * Given a segment of text, returns list of spans (PhraseMatch) that corresponds
   *  to a phrase in the table (filtered by the list of acceptable phrase)
   * @param acceptablePhrases - What phrases to look for (need to be subset of phrases already in table)
   * @param text Input text to search over
   * @return List of all matched spans
   */
  public List<PhraseMatch> findAllMatches(List<Phrase> acceptablePhrases, String text)
  {
    WordList tokens = toNormalizedWordList(text);
    return findAllMatches(acceptablePhrases, tokens, 0, tokens.size(), false);
  }

  /**
   * Given a list of tokens, returns list of spans (PhraseMatch) that corresponds
   *  to a phrase in the table (filtered by the list of acceptable phrase)
   * @param acceptablePhrases - What phrases to look for (need to be subset of phrases already in table)
   * @param tokens List of tokens to search over
   * @return List of all matched spans
   */
  public List<PhraseMatch> findAllMatches(List<Phrase> acceptablePhrases, WordList tokens)
  {
    return findAllMatches(acceptablePhrases, tokens, 0, tokens.size(), true);
  }

  public List<PhraseMatch> findAllMatches(WordList tokens,
                                          int tokenStart, int tokenEnd,
                                          boolean needNormalization)
  {
    return findMatches(null, tokens, tokenStart, tokenEnd,
            needNormalization,
            true /* find all */,
            false /* don't need to match end exactly */);
  }

  public List<PhraseMatch> findAllMatches(List<Phrase> acceptablePhrases,
                                          WordList tokens,
                                          int tokenStart, int tokenEnd,
                                          boolean needNormalization)
  {
    return findMatches(acceptablePhrases, tokens, tokenStart, tokenEnd,
            needNormalization,
            true /* find all */,
            false /* don't need to match end exactly */);
  }

  public List<PhraseMatch> findMatches(String text)
  {
    WordList tokens = toNormalizedWordList(text);
    return findMatches(tokens, 0, tokens.size(), false);
  }

  public List<PhraseMatch> findMatches(WordList tokens)
  {
    return findMatches(tokens, 0, tokens.size(), true);
  }

  public List<PhraseMatch> findMatches(WordList tokens,
                                       int tokenStart, int tokenEnd,
                                       boolean needNormalization)
  {
    return findMatches(null, tokens, tokenStart, tokenEnd,
            needNormalization,
            false /* don't need to find all */,
            false /* don't need to match end exactly */);
  }

  public List<PhraseMatch> findMatches(String text,
                                       int tokenStart, int tokenEnd,
                                       boolean needNormalization)
  {
    WordList tokens = toNormalizedWordList(text);
    return findMatches(tokens, tokenStart, tokenEnd, false);
  }

  protected int checkWordListMatch(Phrase phrase, WordList tokens,
                                   int tokenStart, int tokenEnd,
                                   int checkStart,
                                   boolean matchEnd)
  {
    if (checkStart < tokenStart) return -1;
    int i;
    int phraseSize = phrase.wordList.size();
    for (i = checkStart; i < tokenEnd && i - tokenStart < phraseSize; i++) {
      String word = tokens.getWord(i);
      String phraseWord = phrase.wordList.getWord(i - tokenStart);
      if (!phraseWord.equals(word)) {
        return -1;
      }
    }
    if (i - tokenStart == phraseSize) {
      // All tokens in phrase has been matched!
      if (matchEnd) {
        return (i == tokenEnd)? i:-1;
      } else {
        return i;
      }
    } else {
      return -1;
    }
  }

  public List<PhraseMatch> findNonOverlappingPhrases(List<PhraseMatch> phraseMatches)
  {
    if (phraseMatches.size() > 1) {
      return IntervalTree.getNonOverlapping(phraseMatches, PHRASEMATCH_LENGTH_ENDPOINTS_COMPARATOR);
    } else {
      return phraseMatches;
    }
  }

  protected List<PhraseMatch> findMatches(Collection<Phrase> acceptablePhrases,
                                          WordList tokens, int tokenStart, int tokenEnd,
                                          boolean needNormalization, boolean findAll, boolean matchEnd)
  {
    if (needNormalization) {
      assert(tokenStart >= 0);
      assert(tokenEnd > tokenStart);
      int n = tokenEnd - tokenStart;
      List<String> normalized = new ArrayList<>(n);
      int[] tokenIndexMap = new int[n+1];
      int j = 0, last = 0;
      for (int i = tokenStart; i < tokenEnd; i++) {
        String word = tokens.getWord(i);
        word = getNormalizedForm(word);
        if (word.length() != 0) {
          normalized.add(word);
          tokenIndexMap[j] = i;
          last = i;
          j++;
        }
      }
      tokenIndexMap[j] = Math.min(last+1, tokenEnd);
      List<PhraseMatch> matched = findMatchesNormalized(acceptablePhrases, new StringList(normalized),
              0, normalized.size(), findAll, matchEnd);
      for (PhraseMatch pm:matched) {
        assert(pm.tokenBegin >= 0);
        assert(pm.tokenEnd >= pm.tokenBegin);
        assert(pm.tokenEnd <= normalized.size());
        if (pm.tokenEnd > 0 && pm.tokenEnd > pm.tokenBegin) {
          pm.tokenEnd = tokenIndexMap[pm.tokenEnd-1]+1;
        } else {
          pm.tokenEnd = tokenIndexMap[pm.tokenEnd];
        }
        pm.tokenBegin = tokenIndexMap[pm.tokenBegin];
        assert(pm.tokenBegin >= 0);
        assert(pm.tokenEnd >= pm.tokenBegin);
      }
      return matched;
    } else {
      return findMatchesNormalized(acceptablePhrases, tokens, tokenStart, tokenEnd, findAll, matchEnd);
    }
  }

  protected List<PhraseMatch> findMatchesNormalized(Collection<Phrase> acceptablePhrases,
                                                    WordList tokens, int tokenStart, int tokenEnd,
                                                    boolean findAll, boolean matchEnd)
  {
    List<PhraseMatch> matched = new ArrayList<>();
    Stack<StackEntry> todoStack = new Stack<>();
    todoStack.push(new StackEntry(rootTree, tokenStart, tokenStart, tokenEnd, findAll? tokenStart+1:-1));
    while (!todoStack.isEmpty()) {
      StackEntry cur = todoStack.pop();
      Map<String, Object> tree = cur.tree;
      for (int i = cur.tokenNext; i <= cur.tokenEnd; i++) {
        if (tree.containsKey(PHRASE_END)) {
          Phrase phrase = (Phrase) tree.get(PHRASE_END);
          if (acceptablePhrases == null || acceptablePhrases.contains(phrase)) {
            int matchedTokenEnd = checkWordListMatch(
              phrase, tokens, cur.tokenStart, cur.tokenEnd, i, matchEnd);
            if (matchedTokenEnd >= 0) {
              matched.add(new PhraseMatch(phrase, cur.tokenStart, matchedTokenEnd));
            }
          }
        }
        if (i == cur.tokenEnd) break;
        String word = tokens.getWord(i);
        Object node = tree.get(word);
        if (node == null) {
          break;
        } else if (node instanceof Phrase) {
          // check rest of the phrase matches
          Phrase phrase = (Phrase) node;
          if (acceptablePhrases == null || acceptablePhrases.contains(phrase)) {
            int matchedTokenEnd = checkWordListMatch(
              phrase, tokens, cur.tokenStart, cur.tokenEnd, i+1, matchEnd);
            if (matchedTokenEnd >= 0) {
              matched.add(new PhraseMatch(phrase, cur.tokenStart, matchedTokenEnd));
            }
          }
          break;
        } else if (node instanceof Map) {
          tree = (Map<String, Object>) node;
        } else if (node instanceof List) {
          // Search through list for matches to word (at this point, the table is small, so no Map)
          List lookupList = (List) node;
          for (Object obj:lookupList) {
            if (obj instanceof Phrase) {
              // check rest of the phrase matches
              Phrase phrase = (Phrase) obj;
              if (acceptablePhrases == null || acceptablePhrases.contains(phrase)) {
                int matchedTokenEnd = checkWordListMatch(
                  phrase, tokens, cur.tokenStart, cur.tokenEnd, i+1, matchEnd);
                if (matchedTokenEnd >= 0) {
                  matched.add(new PhraseMatch(phrase, cur.tokenStart, matchedTokenEnd));
                }
              }
            } else if (obj instanceof Map) {
              todoStack.push(new StackEntry((Map<String,Object>) obj, cur.tokenStart, i+1, cur.tokenEnd, -1));
            } else  {
              throw new RuntimeException("Unexpected class in list " + obj.getClass() + " while looking up " + word);
            }
          }
          break;
        } else {
          throw new RuntimeException("Unexpected class " + node.getClass() + " while looking up " + word);
        }
      }
      if (cur.continueAt >= 0) {
        int newStart = (cur.continueAt > cur.tokenStart)? cur.continueAt: cur.tokenStart+1;
        if (newStart < cur.tokenEnd) {
          todoStack.push(new StackEntry(cur.tree, newStart, newStart, cur.tokenEnd, newStart+1));
        }
      }
    }
    return matched;
  }

  public Iterator<Phrase> iterator() {
    return new PhraseTableIterator(this);
  }

  private static class PhraseTableIterator extends AbstractIterator<Phrase> {
    private PhraseTable phraseTable;
    private Stack<Iterator<Object>> iteratorStack = new Stack<>();
    private Phrase next = null;

    public PhraseTableIterator(PhraseTable phraseTable) {
      this.phraseTable = phraseTable;
      this.iteratorStack.push(this.phraseTable.rootTree.values().iterator());
      this.next = getNext();
    }

    private Phrase getNext() {
      while (!iteratorStack.isEmpty()) {
        Iterator<Object> iter = iteratorStack.peek();
        if (iter.hasNext()) {
          Object obj = iter.next();
          if (obj instanceof Phrase) {
            return (Phrase) obj;
          } else if (obj instanceof Map) {
            iteratorStack.push(((Map) obj).values().iterator());
          } else if (obj instanceof List) {
            iteratorStack.push(((List) obj).iterator());
          } else {
            throw new RuntimeException("Unexpected class in phrase table " + obj.getClass());
          }
        } else {
          iteratorStack.pop();
        }
      }
      return null;
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public Phrase next() {
      Phrase res = next;
      next = getNext();
      return res;
    }
  }

  private static class StackEntry
  {
    Map<String, Object> tree;
    int tokenStart;
    int tokenNext;
    int tokenEnd;
    int continueAt;

    private StackEntry(Map<String, Object> tree, int tokenStart, int tokenNext, int tokenEnd, int continueAt) {
      this.tree = tree;
      this.tokenStart = tokenStart;
      this.tokenNext = tokenNext;
      this.tokenEnd = tokenEnd;
      this.continueAt = continueAt;
    }
  }

  /**
   * A phrase is a multiword expression
   */
  public static class Phrase
  {
    /**
     * List of words in this phrase
     */
    WordList wordList;
    String text;
    String tag;
    Object data; // additional data associated with the phrase

    // Alternate forms that can be used for lookup elsewhere
    private Set<String> alternateForms;

    public Phrase(WordList wordList, String text, String tag, Object data) {
      this.wordList = wordList;
      this.text = text;
      this.tag = tag;
      this.data = data;
    }

    public boolean isLonger(Phrase phrase)
    {
      return (this.getWordList().size() > phrase.getWordList().size()
             || (this.getWordList().size() == phrase.getWordList().size()
                 && this.getText().length() > phrase.getText().length()));
    }

    public boolean addForm(String form) {
      if (alternateForms == null) {
        alternateForms = new HashSet<>(4);
        alternateForms.add(text);
      }
      return alternateForms.add(form);
    }

    public WordList getWordList() {
      return wordList;
    }

    public String getText() {
      return text;
    }

    public String getTag() {
      return tag;
    }

    public Object getData() {
      return data;
    }

    public Collection<String> getAlternateForms() {
      if (alternateForms == null) {
        List<String> forms = new ArrayList<>(1);
        forms.add(text);
        return forms;
      }
      return alternateForms;
    }

    public String toString()
    {
      return text;
    }
  }

  public final static Comparator<PhraseMatch> PHRASEMATCH_LENGTH_ENDPOINTS_COMPARATOR =
          Comparators.chain(HasInterval.LENGTH_GT_COMPARATOR, HasInterval.ENDPOINTS_COMPARATOR);

  /**
   * Represents a matched phrase
   */
  public static class PhraseMatch implements HasInterval<Integer>
  {
    Phrase phrase;
    int tokenBegin;
    int tokenEnd;
    transient Interval<Integer> span;

    public PhraseMatch(Phrase phrase, int tokenBegin, int tokenEnd) {
      this.phrase = phrase;
      this.tokenBegin = tokenBegin;
      this.tokenEnd = tokenEnd;
    }

    public Phrase getPhrase() {
      return phrase;
    }

    public int getTokenBegin() {
      return tokenBegin;
    }

    public int getTokenEnd() {
      return tokenEnd;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(phrase);
      sb.append(" at (").append(tokenBegin);
      sb.append(",").append(tokenEnd).append(")");
      return sb.toString();
    }

    public Interval<Integer> getInterval() {
      if (span == null) span = Interval.toInterval(tokenBegin, tokenEnd, Interval.INTERVAL_OPEN_END);
      return span;
    }
  }

  public static String toString(WordList wordList)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < wordList.size(); i++) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(wordList.getWord(i));
    }
    return sb.toString();
  }

  public static interface WordList
  {
    String getWord(int i);
    int size();
  }

  public static class TokenList implements WordList
  {
    private List<? extends CoreMap> tokens;
    private Class textKey = CoreAnnotations.TextAnnotation.class;

    public TokenList(List<CoreLabel> tokens) {
      this.tokens = tokens;
    }

    public TokenList(List<? extends CoreMap> tokens, Class key)
    {
      this.tokens = tokens;
      this.textKey = key;
    }

    public String getWord(int i)
    {
      return (String) tokens.get(i).get(textKey);
    }

    public int size()
    {
      return tokens.size();
    }

    public String toString()
    {
      return PhraseTable.toString(this);
    }
  }

  public static class StringList implements WordList
  {
    private List<String> words;

    public StringList(List<String> words) {
      this.words = words;
    }

    public StringList(String[] wordsArray) {
      this.words = Arrays.asList(wordsArray);
    }

    public String getWord(int i) {
      return words.get(i);
    }

    public int size()
    {
      return words.size();
    }

    public String toString()
    {
      return PhraseTable.toString(this);
    }
  }

/*  public static class PhraseCollection implements Collection<Phrase>
  {

  } */

  public static class PhraseStringCollection implements Collection<String>
  {
    PhraseTable phraseTable;
    boolean useNormalizedLookup;

    public PhraseStringCollection(PhraseTable phraseTable, boolean useNormalizedLookup)
    {
      this.phraseTable = phraseTable;
      this.useNormalizedLookup = useNormalizedLookup;
    }

    public int size() {
      return phraseTable.nStrings;
    }

    public boolean isEmpty() {
      return phraseTable.nStrings == 0;
    }

    public boolean contains(Object o) {
      if (o instanceof String) {
        if (useNormalizedLookup) {
          return (phraseTable.lookupNormalized((String) o) != null);
        } else {
          return (phraseTable.lookup((String) o) != null);
        }
      } else {
        return false;
      }
    }

    public Iterator<String> iterator() {
      throw new UnsupportedOperationException("iterator is not supported for PhraseTable.PhraseStringCollection");
//      return new FunctionApplyingIterator( phraseTable.iterator(), new Function<Phrase,String>() {
//        @Override
//        public String apply(Phrase in) {
//          return in.getText();
//        }
//      });
    }

    public Object[] toArray() {
      throw new UnsupportedOperationException("toArray is not supported for PhraseTable.PhraseStringCollection");
    }

    public <T> T[] toArray(T[] a) {
      throw new UnsupportedOperationException("toArray is not supported for PhraseTable.PhraseStringCollection");
    }

    public boolean add(String s) {
      return phraseTable.addPhrase(s);
    }

    public boolean remove(Object o) {
      throw new UnsupportedOperationException("Remove is not supported for PhraseTable.PhraseStringCollection");
    }

    public boolean containsAll(Collection<?> c) {
      for (Object o:c) {
        if (!contains(o)) {
          return false;
        }
      }
      return true;
    }

    public boolean addAll(Collection<? extends String> c) {
      boolean modified = false;
      for (String s:c) {
        if (add(s)) {
          modified = true;
        }
      }
      return modified;
    }

    public boolean removeAll(Collection<?> c) {
      boolean modified = false;
      for (Object o:c) {
        if (remove(o)) {
          modified = true;
        }
      }
      return modified;
    }

    public boolean retainAll(Collection<?> c) {
      throw new UnsupportedOperationException("retainAll is not supported for PhraseTable.PhraseStringCollection");
    }

    public void clear() {
      phraseTable.clear();
    }
  }
}
