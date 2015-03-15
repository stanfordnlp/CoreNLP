package edu.stanford.nlp.ie.regexp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;

/**
 * A sequence classifier that labels tokens with types based on a simple manual mapping from
 * regular expressions to the types of the entities they are meant to describe.
 * The user provides a file formatted as follows:
 *    regex1    TYPE    overwritableType1,Type2...    priority
 *    regex2    TYPE    overwritableType1,Type2...    priority
 *    ...
 * where each argument is tab-separated, and the last two arguments are optional. Several regexes can be
 * associated with a single type. In the case where multiple regexes match a phrase, the priority ranking
 * is used to choose between the possible types. This classifier is designed to be used as part of a full NER
 * system to label entities that don't fall into the usual NER categories. It only records the label
 * if the token has not already been NER-annotated, or it has been annotated but the NER-type has been
 * designated overwritable (the third argument).
 *
 * NOTE: Following Java regex conventions, some characters in the file need to be escaped. Only a single
 * backslash should be used though, as they are not String literals. Spaces should only be used to
 * separate regular expression tokens; within tokens \\s should be used instead. Genitives and commas
 * at the end of words should be tokenized in the input file.
 *
 * @author jtibs
 * @author Mihai
 *
 */
public class RegexNERSequenceClassifier extends AbstractSequenceClassifier<CoreLabel> {
  private List<Entry> entries;

  /**
   * If true, it overwrites NE labels generated through this regex NER
   * This is necessary because sometimes the RegexNERSequenceClassifier is run successively over the same text (e.g., to overwrite some older annotations)
   */
  private boolean overwriteMyLabels;

  private Set<String> myLabels;

  private boolean ignoreCase;

