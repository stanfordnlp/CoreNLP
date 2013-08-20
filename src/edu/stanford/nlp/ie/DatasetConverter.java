package edu.stanford.nlp.ie;

import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.objectbank.XMLTaggedDocumentIteratorFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.ling.TaggedWord;

import java.util.List;
import java.util.Iterator;
import java.io.*;

/**
 * @author grenager
 *         Date: Jan 3, 2005
 */
public class DatasetConverter {
  /**
   * Converts XML datasets to column style datasets for IE/NER.
   */
  public static void main(String[] args) throws IOException {
    String infile = args[0];
    String outfile = args[1];
    System.err.print("Loading data from " + infile + "...");
    BufferedReader in = new BufferedReader(new FileReader(infile));
    PTBTokenizer.PTBTokenizerFactory tokenizerFactory = PTBTokenizer.PTBTokenizerFactory.newPTBTokenizerFactory(false);
    XMLTaggedDocumentIteratorFactory docFact = new XMLTaggedDocumentIteratorFactory("doc", false, tokenizerFactory, "O");
    ObjectBank ob = new ObjectBank(new ReaderIteratorFactory(in), docFact);
    PrintWriter out = new PrintWriter(new FileOutputStream(outfile));
    int numDocs = 0;
    for (Iterator docIter = ob.iterator(); docIter.hasNext(); ) {
      List doc = (List) docIter.next();
      for (Iterator iterator = doc.iterator(); iterator.hasNext();) {
        TaggedWord tw = (TaggedWord) iterator.next();
        out.println(tw.word() + "\t" + tw.tag());
      }
      out.println();
      numDocs++;
    }
    in.close();
    out.flush();
    out.close();
    System.err.println("done. Got " + numDocs + " documents.");
  }

}
