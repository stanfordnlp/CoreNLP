package edu.stanford.nlp.sequences; 

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * DocumentReader for MUC format.
 *
 * @author Jenny Finkel
 */
public class MUCDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MUCDocumentReaderAndWriter.class);

  private static final long serialVersionUID = -8334720781758500037L;
  private SeqClassifierFlags flags;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;

  @Override
  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    factory = XMLBeginEndIterator.getFactory("DOC", new MUCDocumentParser(), true, true);
  }

  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  static class MUCDocumentParser implements Function<String, List<CoreLabel>> {

    private static final Pattern sgml = Pattern.compile("<([^>\\s]*)[^>]*>");
    private static final Pattern beginEntity = Pattern.compile("<(ENAMEX|TIMEX|NUMEX) TYPE=\"([a-z]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern endEntity = Pattern.compile("</(ENAMEX|TIMEX|NUMEX)>");

    @Override
    public List<CoreLabel> apply(String doc) {

      if (doc == null) { return null; }

      String section = "";
      String entity = "O";
      String entityClass = "";
      int pNum = 0;
      int sNum = 0;
      int wNum = 0;


      PTBTokenizer<CoreLabel> ptb = PTBTokenizer.newPTBTokenizer(new BufferedReader(new StringReader(doc)), false, true);
      List<CoreLabel> words = ptb.tokenize();

      List<CoreLabel> result = new ArrayList<>();

      CoreLabel prev = null;
      String prevString = "";

      for (CoreLabel word : words) {
        Matcher matcher = sgml.matcher(word.word());
        if (matcher.matches()) {
          String tag = matcher.group(1);
          if (word.word().equalsIgnoreCase("<p>")) {
            pNum++;
            sNum = 0;
            wNum = 0;

            if (prev != null) {
              String s = prev.get(CoreAnnotations.AfterAnnotation.class);
              s += word.originalText()+word.after();
              prev.set(CoreAnnotations.AfterAnnotation.class, s);
            }
            prevString += word.before() + word.originalText();

          } else if (word.word().equalsIgnoreCase("<s>")) {
            sNum++;
            wNum = 0;

            if (prev != null) {
              String s = prev.get(CoreAnnotations.AfterAnnotation.class);
              s += word.originalText()+word.after();
              prev.set(CoreAnnotations.AfterAnnotation.class, s);
            }
            prevString += word.before() + word.originalText();

          } else {
            matcher = beginEntity.matcher(word.word());
            if (matcher.matches()) {
              entityClass = matcher.group(1);
              entity = matcher.group(2);
              if (prev != null) {
                String s = prev.get(CoreAnnotations.AfterAnnotation.class);
                s += word.after();
                prev.set(CoreAnnotations.AfterAnnotation.class, s);
              }
              prevString += word.before();
            } else {
              matcher = endEntity.matcher(word.word());
              if (matcher.matches()) {
                entityClass = "";
                entity = "O";
                if (prev != null) {
                  String s = prev.get(CoreAnnotations.AfterAnnotation.class);
                  s += word.after();
                  prev.set(CoreAnnotations.AfterAnnotation.class, s);
                }
                prevString += word.before();
              } else if (word.word().equalsIgnoreCase("<doc>")) {
                prevString += word.before() + word.originalText();
              } else if (word.word().equalsIgnoreCase("</doc>")) {
                String s = prev.get(CoreAnnotations.AfterAnnotation.class);
                s += word.originalText();
                prev.set(CoreAnnotations.AfterAnnotation.class, s);
              } else {
                section = tag.toUpperCase();
                if (prev != null) {
                  String s = prev.get(CoreAnnotations.AfterAnnotation.class);
                  s += word.originalText() + word.after();
                  prev.set(CoreAnnotations.AfterAnnotation.class, s);
                }
                prevString += word.before() + word.originalText();
              }
            }
          }
        } else {
          CoreLabel wi = new CoreLabel();
          wi.setWord(word.word());
          wi.set(CoreAnnotations.OriginalTextAnnotation.class, word.originalText());
          wi.set(CoreAnnotations.BeforeAnnotation.class, prevString+word.before());
          wi.set(CoreAnnotations.AfterAnnotation.class, word.after());
          wi.set(CoreAnnotations.WordPositionAnnotation.class, ""+wNum);
          wi.set(CoreAnnotations.SentencePositionAnnotation.class, ""+sNum);
          wi.set(CoreAnnotations.ParaPositionAnnotation.class, ""+pNum);
          wi.set(CoreAnnotations.SectionAnnotation.class, section);
          wi.set(CoreAnnotations.AnswerAnnotation.class, entity);
          wi.set(CoreAnnotations.EntityClassAnnotation.class, entityClass);
          wNum++;
          prevString = "";
          result.add(wi);
          prev = wi;
        }
      }

      //log.info(doc);
      //log.info(edu.stanford.nlp.util.StringUtils.join(result, "\n"));
      //System.exit(0);

      return result;
    }
  }

  @Override
  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    String prevAnswer = "O";
    String prevClass = "";
    String afterLast = "";
    for (CoreLabel word : doc) {
      if (!prevAnswer.equals("O") && !prevAnswer.equals(word.get(CoreAnnotations.AnswerAnnotation.class))) {
        pw.print("</"+prevClass+">");
        prevClass = "";
      }
      pw.print(word.get(CoreAnnotations.BeforeAnnotation.class));
      if (!word.get(CoreAnnotations.AnswerAnnotation.class).equals("O") && !word.get(CoreAnnotations.AnswerAnnotation.class).equals(prevAnswer)) {
        if (word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("PERSON") ||
            word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("ORGANIZATION") ||
            word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("LOCATION")) {
          prevClass = "ENAMEX";
        } else if (word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("DATE") ||
                   word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("TIME")) {
          prevClass = "TIMEX";
        } else if (word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("PERCENT") ||
                   word.get(CoreAnnotations.AnswerAnnotation.class).equalsIgnoreCase("MONEY")) {
          prevClass = "NUMEX";
        } else {
          log.info("unknown type: "+word.get(CoreAnnotations.AnswerAnnotation.class));
          System.exit(0);
        }
        pw.print("<"+prevClass+" TYPE=\""+word.get(CoreAnnotations.AnswerAnnotation.class)+"\">");
      }
      pw.print(word.get(CoreAnnotations.OriginalTextAnnotation.class));
      afterLast = word.get(CoreAnnotations.AfterAnnotation.class);
      prevAnswer = word.get(CoreAnnotations.AnswerAnnotation.class);
    }
    if (!prevAnswer.equals("O")) {
      pw.print("</"+prevClass+">");
      prevClass = "";
    }
    pw.println(afterLast);
  }

}
