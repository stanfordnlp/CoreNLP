package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
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
 * <p>
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
 * (higher priority is favored) is used to choose between the possible types.
 * When the priority is the same, then longer matches are favored.
 *
 * This annotator is designed to be used as part of a full
 * NER system to label entities that don't fall into the usual NER categories. It only records the label
 * if the token has not already been NER-annotated, or it has been annotated but the NER-type has been
 * designated overwritable (the third argument).
 *
 * It is also possible to use this annotator to annotate fields other than the
 * {@code NamedEntityTagAnnotation} field by
 * and providing the header
 * </p>
 *
 * <p>
 * The first column regex may follow one of two formats:
 * <ol>
 * <li> A TokensRegex expression (marked by starting with "( " and ending with " )".
 *      See {@link TokenSequencePattern} for TokensRegex syntax.
 *    <br/><em>Example</em>: {@code ( /University/ /of/ [ {ner:LOCATION} ] )    SCHOOL}
 * </li>
 * <li> a sequence of regex, each separated by whitespace (matching "\s+").
 *    <br/><em>Example</em>: {@code Stanford    SCHOOL}
 *    <br/>
 *    The regex will match if the successive regex match a sequence of tokens in the input.
 *    Spaces can only be used to separate regular expression tokens; within tokens \s or similar non-space
 *    representations need to be used instead.
 *    <br/>
 *    Notes: Following Java regex conventions, some characters in the file need to be escaped. Only a single
 *    backslash should be used though, as these are not String literals. The input to RegexNER will have
 *    already been tokenized.  So, for example, with our usual English tokenization, things like genitives
 *    and commas at the end of words will be separated in the input and matched as a separate token.</li>
 * </ol>
 * </p>
 *
 * <p>
 * This annotator is similar to {@link RegexNERAnnotator} but uses TokensRegex as the underlying library for matching
 * regular expressions.  This allows for more flexibility in the types of expressions matched as well as utilizing
 * any optimization that is included in the TokensRegex library.
 * </p>
 * <p>
 * Main differences from {@link RegexNERAnnotator}:
 * <ul>
 *   <li>Supports annotation of fields other than the {@code NamedEntityTagAnnotation} field</li>
 *   <li>Supports both TokensRegex patterns and patterns over the text of the tokens</li>
 *   <li>When NER annotation can be overwritten based on the original NER labels.  The rules for when the new NER labels are used
 *       are given below:
 *       <br/>If the found expression overlaps with a previous NER phrase, then the NER labels are not replaced.
 *       <br/>  <em>Example</em>: Old NER phrase: {@code The ABC Company}, Found Phrase: {@code ABC => } Old NER labels are not replaced.
 *       <br/>If the found expression has inconsistent NER tags among the tokens, then the NER labels are replaced.
 *       <br/>  <em>Example</em>: Old NER phrase: {@code The/O ABC/MISC Company/ORG => The/ORG ABC/ORG Company/ORG}
 *   </li>
 *   <li>How {@code validpospattern} is handled for POS tags is specified by {@code PosMatchType}</li>
 *   <li>By default, there is no {@code validPosPattern}</li>
 *   <li>By default, both O and MISC is always replaced</li>
 * </ul>
 * </p>
 * <p>
 *   Configuration:
 * <table>
 *   <tr><th>Field</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code mapping}</td><td>Comma separated list of mapping files to use </td>
 *      <td>{@code edu/stanford/nlp/models/regexner/type_map_clean}</td>
 *   </tr>
 *   <tr><td>{@code mapping.header}</td>
 *       <td>Comma separated list of header fields (or {@code true} if header is specified in the file)</td>
 *       <td>pattern,ner,overwrite,priority,group</td></tr>
 *   <tr><td>{@code mapping.field.<fieldname>}</td>
 *       <td>Class mapping for annotation fields other than ner</td></tr>
 *   <tr><td>{@code commonWords}</td>
 *       <td>Comma separated list of files for common words to not annotate (in case your mapping isn't very clean)</td></tr>
 *   <tr><td>{@code backgroundSymbol}</td><td>Comma separated list of NER labels to always replace</td>
 *      <td>{@code O,MISC}</td></tr>
 *   <tr><td>{@code posmatchtype}</td>
 *     <td>How should {@code validpospattern} be used to match the POS of the tokens.
 *         {@code MATCH_ALL_TOKENS} - All tokens has to match.<br/>
 *         {@code MATCH_AT_LEAST_ONE_TOKEN} - At least one token has to match.<br/>
 *         {@code MATCH_ONE_TOKEN_PHRASE_ONLY} - Only has to match for one token phrases.<br/>
 *      </td>
 *      <td>{@code MATCH_AT_LEAST_ONE_TOKEN}</td>
 *   </tr>
 *   <tr><td>{@code validpospattern}</td><td>Regular expression pattern for matching POS tags.</td>
 *      <td>{@code}</td>
 *   </tr>
 *   <tr><td>{@code noDefaultOverwriteLabels}</td>
 *      <td>Comma separated list of output types for which default NER labels are not overwritten.
 *          For these types, only if the matched expression has NER type matching the
 *          specified overwriteableType for the regex will the NER type be overwritten.</td>
 *      <td>{@code}</td></tr>
 *   <tr><td>{@code ignoreCase}</td><td>If true, case is ignored</td></td>
 *      <td>{@code false}</td></tr>
 *   <tr><td>{@code verbose}</td><td>If true, turns on extra debugging messages.</td>
 *      <td>{@code false}</td></tr>
 * </table>
 * </p>
 *
 * @author Angel Chang
 */
public class TokensRegexNERAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TokensRegexNERAnnotator.class);
  protected static final Redwood.RedwoodChannels logger = Redwood.channels("TokenRegexNER");
  protected static final String PATTERN_FIELD = "pattern";
  protected static final String OVERWRITE_FIELD = "overwrite";
  protected static final String PRIORITY_FIELD = "priority";
  protected static final String WEIGHT_FIELD = "weight";
  protected static final String GROUP_FIELD = "group";

  protected static final Set<String> predefinedHeaderFields = CollectionUtils.asSet(PATTERN_FIELD, OVERWRITE_FIELD, PRIORITY_FIELD, WEIGHT_FIELD, GROUP_FIELD);
  protected static final String defaultHeader = "pattern,ner,overwrite,priority,group";

  private final boolean ignoreCase;
  private final List<Boolean> ignoreCaseList;
  private final Set<String> commonWords;
  private final List<Entry> entries;
  private final Map<SequencePattern<CoreMap>,Entry> patternToEntry;
  private final MultiPatternMatcher<CoreMap>  multiPatternMatcher;
  private final List<Class> annotationFields; // list of fields to annotate (default to just NamedEntityTag)

  private final Set<String> myLabels;  // set of labels to always overwrite
  private final Pattern validPosPattern;
  private final List<Pattern> validPosPatternList;
  private final boolean verbose;

  private final Map<Entry, Integer> entryToMappingFileNumber;

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
  public static final String DEFAULT_BACKGROUND_SYMBOL = SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL + ",MISC";

  public static PropertiesUtils.Property[] SUPPORTED_PROPERTIES = new PropertiesUtils.Property[]{
          new PropertiesUtils.Property("mapping", DefaultPaths.DEFAULT_REGEXNER_RULES, "List of mapping files to use, separated by commas or semi-colons."),
          new PropertiesUtils.Property("mapping.header", defaultHeader, "Comma separated list specifying order of fields in the mapping file"),
          new PropertiesUtils.Property("mapping.field.<fieldname>", "", "Class mapping for annotation fields other than ner"),
          new PropertiesUtils.Property("commonWords", "", "Comma separated list of files for common words to not annotate (in case your mapping isn't very clean)"),
          new PropertiesUtils.Property("ignorecase", "false", "Whether to ignore case or not when matching patterns."),
          new PropertiesUtils.Property("validpospattern", "", "Regular expression pattern for matching POS tags."),
          new PropertiesUtils.Property("posmatchtype", DEFAULT_POS_MATCH_TYPE.name(), "How should 'validpospattern' be used to match the POS of the tokens."),
          new PropertiesUtils.Property("noDefaultOverwriteLabels", "", "Comma separated list of output types for which default NER labels are not overwritten.\n" +
                  " For these types, only if the matched expression has NER type matching the\n" +
                  " specified overwriteableType for the regex will the NER type be overwritten."),
          new PropertiesUtils.Property("backgroundSymbol", DEFAULT_BACKGROUND_SYMBOL, "Comma separated list of NER labels to always replace."),
          new PropertiesUtils.Property("verbose", "false", ""),
  };

  /** Construct a new TokensRegexAnnotator.
   *
   *  @param mapping A comma-separated list of files, URLs, or classpath resources to load mappings from
   */
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

  private static final Pattern FILE_DELIMITERS_PATTERN = Pattern.compile("\\s*[,;]\\s*");
  private static final Pattern COMMA_DELIMITERS_PATTERN = Pattern.compile("\\s*,\\s*");
  private static final Pattern SEMICOLON_DELIMITERS_PATTERN = Pattern.compile("\\s*;\\s*");
  private static final Pattern EQUALS_DELIMITERS_PATTERN = Pattern.compile("\\s*=\\s*");

  public TokensRegexNERAnnotator(String name, Properties properties) {
    String prefix = (name != null && !name.isEmpty())? name + ".":"";
    String backgroundSymbol = properties.getProperty(prefix + "backgroundSymbol", DEFAULT_BACKGROUND_SYMBOL);
    String[] backgroundSymbols = COMMA_DELIMITERS_PATTERN.split(backgroundSymbol);
    String mappingFiles = properties.getProperty(prefix + "mapping",
            DefaultPaths.DEFAULT_KBP_TOKENSREGEX_NER_SETTINGS);
    String[] mappings = processListMappingFiles(mappingFiles);
    String validPosRegex = properties.getProperty(prefix + "validpospattern");
    this.posMatchType = PosMatchType.valueOf(properties.getProperty(prefix + "posmatchtype",
            DEFAULT_POS_MATCH_TYPE.name()));
    String commonWordsFile = properties.getProperty(prefix + "commonWords");
    commonWords = new HashSet<>();
    if (commonWordsFile != null) {
      try {
        BufferedReader reader = IOUtils.getBufferedFileReader(commonWordsFile);
        String line;
        while ((line = reader.readLine()) != null) {
          commonWords.add(line);
        }
        reader.close();
      } catch (IOException ex) {
        throw new RuntimeException("TokensRegexNERAnnotator " + name
            + ": Error opening the common words file: " + commonWordsFile, ex);
      }
    }

    String headerProp = properties.getProperty(prefix + "mapping.header", defaultHeader);
    boolean readHeaderFromFile = headerProp.equalsIgnoreCase("true");
    String[] annotationFieldnames = null;
    String[] headerFields = null;
    if (readHeaderFromFile) {
      // Get header as first line from all files...
      // TODO: support reading header from file
      throw new UnsupportedOperationException("Reading header from file not yet supported!!!");
    } else {
      headerFields = COMMA_DELIMITERS_PATTERN.split(headerProp);
      // Take header fields and remove known headers to get annotation field names
      List<String> fieldNames = new ArrayList<>();
      List<Class> fieldClasses = new ArrayList<>();
      for (String field : headerFields) {
        if (!predefinedHeaderFields.contains(field)) {
          Class fieldClass = EnvLookup.lookupAnnotationKeyWithClassname(null, field);
          if (fieldClass == null) {
            // check our properties
            String classname = properties.getProperty(prefix + "mapping.field." + field);
            fieldClass = EnvLookup.lookupAnnotationKeyWithClassname(null, classname);
          }
          if (fieldClass != null) {
            fieldNames.add(field);
            fieldClasses.add(fieldClass);
          } else {
            logger.warn("TokensRegexNERAnnotator " + name + ": Unknown field: " + field + " cannot find suitable annotation class");
          }
        }
      }
      annotationFieldnames = new String[fieldNames.size()];
      fieldNames.toArray(annotationFieldnames);
      annotationFields = fieldClasses;
    }

    String noDefaultOverwriteLabelsProp = properties.getProperty(prefix + "noDefaultOverwriteLabels", "CITY");
    this.noDefaultOverwriteLabels = Collections.unmodifiableSet(CollectionUtils.asSet(noDefaultOverwriteLabelsProp.split("\\s*,\\s*")));
    this.ignoreCase = PropertiesUtils.getBool(properties, prefix + "ignorecase", false);
    this.verbose = PropertiesUtils.getBool(properties, prefix + "verbose", false);

    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    validPosPatternList = new ArrayList<>();
    ignoreCaseList = new ArrayList<>();
    entryToMappingFileNumber = new HashMap<>();
    processPerFileOptions(name, mappings, ignoreCaseList, validPosPatternList, ignoreCase, validPosPattern);
    entries = Collections.unmodifiableList(readEntries(name, noDefaultOverwriteLabels, ignoreCaseList, entryToMappingFileNumber, verbose, headerFields, annotationFieldnames, mappings));
    IdentityHashMap<SequencePattern<CoreMap>, Entry> patternToEntry = new IdentityHashMap<>();
    multiPatternMatcher = createPatternMatcher(patternToEntry);
    this.patternToEntry = Collections.unmodifiableMap(patternToEntry);
    Set<String> myLabels = Generics.newHashSet();
    // Can always override background or none.
    Collections.addAll(myLabels, backgroundSymbols);
    myLabels.add(null);
    // Always overwrite labels
    for (Entry entry: entries) {
      for (String type:entry.types) {
        myLabels.add(type);
      }
    }
    this.myLabels = Collections.unmodifiableSet(myLabels);
  }

  @Override
  public void annotate(Annotation annotation) {
    if (verbose) {
      log.info("Adding TokensRegexNER annotations ... ");
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
      log.info("done.");
  }

  private MultiPatternMatcher<CoreMap> createPatternMatcher(Map<SequencePattern<CoreMap>, Entry> patternToEntry) {
    // Convert to tokensregex pattern

    List<TokenSequencePattern> patterns = new ArrayList<>(entries.size());
    for (Entry entry:entries) {
      TokenSequencePattern pattern;

      Boolean ignoreCaseEntry = ignoreCaseList.get(entryToMappingFileNumber.get(entry));
      int patternFlags = ignoreCaseEntry? Pattern.CASE_INSENSITIVE:0;
      int stringMatchFlags = ignoreCaseEntry? NodePattern.CASE_INSENSITIVE:0;
      Env env = TokenSequencePattern.getNewEnv();
      env.setDefaultStringPatternFlags(patternFlags);
      env.setDefaultStringMatchFlags(stringMatchFlags);

      NodePattern<String> posTagPattern = (validPosPatternList.get(entryToMappingFileNumber.get(entry)) != null && PosMatchType.MATCH_ALL_TOKENS.equals(posMatchType))?
              new CoreMapNodePattern.StringAnnotationRegexPattern(validPosPatternList.get(entryToMappingFileNumber.get(entry))):null;
      if (entry.tokensRegex != null) {
        // TODO: posTagPatterns...
        pattern = TokenSequencePattern.compile(env, entry.tokensRegex);
      } else {
        List<SequencePattern.PatternExpr> nodePatterns = new ArrayList<>();
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
      if (entry.annotateGroup < 0 || entry.annotateGroup > pattern.getTotalGroups()) {
        throw new RuntimeException("Invalid match group for entry " + entry);
      }
      pattern.setPriority(entry.priority);
      pattern.setWeight(entry.weight);
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

      String str = m.group(g);
      if (commonWords.contains(str)) {
        if (verbose) {
          log.info("Not annotating (common word) '" + str + "': " +
              StringUtils.joinFields(m.groupNodes(g), CoreAnnotations.NamedEntityTagAnnotation.class)
              + " with " + entry.getTypeDescription() + ", sentence is '" + StringUtils.joinWords(tokens, " ") + "'");
        }
        continue;
      }

      boolean overwriteOriginalNer = checkPosTags(tokens, start, end);
      if (overwriteOriginalNer) {
        overwriteOriginalNer = checkOrigNerTags(entry, tokens, start, end);
      }
      if (overwriteOriginalNer) {
        for (int i = start; i < end; i++) {
          CoreLabel token = tokens.get(i);
          for (int j = 0; j < annotationFields.size(); j++) {
            token.set(annotationFields.get(j), entry.types[j]);
          }
         // tokens.get(i).set(CoreAnnotations.NamedEntityTagAnnotation.class, entry.type);
        }
      } else {
        if (verbose) {
          log.info("Not annotating  '" + m.group(g) + "': " +
                  StringUtils.joinFields(m.groupNodes(g), CoreAnnotations.NamedEntityTagAnnotation.class)
                  + " with " + entry.getTypeDescription() + ", sentence is '" + StringUtils.joinWords(tokens, " ") + "'");
        }
      }
    }
  }

  // TODO: roll check into tokens regex pattern?
  // That allows for better matching because unmatched sequences will be eliminated at match time
  private boolean checkPosTags(List<CoreLabel> tokens, int start, int end) {
    if (validPosPattern != null || atLeastOneValidPosPattern(validPosPatternList)) {
      // Need to check POS tag too...
      switch (posMatchType) {
        case MATCH_ONE_TOKEN_PHRASE_ONLY:
          if (tokens.size() > 1) return true;
          // fall through
        case MATCH_AT_LEAST_ONE_TOKEN:
          for (int i = start; i < end; i++) {
            CoreLabel token = tokens.get(i);
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            if (pos != null && validPosPattern != null && validPosPattern.matcher(pos).matches()) {
              return true;
            } else if (pos != null) {
              for (Pattern pattern : validPosPatternList) {
                if (pattern != null && pattern.matcher(pos).matches()) {
                  return true;
                }
              }
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

  private static boolean isLocationOrGpe(CoreLabel token) {
    return "LOCATION".equals(token.ner()) || "GPE".equals(token.ner());
  }

  private boolean checkOrigNerTags(Entry entry, List<CoreLabel> tokens, int start, int end) {
    // cdm Aug 2016: Add in a special hack for Chinese KBP 2016 -- always allow a sequence of GPE or LOCATION to overwrite
    boolean specialCasePass = true;
    for (int i = start; i < end; i++) {
      if ( ! isLocationOrGpe(tokens.get(i))) {
        specialCasePass = false;
        break;
      }
    }
    if (specialCasePass) {
      return true;
    }
    // end special Chinese KBP 2016 code

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
    //noinspection StatementWithEmptyBody
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
          if (/*entry.overwritableTypes.isEmpty() || */!hasNoOverwritableType(noDefaultOverwriteLabels, entry.types)) {
            overwriteOriginalNer = myLabels.contains(startNer);
          }
        }

      }
    }
    return overwriteOriginalNer;
  }

  private static class Entry {
    public final String tokensRegex;
    public final String[] regex; // the regex, tokenized by splitting on white space
    public final String[] types; // the associated types
    public final Set<String> overwritableTypes; // what types can be overwritten by this entry
    public final double priority;
    public final double weight;
    public final int annotateGroup;

    public Entry(String tokensRegex, String[] regex, String[] types, Set<String> overwritableTypes, double priority, double weight, int annotateGroup) {
      this.tokensRegex = tokensRegex;
      this.regex = regex;
      this.types = new String[types.length];
      for (int i = 0; i < types.length; i++) {
        // TODO: for some types, it doesn't make sense to be interning...
        this.types[i] = types[i].intern();
      }
      this.overwritableTypes = overwritableTypes;
      this.priority = priority;
      this.weight = weight;
      this.annotateGroup = annotateGroup;
    }

    public String getTypeDescription() {
      return "[" + StringUtils.join(types, ",") + "]";
    }

    public String toString() {
      return "Entry{" + ((tokensRegex != null) ? tokensRegex: StringUtils.join(regex)) + ' '
          + StringUtils.join(types) + ' ' + overwritableTypes + ' ' + priority + '}';
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
                                         List<Boolean> ignoreCaseList, Map<Entry, Integer> entryToMappingFileNumber, boolean verbose,
                                         String[] headerFields,
                                         String[] annotationFieldnames,
                                         String... mappings) {
    // Unlike RegexNERClassifier, we don't bother sorting the entries
    // We leave it to TokensRegex NER to sort out the priorities and matches
    //   (typically after all the matches has been made since for some TokenRegex expression,
    //       we don't know how many tokens are matched until after the matching is done)
    List<Entry> entries = new ArrayList<>();
    TrieMap<String,Entry> seenRegexes = new TrieMap<>();
    //Arrays.sort(mappings);
    for (int mappingFileIndex = 0; mappingFileIndex < mappings.length; mappingFileIndex++) {
      String mapping = mappings[mappingFileIndex];
      BufferedReader rd = null;
      try {
        rd = IOUtils.readerFromString(mapping);
        readEntries(annotatorName, headerFields, annotationFieldnames, entries, seenRegexes, mapping, rd, noDefaultOverwriteLabels, ignoreCaseList.get(mappingFileIndex), mappingFileIndex, entryToMappingFileNumber, verbose);
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

  private static Map<String,Integer> getHeaderIndexMap(String[] headerFields) {
    Map<String,Integer> map = new HashMap<>();
    for (int i = 0; i < headerFields.length; i++) {
      String field = headerFields[i];
      if (map.containsKey(field)) {
        throw new IllegalArgumentException("Duplicate header field: " + field);
      }
      map.put(field,i);
    }
    return map;
  }


  private static int getIndex(Map<String,Integer> map, String name) {
    Integer index = map.get(name);
    if (index == null) return -1;
    else return index;
  }
  /**
   *  Reads a list of Entries from a mapping file and update the given entries.
   *  Line numbers start from 1.
   *
   *  @return the updated list of Entries
   */
  private static List<Entry> readEntries(String annotatorName,
                                         String[] headerFields,
                                         String[] annotationFieldnames,
                                         List<Entry> entries,
                                         TrieMap<String,Entry> seenRegexes,
                                         String mappingFilename,
                                         BufferedReader mapping,
                                         Set<String> noDefaultOverwriteLabels,
                                         boolean ignoreCase, Integer mappingFileIndex,
                                         Map<Entry, Integer> entryToMappingFileNumber, boolean verbose) throws IOException {
    int origEntriesSize = entries.size();
    int isTokensRegex = 0;
    int lineCount = 0;
    Map<String,Integer> headerIndexMap = getHeaderIndexMap(headerFields);
    int iPattern = getIndex(headerIndexMap, PATTERN_FIELD);
    if (iPattern < 0) {
      throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
        + " ERROR: Header does not contain 'pattern': " + StringUtils.join(headerFields));
    }
    int iOverwrite = getIndex(headerIndexMap, OVERWRITE_FIELD);
    int iPriority = getIndex(headerIndexMap, PRIORITY_FIELD);
    int iWeight = getIndex(headerIndexMap, WEIGHT_FIELD);
    int iGroup = getIndex(headerIndexMap, GROUP_FIELD);
    int[] annotationCols = new int[annotationFieldnames.length];
    int iLastAnnotationField = -1;
    for (int i = 0; i < annotationFieldnames.length; i++) {
      annotationCols[i] = getIndex(headerIndexMap, annotationFieldnames[i]);
      if (annotationCols[i] < 0) {
        throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
            + " ERROR: Header does not contain annotation field '" + annotationFieldnames[i] + "': " + StringUtils.join(headerFields));
      }
      if (annotationCols[i] > iLastAnnotationField) {
        iLastAnnotationField = annotationCols[i];
      }
    }

    int minFields = Math.min(iPattern, iLastAnnotationField);  // Take minimum of "pattern" and last annotation field
    int maxFields = headerFields.length;  // Take maximum number of headerFields
    for (String line; (line = mapping.readLine()) != null; ) {
      lineCount ++;
      String[] split = line.split("\t");
      if (split.length < minFields || split.length > maxFields) {
        throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
            + " ERROR: Provided mapping file is in wrong format. Line " + lineCount + " is bad: " + line);
      }
      String regex = split[iPattern].trim();
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
      String[] types = new String[annotationCols.length];
      for (int i=0; i < annotationCols.length; i++) {
        types[i] = split[annotationCols[i]].trim();
      }

      Set<String> overwritableTypes = Generics.newHashSet();
      double priority = 0.0;

      if (iOverwrite >= 0 && split.length > iOverwrite) {
        overwritableTypes.addAll(Arrays.asList(split[iOverwrite].trim().split("\\s*,\\s*")));
      }
      if (iPriority >= 0 && split.length > iPriority) {
        try {
          priority = Double.parseDouble(split[iPriority].trim());
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
              + " ERROR: Invalid priority in line " + lineCount
              + " in regexner file " + mappingFilename + ": \"" + line + "\"!", e);
        }
      }

      double weight = 0.0;
      if (iWeight >= 0 && split.length > iWeight) {
        try {
          weight = Double.parseDouble(split[iWeight].trim());
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
              + " ERROR: Invalid weight in line " + lineCount
              + " in regexner file " + mappingFilename + ": \"" + line + "\"!", e);
        }
      }
      int annotateGroup = 0;
      // Get annotate group from input....
      if (iGroup>= 0 && split.length > iGroup) {
        // Which group to take (allow for context)
        String context = split[iGroup].trim();
        try {
          annotateGroup = Integer.parseInt(context);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
              + " ERROR: Invalid group in line " + lineCount
              + " in regexner file " + mappingFilename + ": \"" + line + "\"!", e);
        }
      }

      // Print some warnings about the type
      for (int i = 0; i < types.length; i++) {
        String type = types[i];
        // TODO: Have option to allow commas in types
        int commaPos = type.indexOf(',');
        if (commaPos > 0) {
          // Strip the "," and just take first type
          String newType = type.substring(0, commaPos).trim();
          logger.warn("TokensRegexNERAnnotator " + annotatorName +
                  ": Entry has multiple types for " + annotationFieldnames[i] + ": " + line + ".  Taking type to be " + newType);
          types[i] = newType;
        }
      }

      Entry entry = new Entry(tokensRegex, regexes, types, overwritableTypes, priority, weight, annotateGroup);

      if (seenRegexes.containsKey(key)) {
        Entry oldEntry = seenRegexes.get(key);
        if (priority > oldEntry.priority) {
          logger.warn("TokensRegexNERAnnotator " + annotatorName +
                  ": Replace duplicate entry (higher priority): old=" + oldEntry + ", new=" + entry);
        } else {
          String oldTypeDesc = oldEntry.getTypeDescription();
          String newTypeDesc = entry.getTypeDescription();
          if (!oldTypeDesc.equals(newTypeDesc)) {
            if (verbose) {
              logger.warn("TokensRegexNERAnnotator " + annotatorName +
                      ": Ignoring duplicate entry: " + split[0] + ", old type = " + oldTypeDesc + ", new type = " + newTypeDesc);
            }
          // } else {
          //   if (verbose) {
          //     logger.warn("TokensRegexNERAnnotator " + annotatorName +
          //             ": Duplicate entry [ignored]: " + split[0] + ", old type = " + oldEntry.type + ", new type = " + type);
          //   }
          }
          continue;
        }
      }

      // Print some warning if label belongs to noDefaultOverwriteLabels but there is no overwritable types
      if (entry.overwritableTypes.isEmpty() && hasNoOverwritableType(noDefaultOverwriteLabels, entry.types)) {
        logger.warn("TokensRegexNERAnnotator " + annotatorName +
                ": Entry doesn't have overwriteable types " + entry + ", but entry type is in noDefaultOverwriteLabels");
      }

      entries.add(entry);
      entryToMappingFileNumber.put(entry, mappingFileIndex);
      seenRegexes.put(key, entry);
      if (entry.tokensRegex != null) isTokensRegex++;
    }

    logger.log("TokensRegexNERAnnotator " + annotatorName +
            ": Read " + (entries.size() - origEntriesSize) + " unique entries out of " + lineCount + " from " + mappingFilename
       + ", " + isTokensRegex + " TokensRegex patterns.");
    return entries;
  }

  private static boolean hasNoOverwritableType(Set<String> noDefaultOverwriteLabels, String[] types) {
    for (String type:types) {
      if (noDefaultOverwriteLabels.contains(type)) return true;
    }
    return false;
  }

  // todo [cdm 2016]: This logic seems wrong. If you have semi-colons only between files, it doesn't work!
  private static String[] processListMappingFiles(String mappingFiles) {
    if (mappingFiles.contains(";") && mappingFiles.contains(",")) {
      return SEMICOLON_DELIMITERS_PATTERN.split(mappingFiles);
      //Semicolons separate the files and for each file, commas separate the options - options handled later
    } else if (mappingFiles.contains(",")) {
      return COMMA_DELIMITERS_PATTERN.split(mappingFiles);
      //No per-file options, commas separate the files
    } else {
      //Semicolons separate the files
      return SEMICOLON_DELIMITERS_PATTERN.split(mappingFiles);
    }
  }

  private static void processPerFileOptions(String annotatorName, String[] mappings, List<Boolean> ignoreCaseList, List<Pattern> validPosPatternList, boolean ignoreCase, Pattern validPosPattern) {
    Integer numMappingFiles = mappings.length;
    for (int index = 0; index < numMappingFiles; index++) {
      boolean ignoreCaseSet = false;
      boolean validPosPatternSet = false;
      String[] allOptions = COMMA_DELIMITERS_PATTERN.split(mappings[index].trim());
      Integer numOptions = allOptions.length;
      if (numOptions > 1) { // there are some per file options here
        for (int i = 0; i < numOptions-1; i++) {
          String[] optionAndValue = EQUALS_DELIMITERS_PATTERN.split(allOptions[i].trim());
          if (optionAndValue.length != 2) {
            throw new IllegalArgumentException("TokensRegexNERAnnotator " + annotatorName
                    + " ERROR: Incorrectly specified options for mapping file " + mappings[index].trim());
          } else {
            switch (optionAndValue[0].trim().toLowerCase()) {
              case "ignorecase":
                ignoreCaseList.add(Boolean.parseBoolean(optionAndValue[1].trim()));
                ignoreCaseSet = true;
                break;
              case "validpospattern":
                String validPosRegex = optionAndValue[1].trim();
                if (validPosRegex != null && !validPosRegex.equals("")) {
                  validPosPatternList.add(Pattern.compile(validPosRegex));
                } else {
                  validPosPatternList.add(validPosPattern);
                }
                validPosPatternSet = true;
                break;
              default:
                break;
            }
          }
        }
        mappings[index] = allOptions[numOptions-1];
      }

      if (!ignoreCaseSet) {
        ignoreCaseList.add(ignoreCase);
      }

      if (!validPosPatternSet) {
        validPosPatternList.add(validPosPattern);
      }
    }
  }

  private static boolean atLeastOneValidPosPattern(List<Pattern> validPosPatternList) {
    for (Pattern pattern : validPosPatternList) {
      if (pattern != null) return true;
    }
    return false;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    // TODO: we might want to allow for different RegexNER annotators
    // to satisfy different requirements
    return Collections.emptySet();
  }

}
