package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.ling.tokensregex.matcher.TrieMap;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;


/**
 * TokensRegexNERAnnotator labels tokens with types based on a simple manual mapping from
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
 * designated overwritable (the third argument).
 *
 * The first column regex may follow one of two formats:
 * 1) A TokensRegex expression (marked by starting with "( " and ending with " )"
 * 2) a sequence of regex, each separated by whitespace (matching "\\s+").
 *    The regex will match if the successive regex match a sequence of tokens in the input.
 *    Spaces can only be used to separate regular expression tokens; within tokens \\s or similar non-space
 *    representations need to be used instead.
 *    Notes: Following Java regex conventions, some characters in the file need to be escaped. Only a single
 *    backslash should be used though, as these are not String literals. The input to RegexNER will have
 *    already been tokenized.  So, for example, with our usual English tokenization, things like genitives
 *    and commas at the end of words will be separated in the input and matched as a separate token.
 *
 * This annotator is similar to {link @RegexNERAnnotator} but uses TokensRegex as the underlying library for matching
 * regular expressions.  This allows for more flexibility in the types of expressions matched as well as utilizing
 * any optimization that is included in the TokensRegex library.
 *
 * Main differences from {@link RegexNERAnnotator}:
 * <ul>
 *   <li>Supports both TokensRegex patterns and patterns over the text of the tokens</li>
 *   <li>When NER annotation can be overwritten based on the original NER labels.  The rules for when the new NER labels are used
 *       are given below:
 *       <br/>If the found expression overlaps with a previous NER phrase, then the NER labels are not replaced.
 *       <br/>  Example: Old NER phrase: The ABC Company, Found Phrase: ABC => Old NER labels are not replaced.
 *       <br/>If the found expression has inconsistent NER tags among the tokens, then the NER labels are replaced. </li>
 *       <br/>  Example: Old NER phrase: The/O ABC/MISC Company/ORG => The/ORG ABC/ORG Company/ORG
 *   <li>How <code>validpospattern</code> is handled for POS tags is specfied by <code>PosMatchType</code></li>
 *   <li>By default, there is no <code>validPosPattern</code></li>
 *   <li>By default, MISC is always replaced</li>
 * </ul>
 *
 * <p>
 *   Configuration:
 * <table>
 *   <tr><th>Field</th><th>Description</th></tr>
 *   <tr><td><code>mapping</code></td><td>Comma separated list of mapping files to use </td>
 *      <td><code>edu/stanford/nlp/models/regexner/type_map_clean</code></td>
 *   </tr>
 *   <tr><td><code>backgroundSymbol</code></td><td><code></code></td>
 *      <td><code>O,MISC</code></td><td>Comma separated list of NER labels to always replace</td></tr>
 *   <tr><td><code>posmatchtype</code></td>
 *     <td>How should <code>validpospattern</code> be used to match the POS of the tokens.
 *         <code>MATCH_ALL_TOKENS</code> - All tokens has to match.<br/>
 *         <code>MATCH_AT_LEAST_ONE_TOKEN</code> - At least one token has to match.<br/>
 *         <code>MATCH_ONE_TOKEN_PHRASE_ONLY</code> - Only has to match for one token phrases.<br/>
 *      </td>
 *      <td><code>MATCH_AT_LEAST_ONE_TOKEN</code></td>
 *   </tr>
 *   <tr><td><code>validpospattern</code></td><td>Regular expression pattern for matching POS tags.</td>
 *      <td><code></code></td>
 *   </tr>
 *   <tr><td><code>noDefaultOverwriteLabels</code></td>
 *      <td>Comma separated list of output types for which default NER labels are not overwritten.
 *          For this types, only if the matched expression has NER type matching the
 *          specified overwriteableType for the regex will the NER type be overwritten.</td>
 *      <td><code></code></td></tr>
 *   <tr><td><code>ignoreCase</code></td><td><code>Boolean</code></td>
 *      <td><code>false</code></td><td>If true, case is ignored</td></tr>
 *   <tr><td><code>verbose</code></td><td><<code>Boolean</code></td>
 *      <td><code>false</code></td><td>If true, turns on extra debugging messages.</td></tr>
 * </table>
 * </p>
 *
 * @author Angel Chang
 */
