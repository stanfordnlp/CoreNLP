package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.ling.tokensregex.matcher.TrieMap;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Similar to RegexNERAnnotator but uses TokensRegex as the underlying library for matching regular expressions.
 *
 * Some Differences:
 * <ul>
 *   <li>Will not overwrite NER annotation, if found expression overlaps (i.e. does not contain the old NER phrase)</li>
 *   <li>validPosPattern must match the entire POS tag of the token</li>
 *   <li>there are different modes for matching the POS tag (PosMatchType)</li>
 *   <li>By default, there is no validPosPattern</li>
 *   <li>By default, MISC is always replaced</li>
 * </ul>
 *
 * @author Angel Chang
 */
public class TokensRegexNERAnnotator implements Annotator {
  // TODO: Can remove entries and just have a MultiPatternMatcher probably
  private final boolean ignoreCase;
  private final List<Entry> entries;
  private final Map<TokenSequencePattern,Entry> patternToEntry = new IdentityHashMap<TokenSequencePattern,Entry>();
  private final MultiPatternMatcher<CoreMap>  multiPatternMatcher;

  private final Set<String> myLabels;
  private final Pattern validPosPattern;
  private final boolean verbose;

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
    Properties props = new Properties();
    props.setProperty(name + ".mapping", mapping);
    props.setProperty(name +".ignorecase", String.valueOf(ignoreCase));
    if (validPosRegex != null) {
      props.setProperty(name +".validpospattern", validPosRegex);
    }
    return props;
  }

  public TokensRegexNERAnnotator(String name, Properties properties) {
    String backgroundSymbol = properties.getProperty(name + ".backgroundSymbol",
            SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL + ",MISC");
    String[] backgroundSymbols = backgroundSymbol.split("\\s*,\\s*");
    String mapping = properties.getProperty(name + ".mapping", DefaultPaths.DEFAULT_REGEXNER_RULES);
    String validPosRegex = properties.getProperty(name + ".validpospattern");
    this.posMatchType = PosMatchType.valueOf(properties.getProperty(name + ".posmatchtype",
            DEFAULT_POS_MATCH_TYPE.name()));
    boolean overwriteMyLabels = true;

    this.ignoreCase = Boolean.parseBoolean(properties.getProperty(name + ".ignorecase", "false"));
    this.verbose = Boolean.parseBoolean(properties.getProperty(name + ".verbose", "false"));

    if (validPosRegex != null && !validPosRegex.equals("")) {
      validPosPattern = Pattern.compile(validPosRegex);
    } else {
      validPosPattern = null;
    }
    BufferedReader rd = null;
    try {
      rd = IOUtils.readerFromString(mapping);
      entries = readEntries(name, mapping, rd, ignoreCase, verbose);
    } catch (IOException e) {
      throw new RuntimeIOException("Couldn't read TokensRegexNER from " + mapping, e);
    } finally {
      IOUtils.closeIgnoringExceptions(rd);
    }
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
    NodePattern<String> posTagPattern = (validPosPattern != null && PosMatchType.MATCH_ALL_TOKENS.equals(posMatchType))?
            new CoreMapNodePattern.StringAnnotationRegexPattern(validPosPattern):null;
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>(entries.size());
    for (Entry entry:entries) {
      List<SequencePattern.PatternExpr> nodePatterns = new ArrayList<SequencePattern.PatternExpr>();
      for (String p:entry.regex) {
        CoreMapNodePattern c = CoreMapNodePattern.valueOf(p, patternFlags);
        if (posTagPattern != null) {
          c.add(CoreAnnotations.PartOfSpeechAnnotation.class, posTagPattern);
        }
        nodePatterns.add(new SequencePattern.NodePatternExpr(c));
      }
      TokenSequencePattern pattern = TokenSequencePattern.compile(
              new SequencePattern.SequencePatternExpr(nodePatterns));
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
      int start = m.start();
      int end = m.end();

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
          System.err.println("Not annotating  '" + m.group() + "': " +
                  StringUtils.joinFields(m.groupNodes(), CoreAnnotations.NamedEntityTagAnnotation.class)
                  + " with " + entry.type + ", sentence is '" + StringUtils.joinWords(tokens, " ") + "'");
        }
      }
    }
  }

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
    if (prevNerEndIndex != (start-1) && nextNerStartIndex != end) {
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
        if (entry.overwritableTypes.contains(startNer) || myLabels.contains(startNer)) {
          overwriteOriginalNer = true;
        }
      }
    }
    return overwriteOriginalNer;
  }

  private static class Entry {
    public String[] regex; // the regex, tokenized by splitting on white space
    public String type; // the associated type
    public Set<String> overwritableTypes;
    public double priority;

    public Entry(String[] regex, String type, Set<String> overwritableTypes, double priority) {
      this.regex = regex;
      this.type = type.intern();
      this.overwritableTypes = overwritableTypes;
      this.priority = priority;
    }

    public String toString() {
      return "Entry{" + regex + ' ' + type + ' ' + overwritableTypes + ' ' + priority + '}';
    }
  }

  /**
   *  Creates a combined list of Entries using the provided mapping file, and sorts them by
   *  first by priority, then the number of tokens in the regex.
   *
   *  @param mapping The Reader containing RegexNER mappings. It's lines are counted from 1
   *  @return a sorted list of Entries
   */
  private static List<Entry> readEntries(String annotatorName,
                                         String mappingFilename,
                                         BufferedReader mapping,
                                         boolean ignoreCase, boolean verbose) throws IOException {
    List<Entry> entries = new ArrayList<Entry>();
    TrieMap<String,Entry> seenRegexes = new TrieMap<String,Entry>();
    int lineCount = 0;
    for (String line; (line = mapping.readLine()) != null; ) {
      lineCount ++;
      String[] split = line.split("\t");
      if (split.length < 2 || split.length > 4)
        throw new IllegalArgumentException("Provided mapping file is in wrong format");

      String[] regexes = split[0].trim().split("\\s+");
      String[] key = regexes;
      if (ignoreCase) {
        key = new String[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
          key[i] = regexes[i].toLowerCase();
        }
      }
      String type = split[1].trim();

      if (seenRegexes.containsKey(key)) {
        Entry oldEntry = seenRegexes.get(key);
        String[] types = oldEntry.type.split("\\s*,\\s*");
        if (!CollectionUtils.asSet(types).contains(type)) {
          if (verbose) {
            System.err.println("Ignoring duplicate entry: " + split[0] + ", old type = " + oldEntry.type + ", new type = " + type);
          }
        }
        continue;
      }

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

      entries.add(new Entry(regexes, type, overwritableTypes, priority));
      seenRegexes.put(key, entries.get(entries.size()-1));
    }

    System.err.println("TokensRegexAnnotator " + annotatorName +
            ": Read " + entries.size() + " unique entries out of " + lineCount + " from " + mappingFilename);
    // System.err.println(entries);
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
