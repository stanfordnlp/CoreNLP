package edu.stanford.nlp.tagger.maxent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;

/** Provides a main method for running the POS tagger on NER
 *  column-formatted data.
 *
 *  @author Jenny Finkel
 */
public class POSTagNER {

  private POSTagNER() {}

  /** Usage: java POSTagNER file separator savedTaggerFilePrefix.
   *
   *  @param args Command-line arguments, as above
   *  @throws java.lang.Exception If something goes wrong
   */
  public static void main(String[] args) throws Exception {
    MaxentTagger tagger;
    if (args.length > 2) {
      tagger = new MaxentTagger(args[2]);
    } else {
      tagger = new MaxentTagger(MaxentTagger.DEFAULT_NLP_GROUP_MODEL_PATH);
    }

    String separator = args[1];
    if (separator.equals("\\t")) { separator = "\t"; }

    BufferedReader in = new BufferedReader(new FileReader(args[0]));
    String line;

    List<List<ArrayList<POS_NER_Word>>> documents = new ArrayList<List<ArrayList<POS_NER_Word>>>();
    List<ArrayList<POS_NER_Word>> sentences = new ArrayList<ArrayList<POS_NER_Word>>();
    ArrayList<POS_NER_Word> sentence = new ArrayList<POS_NER_Word>();

    int numCRs = 0;

    while ((line = in.readLine()) != null) {
      if (line.trim().length() == 0) {
        if ( ! sentence.isEmpty()) {
          sentences.add(sentence);
          sentence = new ArrayList<POS_NER_Word>();
        }
        documents.add(sentences);
        sentences = new ArrayList<ArrayList<POS_NER_Word>>();
        continue;
      }
      //System.err.println("|"+line+"|");
      int index = line.indexOf(separator);
      String word = line.substring(0,index);
      String ner = line.substring(index+1);
      if (word.equals("*CR*")) {
        numCRs++;
      } else {
        sentence.add(new POS_NER_Word(word, ner, numCRs));
        if (word.equals(".")) {
          sentences.add(sentence);
          sentence = new ArrayList<POS_NER_Word>();
        }
        numCRs = 0;
      }
    }

    if ( ! sentence.isEmpty()) {
      sentences.add(sentence);
    }
    if ( ! sentences.isEmpty()) {
      documents.add(sentences);
    }

    //List taggedDocuments = new ArrayList();
    //List taggedSentences = new ArrayList();

    for (List<ArrayList<POS_NER_Word>> sents : documents) {
      String prevTag = "O";
      for (ArrayList<POS_NER_Word> sent : sents) {
        ArrayList<TaggedWord> taggedSentence = tagger.tagSentence(sent);
        Iterator<TaggedWord> taggedWordIter = taggedSentence.iterator();
        for (POS_NER_Word word : sent) {
          TaggedWord taggedWord = taggedWordIter.next();
          if ( ! taggedWord.word().equals(word.word())) {
            throw new IllegalStateException("Problem: " + taggedWord.word() + " != " + word.word());
          }
          for (int i = 0; i < word.numCRs; i++) {
            String ner = word.ner;
            if ( ! ner.equals(prevTag)) {
              ner = "O";
            }
            System.out.println("*CR*" + separator + "*CR*" + separator + ner);
          }
          System.out.println(word.word() + separator + taggedWord.tag() + separator + word.ner);
          prevTag = word.ner;
        }
        //taggedSentences.add(taggedSentence);
      }
      System.out.println();
      //taggedDocuments.add(taggedSentences);
      //taggedSentences = new ArrayList();
    }
  }


  private static class POS_NER_Word extends TaggedWord {

    /**
     * 
     */
    private static final long serialVersionUID = 3560170253870179598L;
    String ner;
    int numCRs;

    public POS_NER_Word (String word, String ner, int numCRs) {
      super(word);
      this.ner = ner;
      this.numCRs = numCRs;
    }

  } // end class POS_NER_Word

}
