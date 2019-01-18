package edu.stanford.nlp.time;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Annotates text using HeidelTime.
 * The main difference from edu.stanford.nlp.time.HeidelTimeAnnotator is that
 * we handle XML documents that are common in KBP.
 *
 * GUTIME/TimeML specifications can be found at:
 * <a href="http://www.timeml.org/site/tarsqi/modules/gutime/index.html">
 * http://www.timeml.org/site/tarsqi/modules/gutime/index.html</a>.
 *
 * @author Arun Chaganty
 */
public class HeidelTimeKBPAnnotator implements Annotator {

  // This could probably be fixed in newer HeidelTime versions, which even support using our tagger.

  private static final String BASE_PATH = "$NLP_DATA_HOME/packages/heideltime/";
  private static final String DEFAULT_PATH = DataFilePaths.convert(BASE_PATH);
  private final File heideltimePath;
  private final boolean outputResults;
  private final String language;
  private final HeidelTimeOutputReader outputReader = new HeidelTimeOutputReader();

  // if used in a pipeline or constructed with a Properties object,
  // this property tells the annotator where to find the script
  public static final String HEIDELTIME_PATH_PROPERTY = "heideltime.path";
  public static final String HEIDELTIME_LANGUAGE_PROPERTY = "heideltime.language";
  public static final String HEIDELTIME_OUTPUT_RESULTS = "heideltime.outputResults";

  public HeidelTimeKBPAnnotator() {
    this(new File(System.getProperty("heideltime", DEFAULT_PATH)));
  }
  public HeidelTimeKBPAnnotator(File heideltimePath) {
    this(heideltimePath, "english", false);
  }

  public HeidelTimeKBPAnnotator(File heideltimePath, String language, boolean outputResults) {
    this.heideltimePath = heideltimePath;
    this.outputResults = outputResults;
    this.language = language;
  }

  public HeidelTimeKBPAnnotator(String name, Properties props) {
    this.heideltimePath = new File(props.getProperty(HEIDELTIME_PATH_PROPERTY,
            System.getProperty("heideltime",
                    DEFAULT_PATH)));
    this.outputResults = Boolean.valueOf(props.getProperty(HEIDELTIME_OUTPUT_RESULTS, "false"));
    this.language = props.getProperty(HEIDELTIME_LANGUAGE_PROPERTY, "english");
//    this.tagList = Arrays.asList(props.getProperty("clean.xmltags", "").toLowerCase().split("\\|"))
//        .stream().filter(x -> x.length() > 0)
//        .collect(Collectors.toList());
  }

