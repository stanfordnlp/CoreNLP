package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.HashIndex;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Created by jebolton on 7/14/17.
 */
public class PresetSequenceClassifier<IN extends CoreMap>  extends AbstractSequenceClassifier<IN> {

  public PresetSequenceClassifier(Properties props) {
    super(props);
    if (classIndex == null)
      classIndex = new HashIndex<>();
    // classIndex.add("O");
    classIndex.add(flags.backgroundSymbol);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void serializeClassifier(String serializePath) {

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void serializeClassifier(ObjectOutputStream oos) {

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void loadClassifier(ObjectInputStream ois, Properties props) {

  }

  @Override
  public List<IN> classify(List<IN> document) {
    for (IN token : document) {
      String presetAnswer = token.get(CoreAnnotations.PresetAnswerAnnotation.class);
      token.set(CoreAnnotations.AnswerAnnotation.class, presetAnswer);
    }
    return document;
  }

  @Override
  public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }

  /** {@inheritDoc} */
  @Override
  public void train(Collection<List<IN>> objectBankWrapper, DocumentReaderAndWriter<IN> readerAndWriter) {

  }



}
