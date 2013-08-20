package edu.stanford.nlp.sequences;

import edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class BBNReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 2772440315252998258L;

  SeqClassifierFlags flags = null;


  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
  }

  private int fileNum = 1;

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return new BBNIterator(r);
  }

  private class BBNIterator extends AbstractIterator<List<CoreLabel>> {

    public BBNIterator (Reader r) {
      stringIter = splitIntoDocs(r);
    }

    @Override
    public boolean hasNext() { return stringIter.hasNext(); }
    @Override
    public List<CoreLabel> next() { return processDocument(stringIter.next()); }

    private Iterator<String> stringIter = null;

    private Iterator<String> splitIntoDocs(Reader r) {
      System.err.println("reading in file "+(fileNum++));
      return new XMLBeginEndIterator<String>(r, "DOC", true);
    }

    private List<CoreLabel> processDocument(String doc) {

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      Pattern xmlP = Pattern.compile("<.*?>", Pattern.DOTALL);
      Matcher xmlM = xmlP.matcher(doc);

      Pattern typeP = Pattern.compile("<[A-Z]*?EX\\s+TYPE=\"(.*?)\">", Pattern.DOTALL);
      //Pattern typeP = Pattern.compile("<ENAMEX TYPE=\"(.*?)\">");
      Pattern endTypeP = Pattern.compile("</.*?>");

      int loc = 0;
      String ans = flags.backgroundSymbol;
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
          words.add(fl);
        }

        if (found) {
          String tag = xmlM.group(0);
          loc = xmlM.end();
          Matcher m = endTypeP.matcher(tag);
          if (m.matches()) {
            ans = flags.backgroundSymbol;
          } else {
            m = typeP.matcher(tag);
            if (m.matches()) {
              ans = m.group(1);
              if (ans.contains("DESC")) {
                ans = flags.backgroundSymbol;
              } else if (ans.indexOf(':') > 0) {
                ans = ans.substring(0, ans.indexOf(':'));
              }
            } else if (!tag.contains("DOCNO")) {
              System.err.println("tag: "+tag);
            }
          }
        } else {
          break;
        }
      }

      return words;
    }
  }


  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel word : doc) {
       out.println(word.get(TextAnnotation.class)+ '\t' +word.get(GoldAnswerAnnotation.class)+ '\t' +word.get(AnswerAnnotation.class));
    }
    out.println();
    out.flush();
  }


  public static void main(String[] args) {
    DocumentReaderAndWriter<CoreLabel> readerAndWriter = new BBNReaderAndWriter();
    readerAndWriter.init(new SeqClassifierFlags());
    ReaderIteratorFactory rif = new ReaderIteratorFactory(Arrays.asList(args));
    ObjectBank<List<CoreLabel>> ob = new ObjectBank<List<CoreLabel>>(rif, readerAndWriter);
    for (List<CoreLabel> doc : ob) {
      for (CoreLabel word : doc) {
        System.out.println(word.get(TextAnnotation.class)+ '\t' +word.get(AnswerAnnotation.class));
      }
      System.out.println();
    }
  }

}