public class TokensRegexNERAnnotator implements Annotator {
  protected static final Redwood.RedwoodChannels logger = Redwood.channels("TokenRegexNER");

  // TODO: Can remove entries and just have a MultiPatternMatcher probably
  private final boolean ignoreCase;
  private final List<Entry> entries;
  private final Map<TokenSequencePattern,Entry> patternToEntry = new IdentityHashMap<TokenSequencePattern,Entry>();
  private final MultiPatternMatcher<CoreMap>  multiPatternMatcher;

  private final Set<String> myLabels;  // set of labels to always overwrite
  private final Pattern validPosPattern;
  private final boolean verbose;

  // Labels for which we don't use the default overwrite types (mylabels)
  private final Set<String> noDefaultOverwriteLabels;

  enum PosMatchType {
    // all tokens must match the pos pattern
    MATCH_ALL_TOKENS,
    // only one token must match the pos pattern
    MATCH_AT_LEAST_ONE_TOKEN,
    // only single token phrases have to match the pos pattern
    MATCH_ONE_TOKEN_PHRASE_ONLY }
  private final PosMatchType posMatchType;
  public static final PosMatchType DEFAULT_POS_MATCH_TYPE = PosMatchType.MATCH_AT_LEAST_ONE_TOKEN;

  public TokensRegexNERAnnotator(String mapping) {
    this(mapping, false);
  }

  public TokensRegexNERAnnotator(String mapping, boolean ignoreCase) {
    this(mapping, ignoreCase, null);
  }

  public TokensRegexNERAnnotator(String mapping, boolean ignoreCase, String validPosRegex) {
    this("tokenregexner", getProperties("tokenregexner", mapping, ignoreCase, validPosRegex));
  }

  private static Properties getProperties(String name, String mapping, boolean ignoreCase, String validPosRegex) {
    String prefix = (name != null && !name.isEmpty())? name + ".":"";
    Properties props = new Properties();
    props.setProperty(prefix + "mapping", mapping);
    props.setProperty(prefix + "ignorecase", String.valueOf(ignoreCase));
    if (validPosRegex != null) {
      props.setProperty(prefix + "validpospattern", validPosRegex);
    }
    return props;
  }

  public TokensRegexNERAnnotator(String name, Properties properties) {
    String prefix = (name != null && !name.isEmpty())? name + ".":"";
    String backgroundSymbol = properties.getProperty(prefix + "backgroundSymbol",
            SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL + ",MISC");
    String[] backgroundSymbols = backgroundSymbol.split("\\s*,\\s*");
    String mappingFiles = properties.getProperty(prefix + "mapping", DefaultPaths.DEFAULT_REGEXNER_RULES);
    String[] mappings = mappingFiles.split("\\s*[,;]\\s*");
    String validPosRegex = properties.getProperty(prefix + "validpospattern");
    this.posMatchType = PosMatchType.valueOf(properties.getProperty(prefix + "posmatchtype",
            DEFAULT_POS_MATCH_TYPE.name()));
    boolean overwriteMyLabels = true;

    String noDefaultOverwriteLabelsProp = properties.getProperty(prefix + "noDefaultOverwriteLabels");
    this.noDefaultOverwriteLabels = (noDefaultOverwriteLabelsProp != null)?
            CollectionUtils.asSet(noDefaultOverwriteLabelsProp.split("\\s*,\\s*")):new HashSet<String>();
    this.ignoreCase = PropertiesUtils.getBool(properties, prefix + "ignorecase", false);
    this.verbose = PropertiesUtils.getBool(properties, prefix + "verbose", false);

    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    entries = readEntries(name, noDefaultOverwriteLabels, ignoreCase, verbose, mappings);
    multiPatternMatcher = createPatternMatcher();
    myLabels = Generics.newHashSet();
    // Can always override background or none.
    for (String s:backgroundSymbols)
      myLabels.add(s);
    myLabels.add(null);
    if (overwriteMyLabels) {
      for (Entry entry: entries) myLabels.add(entry.type);
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (verbose) {
      System.err.print("Adding TokensRegexNER annotations ... ");
    }

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences != null) {
      for (CoreMap sentence : sentences) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        annotateMatched(tokens);
      }
    } else {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      if (tokens != null){
        annotateMatched(tokens);
      } else {
        throw new RuntimeException("Unable to find sentences or tokens in " + annotation);
      }
    }

