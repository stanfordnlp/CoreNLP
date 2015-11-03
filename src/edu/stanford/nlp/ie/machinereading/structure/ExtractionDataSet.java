package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 
 * @author Andrey Gusev
 * @author Mason Smith
 * @author Mihai
 * 
 */
public class ExtractionDataSet implements Serializable {

  private static final long serialVersionUID = 201150461234284548L;

  private final List<ExtractionSentence> sentences;

  public ExtractionDataSet() {
    sentences = new ArrayList<>();
  }
  
  /**
   * Copy c'tor that performs deep copy of the sentences in the original dataset
   */
  public ExtractionDataSet(ExtractionDataSet original) {
    sentences = new ArrayList<>();
    for(ExtractionSentence sent: original.getSentences()){
      // deep copy of the sentence: we create new entity/relation/event lists here
      // however, we do not deep copy the ExtractionObjects themselves!
      ExtractionSentence sentCopy = new ExtractionSentence(sent);
      sentences.add(sentCopy);
    }
  }

  public ExtractionSentence getSentence(int i) { return sentences.get(i); } 
  
  public int sentenceCount() { return sentences.size(); }

  public void addSentence(ExtractionSentence sentence) {
    this.sentences.add(sentence);
  }
  
  public void addSentences(List<ExtractionSentence> sentences) {
    for(ExtractionSentence sent: sentences){
      addSentence(sent);
    }
  }

  public List<ExtractionSentence> getSentences() {
    return Collections.unmodifiableList(this.sentences);
  }

  public void shuffle() {
    // we use a constant seed for replicability of experiments
    Collections.shuffle(sentences, new Random(0));
  }
  
  /*
  public List<List<CoreLabel>> toCoreLabels(Set<String> annotationsToSkip, boolean useSubTypes) {
    List<List<CoreLabel>> retVal = new ArrayList<List<CoreLabel>>();

    for (ExtractionSentence sentence : sentences) {
      List<CoreLabel> labeledSentence = sentence.toCoreLabels(true, annotationsToSkip, useSubTypes);

      if (labeledSentence != null) {
        // here we accumulate all sentences (we split into training and test set
        // if and when doing cross validation)
        retVal.add(labeledSentence);
      }
    }

    return retVal;
  }  
  */
}
