package edu.stanford.nlp.time;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Annotates text using HeidelTime.
 *
 * GUTIME/TimeML specifications can be found at:
 * <a href="http://www.timeml.org/site/tarsqi/modules/gutime/index.html">
 * http://www.timeml.org/site/tarsqi/modules/gutime/index.html</a>.
 *
 * @author Gabor Angeli
 */
public class HeidelTimeAnnotator implements Annotator {

  // TODO HeidelTime doesn't actually run on the NLP machines :( (TreeTagger doesn't run.)
  // This could probably be fixed in newer HeidelTime versions, which even support using our tagger.

  private static final String BASE_PATH = "$NLP_DATA_HOME/packages/heideltime/";
  private static final String DEFAULT_PATH = DataFilePaths.convert(BASE_PATH);
  private final File heideltimePath;
  private final boolean outputResults;
  private final String language;

  // if used in a pipeline or constructed with a Properties object,
  // this property tells the annotator where to find the script
  public static final String HEIDELTIME_PATH_PROPERTY = "heideltime.path";
  public static final String HEIDELTIME_LANGUAGE_PROPERTY = "heideltime.language";
  public static final String HEIDELTIME_OUTPUT_RESULTS = "heideltime.outputResults";

  public HeidelTimeAnnotator() {
    this(new File(System.getProperty("heideltime", DEFAULT_PATH)));
  }
  public HeidelTimeAnnotator(File heideltimePath) {
    this(heideltimePath, "english", false);
  }

  public HeidelTimeAnnotator(File heideltimePath, String language, boolean outputResults) {
    this.heideltimePath = heideltimePath;
    this.outputResults = outputResults;
    this.language = language;
  }

  public HeidelTimeAnnotator(String name, Properties props) {
    this(new File(props.getProperty(HEIDELTIME_PATH_PROPERTY,
            System.getProperty("heideltime",
                    DEFAULT_PATH))),
        props.getProperty(HEIDELTIME_LANGUAGE_PROPERTY, "english"),
        Boolean.valueOf(props.getProperty(HEIDELTIME_OUTPUT_RESULTS, "false")));
  }

