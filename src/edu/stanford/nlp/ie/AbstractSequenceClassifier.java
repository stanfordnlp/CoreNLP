// AbstractSequenceClassifier -- a framework for probabilistic sequence models.
// Copyright (c) 2002-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/CRF-NER.html

package edu.stanford.nlp.ie;

import edu.stanford.nlp.fsm.DFSA;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RegExFileFilter;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ResettableReaderIteratorFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.CoreTokenFactory;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Sampler;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.concurrent.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * This class provides common functionality for (probabilistic) sequence models.
 * It is a superclass of our CMM and CRF sequence classifiers, and is even used
 * in the (deterministic) NumberSequenceClassifier. See implementing classes for
 * more information.
 *
 * An implementation must implement these 5 abstract methods: <br>
 * {@code List<IN> classify(List<IN> document); } <br>
 * {@code List<IN> classifyWithGlobalInformation(List<IN> tokenSequence, final CoreMap document, final CoreMap sentence); } <br>
 * {@code void train(Collection<List<IN>> docs, DocumentReaderAndWriter<IN> readerAndWriter); } <br>
 * {@code void serializeClassifier(String serializePath); } <br>
 * {@code void loadClassifier(ObjectInputStream in, Properties props) throws IOException,
 * ClassCastException, ClassNotFoundException; } <br>
 * but a runtime (or rule-based) implementation can usefully implement just the first,
 * and throw UnsupportedOperationException for the rest. Additionally, this method throws
 * UnsupportedOperationException by default, but is implemented for some classifiers: <br>
 * {@code Pair<Counter<Integer>, TwoDimensionalCounter<Integer,String>> printProbsDocument(List<CoreLabel> document); } <br>
 *
 * @author Jenny Finkel
 * @author Dan Klein
 * @author Christopher Manning
 * @author Dan Cer
 * @author sonalg (made the class generic)
 */