  @Override
  public void annotate(Annotation annotation) {
    try {
      this.annotate((CoreMap)annotation);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  private static final Map<String, String> TRANSLATE = new HashMap<String, String>() {{
    this.put("*NL*", "\n");
  }};
  public void annotate(CoreMap document) throws IOException {
    try {

      //--Create Input File
      //(create file)
      File inputFile = File.createTempFile("heideltime", ".input");
      //(write to file)
      PrintWriter inputWriter = new PrintWriter(inputFile);
      prepareHeidelTimeInput(inputWriter, document);
      inputWriter.close();
      Optional<String> pubDate = getPubDate(document);

      //--Build Command
      List<String> args = new ArrayList<>(Arrays.asList("java",
          "-jar", this.heideltimePath.getPath() + "/heideltime.jar",
          "-c", this.heideltimePath.getPath() + "/config.props",
          "-l", this.language,
          "-t", "NEWS"));
      if (pubDate.isPresent()) {
        args.add("-dct");
        args.add(pubDate.get());
      }
      args.add(inputFile.getPath());

      // run HeidelTime on the input file
      ProcessBuilder process = new ProcessBuilder(args);
      StringWriter outputWriter = new StringWriter();
      SystemUtils.run(process, outputWriter, null);
      String output = outputWriter.getBuffer().toString();
      List<CoreMap> timexAnns = outputReader.process(document, output);

      document.set(TimeAnnotations.TimexAnnotations.class, timexAnns);
      if (outputResults) {
        System.out.println(timexAnns);
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.err.println("error running HeidelTime on this doc: "+document.get(CoreAnnotations.DocIDAnnotation.class));
//      throw e;
    }
  }

  private Optional<String> getPubDate(CoreMap document) {
    //--Get Date
    //(error checks)
    if (!document.containsKey(CoreAnnotations.CalendarAnnotation.class) && !document.containsKey(CoreAnnotations.DocDateAnnotation.class)) {
      throw new IllegalArgumentException("CoreMap must have either a Calendar or DocDate annotation"); //not strictly necessary, technically...
    }
    //(variables)
    Calendar dateCalendar = document.get(CoreAnnotations.CalendarAnnotation.class);
    if (dateCalendar != null) {
      //(case: calendar annotation)
      return Optional.of(String.format("%TF", dateCalendar));
    } else {
      //(case: docdateannotation)
      String s = document.get(CoreAnnotations.DocDateAnnotation.class);
      if (s != null) {
        return Optional.of(s);
      }
    }
    return Optional.empty();
  }

  private void prepareHeidelTimeInput(PrintWriter stream, CoreMap document) {
    // We really should use the full text annotation because our cleanxml can be useless.
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        String text = token.originalText();
        stream.append(TRANSLATE.getOrDefault(text, text));
        // HACK: will not handle contractions like "del = de + el" properly -- will be deel.
        // stream.append(token.after().length() > 0 ? " " : "");
        // HACK: will not handle things like 12-abr-2011 which are chunked up properly into 12 - abr-2011.
        stream.append(" ");
      }
      stream.append("\n");
    }
  }

  static class HeidelTimeOutputReader {
    static public class Node {
      public final String contents;
      public final int start;
      public final int end;

      public Node(String contents, int start, int end) {
        this.contents = contents;
        this.start = start;
        this.end = end;
      }

      @Override
      public String toString() {
        return "[" + contents + "]";
      }
    }

    static public class TimexNode extends Node {
      public final Timex timex;

      public TimexNode(String contents, int start, int end, Timex timex) {
        super(contents, start, end);
        this.timex = timex;
      }

      @Override
      public String toString() {
        return "[" + contents + "|" + "TIMEX:" + timex + "]" ;
      }
    }

    Pattern timeMLOpen = Pattern.compile(".*<TimeML>", Pattern.DOTALL);
    Pattern timeMLClose = Pattern.compile("</TimeML>.*", Pattern.DOTALL);

    Pattern timexTagOpen = Pattern.compile("<TIMEX3\\s*(?:(?:[a-z]+)=\"(?:[^\"]+)\"\\s*)*>");
    Pattern attr = Pattern.compile("(?<key>[a-z]+)=\"(?<value>[^\"]+)\"");
    Pattern timexTagClose = Pattern.compile("</TIMEX3>");

    public List<CoreMap> process(CoreMap document, String output) {
      ArrayList<CoreMap> ret = new ArrayList<>();

      List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
      List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
      List<Node> nodes = toNodeSequence(output);

      int tokenIdx = 0;
      int nodeIdx = 0;

      String partial = ""; // Things that are left over from previous partially matched tokens.
      for(Node node: nodes) {

        // Get tokens.
        String text = node.contents.trim();

        while (tokens.get(tokenIdx).word().equals("*NL*") && tokenIdx < tokens.size()) tokenIdx +=1; // Skip past stupid *NL* tags.
        int tokenEndIdx = tokenIdx;

        for(CoreLabel token: tokens.subList(tokenIdx, tokens.size())) {
          if (text.length() == 0) break;
          tokenEndIdx++;
          String matchStr = token.originalText().trim();
          // This is necessarily in the middle.
          if (Objects.equals(matchStr, "*NL*")) continue; // This is one weird case where JavaNLP has a whitespace token.

          if ((partial+text).startsWith(matchStr)) {
            text = text.substring(matchStr.length() - partial.length()).trim();
            partial = ""; // And clear partial.
          } else if (matchStr.startsWith(partial+text)) { // uh oh we have a partial match.
            partial = matchStr.substring(0, partial.length() + text.length()); // we need to remember what we matched earlier.
            text = "";
          } else { // This should never happen.
            assert false;
          }
        }
        // Only process time nodes if they span the same sentence.
        if (node instanceof TimexNode && tokens.get(tokenIdx).sentIndex() == tokens.get(tokenEndIdx-1).sentIndex()) {
          TimexNode timexNode = (TimexNode) node;
          CoreMap sentence = sentences.get(tokens.get(tokenIdx).sentIndex());
          ret.add(makeTimexMap(timexNode, tokens.subList(tokenIdx, tokenEndIdx), sentence));
        }
        if (partial.length() > 0) {
          tokenIdx = tokenEndIdx-1; // Move back a token because this is actually shared between the two nodes.
        } else {
          tokenIdx = tokenEndIdx;
        }
        nodeIdx++;
      }
      return ret;
    }

    private CoreMap makeTimexMap(TimexNode node, List<CoreLabel> tokens, CoreMap sentence) {
      CoreMap timexMap = new ArrayCoreMap();
      timexMap.set(TimeAnnotations.TimexAnnotation.class, node.timex);
      timexMap.set(CoreAnnotations.TextAnnotation.class, node.contents);
      timexMap.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, beginOffset(tokens.get(0)));
      timexMap.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, endOffset(tokens.get(tokens.size()-1)));

      timexMap.set(CoreAnnotations.TokenBeginAnnotation.class, tokens.get(0).index());
      timexMap.set(CoreAnnotations.TokenEndAnnotation.class, tokens.get(tokens.size()-1).index());
      timexMap.set(CoreAnnotations.TokensAnnotation.class, tokens);

      if (sentence.get(TimeAnnotations.TimexAnnotations.class) == null) {
        sentence.set(TimeAnnotations.TimexAnnotations.class, new ArrayList<>());
      }
      sentence.get(TimeAnnotations.TimexAnnotations.class).add(timexMap);
      // update NER for tokens
      for (CoreLabel token : tokens) {
        token.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
        token.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, node.timex.value());
        token.set(TimeAnnotations.TimexAnnotation.class, node.timex);
      }

