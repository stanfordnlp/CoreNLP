package edu.stanford.nlp.ie.regexp; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * A sequence classifier that labels tokens with types based on a simple manual mapping from
 * regular expressions to the types of the entities they are meant to describe.
 * The user provides a file formatted as follows:
 * <pre>
 *    regex1    TYPE    overwritableType1,Type2...    priority
 *    regex2    TYPE    overwritableType1,Type2...    priority
 *    ...
 * </pre>
 * where each argument is tab-separated, and the last two arguments are optional. Several regexes can be
 * associated with a single type. In the case where multiple regexes match a phrase, the priority ranking
 * is used to choose between the possible types. This classifier is designed to be used as part of a full
 * NER system to label entities that don't fall into the usual NER categories. It only records the label
 * if the token has not already been NER-annotated, or it has been annotated but the NER-type has been
 * designated overwritable (the third argument).  Note that this is evaluated token-wise in this classifier,
 * and so it may assign a label against a token sequence that is partly background and partly overwritable.
 * (In contrast, RegexNERAnnotator doesn't allow this.)
 * It assigns labels to AnswerAnnotation, while checking for existing labels in NamedEntityTagAnnotation.
 *
 * The first column regex may be a sequence of regex, each separated by whitespace (matching "\\s+").
 * The regex will match if the successive regex match a sequence of tokens in the input.
 * Spaces can only be used to separate regular expression tokens; within tokens \\s or similar non-space
 * representations need to be used instead.
 * Notes: Following Java regex conventions, some characters in the file need to be escaped. Only a single
 * backslash should be used though, as these are not String literals. The input to RegexNER will have
 * already been tokenized.  So, for example, with our usual English tokenization, things like genitives
 * and commas at the end of words will be separated in the input and matched as a separate token.
 *
 * This class isn't implemented very efficiently, since every regex is evaluated at every token position.
 * So it can and does get quite slow if you have a lot of patterns in your NER rules.
 * {@code TokensRegex} is a more general framework to provide the functionality of this class.
 * But at present we still use this class.
 *
 * @author jtibs
 * @author Mihai
 */
public class RegexNERSequenceClassifier extends AbstractSequenceClassifier<CoreLabel>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(RegexNERSequenceClassifier.class);

  private final List<Entry> entries;

  private final Set<String> myLabels;

  private final boolean ignoreCase;

  // Make this a property?  (But already done as a property at CoreNLP level.)
  // ms: but really this should be rewritten from scratch
  //     we should have a language to specify regexes over *tokens*, where each token could be a regular Java regex (over words, POSs, etc.)
  private final Pattern validPosPattern;
  public static final String DEFAULT_VALID_POS = "^(NN|JJ)";


  public RegexNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels) {
    this(mapping, ignoreCase, overwriteMyLabels, DEFAULT_VALID_POS);
  }

  /**
   * Make a new instance of this classifier. The ignoreCase option allows case-insensitive
   * regular expression matching, allowing the idea that the provided file might just
   * be a manual list of the possible entities for each type.
   *
   * @param mapping A String describing a file/classpath/URI for the RegexNER patterns
   * @param ignoreCase The regex in the mapping file should be compiled ignoring case
   * @param overwriteMyLabels If true, this classifier overwrites NE labels generated through
   *                          this regex NER. This is necessary because sometimes the
   *                          RegexNERSequenceClassifier is run successively over the same
   *                          text (e.g., to overwrite some older annotations).
   * @param validPosRegex May be null or an empty String, in which case any (or no) POS is valid
   *                      in matching. Otherwise, this is a regex which is matched with find()
   *                      [not matches()] and which must be matched by the POS of at least one
   *                      word in the sequence for it to be labeled via any matching rules.
   *                      (Note that this is a postfilter; using this will not speed up matching.)
   */
  public RegexNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels, String validPosRegex) {
    super(new Properties());
    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    try (BufferedReader rd = IOUtils.readerFromString(mapping)) {
      entries = readEntries(rd, ignoreCase);
    } catch (IOException e) {
      throw new RuntimeIOException("Couldn't read RegexNER from " + mapping, e);
    }

    this.ignoreCase = ignoreCase;
    myLabels = Generics.newHashSet();
    // Can always override background or none.
    myLabels.add(flags.backgroundSymbol);
    myLabels.add(null);
    if (overwriteMyLabels) {
      for (Entry entry: entries) myLabels.add(entry.type);
    }
    // log.info("RegexNER using labels: " +  myLabels);
  }

  /**
   * Make a new instance of this classifier. The ignoreCase option allows case-insensitive
   * regular expression matching, allowing the idea that the provided file might just
   * be a manual list of the possible entities for each type.
   *
   * @param reader A Reader for the RegexNER patterns
   * @param ignoreCase The regex in the mapping file should be compiled ignoring case
   * @param overwriteMyLabels If true, this classifier overwrites NE labels generated through
   *                          this regex NER. This is necessary because sometimes the
   *                          RegexNERSequenceClassifier is run successively over the same
   *                          text (e.g., to overwrite some older annotations).
   * @param validPosRegex May be null or an empty String, in which case any (or no) POS is valid
   *                      in matching. Otherwise, this is a regex, and only words with a POS that
   *                      match the regex will be labeled via any matching rules.
   */
  public RegexNERSequenceClassifier(BufferedReader reader,
                                    boolean ignoreCase,
                                    boolean overwriteMyLabels,
                                    String validPosRegex) {
    super(new Properties());
    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    try {
      entries = readEntries(reader, ignoreCase);
    } catch (IOException e) {
      throw new RuntimeIOException("Couldn't read RegexNER from reader", e);
    }

    this.ignoreCase = ignoreCase;
    myLabels = Generics.newHashSet();
    // Can always override background or none.
    myLabels.add(flags.backgroundSymbol);
    myLabels.add(null);
    if (overwriteMyLabels) {
      for (Entry entry: entries) myLabels.add(entry.type);
    }
    // log.info("RegexNER using labels: " + myLabels);
  }

  /**
   * Most AbstractSequenceClassifiers have classIndex set.
   * ClassifierCombiner calls labels() to get the values from the
   * index.
   * <br>
   * TODO: chceck that classIndex isn't used anywhere other than the
   * call to labels()
   */
  @Override
  public Set<String> labels() {
    return myLabels;
  }

  private static class Entry implements Comparable<Entry> {
    public List<Pattern> regex; // the regex, tokenized by splitting on white space
    public List<String> exact = new ArrayList<>();
    public String type; // the associated type
    public Set<String> overwritableTypes;
    public double priority;

    public Entry(List<Pattern> regex, String type, Set<String> overwritableTypes, double priority) {
      this.regex = regex;
      this.type = type.intern();
      this.overwritableTypes = overwritableTypes;
      this.priority = priority;
      // Efficiency shortcut
      for (Pattern p : regex) {
        if (p.toString().matches("[a-zA-Z0-9]+")) {
          exact.add(p.toString());
        } else {
          exact.add(null);
        }
      }
    }

    /** If the given priorities are equal, an entry whose regex has more tokens is assigned
     *  a higher priority. This implementation is not fine-grained enough to be consistent with equals.
     */
    @Override
    public int compareTo(Entry other) {
      if (this.priority > other.priority)
        return -1;
      if (this.priority < other.priority)
        return 1;
      return other.regex.size() - this.regex.size();
    }

    public String toString() {
      return "Entry{" + regex + ' ' + type + ' ' + overwritableTypes + ' ' + priority + '}';
    }
  }

  private boolean containsValidPos(List<CoreLabel> tokens, int start, int end) {
    if (validPosPattern == null) {
      return true;
    }
    // log.info("CHECKING " + start + " " + end);
    for (int i = start; i < end; i ++) {
      // log.info("TAG = " + tokens.get(i).tag());
      if (tokens.get(i).tag() == null) {
        throw new IllegalArgumentException("RegexNER was asked to check for valid tags on an untagged sequence. Either tag the sequence, perhaps with the pos annotator, or create RegexNER with an empty validPosPattern, perhaps with the property regexner.validpospattern");
      }
      Matcher m = validPosPattern.matcher(tokens.get(i).tag());
      if (m.find()) return true;
    }
    return false;
  }

  @Override
  public List<CoreLabel> classify(List<CoreLabel> document) {
    // This is pretty deathly slow. It loops over each entry, and then loops over each document token for it.
    // We could gain by compiling into disjunctions patterns for the same class with the same priorities and restrictions?
    for (Entry entry : entries) {
      int start = 0; // the index of the token from which we begin our search each iteration
      while (true) {
        // only search the part of the document that we haven't yet considered
        // log.info("REGEX FIND MATCH FOR " + entry.regex.toString());
        start = findStartIndex(entry, document, start, myLabels, this.ignoreCase);
        if (start < 0) break; // no match found

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

  /**
   *  Creates a combined list of Entries using the provided mapping file, and sorts them by
   *  first by priority, then the number of tokens in the regex.
   *
   *  @param mapping The Reader containing RegexNER mappings. It's lines are counted from 1
   *  @return a sorted list of Entries
   */
  private static List<Entry> readEntries(BufferedReader mapping, boolean ignoreCase) throws IOException {
    List<Entry> entries = new ArrayList<>();

    int lineCount = 0;
    for (String line; (line = mapping.readLine()) != null; ) {
      lineCount ++;
      // skip blank lines
      if (line.trim().equals(""))
        continue;

      String[] split = line.split("\t");
      if (split.length < 2 || split.length > 4)
        throw new IllegalArgumentException("Provided mapping file is in wrong format: " + line);

      String[] regexes = split[0].trim().split("\\s+");
      String type = split[1].trim();
      Set<String> overwritableTypes = Generics.newHashSet();
      double priority = 0.0;
      List<Pattern> tokens = new ArrayList<>();

      if (split.length >= 3) {
        overwritableTypes.addAll(Arrays.asList(split[2].trim().split(",")));
      }
      // by default, always consider overwriting the background symbol
      overwritableTypes.add("O");

      if (split.length == 4) {
        try {
          priority = Double.parseDouble(split[3].trim());
        } catch(NumberFormatException e) {
          throw new IllegalArgumentException("ERROR: Invalid line " + lineCount + " in regexner file " + mapping + ": \"" + line + "\"!", e);
        }
      }

      try {
        for (String str : regexes) {
          if(ignoreCase) tokens.add(Pattern.compile(str, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
          else tokens.add(Pattern.compile(str));
        }
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("ERROR: Invalid line " + lineCount + " in regexner file " + mapping + ": \"" + line + "\"!", e);
      }

      entries.add(new Entry(tokens, type, overwritableTypes, priority));
    }

    Collections.sort(entries);
    // log.info("Read these entries:");
    // log.info(entries);
    return entries;
  }

  /**
   * Checks if the entry's regex sequence is contained in the tokenized document, starting the search
   * from index searchStart. Also requires that each token's current NER-type be overwritable,
   * and that each token has not yet been Answer-annotated.
   *
   * @param entry
   * @param document
   * @return on success, the index of the first token in the matching sequence, otherwise -1
   */
  private static int findStartIndex(Entry entry, List<CoreLabel> document, int searchStart, Set<String> myLabels, boolean ignoreCase) {
    List<Pattern> regex = entry.regex;
    int rSize = regex.size();
    // log.info("REGEX FIND MATCH FOR " + regex.toString() + " length: " + rSize);

    for (int start = searchStart, end = document.size() - regex.size(); start <= end; start++) {
      boolean failed = false;
      for (int i = 0; i < rSize; i++) {
        Pattern pattern = regex.get(i);
        String exact = entry.exact.get(i);
        CoreLabel token = document.get(start + i);
        String NERType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        String currentType = token.get(CoreAnnotations.AnswerAnnotation.class);

        // Note that we allow currentType to be overwritable, but not in our set of provided labels
        // The logic here is that if a higher priority regex matches a section, we don't want
        // a lower priority regex to overwrite it
        // see edu.stanford.nlp.pipeline.RegexNERAnnotatorITest::testPriority for an example
        // TODO: perhaps we could let the current type be the same as the entry's type?
        if (
            (currentType != null && !entry.overwritableTypes.contains(currentType)) ||
            (exact != null && ! (ignoreCase ? exact.equalsIgnoreCase(token.word()) : exact.equals(token.word()))) ||
            ! (entry.overwritableTypes.contains(NERType) || myLabels.contains(NERType))  ||
            ! pattern.matcher(token.word()).matches()  // last, as this is likely the expensive operation
            ) {
          failed = true;
          break;
        }
      }
      if (! failed) {
        // log.info("MATCHED REGEX:");
        // for(int i = start; i < start + regex.size(); i ++) log.info(" " + document.get(i).word());
        // log.info();
        return start;
      }
    }
    return -1;
  }


  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }

  // these methods are not implemented for a rule-based sequence classifier

  @Override
  public void train(Collection<List<CoreLabel>> docs,
                    DocumentReaderAndWriter<CoreLabel> readerAndWriter) {}

  @Override
  public void serializeClassifier(String serializePath) {}

  @Override
  public void serializeClassifier(ObjectOutputStream oos) {}

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props)
          throws IOException, ClassCastException, ClassNotFoundException {}

}