public abstract class AbstractSequenceClassifier<IN extends CoreMap> implements Function<String, String>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(AbstractSequenceClassifier.class);

  public SeqClassifierFlags flags;
  public Index<String> classIndex; // = null;

  /** Support multiple feature factories (NERFeatureFactory, EmbeddingFeatureFactory) - Thang Sep 13, 2013. */
  public List<FeatureFactory<IN>> featureFactories;

  protected IN pad;
  private final CoreTokenFactory<IN> tokenFactory;
  public int windowSize;

  /** Different threads can add or query knownLCWords at the same time,
   *  so we need a concurrent data structure.  Created in reinit().
   */
  protected MaxSizeConcurrentHashSet<String> knownLCWords; // = null;

  /** This field can cache an allocated defaultReaderAndWriter. Never access this variable directly,
   *  as it is lazily allocated. Use the {@link #defaultReaderAndWriter()} method.
   */
  private DocumentReaderAndWriter<IN> defaultReaderAndWriter;

  /** This is the DocumentReaderAndWriter used for reading training and testing files.
   *  It is the DocumentReaderAndWriter specified by the readerAndWriter flag and
   *  defaults to {@code edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter} which
   *  is suitable for reading CoNLL-style TSV files.
   *
   *  @return The default DocumentReaderAndWriter
   */
  public synchronized DocumentReaderAndWriter<IN> defaultReaderAndWriter() {
    if (defaultReaderAndWriter == null) {
      defaultReaderAndWriter = makeReaderAndWriter();
    }
    return defaultReaderAndWriter;
  }

  /** This field can cache an allocated plainTextReaderAndWriter. Never access this variable directly,
   *  as it is lazily allocated. Use the {@link #plainTextReaderAndWriter()} method.
   */
  private DocumentReaderAndWriter<IN> plainTextReaderAndWriter;

  /** This is the default DocumentReaderAndWriter used for reading text files for runtime
   *  classification. It is the DocumentReaderAndWriter specified by the plainTextDocumentReaderAndWriter
   *  flag and defaults to {@code edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter} which
   *  is suitable for reading plain text files, in languages with a Tokenizer available.
   *  This reader is now allocated lazily when required, since many times (such as when using
   *  AbstractSequenceClassifiers in StanfordCoreNLP, these DocumentReaderAndWriters are never used.
   *  Synchronized for safe lazy initialization.
   *
   *  @return The default plain text DocumentReaderAndWriter
   */
  public synchronized DocumentReaderAndWriter<IN> plainTextReaderAndWriter() {
    if (plainTextReaderAndWriter == null) {
      if (flags.readerAndWriter != null &&
          flags.readerAndWriter.equals(flags.plainTextDocumentReaderAndWriter)) {
        plainTextReaderAndWriter = defaultReaderAndWriter();
      } else {
        plainTextReaderAndWriter = makePlainTextReaderAndWriter();
      }
    }
    return plainTextReaderAndWriter;
  }


  /**
   * Construct a SeqClassifierFlags object based on the passed in properties,
   * and then call the other constructor.
   *
   * @param props See SeqClassifierFlags for known properties.
   */
  public AbstractSequenceClassifier(Properties props) {
    this(new SeqClassifierFlags(props));
  }

  /**
   * Initialize the featureFactory and other variables based on the passed in
   * flags.
   *
   * @param flags A specification of the AbstractSequenceClassifier to construct.
   */
  public AbstractSequenceClassifier(SeqClassifierFlags flags) {
    this.flags = flags;

    // Thang Sep13: allow for multiple feature factories.
    this.featureFactories = Generics.newArrayList();
    if (flags.featureFactory != null) {
      FeatureFactory<IN> factory = new MetaClass(flags.featureFactory).createInstance(flags.featureFactoryArgs); // for compatibility
      featureFactories.add(factory);
    }
    if (flags.featureFactories != null) {
      for (int i = 0; i < flags.featureFactories.length; i++) {
        FeatureFactory<IN> indFeatureFactory = new MetaClass(flags.featureFactories[i]).
            createInstance(flags.featureFactoriesArgs.get(i));
        this.featureFactories.add(indFeatureFactory);
      }
    }
    if (flags.tokenFactory == null) {
      tokenFactory = (CoreTokenFactory<IN>) new CoreLabelTokenFactory();
    } else {
      this.tokenFactory = new MetaClass(flags.tokenFactory).createInstance(flags.tokenFactoryArgs);
    }
    pad = tokenFactory.makeToken();
    windowSize = flags.maxLeft + 1;
    reinit();
  }

  /**
   * This method should be called after there have been changes to the flags
   * (SeqClassifierFlags) variable, such as after deserializing a classifier. It
   * is called inside the loadClassifier methods. It assumes that the flags
   * variable and the pad variable exist, but reinitializes things like the pad
   * variable, featureFactory and readerAndWriter based on the flags.
   * <p>
   * <i>Implementation note:</i> At the moment this variable doesn't set
   * windowSize or featureFactory, since they are being serialized separately in
   * the file, but we should probably stop serializing them and just
   * reinitialize them from the flags?
   */
  protected final void reinit() {
    pad.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);
    pad.set(CoreAnnotations.GoldAnswerAnnotation.class, flags.backgroundSymbol);

    for (FeatureFactory featureFactory : featureFactories) {
      featureFactory.init(flags);
    }

    defaultReaderAndWriter = null;
    plainTextReaderAndWriter = null;

    if (knownLCWords == null || knownLCWords.isEmpty()) {
      // reinit limits max (additional) size. We temporarily loosen this during training
      knownLCWords = new MaxSizeConcurrentHashSet<>(flags.maxAdditionalKnownLCWords);
    } else {
      knownLCWords.setMaxSize(knownLCWords.size() + flags.maxAdditionalKnownLCWords);
    }
  }

  public Set<String> getKnownLCWords() {
    return knownLCWords;
  }

  /**
   * Makes a DocumentReaderAndWriter based on the flags the CRFClassifier
   * was constructed with.  Will create an instance of the class specified in
   * the property flags.readerAndWriter and
   * initialize it with the CRFClassifier's flags.
   *
   * @return The appropriate ReaderAndWriter for training/testing this classifier
   */
  public DocumentReaderAndWriter<IN> makeReaderAndWriter() {
    DocumentReaderAndWriter<IN> readerAndWriter;
    try {
      readerAndWriter = ReflectionLoading.loadByReflection(flags.readerAndWriter);
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error loading flags.readerAndWriter: '%s'", flags.readerAndWriter), e);
    }
    readerAndWriter.init(flags);
    return readerAndWriter;
  }

  /**
   * Makes a DocumentReaderAndWriter based on
   * flags.plainTextReaderAndWriter.  Useful for reading in
   * untokenized text documents or reading plain text from the command
   * line.  An example of a way to use this would be to return a
   * edu.stanford.nlp.wordseg.Sighan2005DocumentReaderAndWriter for
   * the Chinese Segmenter.
   */
  public static <INN extends CoreMap> DocumentReaderAndWriter<INN> makePlainTextReaderAndWriter(SeqClassifierFlags flags) {
    String readerClassName = flags.plainTextDocumentReaderAndWriter;
    // We set this default here if needed because there may be models
    // which don't have the reader flag set
    if (readerClassName == null) {
      readerClassName = SeqClassifierFlags.DEFAULT_PLAIN_TEXT_READER;
    }
    DocumentReaderAndWriter<INN> readerAndWriter;
    try {
      readerAndWriter = ReflectionLoading.loadByReflection(readerClassName);
    } catch (Exception e) {
      throw new RuntimeException(String.format("Error loading flags.plainTextDocumentReaderAndWriter: '%s'", flags.plainTextDocumentReaderAndWriter), e);
    }
    readerAndWriter.init(flags);
    return readerAndWriter;
  }

  public DocumentReaderAndWriter<IN> makePlainTextReaderAndWriter() {
    return makePlainTextReaderAndWriter(flags);
  }

  /**
   * Returns the background class for the classifier.
   *
   * @return The background class name
   */
  public String backgroundSymbol() {
    return flags.backgroundSymbol;
  }

  public Set<String> labels() {
    return Generics.newHashSet(classIndex.objectsList());
  }

  /**
   * Classify a List of IN. This method returns a new list of tokens, not
   * the list of tokens passed in, and runs the new tokens through
   * ObjectBankWrapper.  (Both these behaviors are different from that of the
   * classify(List) method.
   *
   * @param tokenSequence The List of IN to be classified.
   * @return The classified List of IN, where the classifier output for
   *         each token is stored in its
   *         {@link edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation}
   *         field.
   */
  public List<IN> classifySentence(List<? extends HasWord> tokenSequence) {
    List<IN> document = preprocessTokens(tokenSequence);
    classify(document);
    return document;
  }

  private List<IN> preprocessTokens(List<? extends HasWord> tokenSequence) {
    // log.info("knownLCWords.size is " + knownLCWords.size() + "; knownLCWords.maxSize is " + knownLCWords.getMaxSize() +
    //                   ", prior to NER for " + getClass().toString());
    List<IN> document = new ArrayList<>();
    int i = 0;
    for (HasWord word : tokenSequence) {
      IN wi; // initialized below
      if (word instanceof CoreMap) {
        // copy all annotations! some are required later in
        // AbstractSequenceClassifier.classifyWithInlineXML
        // wi = (IN) new ArrayCoreMap((ArrayCoreMap) word);
        wi = tokenFactory.makeToken((IN) word);
      } else {
        wi = tokenFactory.makeToken();
        wi.set(CoreAnnotations.TextAnnotation.class, word.word());
        // wi.setWord(word.word());
      }
      wi.set(CoreAnnotations.PositionAnnotation.class, Integer.toString(i));
      wi.set(CoreAnnotations.AnswerAnnotation.class, backgroundSymbol());
      document.add(wi);
      i++;
    }

    // TODO get rid of ObjectBankWrapper
    ObjectBankWrapper<IN> wrapper = new ObjectBankWrapper<>(flags, null, knownLCWords);
    wrapper.processDocument(document);
    // log.info("Size of knownLCWords is " + knownLCWords.size() + ", after NER for " + getClass().toString());
    return document;
  }

  /**
   * Classify a List of IN using whatever additional information is passed in globalInfo.
   * Used by SUTime (NumberSequenceClassifier), which requires the doc date to resolve relative dates.
   *
   * @param tokenSequence The List of IN to be classified.
   * @return The classified List of IN, where the classifier output for
   *         each token is stored in its "answer" field.
   */
  public List<IN> classifySentenceWithGlobalInformation(List<? extends HasWord> tokenSequence, final CoreMap doc, final CoreMap sentence) {
    List<IN> document = preprocessTokens(tokenSequence);
    classifyWithGlobalInformation(document, doc, sentence);
    return document;
  }

  public SequenceModel getSequenceModel(List<IN> doc) {
    throw new UnsupportedOperationException();
  }

  public Sampler<List<IN>> getSampler(final List<IN> input) {
    return new Sampler<List<IN>>() {
      SequenceModel model = getSequenceModel(input);
      SequenceSampler sampler = new SequenceSampler();

      @Override
      public List<IN> drawSample() {
        int[] sampleArray = sampler.bestSequence(model);
        List<IN> sample = new ArrayList<>();
        int i = 0;
        for (IN word : input) {

          IN newWord = tokenFactory.makeToken(word);
          newWord.set(CoreAnnotations.AnswerAnnotation.class, classIndex.get(sampleArray[i++]));
          sample.add(newWord);
        }
        return sample;
      }
    };
  }

  /** Takes a list of tokens and provides the K best sequence labelings of these tokens with their scores.
   *
   *  @param doc The List of tokens
   *  @param answerField The key for each token into which the label for the token will be written
   *  @param k The number of best sequence labelings to generate
   *  @return A Counter where each key is a List of tokens with labels written in the answerField and its value
   *          is the score (conditional probability) assigned to this labeling of the sequence.
   */
  public Counter<List<IN>> classifyKBest(List<IN> doc, Class<? extends CoreAnnotation<String>> answerField, int k) {

    if (doc.isEmpty()) {
      return new ClassicCounter<>();
    }

    // TODO get rid of ObjectBankWrapper
    // i'm sorry that this is so hideous - JRF
    ObjectBankWrapper<IN> obw = new ObjectBankWrapper<>(flags, null, knownLCWords);
    doc = obw.processDocument(doc);

    SequenceModel model = getSequenceModel(doc);

    KBestSequenceFinder tagInference = new KBestSequenceFinder();
    Counter<int[]> bestSequences = tagInference.kBestSequences(model, k);

    Counter<List<IN>> kBest = new ClassicCounter<>();

    for (int[] seq : bestSequences.keySet()) {
      List<IN> kth = new ArrayList<>();
      int pos = model.leftWindow();
      for (IN fi : doc) {
        IN newFL = tokenFactory.makeToken(fi);
        String guess = classIndex.get(seq[pos]);
        fi.remove(CoreAnnotations.AnswerAnnotation.class); // because fake answers will get
                                           // added during testing
        newFL.set(answerField, guess);
        pos++;
        kth.add(newFL);
      }
      kBest.setCount(kth, bestSequences.getCount(seq));
    }

    return kBest;
  }

  private DFSA<String, Integer> getViterbiSearchGraph(List<IN> doc, Class<? extends CoreAnnotation<String>> answerField) {
    if (doc.isEmpty()) {
      return new DFSA<>(null);
    }
    // TODO get rid of ObjectBankWrapper
    ObjectBankWrapper<IN> obw = new ObjectBankWrapper<>(flags, null, knownLCWords);
    doc = obw.processDocument(doc);
    SequenceModel model = getSequenceModel(doc);
    return ViterbiSearchGraphBuilder.getGraph(model, classIndex);
  }

  /**
   * Classify the tokens in a String. Each sentence becomes a separate document.
   *
   * @param str A String with tokens in one or more sentences of text to be
   *          classified.
   * @return {@link List} of classified sentences (each a List of something that
   *         extends {@link CoreMap}).
   */
  public List<List<IN>> classify(String str) {
    ObjectBank<List<IN>> documents = makeObjectBankFromString(str, plainTextReaderAndWriter());
    return classifyObjectBank(documents);
  }
  /**
   * Classify the tokens in a String. Each sentence becomes a separate document.
   * Doesn't override default readerAndWriter.
   *
   * @param str A String with tokens in one or more sentences of text to be classified.
   * @return {@link List} of classified sentences (each a List of something that
   *         extends {@link CoreMap}).
   */
  public List<List<IN>> classifyRaw(String str,
                                    DocumentReaderAndWriter<IN> readerAndWriter) {
    ObjectBank<List<IN>> documents =
      makeObjectBankFromString(str, readerAndWriter);
    return classifyObjectBank(documents);
  }

  /**
   * Classify the contents of a file.
   *
   * @param filename Contains the sentence(s) to be classified.
   * @return {@link List} of classified List of IN.
   */
  public List<List<IN>> classifyFile(String filename) {
    ObjectBank<List<IN>> documents = makeObjectBankFromFile(filename, plainTextReaderAndWriter());
    return classifyObjectBank(documents);
  }


  /**
   * Classify the tokens in an ObjectBank.
   *
   * @param documents The documents in an ObjectBank to classify.
   * @return {@link List} of classified sentences (each a List of something that
   *         extends {@link CoreMap}).
   */
  private List<List<IN>> classifyObjectBank(ObjectBank<List<IN>> documents) {
    List<List<IN>> result = new ArrayList<>();

    for (List<IN> document : documents) {
      classify(document);

      List<IN> sentence = new ArrayList<>();
      for (IN wi : document) {
        // TaggedWord word = new TaggedWord(wi.word(), wi.answer());
        // sentence.add(word);
        sentence.add(wi);
      }
      result.add(sentence);
    }
    return result;
  }

  /**
   * Maps a String input to an XML-formatted rendition of applying NER to the
   * String. Implements the Function interface. Calls
   * classifyWithInlineXML(String) [q.v.].
   */
  @Override
  public String apply(String in) {
    return classifyWithInlineXML(in);
  }

  /**
   * Classify the contents of a {@link String} to one of several String
   * representations that shows the classes. Plain text or XML input is expected
   * and the {@link PlainTextDocumentReaderAndWriter} is used. The classifier
   * will tokenize the text and treat each sentence as a separate document. The
   * output can be specified to be in a choice of three formats: slashTags
   * (e.g., Bill/PERSON Smith/PERSON died/O ./O), inlineXML (e.g.,
   * &lt;PERSON&gt;Bill Smith&lt;/PERSON&gt; went to
   * &lt;LOCATION&gt;Paris&lt;/LOCATION&gt; .), or xml, for stand-off XML (e.g.,
   * &lt;wi num="0" entity="PERSON"&gt;Sue&lt;/wi&gt; &lt;wi num="1"
   * entity="O"&gt;shouted&lt;/wi&gt; ). There is also a binary choice as to
   * whether the spacing between tokens of the original is preserved or whether
   * the (tagged) tokens are printed with a single space (for inlineXML or
   * slashTags) or a single newline (for xml) between each one.
   * <p>
   * <i>Fine points:</i> The slashTags and xml formats show tokens as
   * transformed by any normalization processes inside the tokenizer, while
   * inlineXML shows the tokens exactly as they appeared in the source text.
   * When a period counts as both part of an abbreviation and as an end of
   * sentence marker, it is included twice in the output String for slashTags or
   * xml, but only once for inlineXML, where it is not counted as part of the
   * abbreviation (or any named entity it is part of). For slashTags with
   * preserveSpacing=true, there will be two successive periods such as "Jr.."
   * The tokenized (preserveSpacing=false) output will have a space or a newline
   * after the last token.
   *
   * @param sentences The String to be classified. It will be tokenized and
   *          divided into documents according to (heuristically
   *          determined) sentence boundaries.
   * @param outputFormat The format to put the output in: one of "slashTags", "xml",
   *          "inlineXML", "tsv", or "tabbedEntities"
   * @param preserveSpacing Whether to preserve the input spacing between tokens, which may
   *          sometimes be none (true) or whether to tokenize the text and print
   *          it with one space between each token (false)
   * @return A {@link String} with annotated with classification information.
   */
  public String classifyToString(String sentences, String outputFormat, boolean preserveSpacing) {
    PlainTextDocumentReaderAndWriter.OutputStyle outFormat =
      PlainTextDocumentReaderAndWriter.OutputStyle.fromShortName(outputFormat);
    DocumentReaderAndWriter<IN> textDocumentReaderAndWriter = plainTextReaderAndWriter();

    ObjectBank<List<IN>> documents = makeObjectBankFromString(sentences, textDocumentReaderAndWriter);

    StringBuilder sb = new StringBuilder();
    for (List<IN> doc : documents) {
      List<IN> docOutput = classify(doc);
      if (textDocumentReaderAndWriter instanceof PlainTextDocumentReaderAndWriter) {
        // TODO: implement this particular method and its options in the other documentReaderAndWriters
        sb.append(((PlainTextDocumentReaderAndWriter<IN>) textDocumentReaderAndWriter).getAnswers(docOutput, outFormat, preserveSpacing));
      } else {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        textDocumentReaderAndWriter.printAnswers(docOutput, pw);
        pw.flush();
        sb.append(sw);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Classify the contents of a {@link String}. Plain text or XML is expected
   * and the {@link PlainTextDocumentReaderAndWriter} is used by default.
   * The classifier will treat each sentence as a separate document. The output can be
   * specified to be in a choice of formats: Output is in inline XML format
   * (e.g., &lt;PERSON&gt;Bill Smith&lt;/PERSON&gt; went to
   * &lt;LOCATION&gt;Paris&lt;/LOCATION&gt; .)
   *
   * @param sentences The string to be classified
   * @return A {@link String} with annotated with classification information.
   */
  public String classifyWithInlineXML(String sentences) {
    return classifyToString(sentences, "inlineXML", true);
  }

  /**
   * Classify the contents of a String to a tagged word/class String. Plain text
   * or XML input is expected and the {@link PlainTextDocumentReaderAndWriter}
   * is used by default.
   * Output looks like: My/O name/O is/O Bill/PERSON Smith/PERSON ./O
   *
   * @param sentences The String to be classified
   * @return A String annotated with classification information.
   */
  public String classifyToString(String sentences) {
    return classifyToString(sentences, "slashTags", true);
  }

  /**
   * Classify the contents of a {@link String} to classified character offset
   * spans. Plain text or XML input text is expected and the
   * {@link PlainTextDocumentReaderAndWriter} is used by default.
   * Output is a (possibly
   * empty, but not {@code null}) List of Triples. Each Triple is an entity
   * name, followed by beginning and ending character offsets in the original
   * String. Character offsets can be thought of as fenceposts between the
   * characters, or, like certain methods in the Java String class, as character
   * positions, numbered starting from 0, with the end index pointing to the
   * position AFTER the entity ends. That is, end - start is the length of the
   * entity in characters.
   * <p>
   * <i>Fine points:</i> Token offsets are true wrt the source text, even though
   * the tokenizer may internally normalize certain tokens to String
   * representations of different lengths (e.g., " becoming `` or ''). When a
   * period counts as both part of an abbreviation and as an end of sentence
   * marker, and that abbreviation is part of a named entity, the reported
   * entity string excludes the period.
   *
   * @param sentences The string to be classified
   * @return A {@link List} of {@link Triple}s, each of which gives an entity
   *         type and the beginning and ending character offsets.
   */
  public List<Triple<String, Integer, Integer>> classifyToCharacterOffsets(String sentences) {
    ObjectBank<List<IN>> documents =
      makeObjectBankFromString(sentences, plainTextReaderAndWriter());

    List<Triple<String, Integer, Integer>> entities = new ArrayList<>();
    for (List<IN> doc : documents) {
      String prevEntityType = flags.backgroundSymbol;
      Triple<String, Integer, Integer> prevEntity = null;

      classify(doc);

      for (IN fl : doc) {
        String guessedAnswer = fl.get(CoreAnnotations.AnswerAnnotation.class);
        if (guessedAnswer.equals(flags.backgroundSymbol)) {
          if (prevEntity != null) {
            entities.add(prevEntity);
            prevEntity = null;
          }
        } else {
          if (!guessedAnswer.equals(prevEntityType)) {
            if (prevEntity != null) {
              entities.add(prevEntity);
            }
            prevEntity = new Triple<>(guessedAnswer,
                    fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                    fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          } else {
            assert prevEntity != null; // if you read the code carefully, this
                                       // should always be true!
            prevEntity.setThird(fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          }
        }
        prevEntityType = guessedAnswer;
      }

      // include any entity at end of doc
      if (prevEntity != null) {
        entities.add(prevEntity);
      }

    }
    return entities;
  }

  /**
   * Have a word segmenter segment a String into a list of words.
   * ONLY USE IF YOU LOADED A CHINESE WORD SEGMENTER!!!!!
   *
   * @param sentence The string to be classified
   * @return List of words
   */
  // todo: This method is currently [2016] only called in a very small number of places:
  // the parser's jsp webapp, ChineseSegmenterAnnotator, and SegDemo.
  // Maybe we could eliminate it?
  // It also seems like it should be using the plainTextReaderAndWriter, not default?
  public List<String> segmentString(String sentence) {
    return segmentString(sentence, defaultReaderAndWriter());
  }

  public List<String> segmentString(String sentence, DocumentReaderAndWriter<IN> readerAndWriter) {
    ObjectBank<List<IN>> docs = makeObjectBankFromString(sentence, readerAndWriter);

    StringWriter stringWriter = new StringWriter();
    PrintWriter stringPrintWriter = new PrintWriter(stringWriter);
    for (List<IN> doc : docs) {
      classify(doc);
      readerAndWriter.printAnswers(doc, stringPrintWriter);
      stringPrintWriter.println();
    }
    stringPrintWriter.close();
    String segmented = stringWriter.toString();

    if (segmented.length() == 0) {
      return Collections.emptyList();
    } else {
      // \\p{Zs} would catch more whitespace options than \\s,
      // but hopefully the upstream segmentation handled
      // unusual whitespace such as NBSP already
      return Arrays.asList(segmented.split("\\s"));
    }
  }

  /*
   * Classify the contents of {@link SeqClassifierFlags scf.testFile}. The file
   * should be in the format expected based on {@link SeqClassifierFlags
   * scf.documentReader}.
   *
   * @return A {@link List} of {@link List}s of classified something that
   *         extends {@link CoreMap} where each {@link List} refers to a
   *         document/sentence.
   */
  // public ObjectBank<List<IN>> test() {
  // return test(flags.testFile);
  // }

  /**
   * Classify a {@link List} of something that extends{@link CoreMap}.
   * The classifications are added in place to the items of the document,
   * which is also returned by this method.
   *
   * <i>Warning:</i> In many circumstances, you should not call this method directly.
   * In particular, if you call this method directly, your document will not be preprocessed
   * to add things like word distributional similarity class or word shape features that your
   * classifier may rely on to work correctly. In such cases, you should call
   * {@link #classifySentence(List<? extends HasWord>) classifySentence} instead.
   *
   * @param document A {@link List} of something that extends {@link CoreMap}.
   * @return The same {@link List}, but with the elements annotated with their
   *         answers (stored under the
   *         {@link edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation}
   *         key). The answers will be the class labels defined by the CRF
   *         Classifier. They might be things like entity labels (in BIO
   *         notation or not) or something like "1" vs. "0" on whether to
   *         begin a new token here or not (in word segmentation).
   */
  // todo [cdm 2017]: Check that our own NER code doesn't call this method wrongly anywhere.
  public abstract List<IN> classify(List<IN> document);

  /**
   * Classify a {@link List} of something that extends {@link CoreMap} using as
   * additional information whatever is stored in the document and sentence.
   * This is needed for SUTime (NumberSequenceClassifier), which requires
   * the document date to resolve relative dates.
   *
   * @param tokenSequence A {@link List} of something that extends {@link CoreMap}
   * @param document
   * @param sentence
   * @return Classified version of the input tokenSequence
   */
  public abstract List<IN> classifyWithGlobalInformation(List<IN> tokenSequence, final CoreMap document, final CoreMap sentence);

  /**
   * Classification is finished for the document.
   * Do any cleanup (if information was stored as part of the document for global classification)
   * @param document
   */
  public void finalizeClassification(final CoreMap document) {
  }

  /**
   * Train the classifier based on values in flags. It will use the first of
   * these variables that is defined: trainFiles (and baseTrainDir),
   * trainFileList, trainFile.
   */
  public void train() {
    if (flags.trainFiles != null) {
      train(flags.baseTrainDir, flags.trainFiles, defaultReaderAndWriter());
    } else if (flags.trainFileList != null) {
      String[] files = flags.trainFileList.split(",");
      train(files, defaultReaderAndWriter());
    } else {
      train(flags.trainFile, defaultReaderAndWriter());
    }
  }

  public void train(String filename) {
    train(filename, defaultReaderAndWriter());
  }

  public void train(String filename,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = true;
    train(makeObjectBankFromFile(filename, readerAndWriter), readerAndWriter);
  }

  public void train(String baseTrainDir, String trainFiles,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = true;
    train(makeObjectBankFromFiles(baseTrainDir, trainFiles, readerAndWriter),
          readerAndWriter);
  }

  public void train(String[] trainFileList,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = true;
    train(makeObjectBankFromFiles(trainFileList, readerAndWriter),
          readerAndWriter);
  }

  /**
   * Trains a classifier from a Collection of sequences.
   * Note that the Collection can be (and usually is) an ObjectBank.
   *
   * @param docs An ObjectBank or a collection of sequences of IN
   */
  public void train(Collection<List<IN>> docs) {
    train(docs, defaultReaderAndWriter());
  }

  /**
   * Trains a classifier from a Collection of sequences.
   * Note that the Collection can be (and usually is) an ObjectBank.
   *
   * @param docs An ObjectBank or a collection of sequences of IN
   * @param readerAndWriter A DocumentReaderAndWriter to use when loading test files
   */
  public abstract void train(Collection<List<IN>> docs,
                             DocumentReaderAndWriter<IN> readerAndWriter);

  /**
   * Reads a String into an ObjectBank object. NOTE: that the current
   * implementation of ReaderIteratorFactory will first try to interpret each
   * string as a filename, so this method will yield unwanted results if it
   * applies to a string that is at the same time a filename. It prints out a
   * warning, at least.
   *
   * @param string The String which will be the content of the ObjectBank
   * @return The ObjectBank
   */
  public ObjectBank<List<IN>>
    makeObjectBankFromString(String string,
                             DocumentReaderAndWriter<IN> readerAndWriter) {
    if (flags.announceObjectBankEntries) {
      log.info("Reading data using " + readerAndWriter.getClass());

      if (flags.inputEncoding == null) {
        log.info("Getting data from " + string + " (default encoding)");
      } else {
        log.info("Getting data from " + string + " (" + flags.inputEncoding + " encoding)");
      }
    }
    // return new ObjectBank<List<IN>>(new
    // ResettableReaderIteratorFactory(string), readerAndWriter);
    // TODO
    return new ObjectBankWrapper<>(flags, new ObjectBank<>(new ResettableReaderIteratorFactory(string),
            readerAndWriter), knownLCWords);
  }

  public ObjectBank<List<IN>> makeObjectBankFromFile(String filename) {
    return makeObjectBankFromFile(filename, defaultReaderAndWriter());
  }

  public ObjectBank<List<IN>> makeObjectBankFromFile(String filename,
                                                     DocumentReaderAndWriter<IN> readerAndWriter) {
    String[] fileAsArray = { filename };
    return makeObjectBankFromFiles(fileAsArray, readerAndWriter);
  }

  public ObjectBank<List<IN>> makeObjectBankFromFiles(String[] trainFileList,
                                                      DocumentReaderAndWriter<IN> readerAndWriter) {
    // try{
    Collection<File> files = new ArrayList<>();
    for (String trainFile : trainFileList) {
      File f = new File(trainFile);
      files.add(f);
    }
    // System.err.printf("trainFileList contains %d file%s in encoding %s.%n", files.size(), files.size() == 1 ? "": "s", flags.inputEncoding);
    // TODO get rid of ObjectBankWrapper
    // return new ObjectBank<List<IN>>(new
    // ResettableReaderIteratorFactory(files), readerAndWriter);
    return new ObjectBankWrapper<>(flags, new ObjectBank<>(new ResettableReaderIteratorFactory(files, flags.inputEncoding),
            readerAndWriter), knownLCWords);
    // } catch (IOException e) {
    // throw new RuntimeException(e);
    // }
  }

  public ObjectBank<List<IN>> makeObjectBankFromFiles(String baseDir, String filePattern,
                                                      DocumentReaderAndWriter<IN> readerAndWriter) {

    File path = new File(baseDir);
    FileFilter filter = new RegExFileFilter(Pattern.compile(filePattern));
    File[] origFiles = path.listFiles(filter);
    Collection<File> files = new ArrayList<>();
    for (File file : origFiles) {
      if (file.isFile()) {
        if (flags.announceObjectBankEntries) {
          log.info("Getting data from " + file + " (" + flags.inputEncoding + " encoding)");
        }
        files.add(file);
      }
    }

    if (files.isEmpty()) {
      throw new RuntimeException("No matching files: " + baseDir + '\t' + filePattern);
    }
    // return new ObjectBank<List<IN>>(new
    // ResettableReaderIteratorFactory(files, flags.inputEncoding),
    // readerAndWriter);
    // TODO get rid of ObjectBankWrapper
    return new ObjectBankWrapper<>(flags, new ObjectBank<>(new ResettableReaderIteratorFactory(files,
            flags.inputEncoding), readerAndWriter), knownLCWords);
  }

  public ObjectBank<List<IN>> makeObjectBankFromFiles(Collection<File> files,
                                                      DocumentReaderAndWriter<IN> readerAndWriter) {
    if (files.isEmpty()) {
      throw new RuntimeException("Attempt to make ObjectBank with empty file list");
    }
    // return new ObjectBank<List<IN>>(new
    // ResettableReaderIteratorFactory(files, flags.inputEncoding),
    // readerAndWriter);
    // TODO get rid of ObjectBankWrapper
    return new ObjectBankWrapper<>(flags, new ObjectBank<>(new ResettableReaderIteratorFactory(files,
            flags.inputEncoding), readerAndWriter), knownLCWords);
  }

  /**
   * Set up an ObjectBank that will allow one to iterate over a collection of
   * documents obtained from the passed in Reader. Each document will be
   * represented as a list of IN. If the ObjectBank iterator() is called until
   * hasNext() returns false, then the Reader will be read till end of file, but
   * no reading is done at the time of this call. Reading is done using the
   * reading method specified in {@code flags.documentReader}, and for some
   * reader choices, the column mapping given in {@code flags.map}.
   *
   * @param in
   *          Input data addNEWLCWords do we add new lowercase words from this
   *          data to the word shape classifier
   * @return The list of documents
   */
  public ObjectBank<List<IN>> makeObjectBankFromReader(BufferedReader in,
                                                       DocumentReaderAndWriter<IN> readerAndWriter) {
    if (flags.announceObjectBankEntries) {
      log.info("Reading data using " + readerAndWriter.getClass());
    }
    // TODO get rid of ObjectBankWrapper
    // return new ObjectBank<List<IN>>(new ResettableReaderIteratorFactory(in),
    // readerAndWriter);
    return new ObjectBankWrapper<>(flags, new ObjectBank<>(new ResettableReaderIteratorFactory(in),
            readerAndWriter), knownLCWords);
  }

  /**
   * Takes the file, reads it in, and prints out the likelihood of each possible
   * label at each point.
   *
   * @param filename The path to the specified file
   */
  public void printProbs(String filename,
                         DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = false;

    ObjectBank<List<IN>> docs =
      makeObjectBankFromFile(filename, readerAndWriter);
    printProbsDocuments(docs);
  }

  /**
   * Takes the files, reads them in, and prints out the likelihood of each possible
   * label at each point.
   *
   * @param testFiles A Collection of files
   */
  public void printProbs(Collection<File> testFiles,
                         DocumentReaderAndWriter<IN> readerWriter) {

    ObjectBank<List<IN>> documents = makeObjectBankFromFiles(testFiles, readerWriter);
    printProbsDocuments(documents);
  }


  /**
   * Takes a {@link List} of documents and prints the likelihood of each
   * possible label at each point. Also prints probability calibration information over document collection.
   *
   * @param documents A {@link List} of {@link List} of something that extends
   *          {@link CoreMap}.
   */
  public void printProbsDocuments(ObjectBank<List<IN>> documents) {
    Counter<Integer> calibration = new ClassicCounter<>();
    Counter<Integer> correctByBin = new ClassicCounter<>();
    TwoDimensionalCounter<Integer,String> calibratedTokens = new TwoDimensionalCounter<>();

    for (List<IN> doc : documents) {
      Triple<Counter<Integer>, Counter<Integer>, TwoDimensionalCounter<Integer,String>> triple = printProbsDocument(doc);
      if (triple != null) {
        Counters.addInPlace(calibration, triple.first());
        Counters.addInPlace(correctByBin, triple.second());
        calibratedTokens.addAll(triple.third());
      }
      System.out.println();
    }
    if (calibration.size() > 0) {
      // we stored stuff, so print it out
      PrintWriter pw = new PrintWriter(System.err);
      outputCalibrationInfo(pw, calibration, correctByBin, calibratedTokens);
      pw.flush();
    }
  }

  private static void outputCalibrationInfo(PrintWriter pw,
                                            Counter<Integer> calibration,
                                            Counter<Integer> correctByBin,
                                            TwoDimensionalCounter<Integer, String> calibratedTokens) {
    final int numBins = 10;
    pw.println(); // in practice may well be in middle of line when called
    pw.println("----------------------------------------");
    pw.println("Probability distribution given to tokens (Counts for all class-token pairs; accuracy for this bin; examples are gold entity tokens in bin)");
    pw.println("----------------------------------------");
    for (int i = 0; i < numBins; i++) {
      pw.printf("[%.1f-%.1f%c: %.0f  %.2f%n",
              ((double) i) / numBins,
              ((double) (i+1)) / numBins,
              i == (numBins - 1) ? ']': ')',
              calibration.getCount(i),
              correctByBin.getCount(i) / calibration.getCount(i));
    }
    pw.println("----------------------------------------");
    for (int i = 0; i < numBins; i++) {
      pw.printf("[%.1f-%.1f%c: %s%n",
              ((double) i) / numBins,
              ((double) (i+1)) / numBins,
              i == (numBins - 1) ? ']': ')',
              Counters.toSortedString(calibratedTokens.getCounter(i), 20, "%s=%.0f", ", ", "[%s]"));
    }
    pw.println("----------------------------------------");
  }

  public void classifyStdin() throws IOException {
    classifyStdin(plainTextReaderAndWriter());
  }

  public void classifyStdin(DocumentReaderAndWriter<IN> readerWriter) throws IOException {
    BufferedReader is = IOUtils.readerFromStdin(flags.inputEncoding);
    for (String line; (line = is.readLine()) != null; ) {
      Collection<List<IN>> documents = makeObjectBankFromString(line, readerWriter);
      if (flags.keepEmptySentences && documents.isEmpty()) {
        documents = Collections.<List<IN>>singletonList(Collections.<IN>emptyList());
      }
      classifyAndWriteAnswers(documents, readerWriter, false);
    }
  }

  public Triple<Counter<Integer>, Counter<Integer>, TwoDimensionalCounter<Integer,String>> printProbsDocument(List<IN> document) {
    throw new UnsupportedOperationException("Not implemented for this class.");
  }

  /** Does nothing by default.  Subclasses can override if necessary. */
  public void dumpFeatures(Collection<List<IN>> documents) {}

  /**
   * Load a text file, run the classifier on it, and then print the answers to
   * stdout (with timing to stderr). This uses the value of flags.plainTextDocumentReaderAndWriter
   * to determine how to read the textFile format. By default this gives
   * edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter.
   * <i>Note:</i> This means that it works right for
   * a plain textFile (and not a tab-separated columns test file).
   *
   * @param textFile The file to test on.
   */
  public void classifyAndWriteAnswers(String textFile)
          throws IOException {
    classifyAndWriteAnswers(textFile, plainTextReaderAndWriter(), false);
  }

  /**
   * Load a test file, run the classifier on it, and then print the answers to
   * stdout (with timing to stderr). This uses the value of flags.documentReader
   * to determine testFile format. By default, this means that it is set up to
   * read a tab-separated columns test file
   *
   * @param testFile The file to test on.
   * @param outputScores Whether to calculate and then log performance scores (P/R/F1)
   * @return A Triple of P/R/F1 if outputScores is true, else null
   */
  public Triple<Double,Double,Double> classifyAndWriteAnswers(String testFile, boolean outputScores)
          throws IOException {
    return classifyAndWriteAnswers(testFile, defaultReaderAndWriter(), outputScores);
  }

  /**
   * Load a test file, run the classifier on it, and then print the answers to
   * stdout (with timing to stderr).
   *
   * @param testFile The file to test on.
   * @param readerWriter A reader and writer to use for the output
   * @param outputScores Whether to calculate and then log performance scores (P/R/F1)
   * @return A Triple of P/R/F1 if outputScores is true, else null
   */
  public Triple<Double,Double,Double> classifyAndWriteAnswers(String testFile,
                                                              DocumentReaderAndWriter<IN> readerWriter,
                                                              boolean outputScores)
          throws IOException {
    ObjectBank<List<IN>> documents =
            makeObjectBankFromFile(testFile, readerWriter);
    return classifyAndWriteAnswers(documents, readerWriter, outputScores);
  }

  /** If the flag
   *  {@code outputEncoding} is defined, the output is written in that
   *  character encoding, otherwise in the system default character encoding.
   */
  public Triple<Double,Double,Double> classifyAndWriteAnswers(String testFile, OutputStream outStream,
                                      DocumentReaderAndWriter<IN> readerWriter, boolean outputScores)
          throws IOException {
    ObjectBank<List<IN>> documents = makeObjectBankFromFile(testFile, readerWriter);
    PrintWriter pw = IOUtils.encodedOutputStreamPrintWriter(outStream, flags.outputEncoding, true);
    return classifyAndWriteAnswers(documents, pw, readerWriter, outputScores);
  }

  public Triple<Double,Double,Double> classifyAndWriteAnswers(String baseDir, String filePattern,
                                      DocumentReaderAndWriter<IN> readerWriter,
                                      boolean outputScores)
          throws IOException {
    ObjectBank<List<IN>> documents = makeObjectBankFromFiles(baseDir, filePattern, readerWriter);
    return classifyAndWriteAnswers(documents, readerWriter, outputScores);
  }

  /** Run the classifier on a collection of text files.
   *  Uses the plainTextReaderAndWriter to process them.
   *
   *  @param textFiles A File Collection to process.
   *  @throws IOException For any IO error
   */
  public void classifyFilesAndWriteAnswers(Collection<File> textFiles)
          throws IOException {
    classifyFilesAndWriteAnswers(textFiles, plainTextReaderAndWriter(), false);
  }

  public void classifyFilesAndWriteAnswers(Collection<File> testFiles,
                                           DocumentReaderAndWriter<IN> readerWriter, boolean outputScores)
          throws IOException {
    ObjectBank<List<IN>> documents =
      makeObjectBankFromFiles(testFiles, readerWriter);
    classifyAndWriteAnswers(documents, readerWriter, outputScores);
  }

  public Triple<Double,Double,Double> classifyAndWriteAnswers(Collection<List<IN>> documents,
                                                              DocumentReaderAndWriter<IN> readerWriter,
                                                              boolean outputScores)
          throws IOException {
    return classifyAndWriteAnswers(documents,
                                   IOUtils.encodedOutputStreamPrintWriter(System.out, flags.outputEncoding, true),
                                   readerWriter, outputScores);
  }

  /**
   *
   * @param documents
   * @param printWriter
   * @param readerWriter
   * @param outputScores Whether to calculate and output the performance scores (P/R/F1) of the classifier
   * @return A Triple of overall P/R/F1, if outputScores is true, else {@code null}. The scores are done
   *         on a 0-100 scale like percentages.
   * @throws IOException
   */
  public Triple<Double,Double,Double> classifyAndWriteAnswers(Collection<List<IN>> documents,
                                                              PrintWriter printWriter,
                                                              DocumentReaderAndWriter<IN> readerWriter,
                                                              boolean outputScores)
          throws IOException {
    if (flags.exportFeatures != null) {
      dumpFeatures(documents);
    }

    Timing timer = new Timing();

    Counter<String> entityTP = new ClassicCounter<>();
    Counter<String> entityFP = new ClassicCounter<>();
    Counter<String> entityFN = new ClassicCounter<>();
    boolean resultsCounted = outputScores;
    int numWords = 0;
    int numDocs = 0;

    final AtomicInteger threadCompletionCounter = new AtomicInteger(0);

    ThreadsafeProcessor<List<IN>, List<IN>> threadProcessor =
        new ThreadsafeProcessor<List<IN>, List<IN>>() {
      @Override
      public List<IN> process(List<IN> doc) {
        doc = classify(doc);

        int completedNo = threadCompletionCounter.incrementAndGet();
        if (flags.verboseMode) log.info(completedNo + " examples completed");
        return doc;
      }
      @Override
      public ThreadsafeProcessor<List<IN>, List<IN>> newInstance() {
        return this;
      }
    };

    MulticoreWrapper<List<IN>, List<IN>> wrapper = null;
    if (flags.multiThreadClassifier != 0) {
      wrapper = new MulticoreWrapper<>(flags.multiThreadClassifier, threadProcessor);
    }

    for (List<IN> doc: documents) {
      numWords += doc.size();
      numDocs++;
      if (wrapper != null) {
        wrapper.put(doc);
        while (wrapper.peek()) {
          List<IN> results = wrapper.poll();
          writeAnswers(results, printWriter, readerWriter);
          resultsCounted = resultsCounted && countResults(results, entityTP, entityFP, entityFN);
        }
      } else {
        List<IN> results = threadProcessor.process(doc);
        writeAnswers(results, printWriter, readerWriter);
        resultsCounted = resultsCounted && countResults(results, entityTP, entityFP, entityFN);
      }
    }
    if (wrapper != null) {
      wrapper.join();
      while (wrapper.peek()) {
        List<IN> results = wrapper.poll();
        writeAnswers(results, printWriter, readerWriter);
        resultsCounted = resultsCounted && countResults(results, entityTP, entityFP, entityFN);
      }
    }

    long millis = timer.stop();
    double wordspersec = numWords / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    log.info(StringUtils.getShortClassName(this) +
                       " tagged " + numWords + " words in " + numDocs +
                       " documents at " + nf.format(wordspersec) +
                       " words per second.");
    if (outputScores) {
      return printResults(entityTP, entityFP, entityFN);
    } else {
      return null;
    }
  }

  /**
   * Load a test file, run the classifier on it, and then print the answers to
   * stdout (with timing to stderr). This uses the value of flags.documentReader
   * to determine testFile format.
   *
   * @param testFile The name of the file to test on.
   * @param k How many best to print
   * @param readerAndWriter Class to be used for printing answers
   */
  public void classifyAndWriteAnswersKBest(String testFile, int k,
                                       DocumentReaderAndWriter<IN> readerAndWriter)
    throws IOException {
    ObjectBank<List<IN>> documents = makeObjectBankFromFile(testFile, readerAndWriter);
    PrintWriter pw = IOUtils.encodedOutputStreamPrintWriter(System.out, flags.outputEncoding, true);
    classifyAndWriteAnswersKBest(documents, k, pw, readerAndWriter);
    pw.flush();
  }

  /**
   * Run the classifier on the documents in an ObjectBank, and print the
   * answers to a given PrintWriter (with timing to stderr). The value of
   * flags.documentReader is used to determine testFile format.
   *
   * @param documents The ObjectBank to test on.
   */
  public void classifyAndWriteAnswersKBest(ObjectBank<List<IN>> documents, int k, PrintWriter printWriter,
                                           DocumentReaderAndWriter<IN> readerAndWriter) throws IOException {
    Timing timer = new Timing();
    int numWords = 0;
    int numSentences = 0;

    for (List<IN> doc : documents) {
      Counter<List<IN>> kBest = classifyKBest(doc, CoreAnnotations.AnswerAnnotation.class, k);
      numWords += doc.size();
      List<List<IN>> sorted = Counters.toSortedList(kBest);
      int n = 1;
      for (List<IN> l : sorted) {
        printWriter.println("<sentence id=" + numSentences + " k=" + n + " logProb=" + kBest.getCount(l) + " prob="
            + Math.exp(kBest.getCount(l)) + '>');
        writeAnswers(l, printWriter, readerAndWriter);
        printWriter.println("</sentence>");
        n++;
      }
      numSentences++;
    }

    long millis = timer.stop();
    double wordspersec = numWords / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    log.info(this.getClass().getName() + " tagged " + numWords + " words in " + numSentences
        + " documents at " + nf.format(wordspersec) + " words per second.");
  }

  /**
   * Load a test file, run the classifier on it, and then write a Viterbi search
   * graph for each sequence.
   *
   * @param testFile The file to test on.
   */
  public void classifyAndWriteViterbiSearchGraph(String testFile, String searchGraphPrefix, DocumentReaderAndWriter<IN> readerAndWriter) throws IOException {
    Timing timer = new Timing();
    ObjectBank<List<IN>> documents = makeObjectBankFromFile(testFile, readerAndWriter);
    int numWords = 0;
    int numSentences = 0;

    for (List<IN> doc : documents) {
      DFSA<String, Integer> tagLattice = getViterbiSearchGraph(doc, CoreAnnotations.AnswerAnnotation.class);
      numWords += doc.size();
      PrintWriter latticeWriter = new PrintWriter(new FileOutputStream(searchGraphPrefix + '.' + numSentences
          + ".wlattice"));
      PrintWriter vsgWriter = new PrintWriter(new FileOutputStream(searchGraphPrefix + '.' + numSentences + ".lattice"));
      if (readerAndWriter instanceof LatticeWriter) {
        ((LatticeWriter<IN, String, Integer>) readerAndWriter).printLattice(tagLattice, doc, latticeWriter);
      }
      tagLattice.printAttFsmFormat(vsgWriter);
      latticeWriter.close();
      vsgWriter.close();
      numSentences++;
    }

    long millis = timer.stop();
    double wordspersec = numWords / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    log.info(this.getClass().getName() + " tagged " + numWords + " words in " + numSentences
        + " documents at " + nf.format(wordspersec) + " words per second.");
  }

  /**
   * Write the classifications of the Sequence classifier to a writer in a
   * format determined by the DocumentReaderAndWriter used.
   *
   * @param doc Documents to write out
   * @param printWriter Writer to use for output
   */
  public void writeAnswers(List<IN> doc, PrintWriter printWriter,
                           DocumentReaderAndWriter<IN> readerAndWriter) {
    if (flags.lowerNewgeneThreshold) {
      return;
    }
    if (flags.numRuns <= 1) {
      readerAndWriter.printAnswers(doc, printWriter);
      // out.println();
      printWriter.flush();
    }
  }

  /**
   * Count results using a method appropriate for the tag scheme being used.
   */
  public boolean countResults(List<IN> doc,
                              Counter<String> entityTP,
                              Counter<String> entityFP,
                              Counter<String> entityFN) {
    String bg = (flags.evaluateBackground ? null : flags.backgroundSymbol);
    if (flags.sighanPostProcessing) {
      // TODO: this is extremely indicative of being a Chinese Segmenter,
      // but it would still be better to have something more concrete
      return countResultsSegmenter(doc, entityTP, entityFP, entityFN);
    }
    return IOBUtils.countEntityResults(doc, entityTP, entityFP, entityFN, bg);
  }

  // TODO: could make this a parameter for the model
  private static final String CUT_LABEL = "Cut";

  public static boolean countResultsSegmenter(List<? extends CoreMap> doc,
                                              Counter<String> entityTP,
                                              Counter<String> entityFP,
                                              Counter<String> entityFN) {
    // count from 1 because each label represents cutting or
    // not cutting at a word, so we don't count the first word
    for (int i = 1; i < doc.size(); ++i) {
      CoreMap word = doc.get(i);
      String gold = word.get(CoreAnnotations.GoldAnswerAnnotation.class);
      String guess = word.get(CoreAnnotations.AnswerAnnotation.class);
      if (gold == null || guess == null) {
        return false;
      }
      if (gold.equals("1") && guess.equals("1")) {
        entityTP.incrementCount(CUT_LABEL, 1.0);
      } else if (gold.equals("0") && guess.equals("1")) {
        entityFP.incrementCount(CUT_LABEL, 1.0);
      } else if (gold.equals("1") && guess.equals("0")) {
        entityFN.incrementCount(CUT_LABEL, 1.0);
      }
    }
    return true;
  }


  /**
   * Given counters of true positives, false positives, and false
   * negatives, prints out precision, recall, and f1 for each key.
   */
  public static Triple<Double,Double,Double> printResults(Counter<String> entityTP, Counter<String> entityFP,
                           Counter<String> entityFN) {
    Set<String> entities = new TreeSet<>();
    entities.addAll(entityTP.keySet());
    entities.addAll(entityFP.keySet());
    entities.addAll(entityFN.keySet());
    log.info("         Entity\tP\tR\tF1\tTP\tFP\tFN");
    for (String entity : entities) {
      double tp = entityTP.getCount(entity);
      double fp = entityFP.getCount(entity);
      double fn = entityFN.getCount(entity);
      printPRLine(entity, tp, fp, fn);
    }
    double tp = entityTP.totalCount();
    double fp = entityFP.totalCount();
    double fn = entityFN.totalCount();
    return printPRLine("Totals", tp, fp, fn);
  }

  /**
   * Print a line of precision, recall, and f1 scores, titled by entity.
   *
   * @return A Triple of the P/R/F, done on a 0-100 scale like percentages
   */
  private static Triple<Double,Double,Double> printPRLine(String entity, double tp, double fp, double fn) {
    double precision = (tp == 0.0 && fp == 0.0) ? 0.0 : tp / (tp + fp);
    double recall = (tp == 0.0 && fn == 0.0) ? 1.0 : tp / (tp + fn);
    double f1 = ((precision == 0.0 || recall == 0.0) ?
                 0.0 : 2.0 / (1.0 / precision + 1.0 / recall));
    log.info(String.format("%15s\t%.4f\t%.4f\t%.4f\t%.0f\t%.0f\t%.0f%n",
                      entity, precision, recall, f1, tp, fp, fn));
    return new Triple<>(precision * 100, recall * 100, f1 * 100);
  }

  /**
   * Serialize a sequence classifier to a file on the given path.
   *
   * @param serializePath The path/filename to write the classifier to.
   */
  public abstract void serializeClassifier(String serializePath);

  /** Serialize a sequence classifier to an object output stream **/
  public abstract void serializeClassifier(ObjectOutputStream oos);

  /**
   * Loads a classifier from the given input stream.
   * Any exceptions are rethrown as unchecked exceptions.
   * This method does not close the InputStream.
   *
   * @param in The InputStream to read from
   */
  public void loadClassifierNoExceptions(InputStream in, Properties props) {
    // load the classifier
    try {
      loadClassifier(in, props);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException cnfe) {
      throw new RuntimeException(cnfe);
    }
  }

  /**
   * Load a classifier from the specified InputStream. No extra properties are
   * supplied. This does not close the InputStream.
   *
   * @param in The InputStream to load the serialized classifier from
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public void loadClassifier(InputStream in) throws IOException, ClassCastException, ClassNotFoundException {
    loadClassifier(in, null);
  }

  /**
   * Load a classifier from the specified InputStream. The classifier is
   * reinitialized from the flags serialized in the classifier. This does not
   * close the InputStream.
   *
   * @param in The InputStream to load the serialized classifier from
   * @param props This Properties object will be used to update the
   *          SeqClassifierFlags which are read from the serialized classifier
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public void loadClassifier(InputStream in, Properties props) throws IOException, ClassCastException,
      ClassNotFoundException {
    loadClassifier(new ObjectInputStream(in), props);
  }

  /**
   * Load a classifier from the specified input stream. The classifier is
   * reinitialized from the flags serialized in the classifier.
   *
   * @param in The InputStream to load the serialized classifier from
   * @param props This Properties object will be used to update the
   *          SeqClassifierFlags which are read from the serialized classifier
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public abstract void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException,
      ClassNotFoundException;

  /**
   * Loads a classifier from the file specified by loadPath. If loadPath ends in
   * .gz, uses a GZIPInputStream, else uses a regular FileInputStream.
   */
  public void loadClassifier(String loadPath) throws ClassCastException, IOException, ClassNotFoundException {
    loadClassifier(loadPath, null);
    flags.loadClassifier = loadPath;
  }

  /**
   * Loads a classifier from the file, classpath resource, or URL specified by loadPath. If loadPath ends in
   * .gz, uses a GZIPInputStream.
   */
  public void loadClassifier(String loadPath, Properties props) throws ClassCastException, IOException, ClassNotFoundException {
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(loadPath)) {
      Timing t = new Timing();
      loadClassifier(is, props);
      flags.loadClassifier = loadPath;
      t.done(log, "Loading classifier from " + loadPath);
    }
  }

  public void loadClassifierNoExceptions(String loadPath) {
    loadClassifierNoExceptions(loadPath, null);
  }

  public void loadClassifierNoExceptions(String loadPath, Properties props) {
    try {
      loadClassifier(loadPath, props);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassCastException|ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public void loadClassifier(File file) throws ClassCastException, IOException, ClassNotFoundException {
    loadClassifier(file, null);
    flags.loadClassifier = file.toString();
  }

  /**
   * Loads a classifier from the file specified. If the file's name ends in .gz,
   * uses a GZIPInputStream, else uses a regular FileInputStream. This method
   * closes the File when done.
   *
   * @param file Loads a classifier from this file.
   * @param props Properties in this object will be used to overwrite those
   *          specified in the serialized classifier
   *
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public void loadClassifier(File file, Properties props) throws ClassCastException, IOException,
      ClassNotFoundException {
    Timing t = new Timing();
    BufferedInputStream bis;
    if (file.getName().endsWith(".gz")) {
      bis = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file)));
    } else {
      bis = new BufferedInputStream(new FileInputStream(file));
    }
    try {
      loadClassifier(bis, props);
      flags.loadClassifier = file.toString();
      t.done(log, "Loading classifier from " + file.getAbsolutePath());
    } finally {
      bis.close();
    }
  }

  public void loadClassifierNoExceptions(File file) {
    loadClassifierNoExceptions(file, null);
  }

  public void loadClassifierNoExceptions(File file, Properties props) {
    try {
      loadClassifier(file, props);
    } catch (Exception e) {
      log.info("Error deserializing " + file.getAbsolutePath());
      throw new RuntimeException(e);
    }
  }

  private transient PrintWriter cliqueWriter;
  private transient int writtenNum; // = 0;

  /** Print the String features generated from a IN */
  protected void printFeatures(IN wi, Collection<String> features) {
    if (flags.printFeatures == null || writtenNum >= flags.printFeaturesUpto) {
      return;
    }
    if (cliqueWriter == null) {
      cliqueWriter = IOUtils.getPrintWriterOrDie("features-" + flags.printFeatures + ".txt");
      writtenNum = 0;
    }
    if (wi instanceof CoreLabel) {
      cliqueWriter.print(wi.get(CoreAnnotations.TextAnnotation.class) + ' ' + wi.get(CoreAnnotations.PartOfSpeechAnnotation.class) + ' '
          + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + '\t');
    } else {
      cliqueWriter.print(wi.get(CoreAnnotations.TextAnnotation.class)
          + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + '\t');
    }
    boolean first = true;
    List<String> featsList = new ArrayList<>(features);
    Collections.sort(featsList);
    for (String feat : featsList) {
      if (first) {
        first = false;
      } else {
        cliqueWriter.print(" ");
      }
      cliqueWriter.print(feat);
    }
    cliqueWriter.println();
    writtenNum++;
  }

  /** Print the String features generated from a token. */
  protected void printFeatureLists(IN wi, Collection<Collection<String>> features) {
    if (flags.printFeatures == null || writtenNum >= flags.printFeaturesUpto) {
      return;
    }
    printFeatureListsHelper(wi, features);
  }

  // Separating this method out lets printFeatureLists be inlined, which is good since it is usually a no-op.
  private void printFeatureListsHelper(IN wi, Collection<Collection<String>> features) {
    if (cliqueWriter == null) {
      cliqueWriter = IOUtils.getPrintWriterOrDie("features-" + flags.printFeatures + ".txt");
      writtenNum = 0;
    }
    if (wi instanceof CoreLabel) {
      cliqueWriter.print(wi.get(CoreAnnotations.TextAnnotation.class) + ' ' + wi.get(CoreAnnotations.PartOfSpeechAnnotation.class) + ' '
          + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + '\t');
    } else {
      cliqueWriter.print(wi.get(CoreAnnotations.TextAnnotation.class)
          + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + '\t');
    }
    boolean first = true;
    for (Collection<String> featList : features) {
      List<String> sortedFeatList = new ArrayList<>(featList);
      Collections.sort(sortedFeatList);
      for (String feat : sortedFeatList) {
        if (first) {
          first = false;
        } else {
          cliqueWriter.print(" ");
        }
        cliqueWriter.print(feat);
      }
      cliqueWriter.print("  ");
    }
    cliqueWriter.println();
    writtenNum++;
  }

  public int windowSize() {
    return windowSize;
  }

}
