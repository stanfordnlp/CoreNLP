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

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EntityClassAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ParaPositionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SectionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.WordPositionAnnotation;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * DocumentReader for MUC format.
 * @author Jenny Finkel
 */
public class MUCDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  /**
   *
   */
  private static final long serialVersionUID = -8334720781758500037L;
  private SeqClassifierFlags flags;
  private IteratorFromReaderFactory factory;

  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    factory = XMLBeginEndIterator.getFactory("DOC", new MUCDocumentParser(), true, true);
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  static class MUCDocumentParser implements Function<String, List<CoreLabel>> {

    private static Pattern sgml = Pattern.compile("<([^>\\s]*)[^>]*>");
    private static Pattern beginEntity = Pattern.compile("<(ENAMEX|TIMEX|NUMEX) TYPE=\"([a-z]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static Pattern endEntity = Pattern.compile("</(ENAMEX|TIMEX|NUMEX)>");

    public List<CoreLabel> apply(String doc) {

      if (doc == null) { return null; }

      String section = "";
      String entity = "O";
      String entityClass = "";
      int pNum = 0;
      int sNum = 0;
      int wNum = 0;


      PTBTokenizer ptb = PTBTokenizer.newPTBTokenizer(new BufferedReader(new StringReader(doc)), false, true);
      List<CoreLabel> words = ptb.tokenize();

      List<CoreLabel> result = new ArrayList();

      CoreLabel prev = null;
      String prevString = "";
      Matcher matcher;

      for (CoreLabel word : words) {
        matcher = sgml.matcher(word.word());
        if (matcher.matches()) {
          String tag = matcher.group(1);
          if (word.word().equalsIgnoreCase("<p>")) {
            pNum++;
            sNum = 0;
            wNum = 0;

            if (prev != null) {
              String s = prev.get(AfterAnnotation.class);
              s += word.originalText()+word.after();
              prev.set(AfterAnnotation.class, s);
            }
            prevString += word.before() + word.originalText();

          } else if (word.word().equalsIgnoreCase("<s>")) {
            sNum++;
            wNum = 0;

            if (prev != null) {
              String s = prev.get(AfterAnnotation.class);
              s += word.originalText()+word.after();
              prev.set(AfterAnnotation.class, s);
            }
            prevString += word.before() + word.originalText();

          } else {
            matcher = beginEntity.matcher(word.word());
            if (matcher.matches()) {
              entityClass = matcher.group(1);
              entity = matcher.group(2);
              if (prev != null) {
                String s = prev.get(AfterAnnotation.class);
                s += word.after();
                prev.set(AfterAnnotation.class, s);
              }
              prevString += word.before();
            } else {
              matcher = endEntity.matcher(word.word());
              if (matcher.matches()) {
                entityClass = "";
                entity = "O";
                if (prev != null) {
                  String s = prev.get(AfterAnnotation.class);
                  s += word.after();
                  prev.set(AfterAnnotation.class, s);
                }
                prevString += word.before();
              } else if (word.word().equalsIgnoreCase("<doc>")) {
                prevString += word.before() + word.originalText();
              } else if (word.word().equalsIgnoreCase("</doc>")) {
                String s = prev.get(AfterAnnotation.class);
                s += word.originalText();
                prev.set(AfterAnnotation.class, s);
              } else {
                section = tag.toUpperCase();
                if (prev != null) {
                  String s = prev.get(AfterAnnotation.class);
                  s += word.originalText() + word.after();
                  prev.set(AfterAnnotation.class, s);
                }
                prevString += word.before() + word.originalText();
              }
            }
          }
        } else {
          CoreLabel wi = new CoreLabel();
          wi.setWord(word.word());
          wi.set(OriginalTextAnnotation.class, word.originalText());
          wi.set(BeforeAnnotation.class, prevString+word.before());
          wi.set(AfterAnnotation.class, word.after());
          wi.set(WordPositionAnnotation.class, ""+wNum);
          wi.set(CoreAnnotations.SentencePositionAnnotation.class, ""+sNum);
          wi.set(ParaPositionAnnotation.class, ""+pNum);
          wi.set(SectionAnnotation.class, section);
          wi.set(AnswerAnnotation.class, entity);
          wi.set(EntityClassAnnotation.class, entityClass);
          wNum++;
          prevString = "";
          result.add(wi);
          prev = wi;
        }
      }

      //System.err.println(doc);
      //System.err.println(edu.stanford.nlp.util.StringUtils.join(result, "\n"));
      //System.exit(0);

      return result;
    }
  }

  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    String prevAnswer = "O";
    String prevClass = "";
    String afterLast = "";
    for (CoreLabel word : doc) {
      if (!prevAnswer.equals("O") && !prevAnswer.equals(word.get(AnswerAnnotation.class))) {
        pw.print("</"+prevClass+">");
        prevClass = "";
      }
      pw.print(word.get(BeforeAnnotation.class));
      if (!word.get(AnswerAnnotation.class).equals("O") && !word.get(AnswerAnnotation.class).equals(prevAnswer)) {
        if (word.get(AnswerAnnotation.class).equalsIgnoreCase("PERSON") ||
            word.get(AnswerAnnotation.class).equalsIgnoreCase("ORGANIZATION") ||
            word.get(AnswerAnnotation.class).equalsIgnoreCase("LOCATION")) {
          prevClass = "ENAMEX";
        } else if (word.get(AnswerAnnotation.class).equalsIgnoreCase("DATE") ||
                   word.get(AnswerAnnotation.class).equalsIgnoreCase("TIME")) {
          prevClass = "TIMEX";
        } else if (word.get(AnswerAnnotation.class).equalsIgnoreCase("PERCENT") ||
                   word.get(AnswerAnnotation.class).equalsIgnoreCase("MONEY")) {
          prevClass = "NUMEX";
        } else {
          System.err.println("unknown type: "+word.get(AnswerAnnotation.class));
          System.exit(0);
        }
        pw.print("<"+prevClass+" TYPE=\""+word.get(AnswerAnnotation.class)+"\">");
      }
      pw.print(word.get(OriginalTextAnnotation.class));
      afterLast = word.get(AfterAnnotation.class);
      prevAnswer = word.get(AnswerAnnotation.class);
    }
    if (!prevAnswer.equals("O")) {
      pw.print("</"+prevClass+">");
      prevClass = "";
    }
    pw.println(afterLast);
  }

}
