package edu.stanford.nlp.time;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts time expressions
 *
 * @author Angel Chang
 */
@SuppressWarnings("unchecked")
public class TimeExpressionExtractorImpl implements TimeExpressionExtractor {
  protected static final Logger logger = Logger.getLogger(TimeExpressionExtractorImpl.class.getName());

  // Patterns for extracting time expressions
  TimeExpressionPatterns timexPatterns;

  CoreMapExpressionExtractor expressionExtractor;

  // Index of temporal object to ids
  //SUTime.TimeIndex timeIndex = new SUTime.TimeIndex();

  // Options
  Options options;

  public TimeExpressionExtractorImpl()
  {
    init(new Options());
  }

  public TimeExpressionExtractorImpl(String name, Properties props)
  {
    init(name, props);
  }

  public void init(String name, Properties props)
  {
    init(new Options(name, props));
  }

  public void init(Options options)
  {
    this.options = options;
    // TODO: does not allow for multiple loggers
    if (options.verbose) {
      logger.setLevel(Level.FINE);
    } else {
      logger.setLevel(Level.SEVERE);
    }
    NumberNormalizer.setVerbose(options.verbose);
    if (options.grammarFilename != null) {
      timexPatterns = new GenericTimeExpressionPatterns(options);
    } else {
      timexPatterns = new EnglishTimeExpressionPatterns(options);
    }
    this.expressionExtractor = timexPatterns.createExtractor();
    this.expressionExtractor.setLogger(logger);
  }

