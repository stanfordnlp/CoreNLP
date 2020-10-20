// ChineseEnglishWordMap -- a mapping from Chinese to English words.
// Copyright (c) 2002, 2003, 2004 The Board of Trustees of
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
// Map is taken from CEDict Chinese-English Lexicon.  Future versions
// will support multiple Lexicons.
//
// http://www.mandarintools.com/cedict.html
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

package edu.stanford.nlp.trees.international.pennchinese; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for mapping Chinese words to English.  Uses CEDict free Lexicon.
 *
 * @author Galen Andrew
 */
public class ChineseEnglishWordMap implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseEnglishWordMap.class);

  /**
   * 
   */
  private static final long serialVersionUID = 7655332268578049993L;

  private Map<String, Set<String>> map = Generics.newHashMap(10000); // large dictionary!

  private static final String defaultPattern = "[^ ]+ ([^ ]+)[^/]+/(.+)/";
  private static final String defaultDelimiter = "[/;]";
  private static final String defaultCharset = "UTF-8";

  private static final String punctuations[] = {
    "\uff08.*?\uff09",
    "\\(.*?\\)",
    "<.*?>",
    "[\u2033\u20dd\u25cb\u25ef\u2039\u2329\u27e8\u203a\u232a\u27e9\u00ab\u27ea\u00bb\u27eb\u2308\u230b\u27e6\u27e7\u3030\uff5e\u201c\u2036\u201d\u2033\u2307\u301c\u3012\u29c4\u300a\u300b\u3000]",
    "^to "};

  private static final boolean DEBUG = false;

  private boolean normalized = false;


  /**
   * SingletonHolder is loaded on the first execution of getInstance().
   */
  private static class SingletonHolder {
    private SingletonHolder() {}
    private final static ChineseEnglishWordMap INSTANCE = new ChineseEnglishWordMap();
  }


  /**
   * A method for getting a singleton instance of this class.
   * In general, you should use this method rather than the constructor,
   * since each instance of the class is a large data file in memory.
   *
   * @return An instance of ChineseEnglishWordMap
   */
  public static ChineseEnglishWordMap getInstance() {
    return SingletonHolder.INSTANCE;
  }

  /**
   * Does the word exist in the dictionary?
   * @param key The word in Chinese
   * @return Whether it is in the dictionary
   */
  public boolean containsKey(String key) {
    key = key.toLowerCase();
    key = key.trim();
    return map.containsKey(key);
  }

  /**
   *
   * @param key a Chinese word
   * @return the English translation (null if not in dictionary)
   */
  public Set<String> getAllTranslations(String key) {
    key = key.toLowerCase();
    key = key.trim();
    return map.get(key);
  }

  /**
   *
   * @param key a Chinese word
   * @return the English translations as an array (null if not in dictionary)
   */
  public String getFirstTranslation(String key) {
    key = key.toLowerCase();
    key = key.trim();
    Set<String> strings = map.get(key);
    if (strings == null) return null;
    else return strings.iterator().next();
  }

  public void readCEDict(String dictPath) {
    readCEDict(dictPath, defaultPattern, defaultDelimiter, defaultCharset);
  }


  private String normalize(String t) {
    String origT;
    if (DEBUG) { origT = t; }

    if ( ! this.normalized) {
      return t;
    }
    for (String punc : punctuations) {
      t = t.replaceAll(punc, "");
    }
    t = t.trim();
    if (DEBUG && !origT.equals(t)) {
      log.info("orig="+origT);
      log.info("norm="+t);
    }
    return t;
  }

  private Set<String> normalize(Set<String> trans) {
    if (!this.normalized) {
      return trans;
    }

    Set<String> set = Generics.newHashSet();

    for (String t : trans) {
      t = normalize(t);
      if ( ! t.equals("")) {
        set.add(t);
      }
    }
    return set;
  }

  public void readCEDict(String dictPath, String pattern, String delimiter, String charset) {
    try {
      BufferedReader infile = new BufferedReader(new InputStreamReader(new FileInputStream(dictPath), charset));

      Pattern p = Pattern.compile(pattern);
      for (String line = infile.readLine(); line != null; line = infile.readLine()) {
        Matcher m = p.matcher(line);
        if (m.matches()) {
          String word = (m.group(1)).toLowerCase();
          word = word.trim(); // don't want leading or trailing spaces
          String transGroup = m.group(2);
          String[] trans = transGroup.split(delimiter);
          // TODO: strip out punctuations from translation
          if (map.containsKey(word)) {
            Set<String> oldtrans = map.get(word);
            for (String t : trans) {
              t = normalize(t);
              if ( ! t.equals("")) {
                if ( ! oldtrans.contains(t)) {
                  oldtrans.add(t);
                }
              }
            }
          } else {
            Set<String> transList = new LinkedHashSet<>(Arrays.asList(trans));
            String normW = normalize(word);
            Set<String> normSet = normalize(transList);
            if ( ! normW.equals("") && normSet.size() > 0) {
              map.put(normW, normSet);
            }
          }
        }
      }
      infile.close();
    } catch (IOException e) {
      throw new RuntimeException("IOException reading CEDict from file " + dictPath, e);
    }
  }

  /**
   * Make a ChineseEnglishWordMap with a default CEDict path.
   * It looks for the file "cedict_ts.u8" in the working directory, for the
   * value of the CEDICT environment variable, and in a Stanford NLP Group
   * specific place.  It throws an exception if a dictionary cannot be found.
   */
  public ChineseEnglishWordMap() {
    String path = CEDict.path();
    readCEDict(path);
  }

  /**
   * Make a ChineseEnglishWordMap
   * @param dictPath the path/filename of the CEDict
   */
  public ChineseEnglishWordMap(String dictPath) {
    readCEDict(dictPath);
  }

  /**
   * Make a ChineseEnglishWordMap
   * @param dictPath the path/filename of the CEDict
   * @param normalized whether the entries in dictionary are normalized or not
   */
  public ChineseEnglishWordMap(String dictPath, boolean normalized) {
    this.normalized = normalized;
    readCEDict(dictPath);
  }

  public ChineseEnglishWordMap(String dictPath, String pattern, String delimiter, String charset) {
    readCEDict(dictPath, pattern, delimiter, charset);
  }

  public ChineseEnglishWordMap(String dictPath, String pattern, String delimiter, String charset, boolean normalized) {
    this.normalized = normalized;
    readCEDict(dictPath, pattern, delimiter, charset);
  }


  private static boolean isDigits(String in) {
    for (int i = 0, len = in.length(); i < len; i++) {
      if ( ! Character.isDigit(in.charAt(i))) {
	return false;
      }
    }
    return true;
  }

  /**
   * Returns a reversed map of the current map.
   *
   * @return A reversed map of the current map.
   */
  public Map<String, Set<String>> getReverseMap() {
    Set<Map.Entry<String,Set<String>>> entries = map.entrySet();
    Map<String, Set<String>> rMap = Generics.newHashMap(entries.size());
    for (Map.Entry<String,Set<String>> me : entries) {
      String k = me.getKey();
      Set<String> transList = me.getValue();
      for (String trans : transList) {
        Set<String> entry = rMap.get(trans);
        if (entry == null) {
          // reduce default size as most will be small
          Set<String> toAdd = new LinkedHashSet<>(6);
          toAdd.add(k);
          rMap.put(trans, toAdd);
        } else {
          entry.add(k);
        }
      }
    }
    return rMap;
  }

  /**
   * Add all of the mappings from the specified map to the current map.
   */
  public int addMap(Map<String, Set<String>> addM) {
    int newTrans = 0;

    for (Map.Entry<String,Set<String>> me : addM.entrySet()) {
      String k = me.getKey();
      Set<String> addList = me.getValue();
      Set<String> origList = map.get(k);
      if (origList == null) {
        map.put(k, new LinkedHashSet<>(addList));
        Set<String> newList = map.get(k);
        if (newList != null && newList.size() != 0) {
          newTrans+=addList.size();
        }
      } else {
        for (String toAdd : addList) {
          if (!(origList.contains(toAdd))) {
            origList.add(toAdd);
            newTrans++;
          }
        }
      }
    }
    return newTrans;
  }



  @Override
  public String toString() {
    return map.toString();
  }

  public int size() {
    return map.size();
  }


  /**
   * The main method reads (segmented, whitespace delimited) words from a file
   * and prints them with their English translation(s).
   *
   * The path and filename of the CEDict Lexicon can be supplied via the
   * "-dictPath" flag; otherwise the default filename "cedict_ts.u8" in the
   * current directory is checked.
   *
   * By default, only the first translation is printed.  If the "-all" flag
   * is given, all translations are printed.
   *
   * The input and output encoding can be specified using the "-encoding" flag.
   * Otherwise UTF-8 is assumed.
   */
  public static void main(String[] args) throws IOException {
    Map<String, Integer> flagsToNumArgs = Generics.newHashMap();
    flagsToNumArgs.put("-dictPath" , 1);
    flagsToNumArgs.put("-encoding" , 1);
    Map<String, String[]> argMap = StringUtils.argsToMap(args, flagsToNumArgs);
    String[] otherArgs = argMap.get(null);
    if (otherArgs.length < 1) {
      log.info("usage: ChineseEnglishWordMap [-all] [-dictPath path] [-encoding enc_string] inputFile");
      System.exit(1);
    }
    String filename = otherArgs[0];
    boolean allTranslations = argMap.containsKey("-all");
    String charset = defaultCharset;
    if (argMap.containsKey("-encoding")) {
      charset = argMap.get("-encoding")[0];
    }
    BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(filename), charset));

    TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
    String[] dpString = argMap.get("-dictPath");
    ChineseEnglishWordMap cewm = (dpString == null) ? new ChineseEnglishWordMap() : new ChineseEnglishWordMap(dpString[0]);
    int totalWords = 0, coveredWords = 0;

    PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, charset), true);

    for (String line = r.readLine(); line != null; line = r.readLine()) {
      String[] words = line.split("\\s", 1000);
      for (String word : words) {
        totalWords++;
        if (word.length() == 0) continue;
        pw.print(StringUtils.pad(word + ':', 8));
        if (tlp.isPunctuationWord(word)) {
          totalWords--;
          pw.print(word);
	} else if (isDigits(word)) {
	  pw.print(word + " [NUMBER]");
        } else if (cewm.containsKey(word)) {
          coveredWords++;
          if (allTranslations) {
            List<String> trans = new ArrayList<>(cewm.getAllTranslations(word));
            for (String s : trans) {
              pw.print((trans.indexOf(s) > 0 ? "|" : "") + s);
            }
          } else {
            pw.print(cewm.getFirstTranslation(word));
          }
        } else {
          pw.print("[UNK]");
        }
	pw.println();
      }
      pw.println();
    }
    r.close();
    log.info("Finished translating " + totalWords + " words (");
    log.info(coveredWords + " were in dictionary).");
  }
}
