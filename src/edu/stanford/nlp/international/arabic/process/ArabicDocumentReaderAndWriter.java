package edu.stanford.nlp.international.arabic.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.process.TokenizerFactory;
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

  private static final long serialVersionUID = 3667837672769424178L;

  private final IteratorFromReaderFactory<List<CoreLabel>> factory;

  private final TokenizerFactory<CoreLabel> tf;

  // The segmentation marker used in the ATBv3 training data.
  private static final Character DEFAULT_SEG_MARKER = '-';

  private final Character segMarker;

  // TODO(spenceg): Make this configurable.
  private static final String tagDelimiter = "|||";
  private static final String rewriteDelimiter = ">>>";

  private final boolean inputHasTags;
  private final boolean inputHasDomainLabels;
  private final String inputDomain;
  private final boolean shouldStripRewrites;

  public static class RewrittenArabicAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

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
    this(hasSegMarkers, hasTags, false, "123", tokFactory);
  }
  
  /**
   *
   * @param hasSegMarkers if true, input has segmentation markers
   * @param hasTags if true, input has morphological analyses separated by tagDelimiter.
   * @param hasDomainLabels if true, input has a whitespace-terminated domain at the beginning
   *     of each line of text
   * @param tokFactory a TokenizerFactory for the input
   */
  public ArabicDocumentReaderAndWriter(boolean hasSegMarkers,
                                       boolean hasTags,
                                       boolean hasDomainLabels,
                                       String domain,
                                       TokenizerFactory<CoreLabel> tokFactory) {
    this(hasSegMarkers, hasTags, hasDomainLabels, domain, false, tokFactory);
  }
  
  /**
  *
  * @param hasSegMarkers if true, input has segmentation markers
  * @param hasTags if true, input has morphological analyses separated by tagDelimiter.
  * @param hasDomainLabels if true, input has a whitespace-terminated domain at the beginning
  *     of each line of text
  * @param stripRewrites if true, erase orthographical rewrites from the gold labels (for
  *     comparison purposes)
  * @param tokFactory a TokenizerFactory for the input
  */
  public ArabicDocumentReaderAndWriter(boolean hasSegMarkers,
      boolean hasTags,
      boolean hasDomainLabels,
      String domain,
      boolean stripRewrites,
      TokenizerFactory<CoreLabel> tokFactory) {
    tf = tokFactory;
    inputHasTags = hasTags;
    inputHasDomainLabels = hasDomainLabels;
    inputDomain = domain;
    shouldStripRewrites = stripRewrites;
    segMarker = hasSegMarkers ? DEFAULT_SEG_MARKER : null;
    factory = LineIterator.getFactory(new SerializableFunction<String, List<CoreLabel>>() {
      private static final long serialVersionUID = 5243251505653686497L;
      public List<CoreLabel> apply(String in) {
        List<CoreLabel> tokenList;
        
        String lineDomain = "";
        if (inputHasDomainLabels) {
          String[] domainAndData = in.split("\\s+", 2);
          if (domainAndData.length < 2) {
            System.err.println("Missing domain label or text: ");
            System.err.println(in);
          } else {
            lineDomain = domainAndData[0];
            in = domainAndData[1];
          }
        } else {
          lineDomain = inputDomain;
        }

        if (inputHasTags) {
          String[] toks = in.split("\\s+");
          List<CoreLabel> input = new ArrayList<CoreLabel>(toks.length);
          final String tagDelim = Pattern.quote(tagDelimiter);
          final String rewDelim = Pattern.quote(rewriteDelimiter);
          for (String wordTag : toks) {
            String[] wordTagPair = wordTag.split(tagDelim);
            assert wordTagPair.length == 2;
            String[] rewritePair = wordTagPair[0].split(rewDelim);
            assert rewritePair.length == 1 || rewritePair.length == 2;
            String raw = rewritePair[0];
            String rewritten = raw;
            if (rewritePair.length == 2)
              rewritten = rewritePair[1];

            CoreLabel cl = new CoreLabel();
            if (tf != null) {
              List<CoreLabel> lexListRaw = tf.getTokenizer(new StringReader(raw)).tokenize();
              List<CoreLabel> lexListRewritten = tf.getTokenizer(new StringReader(rewritten)).tokenize();
              if (lexListRewritten.size() != lexListRaw.size()) {
                System.err.printf("%s: Different number of tokens in raw and rewritten: %s>>>%s%n", this.getClass().getName(), raw, rewritten);
                lexListRewritten = lexListRaw;

              }
              if (lexListRaw.size() == 0) {
                continue;
              
              } else if (lexListRaw.size() == 1) {
                raw = lexListRaw.get(0).value();
                rewritten = lexListRewritten.get(0).value();
              
              } else if (lexListRaw.size() > 1) {
                String secondWord = lexListRaw.get(1).value();
                if (secondWord.equals(String.valueOf(segMarker))) {
                  // Special case for the null marker in the vocalized section
                  raw = lexListRaw.get(0).value() + segMarker;
                  rewritten = lexListRewritten.get(0).value() + segMarker;
                } else {
                  System.err.printf("%s: Raw token generates multiple segments: %s%n", this.getClass().getName(), raw);
                  raw = lexListRaw.get(0).value();
                  rewritten = lexListRewritten.get(0).value();
                }
              }
            }
            cl.setValue(raw);
            cl.setWord(raw);
            cl.setTag(wordTagPair[1]);
            cl.set(CoreAnnotations.DomainAnnotation.class, lineDomain);
            cl.set(RewrittenArabicAnnotation.class, rewritten);
            input.add(cl);
          }
          tokenList = IOBUtils.StringToIOB(input, segMarker, true, shouldStripRewrites);

        } else if (tf == null) {
          tokenList = IOBUtils.StringToIOB(in, segMarker);

        } else {
          List<CoreLabel> line = tf.getTokenizer(new StringReader(in)).tokenize();
          tokenList = IOBUtils.StringToIOB(line, segMarker, false);
        }
        
        if (inputHasDomainLabels && !inputHasTags)
          IOBUtils.labelDomain(tokenList, lineDomain);
        else if (!inputHasDomainLabels)
          IOBUtils.labelDomain(tokenList, inputDomain);
        return tokenList;
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
      pw.printf("%s\t%s\t%s%n", word.get(CoreAnnotations.AnswerAnnotation.class),
                                word.get(CoreAnnotations.GoldAnswerAnnotation.class),
                                word.get(CoreAnnotations.CharAnnotation.class));
    }
  }
  
  /**
   * For debugging.
   * 
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.printf("Usage: java %s file > output%n", ArabicDocumentReaderAndWriter.class.getName());
      System.exit(-1);
    }
    String fileName = args[0];
    TokenizerFactory<CoreLabel> tokFactory = ArabicTokenizer.atbFactory();
    String atbVocOptions = "removeProMarker,removeMorphMarker";
    tokFactory.setOptions(atbVocOptions);
    
    BufferedReader reader = IOUtils.readerFromString(fileName);
    for (String line; (line = reader.readLine()) != null; ) {
      String[] toks = line.split("\\s+");
      final String delim = Pattern.quote(tagDelimiter);
      boolean isStart = true;
      for (String wordTag : toks) {
        String[] wordTagPair = wordTag.split(delim);
        assert wordTagPair.length == 2;
        String word = wordTagPair[0];
        if (tokFactory != null) {
          List<CoreLabel> lexList = tokFactory.getTokenizer(new StringReader(word)).tokenize();
          if (lexList.size() == 0) {
            continue;
          
          } else if (lexList.size() == 1) {
            word = lexList.get(0).value();
          
          } else if (lexList.size() > 1) {
            String secondWord = lexList.get(1).value();
            if (secondWord.equals(String.valueOf(DEFAULT_SEG_MARKER))) {
              // Special case for the null marker in the vocalized section
              word = lexList.get(0).value() + String.valueOf(DEFAULT_SEG_MARKER);
            } else {
              System.err.printf("%s: Raw token generates multiple segments: %s%n", ArabicDocumentReaderAndWriter.class.getName(), word);
              word = lexList.get(0).value();
            }
          }
        }
        if ( ! isStart ) System.out.print(" ");
        System.out.print(word);
        isStart = false;
      }
      System.out.println();
    }
   
//    DocumentReaderAndWriter<CoreLabel> docReader = new ArabicDocumentReaderAndWriter(true,
//        true,
//        false,
//        tokFactory);
//    Iterator<List<CoreLabel>> itr = docReader.getIterator(new InputStreamReader(new FileInputStream(new File(fileName))));
//    while(itr.hasNext()) {
//      List<CoreLabel> line = itr.next();
//      System.out.println(Sentence.listToString(line));
//    }
  }
}