  public RegexNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels) {
    this(mapping, ignoreCase, overwriteMyLabels, DEFAULT_VALID_POS);
  }

  /**
   * Make a new instance of this classifier. The ignoreCase option allows case-insensitive
   * regular expression matching, provided with the idea that the provided file might just
   * be a manual list of the possible entities for each type.
   * @param mapping
   * @param ignoreCase
   */
  public RegexNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels, String validPosRegex) {
    super(new Properties());
    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    entries = readEntries(mapping, ignoreCase);
    this.ignoreCase = ignoreCase;
    this.overwriteMyLabels = overwriteMyLabels;
    myLabels = new HashSet<String>();
    if(this.overwriteMyLabels) {
      for(Entry entry: entries) myLabels.add(entry.type);
    }
    //System.err.println("RegexNERSequenceClassifier using labels: " +
    //                   myLabels);
  }

  private static class Entry implements Comparable<Entry> {
    public List<Pattern> regex; // the regex, tokenized by splitting on white space
    public String type; // the associated type
    public Set<String> overwritableTypes;
    public double priority;

    public Entry(List<Pattern> regex, String type, Set<String> overwritableTypes, double priority) {
      this.regex = regex;
      this.type = type.intern();
      this.overwritableTypes = overwritableTypes;
      this.priority = priority;
    }

    // if the given priorities are equal, an entry whose regex has more tokens is assigned
    // a higher priority
    public int compareTo(Entry other) {
      if (this.priority > other.priority)
        return -1;
      if (this.priority < other.priority)
        return 1;
      return other.regex.size() - this.regex.size();
    }
  }

  // TODO: make this a property?
  // ms: but really this should be rewritten from scratch
  //     we should have a language to specify regexes over *tokens*, where each token could be a regular Java regex (over words, POSs, etc.)
  private final Pattern validPosPattern;
  public static final String DEFAULT_VALID_POS = "^(NN|JJ)";

  private boolean containsValidPos(List<CoreLabel> tokens, int start, int end) {
    if (validPosPattern == null) {
      return true;
    }
    // System.err.println("CHECKING " + start + " " + end);
    for(int i = start; i < end; i ++){
      // System.err.println("TAG = " + tokens.get(i).tag());
      if (tokens.get(i).tag() == null) {
        throw new IllegalArgumentException("The regex ner was asked to check for valid tags on an untagged sequence.  Either tag the sequence, perhaps with the pos annotator, or create the regex ner with an empty pos tag, perhaps with the flag regexner.validpospattern=");
      }
      Matcher m = validPosPattern.matcher(tokens.get(i).tag());
      if(m.find()) return true;
    }
    return false;
  }

  @Override
  public List<CoreLabel> classify(List<CoreLabel> document) {
    for (Entry entry : entries) {
      int start = 0; // the index of the token from which we begin our search each iteration

      while (true) {
        // only search the part of the document that we haven't yet considered
        // System.err.println("REGEX FIND MATCH FOR " + entry.regex.toString());
        start = findStartIndex(entry, document, start, myLabels);
        if (start == -1) break; // no match found

        // make sure we annotate only valid POS tags
        if (containsValidPos(document, start, start + entry.regex.size())) {
          // annotate each matching token
          for (int i = start; i < start + entry.regex.size(); i++) {
            CoreLabel token = document.get(i);
            token.set(CoreAnnotations.AnswerAnnotation.class, entry.type);
          }
        }
        start++;
      }
    }
    return document;
  }

  public void train(Collection<List<CoreLabel>> docs,
                    DocumentReaderAndWriter<CoreLabel> readerAndWriter) {}

  public void printProbsDocument(List<CoreLabel> document) {}

  public void serializeClassifier(String serializePath) {}

  public void loadClassifier(ObjectInputStream in, Properties props)
          throws IOException, ClassCastException, ClassNotFoundException {}

  /**
   *  Creates a combined list of Entries using the provided mapping file, and sorts them by
   *  first by priority, then the number of tokens in the regex.
   *
   *  @param mapping The path to a file of mappings
   *  @return a sorted list of Entries
   */
  private List<Entry> readEntries(String mapping, boolean ignoreCase) {
    List<Entry> entries = new ArrayList<Entry>();

    try {
      // ms, 2010-10-05: try to load the file from the CLASSPATH first
      InputStream is = getClass().getClassLoader().getResourceAsStream(mapping);
      // if not found in the CLASSPATH, load from the file system
      if (is == null) is = new FileInputStream(mapping);
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));

      int lineCount = 0;
      for (String line; (line = rd.readLine()) != null; ) {
        lineCount ++;
        String[] split = line.split("\t");
        if (split.length < 2 || split.length > 4)
          throw new RuntimeException("Provided mapping file is in wrong format");

        String[] regexes = split[0].trim().split("\\s+");
        String type = split[1].trim();
        Set<String> overwritableTypes = new HashSet<String>();
        overwritableTypes.add(flags.backgroundSymbol);
        overwritableTypes.add(null);
        double priority = 0;
        List<Pattern> tokens = new ArrayList<Pattern>();

        try {
          if (split.length >= 3)
            overwritableTypes.addAll(Arrays.asList(split[2].trim().split(",")));
          if (split.length == 4)
            priority = Double.parseDouble(split[3].trim());

          for (String str : regexes) {
            if(ignoreCase) tokens.add(Pattern.compile(str, Pattern.CASE_INSENSITIVE));
            else tokens.add(Pattern.compile(str));
          }
        } catch(NumberFormatException e) {
          System.err.println("ERROR: Invalid line " + lineCount + " in regexner file " + mapping + ": \"" + line + "\"!");
          throw e;
        }

        entries.add(new Entry(tokens, type, overwritableTypes, priority));
      }
      rd.close();
      is.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    Collections.sort(entries);
    return entries;
  }

  /**
   * Checks if the entry's regex sequence is contained in the tokenized document, starting the search
   * from index searchStart. Also requires that each token's current NER-type be overwritable,
   * and that each token has not yet been Answer-annotated.
   * @param entry
   * @param document
   * @return on success, the index of the first token in the matching sequence, otherwise -1
   */
  private static int findStartIndex(Entry entry, List<CoreLabel> document, int searchStart, Set<String> myLabels) {
    List<Pattern> regex = entry.regex;
    for (int start = searchStart; start <= document.size() - regex.size(); start++) {
      boolean failed = false;
      for (int i = 0; i < regex.size(); i++) {
        Pattern pattern = regex.get(i);
        CoreLabel token = document.get(start + i);
        String NERType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        String currentType = token.get(CoreAnnotations.AnswerAnnotation.class);

        if (! pattern.matcher(token.word()).matches() ||
            currentType != null ||
            ! (entry.overwritableTypes.contains(NERType) ||
               myLabels.contains(NERType) ||
               NERType.equals("O"))) {
          failed = true;
          break;
        }
      }
      if(! failed) {
        //System.err.print("MATCHED REGEX:");
        //for(int i = start; i < start + regex.size(); i ++) System.err.print(" " + document.get(i).word());
        //System.err.println();
        return start;
      }
    }
    return -1;
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }
}
