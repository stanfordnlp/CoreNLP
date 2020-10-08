package edu.stanford.nlp.time;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Extracts time expressions.
 *
 * @author Angel Chang
 */
@SuppressWarnings("unchecked")
public class TimeExpressionExtractorImpl implements TimeExpressionExtractor {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(TimeExpressionExtractorImpl.class);

  // Patterns for extracting time expressions
  private TimeExpressionPatterns timexPatterns;

  private CoreMapExpressionExtractor expressionExtractor;

  // Options
  private Options options;

  public TimeExpressionExtractorImpl() {
    init(new Options());
  }

  public TimeExpressionExtractorImpl(String name, Properties props) {
    init(name, props);
  }

  @Override
  public void init(String name, Properties props) {
    init(new Options(name, props));
  }

  @Override
  public void init(Options options) {
    this.options = options;
    // NumberNormalizer.setVerbose(options.verbose); // cdm 2016: Try omitting this: Don't we want to see errors?
    CoreMapExpressionExtractor.setVerbose(options.verbose);
    if (options.grammarFilename == null) {
      options.grammarFilename = Options.DEFAULT_GRAMMAR_FILES;
      logger.warning("Time rules file is not specified: using default rules at " + options.grammarFilename);
    }
    logger.info("Using following SUTime rules: "+options.grammarFilename);
    timexPatterns = new GenericTimeExpressionPatterns(options);
    this.expressionExtractor = timexPatterns.createExtractor();
  }

