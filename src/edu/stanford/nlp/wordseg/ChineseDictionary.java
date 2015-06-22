package edu.stanford.nlp.wordseg;

import java.io.*;
import java.io.BufferedInputStream;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.process.ChineseDocumentToSentenceProcessor;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import java.util.zip.GZIPInputStream;

/** This class provides a main method that loads various dictionaries, and
 *  saves them in a serialized version, and runtime compiles them into a word list used as a feature in the segmenter, and
 * @author Pi-Chuan Chang
 */

public class ChineseDictionary {

  private static final boolean DEBUG = false;

  public static final int MAX_LEXICON_LENGTH = 6;
  @SuppressWarnings({"unchecked"})
  Set<String>[] words_ = new HashSet[MAX_LEXICON_LENGTH+1];

  private ChineseDocumentToSentenceProcessor cdtos_; // = null;

  private void serializeDictionary(String serializePath) {
    System.err.print("Serializing dictionaries to " + serializePath + "...");

    try {
      ObjectOutputStream oos = IOUtils.writeStreamFromString(serializePath);

      //oos.writeObject(MAX_LEXICON_LENGTH);
      oos.writeObject(words_);
      //oos.writeObject(cdtos_);
      oos.close();
      System.err.println("done.");
    } catch (Exception e) {
      System.err.println("Failed");
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"unchecked"})
  private static Set<String>[] loadDictionary(String serializePath) {
    Set<String>[] dict = new HashSet[MAX_LEXICON_LENGTH+1];
    for (int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
      dict[i] = Generics.newHashSet();
    }

    System.err.print("loading dictionaries from " + serializePath + "...");

    try {
      // once we read MAX_LEXICON_LENGTH and cdtos as well
      // now these files only store one object we care about
      //ChineseDictionary.MAX_LEXICON_LENGTH = (int) ois.readObject();
      dict = IOUtils.readObjectFromURLOrClasspathOrFileSystem(serializePath);
    } catch (Exception e) {
      System.err.println("Failed to load Chinese dictionary " + serializePath);
      throw new RuntimeException(e);
    }
    return dict;
  }


  public ChineseDictionary(String dict) {
    this(new String[] { dict });
  }

  public ChineseDictionary(String[] dicts) {
    this(dicts, null);
  }

  public ChineseDictionary(String[] dicts,
                           ChineseDocumentToSentenceProcessor cdtos) {
    this(dicts, cdtos, false);
  }

  /**
   * The first argument can be one file path, or multiple files separated by
   * commas
   *
   **/
  public ChineseDictionary(String serDicts,
                           ChineseDocumentToSentenceProcessor cdtos,
                           boolean expandMidDot) {
    this(serDicts.split(","), cdtos, expandMidDot);
  }

  public ChineseDictionary(String[] dicts,
                           ChineseDocumentToSentenceProcessor cdtos,
                           boolean expandMidDot) {
    System.err.println("Loading Chinese dictionaries from "
                       +dicts.length
                       +" files:");
    for(String dict : dicts) {
      System.err.println("  "+dict);
    }
    System.err.println();

    for (int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
      words_[i] = Generics.newHashSet();
    }

    this.cdtos_ = cdtos;

    for(String dict : dicts) {
      if(dict.endsWith("ser.gz")) {
        // TODO: the way this is written would not work if we allow
        // dictionaries to have different settings of MAX_LEXICON_LENGTH
        Set<String>[] dictwords = loadDictionary(dict);
        for (int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
          words_[i].addAll(dictwords[i]);
          dictwords[i] = null;
        }
      } else {
        addDict(dict, expandMidDot);
      }
    }

    int total = 0;
    for(int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
      total += words_[i].size();
    }
    System.err.println("Done. Unique words in ChineseDictionary is: " + total);
  }

  private final Pattern midDot = Pattern.compile(ChineseUtils.MID_DOT_REGEX_STR);

  private void addDict(String dict, boolean expandMidDot) {
    String content = IOUtils.slurpFileNoExceptions(dict,"utf-8");
    String[] lines = content.split("\n");
    System.err.println("  " + dict + ": " + lines.length + " entries");
    for (String line : lines) {
      line = line.trim();
      // normalize any midDot
      if (expandMidDot) {
        // normalize down middot chars
        line = line.replaceAll(ChineseUtils.MID_DOT_REGEX_STR, "\u00B7");
      }
      addOneDict(line);
      if (DEBUG) EncodingPrintWriter.err.println("ORIG: " + line, "UTF-8");
      if (expandMidDot && midDot.matcher(line).find()) {
        line = line.replaceAll(ChineseUtils.MID_DOT_REGEX_STR, "");
        if (DEBUG) EncodingPrintWriter.err.println("ALSO: " + line, "UTF-8");
        addOneDict(line);
      }
    }
  }

  private void addOneDict(String item) {
    int length = item.length();
    if (length == 0) {
      // Do nothing for empty items
    } else if (length <= MAX_LEXICON_LENGTH-1) {
      if (cdtos_ != null) {
        item = cdtos_.normalization(item);
      }
      if (DEBUG) EncodingPrintWriter.err.println("DICT: "+item, "UTF-8");
      words_[length].add(item);
    } else {
      // insist on new String as it may save memory
      String subitem = new String(item.substring(0,MAX_LEXICON_LENGTH));
      if (cdtos_ != null) {
        subitem = cdtos_.normalization(subitem);
      }
      if (DEBUG) EncodingPrintWriter.err.println("DICT: "+subitem, "UTF-8");
      // length=MAX_LEXICON_LENGTH and MAX_LEXICON_LENGTH+
      words_[MAX_LEXICON_LENGTH].add(subitem);
    }
  }

  public boolean contains(String word) {
    int length = word.length();
    if (length <= MAX_LEXICON_LENGTH-1) {
      return words_[length].contains(word);
    } else {
      length = MAX_LEXICON_LENGTH;
      return words_[length].contains(word.substring(0,6));
    }
  }

  public static void main(String[] args) {
    String inputDicts = "/u/nlp/data/chinese-dictionaries/plain/ne_wikipedia-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/newsexplorer_entities_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/Ch-name-list-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt,/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt";

    String output = "/u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz";


    Map<String,Integer> flagMap = Generics.newHashMap();
    flagMap.put("-inputDicts", 1);
    flagMap.put("-output", 1);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args,flagMap);
    // args = argsMap.get(null);
    if(argsMap.keySet().contains("-inputDicts")) {
      inputDicts = argsMap.get("-inputDicts")[0];
    }
    if(argsMap.keySet().contains("-output")) {
      output = argsMap.get("-output")[0];
    }

    String[] dicts = inputDicts.split(",");

    ChineseDocumentToSentenceProcessor cdtos
      = new ChineseDocumentToSentenceProcessor(null);
    boolean expandMidDot = true;

    ChineseDictionary dict = new ChineseDictionary(dicts, cdtos, expandMidDot);
    dict.serializeDictionary(output);

    /*
    //ChineseDictionary dict = new ChineseDictionary(args[0]);
    for (int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
      System.err.println("Length: " + i+": "+dict.words[i].size());
    }
    for (int i = 0; i <= MAX_LEXICON_LENGTH; i++) {
      System.err.println("Length: " + i+": "+dict.words[i].size());
      if (dict.words[i].size() < 1000) {
        for (String word : dict.words[i]) {
          EncodingPrintWriter.err.println(word, "UTF-8");
        }
      }
    }
    for  (int i = 1; i < args.length; i++) {
      System.err.println(args[i] + " " + Boolean.valueOf(dict.contains(args[i])).toString());
    }
    */
  }

}
