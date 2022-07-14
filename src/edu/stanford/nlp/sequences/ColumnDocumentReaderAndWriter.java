package edu.stanford.nlp.sequences;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.StringUtils;
import java.util.function.Function;


/**
 * DocumentReader for column format.
 *
 * @author Jenny Finkel
 */
public class ColumnDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ColumnDocumentReaderAndWriter.class);

  private static final long serialVersionUID = 3806263423697973704L;
  private static final boolean includeProbabilities = false;

//  private SeqClassifierFlags flags; // = null;
  //map can be something like "word=0,tag=1,answer=2"
  @SuppressWarnings("rawtypes")
  private Class[] map; // = null;
  private int wordColumn = -1;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;

//  public void init(SeqClassifierFlags flags) {
//    this.flags = flags;
//    this.map = StringUtils.mapStringToArray(flags.map);
//    factory = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new ColumnDocParser());
//  }

  @Override
  public void init(SeqClassifierFlags flags) {
    init(flags.map);
  }


  public void init(String map) {
    // this.flags = null;
    this.map = CoreLabel.parseStringKeys(StringUtils.mapStringToArray(map));
    this.wordColumn = ArrayUtils.indexOf(this.map, CoreAnnotations.TextAnnotation.class);
    factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new ColumnDocParser());
  }

  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  // private int num; // = 0;


  private class ColumnDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266332661459630572L;
    private final Pattern whitePattern = Pattern.compile("\\s+"); // should this really only do a tab?

    private int lineCount; // = 0;

    @Override
    public List<CoreLabel> apply(String doc) {
      // if (num > 0 && num % 1000 == 0) { log.info("["+num+"]"); } // cdm: Not so useful to do in new logging world
      // num++;

      List<CoreLabel> words = new ArrayList<>();
      String[] lines = doc.split("\n");

      for (String line : lines) {
        ++lineCount;
        if (line.trim().isEmpty()) {
          continue;
        }
        // Optimistic splitting on tabs first. If that doesn't work, use any whitespace (slower, because of regexps).
        String[] info = line.split("\t");
        if (info.length == 1) {
          info = whitePattern.split(line);
        }
        // Trimming later rather than splitting on all whitespace
        // gives us the possibility of tokens with whitespace in them
        // although obviously not at the start or end...
        // doesn't slow the classifier down too much
        if (wordColumn >= 0) {
          info[wordColumn] = info[wordColumn].trim();
        }
        CoreLabel wi;
        try {
          wi = new CoreLabel(map, info);
          // Since the map normally only specified answer, we copy it to GoldAnswer unless they've put something else there!
          if ( ! wi.containsKey(CoreAnnotations.GoldAnswerAnnotation.class) && wi.containsKey(CoreAnnotations.AnswerAnnotation.class)) {
            wi.set(CoreAnnotations.GoldAnswerAnnotation.class, wi.get(CoreAnnotations.AnswerAnnotation.class));
          }
        } catch (RuntimeException e) {
          log.info("Error on line " + lineCount + ": " + line);
          throw e;
        }
        words.add(wi);
      }
      return words;
    }

  } // end class ColumnDocParser


  @Override
  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String goldAnswer = wi.get(CoreAnnotations.GoldAnswerAnnotation.class);
      if (includeProbabilities) {
        double answerProb = wi.get(CoreAnnotations.AnswerProbAnnotation.class);
        out.println(wi.word() + '\t' + goldAnswer + '\t' + answer + '\t' + answerProb);
      } else {
        out.println(wi.word() + '\t' + goldAnswer + '\t' + answer);
      }
    }
    out.println();
  }

}
