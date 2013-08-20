package edu.stanford.nlp.sequences;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UnknownAnnotation;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import edu.stanford.nlp.process.WordToSentenceProcessor;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jenny Finkel
 */
public class TrueCasingDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 1731527027473052481L;


  public void init(SeqClassifierFlags flags) {}

  private static final Pattern sgml = Pattern.compile("<[^>]*>");
  private static final Pattern allLower = Pattern.compile("[^A-Z]*?[a-z]+[^A-Z]*?");
  private static final Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");
  private static final Pattern startUpper = Pattern.compile("[A-Z].*");

  private static WordToSentenceProcessor<CoreLabel> wts = new WordToSentenceProcessor<CoreLabel>();

  public static Set<String> knownWords; // = null;

  public static boolean known(String s) {
    return knownWords.contains(s.toLowerCase());
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {

    List<List<CoreLabel>> documents = new ArrayList<List<CoreLabel>>();

    String s = IOUtils.slurpReader(r);

    Set<String> wordsSeenOnce = new HashSet<String>();
    Set<String> wordsSeenMultiple = new HashSet<String>();

    XMLBeginEndIterator xmlIter = new XMLBeginEndIterator(new StringReader(s), "TEXT");
    while (xmlIter.hasNext()) {
      PTBTokenizer<CoreLabel> ptb = PTBTokenizer.newPTBTokenizer(new StringReader((String)xmlIter.next()), false, true);

      List<CoreLabel> document = new ArrayList<CoreLabel>();
      Set<String> words = new HashSet<String>();

      while (ptb.hasNext()) {
        CoreLabel w = ptb.next();
        words.add(w.word().toLowerCase());
        Matcher m = sgml.matcher(w.word());
        if (m.matches()) {
          if (document.size() > 0) {
            documents.addAll(wts.process(document));
            document = new ArrayList<CoreLabel>();
          }
          continue;
        }
        document.add(w);
      }
      if (document.size() > 0) {
        documents.addAll(wts.process(document));
      }

      for (String word : words) {
        if (wordsSeenMultiple.contains(word)) {
          /* continue */
        } else if (wordsSeenOnce.contains(word)) {
          wordsSeenOnce.remove(word);
          wordsSeenMultiple.add(word);
        } else {
          wordsSeenOnce.add(word);
        }
      }

    }

    xmlIter = new XMLBeginEndIterator(new StringReader(s), "TXT");
    while (xmlIter.hasNext()) {
      PTBTokenizer<CoreLabel> ptb = PTBTokenizer.newPTBTokenizer(new StringReader((String)xmlIter.next()), false, true);

      List<CoreLabel> document = new ArrayList<CoreLabel>();
      Set<String> words = new HashSet<String>();

      while (ptb.hasNext()) {
        CoreLabel w = ptb.next();
        words.add(w.word().toLowerCase());
        Matcher m = sgml.matcher(w.word());
        if (m.matches()) {
          if (document.size() > 0) {
            documents.addAll(wts.process(document));
            document = new ArrayList<CoreLabel>();
          }
          continue;
        }
        document.add(w);
      }
      if (document.size() > 0) {
        documents.addAll(wts.process(document));
      }

      for (String word : words) {
        if (wordsSeenMultiple.contains(word)) {
          /* continue */
        } else if (wordsSeenOnce.contains(word)) {
          wordsSeenOnce.remove(word);
          wordsSeenMultiple.add(word);
        } else {
          wordsSeenOnce.add(word);
        }
      }

    }

    knownWords = wordsSeenMultiple;
    knownWords.addAll(wordsSeenOnce);
    wordsSeenMultiple = null;

    List<List<CoreLabel>> docs = new ArrayList<List<CoreLabel>>();

    for (List<CoreLabel> document : documents) {
      System.err.println(document);
      List<CoreLabel> doc = new ArrayList<CoreLabel>();
      int pos = 0;
      for (CoreLabel w : document) {
        CoreLabel wi = new CoreLabel();

        Matcher lowerMatcher = allLower.matcher(w.word());

        if (lowerMatcher.matches()) {
          wi.set(AnswerAnnotation.class, "LOWER");
        } else {
          Matcher upperMatcher = allUpper.matcher(w.word());
          if (upperMatcher.matches()) {
            wi.set(AnswerAnnotation.class, "UPPER");
          } else {
            Matcher startUpperMatcher = startUpper.matcher(w.word());
            if (startUpperMatcher.matches()) {
              wi.set(AnswerAnnotation.class, "INIT_UPPER");
            } else {
              wi.set(AnswerAnnotation.class, "O");
            }
          }
        }

        wi.setWord(w.word().toLowerCase());
        wi.set(UnknownAnnotation.class, (wordsSeenOnce.contains(w.word().toLowerCase()) ? "true" : "false"));
        wi.set(PositionAnnotation.class, Integer.toString(pos));
        if (wi.get(UnknownAnnotation.class).equals("true")) {
          System.err.println(wi.word()+" :: "+wi.get(UnknownAnnotation.class)+" :: "+wi.get(PositionAnnotation.class));
        }
        doc.add(wi);
        pos++;
      }
      System.err.println();
      docs.add(doc);
    }
    return docs.iterator();
  }


  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {

      // cdm note: jan 2009: This used to pad with the {Prev,After}SGMLAnnotation
      // but I think this was just wrong, and it should have been the regular
      // whitespace annotation. I changed it to that while removing SGML
      String prev = wi.get(BeforeAnnotation.class);
      out.print(prev);

      String w = wi.word();
      if (wi.get(AnswerAnnotation.class).equals("UPPER")) {
        out.print(w.toUpperCase());
      } else if (wi.get(AnswerAnnotation.class).equals("LOWER")) {
        out.print(w.toLowerCase());
      } else if (wi.get(AnswerAnnotation.class).equals("INIT_UPPER")) {
        out.print(w.substring(0,1).toUpperCase());
        out.print(w.substring(1));
      } else {
        out.print(w);
      }
      String after = wi.get(AfterAnnotation.class);
      out.print(after);
    }
    out.println();
  }

}
