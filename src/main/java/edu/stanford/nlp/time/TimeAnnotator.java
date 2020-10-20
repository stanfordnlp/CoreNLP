package edu.stanford.nlp.time;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Annotate temporal expressions in text with {@link SUTime}.
 * The expressions recognized by SUTime are loosely based on GUTIME.
 *
 * After annotation, the {@link TimeAnnotations.TimexAnnotations} annotation
 * will be populated with a {@code List&lt;CoreMap&gt;}, each of which
 * will represent one temporal expression.
 *
 * If a reference time is set (via {@link edu.stanford.nlp.ling.CoreAnnotations.DocDateAnnotation}),
 * then temporal expressions are resolved with respect to the document date.  You set it on an
 * Annotation as follows:
 * <blockquote>{@code annotation.set(CoreAnnotations.DocDateAnnotation.class, "2013-07-14");}</blockquote>
 * <p>
 * <br>
 * <table border="1">
 *   <caption><b>Input annotations</b></caption>
 *   <tr>
 *     <th>Annotation</th>
 *     <th>Type</th>
 *     <th>Description</th>
 *     <th>Required?</th>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.DocDateAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>If present, then the string is interpreted as a date/time and
 *         used as the reference document date with respect to which other
 *         temporal expressions are resolved</td>
 *     <td>Optional</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation}</td>
 *     <td>{@code List&lt;CoreMap&gt;}</td>
 *     <td>If present, time expressions will be extracted from each sentence
 *         and each sentence will be annotated individually.</td>
 *     <td>Optional (good to have)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation}</td>
 *     <td>{@code List&lt;CoreLabel&gt;}</td>
 *     <td>Tokens (for each sentence or for entire annotation if no sentences)</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>Text (for each sentence or for entire annotation if no sentences)</td>
 *     <td>Optional</td>
 *   </tr>
 *   <tr><td colspan="4"><center><b>Per token annotations</b></center></td></tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>Token text (normalized)</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>Token text (original)</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first character of this token
 *        (0-based wrt to TextAnnotation of the annotation containing the TokensAnnotation).</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first character after this token
 *        (0-based wrt to TextAnnotation of the annotation containing the TokensAnnotation).</td>
 *     <td>Required</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>Token part of speech</td>
 *     <td>Optional</td>
 *   </tr>
 * </table>
 *
 * <p>
 * <br>
 * <table border="1">
 *   <caption><b>Output annotations</b></caption>
 *   <tr>
 *     <th>Annotation</th>
 *     <th>Type</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>{@link TimeAnnotations.TimexAnnotations}</td>
 *     <td>{@code List&lt;CoreMap&gt;}</td>
 *     <td>List of temporal expressions (on the entire annotation and also for each sentence)</td>
 *   </tr>
 *   <tr><td colspan="3"><center><b>Per each temporal expression</b></center></td></tr>
 *   <tr>
 *     <td>{@link TimeAnnotations.TimexAnnotation}</td>
 *     <td>{@link Timex}</td>
 *     <td>Timex object with TIMEX3 XML attributes, use for exporting TIMEX3 information</td>
 *   </tr>
 *   <tr>
 *     <td>{@link TimeExpression.Annotation}</td>
 *     <td>{@link TimeExpression}</td>
 *     <td>TimeExpression object.  Use {@code getTemporal()} to get internal temporal representation.</td>
 *   </tr>
 *   <tr>
 *     <td>{@link TimeExpression.ChildrenAnnotation}</td>
 *     <td>{@code List&lt;CoreMap&gt;}</td>
 *     <td>List of chunks forming this time expression (inner chunks can be tokens, nested time expressions,
 *         numeric expressions, etc)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation}</td>
 *     <td>{@code String}</td>
 *     <td>Text of this time expression</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation}</td>
 *     <td>{@code List&lt;CoreLabel&gt;}</td>
 *     <td>Tokens that make up this time expression</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first character of this token (0-based).</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first character after this token (0-based).</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first token of this time expression (0-based).</td>
 *   </tr>
 *   <tr>
 *     <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation}</td>
 *     <td>{@code Integer}</td>
 *     <td>The index of the first token after this time expression (0-based).</td>
 *   </tr>
 * </table>
 */

public class TimeAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TimeAnnotator.class);

  private final TimeExpressionExtractorImpl timexExtractor;
  private final boolean quiet;

  public TimeAnnotator() {
    this(false);
  }

  public TimeAnnotator(boolean quiet) {
    timexExtractor = new TimeExpressionExtractorImpl();
    this.quiet = quiet;
  }

  public TimeAnnotator(String name, Properties props) {
    this(name, props, false);
  }

  public TimeAnnotator(String name, Properties props, boolean quiet) {
    timexExtractor = new TimeExpressionExtractorImpl(name, props);
    this.quiet = quiet;
  }

  @Override
  public void annotate(Annotation annotation) {
    SUTime.TimeIndex timeIndex = new SUTime.TimeIndex();
    String docDate = annotation.get(CoreAnnotations.DocDateAnnotation.class);
    if (docDate == null) {
      Calendar cal = annotation.get(CoreAnnotations.CalendarAnnotation.class);
      if (cal == null) {
        if ( ! quiet) { log.warn("No document date specified"); }
      } else {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd:hh:mm:ss");
        docDate = dateFormat.format(cal.getTime());
      }
    }
    List<CoreMap> allTimeExpressions; // initialized below = null;
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences != null) {
      allTimeExpressions = new ArrayList<>();
      List<CoreMap> allNumerics = new ArrayList<>();
      for (CoreMap sentence: sentences) {
        // make sure that token character offsets align with the actual sentence text
        // They may not align due to token normalizations, such as "(" to "-LRB-".
        CoreMap alignedSentence =  NumberSequenceClassifier.alignSentence(sentence);
        // uncomment the next line for verbose dumping of tokens....
        // log.info("SENTENCE: " + ((ArrayCoreMap) sentence).toShorterString());
        List<CoreMap> timeExpressions =
          timexExtractor.extractTimeExpressionCoreMaps(alignedSentence, docDate, timeIndex);
        if (timeExpressions != null) {
          allTimeExpressions.addAll(timeExpressions);
          sentence.set(TimeAnnotations.TimexAnnotations.class, timeExpressions);
          for (CoreMap timeExpression:timeExpressions) {
            timeExpression.set(CoreAnnotations.SentenceIndexAnnotation.class, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          }
        }
        List<CoreMap> numbers = alignedSentence.get(CoreAnnotations.NumerizedTokensAnnotation.class);
        if(numbers != null){
          sentence.set(CoreAnnotations.NumerizedTokensAnnotation.class, numbers);
          allNumerics.addAll(numbers);
        }
      }
      annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, allNumerics);
    } else {
      allTimeExpressions = annotateSingleSentence(annotation, docDate, timeIndex);
    }
    annotation.set(TimeAnnotations.TimexAnnotations.class, allTimeExpressions);
  }

  /**
   * Helper method for people not working from a complete Annotation.
   *
   * @return A list of CoreMap.  Each CoreMap represents a detected temporal expression.
   */
  public List<CoreMap> annotateSingleSentence(CoreMap sentence, String docDate, SUTime.TimeIndex timeIndex) {
    CoreMap annotationCopy = NumberSequenceClassifier.alignSentence(sentence);
    if (docDate != null && docDate.isEmpty()) {
      docDate = null;
    }
    return timexExtractor.extractTimeExpressionCoreMaps(annotationCopy, docDate, timeIndex);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.singleton(CoreAnnotations.TokensAnnotation.class);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(TimeAnnotations.TimexAnnotations.class);
  }

}