  @Override
  public List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, CoreMap docAnnotation) {
    SUTime.TimeIndex timeIndex; // initialized immediately below
    String docDate = null;
    if (docAnnotation != null) {
      timeIndex = docAnnotation.get(TimeExpression.TimeIndexAnnotation.class);
      if (timeIndex == null) {
        docAnnotation.set(TimeExpression.TimeIndexAnnotation.class, timeIndex = new SUTime.TimeIndex());
      }
      // default look for the sentence's forum post date
      // if it doesn't have one, back off to the document date
      if (annotation.get(CoreAnnotations.SectionDateAnnotation.class) != null) {
        docDate = annotation.get(CoreAnnotations.SectionDateAnnotation.class);
      } else {
        docDate = docAnnotation.get(CoreAnnotations.DocDateAnnotation.class);
      }
      if (docDate == null) {
        Calendar cal = docAnnotation.get(CoreAnnotations.CalendarAnnotation.class);
        if (cal == null) {
          if (options.verbose) {
            logger.warn("WARNING: No document date specified");
          }
        } else {
          SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd:hh:mm:ss");
          docDate = dateFormat.format(cal.getTime());
        }
      }
    } else {
      timeIndex = new SUTime.TimeIndex();
    }
    if (StringUtils.isNullOrEmpty(docDate)) {
      docDate = null;
    }
    if (timeIndex.docDate == null && docDate != null) {
      try {
        // TODO: have more robust parsing of document date?  docDate may not have century....
        // TODO: if docDate didn't change, we can cache the parsing of the docDate and not repeat it for every sentence
        timeIndex.docDate = SUTime.parseDateTime(docDate,true);
      } catch (Exception e) {
        throw new RuntimeException("Could not parse date string: [" + docDate + "]", e);
      }
    }
    String sectionDate = annotation.get(CoreAnnotations.SectionDateAnnotation.class);
    String refDate = (sectionDate != null) ? sectionDate: docDate;
    return extractTimeExpressionCoreMaps(annotation, refDate, timeIndex);
  }

  @Override
  public List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate) {
    SUTime.TimeIndex timeIndex = new SUTime.TimeIndex();
    return extractTimeExpressionCoreMaps(annotation, docDate, timeIndex);
  }

  public List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate, SUTime.TimeIndex timeIndex) {
    List<TimeExpression> timeExpressions = extractTimeExpressions(annotation, docDate, timeIndex);
    return toCoreMaps(annotation, timeExpressions, timeIndex);
  }

  @Override
  public void finalize(CoreMap docAnnotation) {
    docAnnotation.remove(TimeExpression.TimeIndexAnnotation.class);
  }

  private List<CoreMap> toCoreMaps(CoreMap annotation, List<TimeExpression> timeExpressions, SUTime.TimeIndex timeIndex)
  {
    if (timeExpressions == null) return null;
    List<CoreMap> coreMaps = new ArrayList<>(timeExpressions.size());
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
          if (options.verbose) {
            logger.warn("Failed to get attributes from " + text + ", timeIndex " + timeIndex);
            logger.warn(e);
          }
          continue;
        }
        Timex timex;
        try {
          timex = Timex.fromMap(text, timexAttributes);
        } catch (Exception e) {
          if (options.verbose) {
            logger.warn("Failed to process timex " + text + " with attributes " + timexAttributes);
            logger.warn(e);
          }
          continue;
        }
        assert timex != null;  // Timex.fromMap never returns null and if it exceptions, we've already done a continue
        cm.set(TimeAnnotations.TimexAnnotation.class, timex);
        coreMaps.add(cm);
      }
    }
    return coreMaps;
  }

  public List<TimeExpression> extractTimeExpressions(CoreMap annotation, String refDateStr, SUTime.TimeIndex timeIndex) {
    SUTime.Time refDate = null;
    if (refDateStr != null) {
      try {
        // TODO: have more robust parsing of document date?  docDate may not have century....
        // TODO: if docDate didn't change, we can cache the parsing of the docDate and not repeat it for every sentence
        refDate = SUTime.parseDateTime(refDateStr,true);
      } catch (Exception e) {
        throw new RuntimeException("Could not parse date string: [" + refDateStr + "]", e);
      }
    }
    return extractTimeExpressions(annotation, refDate, timeIndex);
  }

  public List<TimeExpression> extractTimeExpressions(CoreMap annotation, SUTime.Time refDate, SUTime.TimeIndex timeIndex) {
    if (!annotation.containsKey(CoreAnnotations.NumerizedTokensAnnotation.class)) {
      try {
        List<CoreMap> mergedNumbers = NumberNormalizer.findAndMergeNumbers(annotation);
        annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbers);
      } catch (NumberFormatException e) {
        logger.warn("Caught bad number: " + e.getMessage());
        annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, new ArrayList<>());
      }
    }

    List<? extends MatchedExpression> matchedExpressions = expressionExtractor.extractExpressions(annotation);
    List<TimeExpression> timeExpressions = new ArrayList<>(matchedExpressions.size());
    for (MatchedExpression expr : matchedExpressions) {
      // Make sure we have the correct type (instead of just MatchedExpression)
      //timeExpressions.add(TimeExpression.TimeExpressionConverter.apply(expr));

      // TODO: Fix the extraction pipeline so it creates TimeExpression instead of MatchedExpressions
      // For now, grab the time expression from the annotation (this is good, so we don't have duplicate copies)
      TimeExpression annoTe = expr.getAnnotation().get( TimeExpression.Annotation.class );
      if (annoTe != null) {
        timeExpressions.add(annoTe);
      }
    }
    // We cache the document date in the timeIndex
    if (timeIndex.docDate == null) {
      if (refDate != null) timeIndex.docDate = refDate;
      else if (options.searchForDocDate) {
        // there was no document date but option was set to look for document date
        timeIndex.docDate = findReferenceDate(timeExpressions);
      }
    }
    // Didn't have a reference date - try using cached doc date
    if (refDate == null) refDate = timeIndex.docDate;

    // Some resolving is done even if refDate null...
    resolveTimeExpressions(annotation, timeExpressions, refDate);

    if (options.restrictToTimex3) {
      // Keep only TIMEX3 compatible timeExpressions
      List<TimeExpression> kept = new ArrayList<>(timeExpressions.size());
      for (TimeExpression te:timeExpressions) {
        if (te.getTemporal() != null && te.getTemporal().getTimexValue() != null) {
          kept.add(te);
        } else {
          List<? extends CoreMap> children = te.getAnnotation().get(TimeExpression.ChildrenAnnotation.class);
          if (children != null) {
            for (CoreMap child:children) {
              TimeExpression childTe = child.get(TimeExpression.Annotation.class);
              if (childTe != null) {
                resolveTimeExpression(annotation, childTe, refDate);
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
      List<TimeExpression> nestedTimeExpressions = new ArrayList<>();
      for (TimeExpression te : timeExpressions) {
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
      resolveTimeExpressions(annotation, nestedTimeExpressions, refDate);
      timeExpressions.addAll(nestedTimeExpressions);
    }
    Collections.sort(timeExpressions, MatchedExpression.EXPR_TOKEN_OFFSETS_NESTED_FIRST_COMPARATOR);
    // Some resolving is done even if refDate null...
    resolveTimeExpressions(annotation, timeExpressions, refDate);
    return timeExpressions;
  }

  private void resolveTimeExpression(CoreMap annotation, TimeExpression te, SUTime.Time docDate) {
    SUTime.Temporal temporal = te.getTemporal();
    if (temporal != null) {
      // TODO: use correct time for anchor
      try {
        int flags = timexPatterns.determineRelFlags(annotation, te);
        //int flags = 0;
        SUTime.Temporal grounded = temporal.resolve(docDate, flags);
        if (grounded == null) {
          logger.debug("Error resolving " + temporal + ", using docDate=" + docDate);
        }
        if (grounded != temporal) {
          te.origTemporal = temporal;
          te.setTemporal(grounded);
        }
      } catch (Exception ex) {
        if (options.verbose) {
          logger.warn("Error resolving " + temporal, ex);
          logger.warn(ex);
        }
      }
    }
  }

  private void resolveTimeExpressions(CoreMap annotation, List<TimeExpression> timeExpressions, SUTime.Time docDate) {
    for (TimeExpression te:timeExpressions) {
      resolveTimeExpression(annotation, te, docDate);
    }
  }

  private static SUTime.Time findReferenceDate(List<TimeExpression> timeExpressions) {
    // Find first full date in this annotation with year, month, and day
    for (TimeExpression te:timeExpressions) {
      SUTime.Temporal t = te.getTemporal();
      if (t instanceof SUTime.Time) {
        if (t.isGrounded()) {
          return t.getTime();
        } else if (t instanceof SUTime.PartialTime) {
          if (JodaTimeUtils.hasYYYYMMDD(t.getTime().getJodaTimePartial())) {
            return t.getTime();
          } else if (JodaTimeUtils.hasYYMMDD(t.getTime().getJodaTimePartial())) {
            return t.getTime().resolve(SUTime.getCurrentTime()).getTime();
          }
        }
      }
    }
    return null;
  }

}
