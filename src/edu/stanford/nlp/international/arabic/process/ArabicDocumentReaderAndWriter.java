package edu.stanford.nlp.international.arabic.process;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

/**
 * Reads newline delimited UTF-8 Arabic sentences with or without
 * gold segmentation markers. When segmentation markers are present,
 * this class may be used for 
 *
 * @author Spence Green
 */
public class ArabicDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 6730676681967976015L;

  private final IteratorFromReaderFactory<List<CoreLabel>> factory;
  
  private final TokenizerFactory<CoreLabel> tf;

  // The segmentation marker used in the ATBv3 training data.
  private static final Character DEFAULT_SEG_MARKER = '-';
  
  private final Character segMarker;

  // TODO(spenceg): Make this configurable.
  private final String tagDelimiter = "|||";
  
  private final boolean inputHasTags;
    
  /**
   * 
   * @param hasSegMarkers if true, input has segmentation markers
   */
  public ArabicDocumentReaderAndWriter(boolean hasSegMarkers) {
    this(hasSegMarkers, null);
  }
  
  /**
   * 
   * @param hasSegMarkers if true, input has segmentation markers
   * @param tokFactory a TokenizerFactory for the input
   */
  public ArabicDocumentReaderAndWriter(boolean hasSegMarkers, TokenizerFactory<CoreLabel> tokFactory) {
    this(hasSegMarkers, false, tokFactory);
  }
  
  /**
   * 
   * @param hasSegMarkers if true, input has segmentation markers
   * @param hasTags if true, input has morphological analyses separated by tagDelimiter.
   * @param tokFactory a TokenizerFactory for the input
   */
  public ArabicDocumentReaderAndWriter(boolean hasSegMarkers, 
                                       boolean hasTags, 
                                       TokenizerFactory<CoreLabel> tokFactory) {
    tf = tokFactory;
    inputHasTags = hasTags;
    segMarker = hasSegMarkers ? DEFAULT_SEG_MARKER : null;
    factory = LineIterator.getFactory(new SerializableFunction<String, List<CoreLabel>>() {
      private static final long serialVersionUID = 5243251505653686497L;
      public List<CoreLabel> apply(String in) {
        if (inputHasTags) {
          String[] toks = in.split("\\s+");
          List<CoreLabel> input = new ArrayList<CoreLabel>(toks.length);
          final String delim = Pattern.quote(tagDelimiter);
          for (String wordTag : toks) {
            String[] wordTagPair = wordTag.split(delim);
            assert wordTagPair.length == 2;
            CoreLabel cl = new CoreLabel();
            String word = wordTagPair[0];
            if (tf != null) {
              List<CoreLabel> lexList = tf.getTokenizer(new StringReader(word)).tokenize();
              if (lexList.size() == 0) { 
                continue;
              } else if (lexList.size() > 1) {
                System.err.printf("%s: Raw token generates multiple segments: %s%n", this.getClass().getName(), word);
              }
              word = lexList.get(0).value();
            }
            cl.setValue(word);
            cl.setWord(word);
            cl.setTag(wordTagPair[1]);
            input.add(cl);
          }
          return IOBUtils.StringToIOB(input, segMarker, true);
        
        } else if (tf == null) {
          return IOBUtils.StringToIOB(in, segMarker);
        
        } else {
          List<CoreLabel> line = tf.getTokenizer(new StringReader(in)).tokenize();
          return IOBUtils.StringToIOB(line, segMarker, false);
        }
      }
    });    
  }

  /**
   * Required, but unused.
   */
  public void init(SeqClassifierFlags flags) {}

  /**
   * Iterate over an input document.
   */
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    pw.println("Answer\tGoldAnswer\tCharacter");
    for(CoreLabel word : doc) {
      pw.printf("%s\t%s\t%s%n", word.get(AnswerAnnotation.class), 
                                word.get(GoldAnswerAnnotation.class),
                                word.get(CharAnnotation.class));
    }
  }
}