  public List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate)
  {
    SUTime.TimeIndex timeIndex = new SUTime.TimeIndex();
    return extractTimeExpressionCoreMaps(annotation, docDate, timeIndex);
  }

  public List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate, SUTime.TimeIndex timeIndex)
  {
    List<TimeExpression> timeExpressions = extractTimeExpressions(annotation, docDate);
    return toCoreMaps(annotation, timeExpressions, timeIndex);
  }

  private List<CoreMap> toCoreMaps(CoreMap annotation, List<TimeExpression> timeExpressions, SUTime.TimeIndex timeIndex)
  {
    if (timeExpressions == null) return null;
    List<CoreMap> coreMaps = new ArrayList<CoreMap>(timeExpressions.size());
    for (TimeExpression te:timeExpressions) {
      CoreMap cm = te.getAnnotation();
      SUTime.Temporal temporal = te.getTemporal();
      if (temporal != null) {
        String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
        String text = cm.get(CoreAnnotations.TextAnnotation.class);
        if (origText != null) {
          // Make sure the text is from original (and not from concatenated tokens)
          ChunkAnnotationUtils.annotateChunkText(cm, annotation);
          text = cm.get(CoreAnnotations.TextAnnotation.class);
        }
        Map<String,String> timexAttributes;
        try {
          timexAttributes = temporal.getTimexAttributes(timeIndex);
          if (options.includeRange) {
            SUTime.Temporal rangeTemporal = temporal.getRange();
            if (rangeTemporal != null) {
              timexAttributes.put("range", rangeTemporal.toString());
            }
          }
        } catch (Exception e) {
          logger.log(Level.WARNING, "Failed to get attributes from " + text + ", timeIndex " + timeIndex, e);
          continue;
        }
        Timex timex;
        try {
          timex = Timex.fromMap(text, timexAttributes);
        } catch (Exception e) {
          logger.log(Level.WARNING, "Failed to process " + text + " with attributes " + timexAttributes, e);
          continue;
        }
        cm.set(TimeAnnotations.TimexAnnotation.class, timex);
        if (timex != null) {
          coreMaps.add(cm);
        } else {
          logger.warning("No timex expression for: " + text);
        }
      }
    }
    return coreMaps;
  }

  public List<TimeExpression> extractTimeExpressions(CoreMap annotation, String docDateStr)
  {
    List<CoreMap> mergedNumbers = NumberNormalizer.findAndMergeNumbers(annotation);
    annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbers);

    // TODO: docDate may not have century....

    SUTime.Time docDate = SUTime.parseDateTime(docDateStr);
    List<? extends MatchedExpression> matchedExpressions = expressionExtractor.extractExpressions(annotation);
    List<TimeExpression> timeExpressions = new ArrayList<TimeExpression>(matchedExpressions.size());
    for (MatchedExpression expr:matchedExpressions) {
      if (expr instanceof TimeExpression) {
        timeExpressions.add((TimeExpression) expr);
      } else {
        timeExpressions.add(new TimeExpression(expr));
      }
    }
    // Some resolving is done even if docDate null...
    if ( /*docDate != null && */ timeExpressions != null) {
      resolveTimeExpressions(annotation, timeExpressions, docDate);
    }
    if (options.restrictToTimex3) {
      // Keep only TIMEX3 compatible timeExpressions
      List<TimeExpression> kept = new ArrayList<TimeExpression>(timeExpressions.size());
      for (TimeExpression te:timeExpressions) {
        if (te.getTemporal() != null && te.getTemporal().getTimexValue() != null) {
          kept.add(te);
        } else {
          List<? extends CoreMap> children = te.getAnnotation().get(TimeExpression.ChildrenAnnotation.class);
          if (children != null) {
            for (CoreMap child:children) {
              TimeExpression childTe = child.get(TimeExpression.Annotation.class);
              if (childTe != null) {
                resolveTimeExpression(annotation, childTe, docDate);
                if (childTe.getTemporal() != null && childTe.getTemporal().getTimexValue() != null) {
                  kept.add(childTe);
                }
              }
            }
          }
        }
      }
      timeExpressions = kept;
    }

    // Add back nested time expressions for ranges....
    // For now only one level of nesting...
    if (options.includeNested) {
      List<TimeExpression> nestedTimeExpressions = new ArrayList<TimeExpression>();
      for (TimeExpression te:timeExpressions) {
        if (te.isIncludeNested())  {
          List<? extends CoreMap> children = te.getAnnotation().get(TimeExpression.ChildrenAnnotation.class);
          if (children != null) {
            for (CoreMap child:children) {
              TimeExpression childTe = child.get(TimeExpression.Annotation.class);
              if (childTe != null) {
                nestedTimeExpressions.add(childTe);
              }
            }
          }
        }
      }
      resolveTimeExpressions(annotation, nestedTimeExpressions, docDate);
      timeExpressions.addAll(nestedTimeExpressions);
    }
    Collections.sort(timeExpressions, MatchedExpression.EXPR_TOKEN_OFFSETS_NESTED_FIRST_COMPARATOR);
    // Some resolving is done even if docDate null...
    if ( /*docDate != null && */ timeExpressions != null) {
      resolveTimeExpressions(annotation, timeExpressions, docDate);
    }
    return timeExpressions;
  }

  private void resolveTimeExpression(CoreMap annotation, TimeExpression te, SUTime.Time docDate)
  {
    SUTime.Temporal temporal = te.getTemporal();
    if (temporal != null) {
      // TODO: use correct time for anchor
      try {
        int flags = timexPatterns.determineRelFlags(annotation, te);
        //int flags = 0;
        SUTime.Temporal grounded = temporal.resolve(docDate, flags);
        if (grounded == null) {
          logger.warning("Error resolving " + temporal + ", using docDate=" + docDate);
        }
        if (grounded != temporal) {
          te.origTemporal = temporal;
          te.setTemporal(grounded);
        }
      } catch (Exception ex) {
        logger.log(Level.WARNING, "Error resolving " + temporal, ex);
      }
    }
  }

  private void resolveTimeExpressions(CoreMap annotation, List<TimeExpression> timeExpressions, SUTime.Time docDate)
  {
    for (TimeExpression te:timeExpressions) {
      resolveTimeExpression(annotation, te, docDate);
    }
  }

}