    if (verbose)
      System.err.println("done.");
  }

  private MultiPatternMatcher<CoreMap> createPatternMatcher() {
    // Convert to tokensregex pattern
    int patternFlags = ignoreCase? Pattern.CASE_INSENSITIVE:0;
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultStringPatternFlags(patternFlags);
    NodePattern<String> posTagPattern = (validPosPattern != null && PosMatchType.MATCH_ALL_TOKENS.equals(posMatchType))?
            new CoreMapNodePattern.StringAnnotationRegexPattern(validPosPattern):null;
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>(entries.size());
    for (Entry entry:entries) {
      TokenSequencePattern pattern;
      if (entry.tokensRegex != null) {
        // TODO: posTagPatterns...
        pattern = TokenSequencePattern.compile(env, entry.tokensRegex);
      } else {
        List<SequencePattern.PatternExpr> nodePatterns = new ArrayList<SequencePattern.PatternExpr>();
        for (String p:entry.regex) {
          CoreMapNodePattern c = CoreMapNodePattern.valueOf(p, patternFlags);
          if (posTagPattern != null) {
            c.add(CoreAnnotations.PartOfSpeechAnnotation.class, posTagPattern);
          }
          nodePatterns.add(new SequencePattern.NodePatternExpr(c));
        }
        pattern = TokenSequencePattern.compile(
                new SequencePattern.SequencePatternExpr(nodePatterns));
      }
      pattern.setPriority(entry.priority);
      patterns.add(pattern);
      patternToEntry.put(pattern, entry);
    }
    return TokenSequencePattern.getMultiPatternMatcher(patterns);
  }

  private void annotateMatched(List<CoreLabel> tokens) {
    List<SequenceMatchResult<CoreMap>> matched = multiPatternMatcher.findNonOverlapping(tokens);
    for (SequenceMatchResult<CoreMap> m:matched) {
      Entry entry = patternToEntry.get(m.pattern());

      // Check if we will overwrite the existing annotation with this annotation
      int g = entry.annotateGroup;
      int start = m.start(g);
      int end = m.end(g);

      boolean overwriteOriginalNer = checkPosTags(entry, tokens, start, end);
      if (overwriteOriginalNer) {
        overwriteOriginalNer = checkOrigNerTags(entry, tokens, start, end);
      }
      if (overwriteOriginalNer) {
        for (int i = start; i < end; i++) {
          tokens.get(i).set(CoreAnnotations.NamedEntityTagAnnotation.class, entry.type);
        }
      } else {
        if (verbose) {
          System.err.println("Not annotating  '" + m.group(g) + "': " +
                  StringUtils.joinFields(m.groupNodes(g), CoreAnnotations.NamedEntityTagAnnotation.class)
                  + " with " + entry.type + ", sentence is '" + StringUtils.joinWords(tokens, " ") + "'");
        }
      }
    }
  }

  // TODO: roll check into tokens regex pattern?
  // That allows for better matching because unmatched sequences will be eliminated at match time
  private boolean checkPosTags(Entry entry, List<CoreLabel> tokens, int start, int end) {
    if (validPosPattern != null) {
      // Need to check POS tag too...
      switch (posMatchType) {
        case MATCH_ONE_TOKEN_PHRASE_ONLY:
          if (tokens.size() > 1) return true;
          // fall through
        case MATCH_AT_LEAST_ONE_TOKEN:
          for (int i = start; i < end; i++) {
            CoreLabel token = tokens.get(i);
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            if (pos != null && validPosPattern.matcher(pos).matches()) {
              return true;
            }
          }
          return false;
        case MATCH_ALL_TOKENS:
          // Checked else where
          return true;
        default:
          // Don't know this match type....
          return true;
      }
    }
    return true;
  }

  private boolean checkOrigNerTags(Entry entry, List<CoreLabel> tokens, int start, int end) {
    int prevNerEndIndex = start-1;
    int nextNerStartIndex = end;

    // Check if we found a pattern that overlaps with existing ner labels
    // tag1 tag1 x   x  tag2 tag2
    //      tag tag tag tag
    // Don't overwrite the old ner label if we overlap like this
    String startNer = tokens.get(start).ner();
    String endNer = tokens.get(end-1).ner();
    if (startNer != null && !myLabels.contains(startNer)) {
      while (prevNerEndIndex >= 0) {
        // go backwards to find different entity type
        String ner = tokens.get(prevNerEndIndex).ner();
        if (ner == null || !ner.equals(startNer)) {
          break;
        }
        prevNerEndIndex--;
      }
    }
    if (endNer != null && !myLabels.contains(endNer)) {
      while (nextNerStartIndex < tokens.size()) {
        // go backwards to find different entity type
        String ner = tokens.get(nextNerStartIndex).ner();
        if (ner == null || !ner.equals(endNer)) {
          break;
        }
        nextNerStartIndex++;
      }
    }
    boolean overwriteOriginalNer = false;
    if (prevNerEndIndex != (start-1) || nextNerStartIndex != end) {
      // Cutting across already recognized NEs don't disturb
    } else if (startNer == null) {
      // No old ner, okay to replace
      overwriteOriginalNer = true;
    } else {
      // Check if we have one consistent NER tag
      // if not, overwrite
      // if consistent, overwrite only if in our set of ner tags that we overwrite
      for (int i = start+1; i < end; i++) {
        if (!startNer.equals(tokens.get(i).ner())) {
          overwriteOriginalNer = true;
          break;
        }
      }
      if (!overwriteOriginalNer) {
        // check if old ner type was one that was specified as explicitly overwritable by this entry
        if (entry.overwritableTypes.contains(startNer)) {
          overwriteOriginalNer = true;
        } else {
          // if this ner type doesn't belong to the labels for which we don't overwrite the default labels (noDefaultOverwriteLabels)
          // we check mylabels to see if we can overwrite this entry
          if (/*entry.overwritableTypes.isEmpty() || */!noDefaultOverwriteLabels.contains(entry.type)) {
            overwriteOriginalNer = myLabels.contains(startNer);
          }
        }

      }
    }
    return overwriteOriginalNer;
  }

  private static class Entry {
    public String tokensRegex;
    public String[] regex; // the regex, tokenized by splitting on white space
    public String type; // the associated type
    public Set<String> overwritableTypes; // what types can be overwritten by this entry
    public double priority;
    public int annotateGroup;

    public Entry(String tokensRegex, String[] regex, String type, Set<String> overwritableTypes, double priority, int annotateGroup) {
      this.tokensRegex = tokensRegex;
      this.regex = regex;
      this.type = type.intern();
      this.overwritableTypes = overwritableTypes;
      this.priority = priority;
      this.annotateGroup = annotateGroup;
    }

    public String toString() {
      return "Entry{" + ((tokensRegex != null)? tokensRegex:StringUtils.join(regex)) + ' ' + type + ' ' + overwritableTypes + ' ' + priority + '}';
    }
  }

  /**
   *  Creates a combined list of Entries using the provided mapping files.
   *
   *  @param mappings List of mapping files
   *  @return list of Entries
   */
  private static List<Entry> readEntries(String annotatorName,
                                         Set<String> noDefaultOverwriteLabels,
                                         boolean ignoreCase, boolean verbose,
                                         String... mappings) {
    // Unlike RegexNERClassifier, we don't bother sorting the entries
    // We leave it to TokensRegex NER to sort out the priorities and matches
    //   (typically after all the matches has been made since for some TokenRegex expression,
    //       we don't know how many tokens are matched until after the matching is done)
    List<Entry> entries = new ArrayList<Entry>();
    TrieMap<String,Entry> seenRegexes = new TrieMap<String,Entry>();
    for (String mapping:mappings) {
      BufferedReader rd = null;
      try {
        rd = IOUtils.readerFromString(mapping);
        readEntries(annotatorName, entries, seenRegexes, mapping, rd, noDefaultOverwriteLabels, ignoreCase, verbose);
      } catch (IOException e) {
        throw new RuntimeIOException("Couldn't read TokensRegexNER from " + mapping, e);
      } finally {
        IOUtils.closeIgnoringExceptions(rd);
      }
    }

    if (mappings.length != 1) {
      logger.log("TokensRegexNERAnnotator " + annotatorName +
            ": Read " + entries.size() + " unique entries from " + mappings.length + " files");
    }
    return entries;
  }

  /**
   *  Reads a list of Entries from a mapping file and update the given entries.
   *  Line numbers start from 1.
   *
   *  @return the updated list of Entries
   */
  private static List<Entry> readEntries(String annotatorName,
                                         List<Entry> entries,
                                         TrieMap<String,Entry> seenRegexes,
                                         String mappingFilename,
                                         BufferedReader mapping,
                                         Set<String> noDefaultOverwriteLabels,
                                         boolean ignoreCase, boolean verbose) throws IOException {
    int origEntriesSize = entries.size();
    int isTokensRegex = 0;
    int lineCount = 0;
    for (String line; (line = mapping.readLine()) != null; ) {
      lineCount ++;
      String[] split = line.split("\t");
      if (split.length < 2 || split.length > 4)
        throw new IllegalArgumentException("Provided mapping file is in wrong format");

      String regex = split[0].trim();
      String tokensRegex = null;
      String[] regexes = null;
      if (regex.startsWith("( ") && regex.endsWith(" )")) {
        // Tokens regex (remove start and end parenthesis)
        tokensRegex = regex.substring(1,regex.length()-1).trim();
      } else {
        regexes = regex.split("\\s+");
      }
      String[] key = (regexes != null)? regexes: new String[] { tokensRegex };
      if (ignoreCase) {
        String[] norm = new String[key.length];
        for (int i = 0; i < key.length; i++) {
          norm[i] = key[i].toLowerCase();
        }
        key = norm;
      }
      String type = split[1].trim();

      Set<String> overwritableTypes = Generics.newHashSet();
      double priority = 0.0;

      if (split.length >= 3) {
        overwritableTypes.addAll(Arrays.asList(split[2].trim().split(",")));
      }
      if (split.length == 4) {
        try {
          priority = Double.parseDouble(split[3].trim());
        } catch(NumberFormatException e) {
          throw new IllegalArgumentException("ERROR: Invalid line " + lineCount
                  + " in regexner file " + mappingFilename + ": \"" + line + "\"!", e);
        }
      }

      // TODO: Get annotate group from input....
      int annotateGroup = 0;
      Entry entry = new Entry(tokensRegex, regexes, type, overwritableTypes, priority, annotateGroup);
      if (seenRegexes.containsKey(key)) {
        Entry oldEntry = seenRegexes.get(key);
        if (priority > oldEntry.priority) {
          logger.warn("TokensRegexNERAnnotator " + annotatorName +
                  ": Replace duplicate entry (higher priority): old=" + oldEntry + ", new=" + entry);
        } else {
          if (!oldEntry.type.equals(type)) {
            if (verbose) {
              logger.warn("TokensRegexNERAnnotator " + annotatorName +
                      ": Ignoring duplicate entry: " + split[0] + ", old type = " + oldEntry.type + ", new type = " + type);
            }
          }
          continue;
        }
      }

      // Print some warning about the type
      int commaPos = entry.type.indexOf(',');
      if (commaPos > 0) {
        // Strip the "," and just take first type
        String newType = entry.type.substring(0, commaPos).trim();
        logger.warn("TokensRegexNERAnnotator " + annotatorName +
                ": Entry has multiple type " + entry + ", taking type to be " + newType);
        entry.type = newType;
      }

      // Print some warning if label belongs to noDefaultOverwriteLabels but there is no overwritable types
      if (entry.overwritableTypes.isEmpty() && noDefaultOverwriteLabels.contains(entry.type)) {
        logger.warn("TokensRegexNERAnnotator " + annotatorName +
                ": Entry doesn't have overwriteable types " + entry + ", but entry type is in noDefaultOverwriteLabels");
      }

      entries.add(entry);
      seenRegexes.put(key, entry);
      if (entry.tokensRegex != null) isTokensRegex++;
    }

    logger.log("TokensRegexNERAnnotator " + annotatorName +
            ": Read " + (entries.size() - origEntriesSize) + " unique entries out of " + lineCount + " from " + mappingFilename
       + ", " + isTokensRegex + " TokensRegex patterns.");
    return entries;
  }


  @Override
  public Set<Requirement> requires() {
    return StanfordCoreNLP.TOKENIZE_AND_SSPLIT;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    // TODO: we might want to allow for different RegexNER annotators
    // to satisfy different requirements
    return Collections.emptySet();
  }
}