  @Override
  public void annotate(Annotation annotation) {
    try {
      this.annotate((CoreMap)annotation);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public void annotate(CoreMap document) throws IOException {
    //--Create Input File
    //(create file)
    File inputFile = File.createTempFile("heideltime", ".input");
    //(write to file)
    PrintWriter inputWriter = new PrintWriter(inputFile);
    inputWriter.println(document.get(CoreAnnotations.TextAnnotation.class));
    inputWriter.close();

    //--Get Date
    //(error checks)
    if(!document.containsKey(CoreAnnotations.CalendarAnnotation.class) && !document.containsKey(CoreAnnotations.DocDateAnnotation.class)){
      throw new IllegalArgumentException("CoreMap must have either a Calendar or DocDate annotation"); //not strictly necessary, technically...
    }
    //(variables)
    Calendar dateCalendar = document.get(CoreAnnotations.CalendarAnnotation.class);
    String pubDate = null;
    if (dateCalendar != null) {
      //(case: calendar annotation)
      pubDate = String.format("%TF", dateCalendar);
    } else {
      //(case: docdateannotation)
      String s = document.get(CoreAnnotations.DocDateAnnotation.class);
      if (s != null) {
        pubDate = s;
      }
    }

    //--Build Command
    ArrayList<String> args = new ArrayList<>();
    args.add("java");
    args.add("-jar"); args.add(this.heideltimePath.getPath() + "/heideltime.jar");
    args.add("-c"); args.add(this.heideltimePath.getPath()+"/config.props");
    args.add("-l"); args.add(this.language);
    args.add("-t"); args.add("NEWS");
    if(pubDate != null){
      args.add("-dct"); args.add(pubDate);
    }
    args.add(inputFile.getPath());
    // run HeidelTime on the input file
    ProcessBuilder process = new ProcessBuilder(args);

    StringWriter outputWriter = new StringWriter();
    SystemUtils.run(process, outputWriter, null);
    String output = outputWriter.getBuffer().toString();
    Pattern docClose = Pattern.compile("</DOC>.*", Pattern.DOTALL);
    output = docClose.matcher(output).replaceAll("</DOC>").replaceAll("<!DOCTYPE TimeML SYSTEM \"TimeML.dtd\">",""); //TODO TimeML.dtd? FileNotFoundException if we leave it in
    Pattern badNestedTimex = Pattern.compile(Pattern.quote("<T</TIMEX3>IMEX3"));
    output = badNestedTimex.matcher(output).replaceAll("</TIMEX3><TIMEX3");
    Pattern badNestedTimex2 = Pattern.compile(Pattern.quote("<TI</TIMEX3>MEX3"));
    output = badNestedTimex2.matcher(output).replaceAll("</TIMEX3><TIMEX3");
    //output = output.replaceAll("\\n\\n<TimeML>\\n\\n","<TimeML>");
    // These tags are needed for the xml to operate
    //output = output.replaceAll("<TimeML>", "");
    //output = output.replaceAll("</TimeML>", "");

    // parse the HeidelTime output
    Element outputXML;
    try {
      outputXML = XMLUtils.parseElement(output);
    } catch (Exception ex) {
      throw new RuntimeException(String.format("error:\n%s\ninput:\n%s\noutput:\n%s",
              ex, IOUtils.slurpFile(inputFile), output), ex);
    }
    inputFile.delete();

    // get Timex annotations
    List<CoreMap> timexAnns = toTimexCoreMaps(outputXML, document);
    document.set(TimeAnnotations.TimexAnnotations.class, timexAnns);
    if (outputResults) {
      System.out.println(timexAnns);
    }

    // align Timex annotations to sentences
    int timexIndex = 0;
    for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {
      int sentBegin = beginOffset(sentence);
      int sentEnd = endOffset(sentence);

      // skip times before the sentence
      while (timexIndex < timexAnns.size() && beginOffset(timexAnns.get(timexIndex)) < sentBegin) {
        ++timexIndex;
      }

      // determine times within the sentence
      int sublistBegin = timexIndex;
      int sublistEnd = timexIndex;
      while (timexIndex < timexAnns.size() &&
              sentBegin <= beginOffset(timexAnns.get(timexIndex)) &&
              endOffset(timexAnns.get(timexIndex)) <= sentEnd) {
        ++sublistEnd;
        ++timexIndex;
      }

      // set the sentence timexes
      sentence.set(TimeAnnotations.TimexAnnotations.class, timexAnns.subList(sublistBegin, sublistEnd));
    }

  }


  private static int beginOffset(CoreMap ann) {
    return ann.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
  }

  private static int endOffset(CoreMap ann) {
    return ann.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
  }

  private static List<CoreMap> toTimexCoreMaps(Element docElem, CoreMap originalDocument) {
    //--Collect Token Offsets
    Map<Integer,Integer> beginMap = Generics.newHashMap();
    Map<Integer,Integer> endMap = Generics.newHashMap();
    boolean haveTokenOffsets = true;
    for(CoreMap sent : originalDocument.get(CoreAnnotations.SentencesAnnotation.class)){
      for(CoreLabel token : sent.get(CoreAnnotations.TokensAnnotation.class)){
        Integer tokBegin = token.get(CoreAnnotations.TokenBeginAnnotation.class);
        Integer tokEnd = token.get(CoreAnnotations.TokenEndAnnotation.class);
        if(tokBegin == null || tokEnd == null){ haveTokenOffsets = false; }
        int charBegin = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int charEnd = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        beginMap.put(charBegin,tokBegin);
        endMap.put(charEnd,tokEnd);
      }
    }
    List<CoreMap> timexMaps = new ArrayList<>();
    int offset = 0;
    NodeList docNodes = docElem.getChildNodes();
    for (int i = 0; i < docNodes.getLength(); i++) {
      Node content = docNodes.item(i);
      if (content instanceof Text) {
        Text text = (Text)content;
        offset += text.getWholeText().length();
      } else if (content instanceof Element) {
        Element child = (Element)content;
        if (child.getNodeName().equals("TIMEX3")) {
          Timex timex = new Timex(child);
          if (child.getChildNodes().getLength() != 1) {
            throw new RuntimeException("TIMEX3 should only contain text " + child);
          }
          String timexText = child.getTextContent();
          CoreMap timexMap = new ArrayCoreMap();
          timexMap.set(TimeAnnotations.TimexAnnotation.class, timex);
          timexMap.set(CoreAnnotations.TextAnnotation.class, timexText);
          int charBegin = offset;
          timexMap.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
          offset += timexText.length();
          timexMap.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset);
          int charEnd = offset;
          //(tokens)
          if(haveTokenOffsets){
            Integer tokBegin = beginMap.get(charBegin);
            int searchStep = 1;          //if no exact match, search around the character offset
            while(tokBegin == null){
              tokBegin = beginMap.get(charBegin - searchStep);
              if(tokBegin == null){
                tokBegin = beginMap.get(charBegin + searchStep);
              }
              searchStep += 1;
            }
            searchStep = 1;
            Integer tokEnd = endMap.get(charEnd);
            while(tokEnd == null){
              tokEnd = endMap.get(charEnd - searchStep);
              if(tokEnd == null){
                tokEnd = endMap.get(charEnd + searchStep);
              }
              searchStep += 1;
            }
            timexMap.set(CoreAnnotations.TokenBeginAnnotation.class, tokBegin);
            timexMap.set(CoreAnnotations.TokenEndAnnotation.class, tokEnd);
          }
          timexMaps.add(timexMap);
        } else {
          throw new RuntimeException("unexpected element " + child);
        }
      } else {
        throw new RuntimeException("unexpected content " + content);
      }
    }
    return timexMaps;
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
    return Collections.singleton(TimeAnnotations.TimexAnnotations.class);
  }

}
