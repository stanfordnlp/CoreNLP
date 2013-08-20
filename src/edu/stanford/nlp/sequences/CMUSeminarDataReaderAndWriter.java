package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PredictedAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;

import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * This class is for reading in the CMU Seminars dataset.
 * In the {@link SeqClassifierFlags} flags given on
 * construction, for train and test files, you should actually specify
 * files which give the locations to the seminars files.
 *
 * @author Jenny Finkel
 */

public class CMUSeminarDataReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private SeqClassifierFlags flags = null;
  private SequenceClassifier predModel = null;

  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    if (flags.usePrediction) {
      try {
        predModel = SequenceClassifier.getClassifier(new String[]{"-prop", flags.predProp});
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return new CMUIterator(r);
  }

  private class CMUIterator extends AbstractIterator<List<CoreLabel>> {

    public CMUIterator (Reader r) {
      stringIter = splitIntoDocs(r);
    }

    @Override
    public boolean hasNext() { return stringIter.hasNext(); }
    @Override
    public List<CoreLabel> next() { return processDocument(stringIter.next()); }

    private Iterator<String> stringIter = null;

    private Iterator<String> splitIntoDocs(Reader r) {
      String[] files = IOUtils.slurpReader(r).split("\n");
      List<String> docs = Generics.newArrayList();
      for (String file : files) {
        docs.add(IOUtils.slurpFileNoExceptions(file+".tagged"));
      }
      return docs.iterator();
    }

    private Pattern columnPattern = Pattern.compile("([^\t\n]*\t[^\t\n]*\t[^\t\n]*\n)*");

    private SequenceClassifier predModel = null;

    public List<CoreLabel> processDocument(String doc) {

      //  Matcher columnM = columnPattern.matcher(doc);
      //if (      columnM.matches()) {
      if (true) {
        List<CoreLabel> words = new ArrayList<CoreLabel>();
        String[] lines = doc.split("\n");
        for (String line : lines) {
          String[] bits = line.split("\t");
          CoreLabel fl = new CoreLabel();
          fl.setWord(bits[0]);
          fl.setTag(bits[1]);
          fl.set(AnswerAnnotation.class, bits[2]);
          words.add(fl);
        }
        if (flags.usePrediction) {
          predModel.testSentence(words, PredictedAnswerAnnotation.class);
        }
        return words;
      }

      if (tagger == null) {
        try{
          tagger = new MaxentTagger(modelFile);
        } catch(Exception e){
          throw new RuntimeException(e.getMessage());
        }
      }

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      Pattern xmlP = Pattern.compile("<.*?>");
      Matcher xmlM = xmlP.matcher(doc);

      Pattern entityP = Pattern.compile("<(speaker|location|stime|etime)>");
      Pattern endEntityP = Pattern.compile("</(speaker|location|stime|etime)>");

      Pattern startSent = Pattern.compile("<sentence>", Pattern.CASE_INSENSITIVE);
      Pattern endSent = Pattern.compile("</sentence>", Pattern.CASE_INSENSITIVE);

      int loc = 0;
      String ans = flags.backgroundSymbol;

      //if we get rid of Sentence, which we should, then feel free
      // to change this to whatever the tagger returns instead.
      ArrayList<CoreLabel> posSentence = new ArrayList<CoreLabel>();
      while (true) {
        boolean found = xmlM.find();
        String prev;
        if (found) { prev = doc.substring(loc, xmlM.start()); }
        else { prev = doc.substring(loc); }

        PTBTokenizer ptb = PTBTokenizer.newPTBTokenizer(new BufferedReader(new StringReader(prev)));
        while (ptb.hasNext()) {
          String w = ptb.next().toString();
          CoreLabel fl = new CoreLabel();
          fl.set(TextAnnotation.class, w);
          fl.set(AnswerAnnotation.class, ans);
          posSentence.add(fl);
        }

        if (found) {
          String tag = xmlM.group(0);
          loc = xmlM.end();
          Matcher m = endEntityP.matcher(tag);
          if (m.matches()) {
            ans = flags.backgroundSymbol;
          } else {
            m = entityP.matcher(tag);
            if (m.matches()) {
              if (!ans.equals(flags.backgroundSymbol)) {
                throw new RuntimeException("Unterminated tag!\n\n"+doc);
              }
              ans = m.group(1);
            } else {
              m = startSent.matcher(tag);
              Matcher m1 = startSent.matcher(tag);
              if (m.matches() || m1.matches()) {
                if (posSentence.size() > 0) {
                  posTagAndAdd(posSentence, words);
                  posSentence = new ArrayList<CoreLabel>();
                }
              }
            }
          }
        } else {
          if (posSentence.size() > 0) {
            posTagAndAdd(posSentence, words);
          }
          break;
        }
      }

      if (!ans.equals(flags.backgroundSymbol)) {
        throw new RuntimeException("Unterminated tag!\n\n"+doc);
      }

      return words;

    }

    private static final String modelFile = "/u/nlp/data/pos-tagger/wsj3t0-18-bidirectional/bidirectional-wsj-0-18.tagger";

    private MaxentTagger tagger = null;

    private void posTagAndAdd(ArrayList<CoreLabel> sent, List<CoreLabel> words) {
      System.err.println("POS");
      ArrayList<TaggedWord> tagged = tagger.tagSentence(sent);
      List<CoreLabel> newSent = new ArrayList<CoreLabel>();
      for (int i = 0; i < sent.size(); i++) {
        String tag = tagged.get(i).tag();
        CoreLabel fl = sent.get(i);
        fl.set(PartOfSpeechAnnotation.class, tag);
        newSent.add(fl);
      }

      if (predModel != null) {
        predModel.testSentence(newSent, PredictedAnswerAnnotation.class);
        System.err.println(Sentence.listToString(newSent));
      }

      words.addAll(newSent);
    }
  }

  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel word : doc) {
      out.println(word.get(TextAnnotation.class)+"\t"+word.get(GoldAnswerAnnotation.class)+"\t"+word.get(AnswerAnnotation.class));
    }
    out.println();
    out.flush();
  }

  private static final long serialVersionUID = 755129769613443451L;

}