      return timexMap;
    }

    private List<Node> toNodeSequence(String output) {
      // First of all, get rid of all XML markup that HeidelTime inserts.
      output = timeMLOpen.matcher(output).replaceAll("").trim();
      output = timeMLClose.matcher(output).replaceAll("").trim();

      // Now go through and chunk sequence into <TIMEX3> tag regions.
      Matcher openMatcher = timexTagOpen.matcher(output);
      Matcher attrMatcher = attr.matcher(output);
      Matcher closeMatcher = timexTagClose.matcher(output);

      List<Node> ret = new ArrayList<>();

      // TODO: save metadata of TIMEX token positions or stuff.
      int charIdx = 0;
      HashMap<String, String> attrs = new HashMap<>();
      while(openMatcher.find(charIdx)) {
        int tagBegin = openMatcher.start();
        int tagBeginEnd = openMatcher.end();

        // Add everything before this tagBegin to a node.
        if (charIdx < tagBegin) {
          ret.add(new Node(output.substring(charIdx, tagBegin), charIdx, tagBegin));
        }

        attrs.clear();
        // Get the attributes
        while(attrMatcher.find(tagBegin+1) && attrMatcher.end() < tagBeginEnd) {
          attrs.put(attrMatcher.group("key"), attrMatcher.group("value"));
          tagBegin = attrMatcher.end();
        }
        // Ok, move to the close tag.
        boolean matched = closeMatcher.find(tagBeginEnd);
        assert matched; // Assert statements are sometimes ignored.
        int tagEndBegin = closeMatcher.start();
        int tagEnd = closeMatcher.end();

        String text = output.substring(tagBeginEnd, tagEndBegin);
        Timex timex = toTimex(text, attrs);
        ret.add(new TimexNode(text, tagBeginEnd, tagEndBegin, timex));

        charIdx = closeMatcher.end();
      }
      // Add everything before this tagBegin to a node. to the
      if (charIdx < output.length()) {
        ret.add(new Node(output.substring(charIdx, output.length()), charIdx, output.length()));
      }

      return ret;
    }

    private Timex toTimex(String text, Map<String, String> attrs) {
      // Mandatory attributes
      String tid = attrs.get("tid");
      String val = attrs.getOrDefault("val", attrs.get("value"));
      String altVal = attrs.get("alTVal");
      String type = attrs.get("type");

      // Optional attributes
      int beginPoint = Integer.parseInt(attrs.getOrDefault("beginpoint", "-1"));
      int endPoint = Integer.parseInt(attrs.getOrDefault("endpoint", "-1"));

      // NOTE(chaganty): I do not support timex ranges.

      return new Timex(type, val, altVal, tid, text, beginPoint, endPoint);
    }
  }

  private static int beginOffset(CoreMap ann) {
    return ann.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
  }
  private static int endOffset(CoreMap ann) {
    return ann.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
  }

//  static final List<String> skipList = Arrays.asList("*NL*");
//  private static int updateTokenIdx(List<CoreLabel> tokens, int currentIndex, String text) {
//    text = text.trim();
//    while(currentIndex < tokens.size() && text.length() > 0) {
//      CoreLabel token = tokens.get(currentIndex++);
//      if (skipList.contains(token.originalText())) continue;
//
//      if (text.startsWith(token.originalText())) {
//        text = text.substring(token.originalText().length()).trim();
//      } else if (token.originalText().startsWith(text)) { // In case text is smaller than original text
//          text = text.substring(text.length()).trim();
//      } else { // skip
//        logf("WARNING: Could not figure out how to match token %s to string %s; skipping", token.originalText(), text.substring(0, Math.min(40, text.length())));
//        if (text.indexOf(token.originalText()) > 0) {
//          text = text.substring(text.indexOf(token.originalText()));
//        } else {
//          text = text.substring(text.length()).trim();
//        }
//        currentIndex--;
//      }
//    }
////    return advanceTokenIdx(tokens, currentIndex);
//  }

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
    return Collections.singleton(TimeAnnotations.TimexAnnotations.class);
  }

}
