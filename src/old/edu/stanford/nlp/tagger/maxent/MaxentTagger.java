//MaxentTagger -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2009 Leland Stanford Junior University


//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//Support/Questions: java-nlp-user@lists.stanford.edu
//Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml


package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.io.IOUtils;
import old.edu.stanford.nlp.io.PrintFile;
import old.edu.stanford.nlp.io.RuntimeIOException;
import old.edu.stanford.nlp.ling.*;
import old.edu.stanford.nlp.objectbank.ObjectBank;
import old.edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import old.edu.stanford.nlp.objectbank.TokenizerFactory;
import old.edu.stanford.nlp.process.*;
import old.edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import old.edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter;
import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.util.Timing;
import old.edu.stanford.nlp.util.XMLUtils;

import java.io.*;
import java.util.*;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.DecimalFormat;


/**
 * The main class for users to run, train, and test the part of speech tagger.
 *
 * You can tag things through the Java API or from the command line.
 * The two English taggers included in this distribution are:
 * <ul>
 * <li> A bi-directional dependency network tagger in models/bidirectional-distsim-wsj-0-18.tagger.
 *      Its accuracy was 97.32% on Penn Treebank WSJ secs. 22-24.</li>
 * <li> A model using only left sequence information and similar but less
 *      unknown words and lexical features as the previous model in
 *      models/left3words-wsj-0-18.tagger. This tagger runs a lot faster.
 *      Its accuracy was 96.92% on Penn Treebank WSJ secs. 22-24.</li>
 * </ul>
 *
 * <h3>Using the Java API</h3>
 * <dl>
 * <dt>
 * A MaxentTagger can be made with a constructor taking as argument the location of parameter files for a trained tagger: </dt>
 * <dd> <code>MaxentTagger tagger = new MaxentTagger("models/left3words-wsj-0-18.tagger");</code></dd>
 * <p>
 * <dt>Alternatively, a constructor with no arguments can be used, which reads the parameters from a default location
 *  (which has to be set in the source file, and is set to a path that works on the Stanford NLP machines):</dt>
 * <dd><code>MaxentTagger tagger = new MaxentTagger(); </code></dd>
 * <p>
 * <dt>To tag a Sentence and get a TaggedSentence: </dt>
 * <dd><code>Sentence taggedSentence = maxentTagger.tagSentence(Sentence sentence)</code></dd>
 * <dd><code>Sentence taggedSentence = maxentTagger.apply(Sentence sentence)</code></dd>
 * <p>
 * <dt>To tag a list of sentences and get back a list of tagged sentences:
 * <dd><code> List taggedList = maxentTagger.process(List sentences)</code></dd>
 * <p>
 * <dt>To tag a String of text and to get back a String with tagged words:</dt>
 * <dd> <code>String taggedString = maxentTagger.tagString("Here's a tagged string.")</code></dd>
 * <p>
 * <dt>To tag a string of <i>correctly tokenized</i>, whitespace-separated words and get a string of tagged words back:</dt>
 * <dd> <code>String taggedString = maxentTagger.tagTokenizedString("Here 's a tagged string .")</code></dd>
 * </dl>
 * <p>
 * The <code>tagString</code> method uses the default tokenizer (PTBTokenizer).
 * If you wish to control tokenization, you may wish to call
 * {@link #tokenizeText(Reader, TokenizerFactory)} and then to call
 * <code>process()</code> on the result.
 * </p>
 *
 * <h3>Using the command line</h3>
 *
 * Tagging, testing, and training can all also be done via the command line.
 * <h3>Training from the command line</h3>
 * To train a model from the command line, first generate a property file:
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -genprops </pre>
 *
 * This gets you a default properties file with descriptions of each parameter you can set in
 * your trained model.  You can modify the properties file , or use the default options.  To train, run:
 * <pre>java -mx1g edu.stanford.nlp.tagger.maxent.MaxentTagger -props myPropertiesFile.props </pre>
 *
 *  with the appropriate properties file specified; any argument you give in the properties file can also
 *  be specified on the command line.  You must have specified a model using -model, either in the properties file
 *  or on the command line, as well as a file containing tagged words using -trainFile.
 *
 * <h3>Tagging and Testing from the command line</h3>
 *
 * Usage:
 * For tagging (plain text):
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -model &lt;modelFile&gt; -textFile &lt;textfile&gt; </pre>
 * For testing (evaluating against tagged text):
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -model &lt;modelFile&gt; -testFile &lt;testfile&gt; </pre>
 * You can use the same properties file as for training
 * if you pass it in with the "-props" argument. The most important
 * arguments for tagging (besides "model" and "file") are "tokenize"
 * and "tokenizerFactory". See below for more details.
 *
 * Note that the tagger assumes input has not yet been tokenized and by default tokenizes it using a default
 * English tokenizer.  If your input has already been tokenized, use the flag "-tokenized".
 *
 * <p> Parameters can be defined using a Properties file
 * (specified on the command-line with <code>-prop</code> <i>propFile</i>),
 * or directly on the command line (by preceding their name with a minust sign
 * ("-") to turn them into a flag. The following properties are recognized:
 * </p>
 * <table border="1">
 * <tr><td><b>Property Name</b></td><td><b>Type</b></td><td><b>Default Value</b></td><td><b>Relevant Phase(s)</b></td><td><b>Description</b></td></tr>
 * <tr><td>model</td><td>String</td><td>N/A</td><td>All</td><td>Path and filename where you would like to save the model (training) or where the model should be loaded from (testing, tagging).</td></tr>
 * <tr><td>trainFile</td><td>String</td><td>N/A</td><td>Train</td><td>Path to the file holding the training data; specifying this option puts the tagger in training mode.  Only one of 'trainFile','testFile','texFile', and 'convertToSingleFile' may be specified.</td></tr>
 * <tr><td>testFile</td><td>String</td><td>N/A</td><td>Test</td><td>Path to the file holding the test data; specifying this option puts the tagger in testing mode.  Only one of 'trainFile','testFile','texFile', and 'convertToSingleFile' may be specified.</td></tr>
 * <tr><td>textFile</td><td>String</td><td>N/A</td><td>Tag</td><td>Path to the file holding the text to tag; specifying this option puts the tagger in tagging mode.  Only one of 'trainFile','testFile','textFile', and 'convertToSingleFile' may be specified.</td></tr>
 * <tr><td>convertToSingleFile</td><td>String</td><td>N/A</td><td>N/A</td><td>Provided only for backwards compatibility, this option allows you to convert a tagger trained using a previous version of the tagger to the new single-file format.  The value of this flag should be the path for the new model file, 'model' should be the path prefix to the old tagger (up to but not including the ".holder"), and you should supply the properties configuration for the old tagger with -props (before these two arguments).</td></tr>
 * <tr><td>genprops</td><td>boolean</td><td>N/A</td><td>N/A</td><td>Use this option to output a default properties file, containing information about each of the possible configuration options.</td></tr>
 * <tr><td>delimiter</td><td>char</td><td>/</td><td>All</td><td>Delimiter character that separates word and part of speech tags.  For training and testing, this is the delimiter used in the train/test files.  For tagging, this is the character that will be inserted between words and tags in the output.</td></tr>
 * <tr><td>encoding</td><td>String</td><td>UTF-8</td><td>All</td><td>Encoding of the read files (training, testing) and the output text files.</td></tr>
 * <tr><td>tokenize</td><td>boolean</td><td>true</td><td>Tag,Test</td><td>Whether or not the file has been tokenized (so that white space separates all and only those things that should be tagged as separate words).</td></tr>
 * <tr><td>tokenizerFactory</td><td>String</td><td>edu.stanford.nlp.process.PTBTokenizer</td><td>Tag,Test</td><td>Fully qualified classname of the tokenizer to use.  edu.stanford.nlp.process.PTBTokenizer does basic English tokenization.</td></tr>
 * <tr><td>tokenizerOptions</td><td>String</td><td></td><td>Tag,Test</td><td>Known options for the particular tokenizer used. A comma-separated list. For PTBTokenizer, options of interest include <code>americanize=false</code> and <code>asciiQuotes</code> (for German). Note that any choice of tokenizer options that conflicts with the tokenization used in the tagger training data will likely degrade tagger performance.</td></tr>
 * <tr><td>arch</td><td>String</td><td>generic</td><td>Train</td><td>Architecture of the model, as a comma-separated list of options, some with a parenthesized integer argument written k here: this determines what features are sed to build your model.  Options are 'left3words', 'left5words', 'bidirectional', 'bidirectional5words', generic', 'sighan2005' (Chinese), 'german', 'words(k),' 'naacl2003unknowns', 'naacl2003conjunctions', wordshapes(k), motleyUnknown, suffix(k), prefix(k), prefixsuffix(k), capitalizationsuffix(k), distsim(s), chinesedictionaryfeatures(s), lctagfeatures, unicodeshapes(k). The left3words architectures are faster, but slightly less accurate, than the bidirectional architectures.  'naacl2003unknowns' was our traditional set of unknown word features, but you can now specify features more flexibility via the various other supported keywords. The 'shapes' options map words to equivalence classes, which slightly increase accuracy.</td></tr>
 * <tr><td>lang</td><td>String</td><td>english</td><td>Train</td><td>Language from which the part of speech tags are drawn. This option determines which tags are considered closed-class (only fixed set of words can be tagged with a closed-class tag, such as prepositions). Defined languages are 'english' (Penn tagset), 'polish' (very rudimentary), 'chinese', 'arabic', 'german', and 'medline'.  </td></tr>
 * <tr><td>openClassTags</td><td>String</td><td>N/A</td><td>Train</td><td>Space separated list of tags that should be considered open-class.  All tags encountered that are not in this list are considered closed-class.  E.g. format: "NN VB"</td></tr>
 * <tr><td>closedClassTags</td><td>String</td><td>N/A</td><td>Train</td><td>Space separated list of tags that should be considered closed-class.  All tags encountered that are not in this list are considered open-class.</td></tr>
 * <tr><td>learnClosedClassTags</td><td>boolean</td><td>false</td><td>Train</td><td>If true, induce which tags are closed-class by counting as closed-class tags all those tags which have fewer unique word tokens than closedClassTagThreshold. </td></tr>
 * <tr><td>closedClassTagThreshold</td><td>int</td><td>int</td><td>Train</td><td>Number of unique word tokens that a tag may have and still be considered closed-class; relevant only if learnClosedClassTags is true.</td></tr>
 * <tr><td>sgml</td><td>boolean</td><td>false</td><td>Tag, Test</td><td>Very basic tagging of the contents of all sgml fields; for more complex mark-up, consider using the xmlInput option.</td></tr>
 * <tr><td>xmlInput</td><td>String</td><td></td><td>Tag, Test</td><td>Give a space separated list of tags in an XML file whose content you would like tagged.  Any internal tags that appear in the content of fields you would like tagged will be discarded; the rest of the XML will be preserved and the original text of specified fields will be replaced with the tagged text.</td></tr>
 * <tr><td>xmlOutput</td><td>String</td><td>""</td><td>Tag</td><td>If a path is given, the tagged data be written out to the given file in xml.  If non-empty, each word will be written out within a word tag, with the part of speech as an attribute.  If original input was XML, this will just appear in the field where the text originally came from.  Otherwise, word tags will be surrounded by sentence tags as well.  E.g., &lt;sentence id="0"&gt;&lt;word id="0" pos="NN"&gt;computer&lt;/word&gt;&lt;/sentence&gt;</td></tr>
 * <tr><td>tagInside</td><td>String</td><td>""</td><td>Tag</td><td>Tags inside elements that match the regular expression given in the String.</td></tr>
 * <tr><td>search</td><td>String</td><td>cg</td><td>Train</td><td>Specify the search method to be used in the optimization method for training.  Options are 'cg' (conjugate gradient) or 'iis' (improved iterative scaling).</td></tr>
 * <tr><td>sigmaSquared</td><td>double</td><td>0.5</td><td>Train</td><td>Sigma-squared smoothing/regularization parameter to be used for conjugate gradient search.  Default usually works reasonably well.</td></tr>
 * <tr><td>iterations</td><td>int</td><td>100</td><td>Train</td><td>Number of iterations to be used for improved iterative scaling.</td></tr>
 * <tr><td>rareWordThresh</td><td>int</td><td>5</td><td>Train</td><td>Words that appear fewer than this number of times during training are considered rare words and use extra rare word features.</td></tr>
 * <tr><td>minFeatureThreshold</td><td>int</td><td>5</td><td>Train</td><td>Features whose history appears fewer than this number of times are discarded.</td></tr>
 * <tr><td>curWordMinFeatureThreshold</td><td>int</td><td>2</td><td>Train</td><td>Words that occur more than this number of times will generate features with all of the tags they've been seen with.</td></tr>
 * <tr><td>rareWordMinFeatureThresh</td><td>int</td><td>10</td><td>Train</td><td>Features of rare words whose histories occur fewer than this number of times are discarded.</td></tr>
 * <tr><td>veryCommonWordThresh</td><td>int</td><td>250</td><td>Train</td><td>Words that occur more than this number of times form an equivalence class by themselves.  Ignored unless you are using ambiguity classes.</td></tr>
 * <tr><td>debug</td><td>boolean</td><td>boolean</td><td>All</td><td>Whether to write debugging information (words, top words, unknown words).  Useful for error analysis.</td></tr>
 * <tr><td>debugPrefix</td><td>String</td><td>N/A</td><td>All</td><td>File (path) prefix for where to write out the debugging information (relevant only if debug=true).</td></tr>
 * </table>
 * <p/>
 *
 * @author Kristina Toutanova
 * @author Miler Lee
 * @author Joseph Smarr
 * @author Anna Rafferty
 * @author Michel Galley
 * @author Christopher Manning
 */
public class MaxentTagger implements Function<Sentence<? extends HasWord>,Sentence<TaggedWord>>, SentenceProcessor, ListProcessor<Sentence<? extends HasWord>,Sentence<TaggedWord>> {

  // TODO: Add a flag to lemmatize words (Morphology class) on output of tagging

  private static boolean isInitialized; // = false;

  public static final String DEFAULT_NLP_GROUP_MODEL_PATH = "/u/nlp/data/pos-tagger/wsj3t0-18-left3words/left3words-wsj-0-18.tagger";
  public static final String DEFAULT_DISTRIBUTION_PATH = "models/left3words-wsj-0-18.tagger";
  public static final String DEFAULT_JAR_PATH =
          "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";

  private static TestSentence ts;


  /**
   * Constructor for a tagger using a model stored in a particular file.
   * The <code>modelFile</code> is a filename for the model data.
   * The tagger data is loaded when the
   * constructor is called (this can be slow).
   * This constructor first constructs a TaggerConfig object, which loads
   * the tagger options from the modelFile.
   * <p>
   * The tagger does not support
   * multithreaded operation.  Since some of the data
   * for the tagger is static, two different taggers cannot exist at
   * the same time.
   *
   * @param modelFile filename of the trained model
   * @throws Exception if IO problem
   */
  public MaxentTagger(String modelFile) throws Exception {
    TaggerConfig config = new TaggerConfig(new String[] {"-model", modelFile});
    init(modelFile, config);
  }

  /**
   * Constructor for a tagger using a model stored in a particular file,
   * with options taken from the supplied TaggerConfig.
   * The <code>modelFile</code> is a filename for the model data.
   * The tagger data is loaded when the
   * constructor is called (this can be slow).
   * This version assumes that the tagger options in the modelFile have
   * already been loaded into the TaggerConfig (if that is desired).
   * <p>
   * The tagger does not support
   * multithreaded operation.  Since some of the data
   * for the tagger is static, two different taggers cannot exist at
   * the same time.
   *
   * @param modelFile filename of the trained model
   * @param config The configuration for the tagger
   * @throws Exception if IO problem
   */
  public MaxentTagger(String modelFile, TaggerConfig config) throws Exception {
    init(modelFile, config);
  }

  /**
   * Static initializer that loads the tagger.  This maintains a flag as
   * to whether initialization has been done previously, and if so,
   * running this is a no-op.
   *
   * @param config TaggerConfig based on command-line arguments
   * @param modelFile Where to initialize the tagger from.
   *        Most commonly, this is the filename of the trained model, for example, <code>
   *        /u/nlp/data/pos-tagger/wsj3t0-18-left3words/left3words-wsj-0-18.tagger
   *        </code>.  However, if it starts with "https?://" it will be
   *        interpreted as a URL, and if it starts with "jar:" it will be
   *        taken as a resources in the /models/ path of the current jar file.
   * @throws Exception if IO problem
   */
  private static void init(String modelFile, TaggerConfig config) throws Exception {
    if ( ! isInitialized) {
      GlobalHolder.readModelAndInit(config, modelFile, true);
      ts = new TestSentence(GlobalHolder.getLambdaSolve());
      isInitialized = true;
    }
  }


  @SuppressWarnings({"MethodMayBeStatic"})
  public TestSentence getTestSentence() {
    return ts;
  }


  /**
   * Tags the tokenized input string and returns the tagged version.
   * This method requires the input to already be tokenized.
   * The tagger wants input that is whitespace separated tokens, tokenized
   * according to the conventions of the training data. (For instance,
   * for the Penn Treebank, punctuation marks and possessive "'s" should
   * be separated from words.)
   *
   * @param toTag The untagged input String
   * @return The same string with tags inserted in the form word/tag
   * @throws Exception If there are IO errors or class initialization problems
   */
  public static synchronized String tagTokenizedString(String toTag) throws Exception {

    if ( ! isInitialized) {
      new MaxentTagger(DEFAULT_NLP_GROUP_MODEL_PATH); // initialize static data structures
    }

    if (isInitialized) {
      try {
        Sentence<Word> sent = Sentence.toSentence(Arrays.asList(toTag.split("\\s+")));
        ts.tagSentence(sent);
        return ts.getTaggedNice();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * Tags the input string and returns the tagged version.
   * This method tokenizes the input into words in perhaps multiple sentences
   * and then tags those sentences.  The default (PTB English)
   * tokenizer is used.
   * <p>
   * Note that this method is static and the model used, etc., will depend on what
   * was set up in an earlier call to the class constructor!
   *
   * @param toTag The untagged input String
   * @return A String of sentences with tags inserted in the form word/tag
   */
  public static synchronized String tagString(String toTag) {
    TaggerWrapper tw = new TaggerWrapper();
    return tw.apply(toTag);
  }

  /**
   * Expects a sentence and returns a tagged sentence.  The input Sentence items
   *
   *
   * @param in This needs to be a Sentence
   * @return A Sentence of TaggedWord
   */
  public synchronized Sentence<TaggedWord> apply(Sentence<? extends HasWord> in) {
    try {
      return ts.tagSentence(in);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tags the Words in each Sentence in the given List with their
   * grammatical part-of-speech. The returned List contains Sentences
   * consisting of TaggedWords.
   * <p><b>NOTE: </b>The input document must contain sentences as its elements,
   * not words. To turn a Document of words into a Document of sentences, run
   * it through {@link WordToSentenceProcessor}.
   *
   * @param sentences A List of Sentence
   * @return A List of Sentence of TaggedWord (final generification cannot be listed due to lack of complete generification of super classes)
   */
  public List<Sentence<TaggedWord>> process(List<? extends Sentence<? extends HasWord>> sentences) {
    List<Sentence<TaggedWord>> taggedSentences = new ArrayList<Sentence<TaggedWord>>();

    for (Sentence<? extends HasWord> sentence : sentences) {
      taggedSentences.add(ts.tagSentence(sentence));
    }
    return taggedSentences;
  }


  /**
   * Returns a new Sentence that is a copy of the given sentence with all the
   * words tagged with their part-of-speech. Convenience method when you only
   * want to tag a single Sentence instead of a Document of sentences.
   */
  @SuppressWarnings({"unchecked"})
  public Sentence<TaggedWord> processSentence(Sentence sentence) {
    return tagSentence(sentence);
  }


  /**
   * Returns a new Sentence that is a copy of the given sentence with all the
   * words tagged with their part-of-speech. Convenience method when you only
   * want to tag a single Sentence instead of a Document of sentences.
   * @param sentence sentence to tag
   * @return tagged sentence
   */
  public static Sentence<TaggedWord> tagSentence(List<? extends HasWord> sentence) {
    return ts.tagSentence(sentence);
  }


  // NOTE: This method is used in the TaggerDemo code.  Please don't delete it.
  /**
   * Reads data from r, tokenizes it with the default (Penn Treebank)
   * tokenizer, and returns a List of Sentence objects, which can
   * then be fed into tagSentence.
   *
   * @param r Reader where untokenized text is read
   * @return List of tokenized sentences
   */
  public static List<Sentence<? extends HasWord>> tokenizeText(Reader r) {
    return tokenizeText(r, null);
  }


  /**
   * Reads data from r, tokenizes it with the given tokenizer, and
   * returns a List of Lists of (extends) HasWord objects, which can then be
   * fed into tagSentence.
   *
   * @param r Reader where untokenized text is read
   * @param tokenizerFactory Tokenizer.  This can be <code>null</code> in which case
   *     the default English tokenizer (PTBTokenizerFactory) is used.
   * @return List of tokenized sentences
   */
  @SuppressWarnings({"unchecked"})
  protected static List<Sentence<? extends HasWord>> tokenizeText(Reader r, TokenizerFactory tokenizerFactory) {
    DocumentPreprocessor documentPreprocessor = (tokenizerFactory == null) ?
            new DocumentPreprocessor(): new DocumentPreprocessor(tokenizerFactory);
    List<List<? extends HasWord>> lis = documentPreprocessor.getSentencesFromText(r);
    List<Sentence<? extends HasWord>> out = new ArrayList<Sentence<? extends HasWord>>(lis.size());
    for (List<? extends HasWord> item : lis) {
      out.add(new Sentence(item));
    }
    return out;
  }


  /**
   * This method reads in a file in the old multi-file format and saves it to a single file
   * named newName.  The resulting model can then be used with the current architecture. A
   * model must be specified in config that corresponds to the model prefix of the existing
   * multi-file tagger. The new file will be saved to the path specified for the property
   * "convertToSingleFile".
   *
   * @param config The processed form of the command-line arguments.
   */
  private static void convertToSingleFileFormat(TaggerConfig config) {
    try {
      config.dump();
      GlobalHolder.convertMultifileTagger(config.getModel() + ".holder", config.getFile(), config);
    } catch (Exception e) {
      System.err.println("An error occurred while converting to the new tagger format.");
      e.printStackTrace();
    }

  }

  private static void dumpModel(TaggerConfig config) {
    try {
      TaggerConfig fileConfig = GlobalHolder.readModelAndInit(config, config.getFile(), false);
      System.err.println("Serialized tagger built with config:");
      fileConfig.dump();
      GlobalHolder.dumpModel();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Tests a tagger on data with gold tags available.  This is TEST mode.
   *
   * @param config Properties giving parameters for the testing run
   */
  private static void runTest(TaggerConfig config) {
    if (config.getVerbose()) {
      System.err.println("## tagger testing invoked at " + new Date() + " with arguments:");
      config.dump();
    }
    if (config.getDebug()) {
      TestClassifier.setDebug(true);
    }

    try {
      GlobalHolder.readModelAndInit(config, config.getModel(), true);

      Timing t = new Timing();
      TestClassifier tC = new TestClassifier(config);
      long millis = t.stop();
      printErrWordsPerSec(millis, tC.getNumWords());
      tC.printModelAndAccuracy(config);
    } catch (Exception e) {
      System.err.println("An error occured while testing the tagger.");
      e.printStackTrace();
    }
  }

  /**
   * Trains a tagger model.
   *
   * @param config Properties giving parameters for the training run
   */
  private static void runTraining(TaggerConfig config) {
    Date now = new Date();

    System.err.println("## tagger training invoked at " + now + " with arguments:");
    config.dump();
    Timing tim = new Timing();
    try {
      PrintFile log = new PrintFile(config.getModel() + ".props");
      log.println("## tagger training invoked at " + now + " with arguments:");
      config.dump(log);
      log.close();

      TestClassifier.trainAndSaveModel(config);
      tim.done("Training POS tagger");
    } catch(Exception e) {
      System.err.println("An error occurred while training a new tagger.");
      e.printStackTrace();
    }
  }


  private static void printErrWordsPerSec(long milliSec, int numWords) {
    double wordspersec = numWords / (((double) milliSec) / 1000);
    NumberFormat nf = new DecimalFormat("0.00");
    System.err.println("Tagged " + numWords + " words at " +
        nf.format(wordspersec) + " words per second.");
  }


  static class TaggerWrapper implements Function<String, String> {

    private final TaggerConfig config;
    private TokenizerFactory tokenizerFactory;
    private int sentNum = 0;

    protected TaggerWrapper() {
      this(null);
    }

    protected TaggerWrapper(TaggerConfig config) {
      this.config = config;
      if (config != null) {
        if (config.getTokenizerFactory().trim().length() != 0) {
          try {
            tokenizerFactory = (TokenizerFactory) Class.forName(config.getTokenizerFactory()).newInstance();
          } catch (Exception e) {
            System.err.println("Error in tokenizer factory instantiation for class: " + config.getTokenizerFactory());
            e.printStackTrace();
            tokenizerFactory = PTBTokenizerFactory.newWordTokenizerFactory(config.getTokenizerOptions());
          }
        } else if (config.getTokenize()){
          tokenizerFactory = PTBTokenizerFactory.newWordTokenizerFactory(config.getTokenizerOptions());
        }
      } else {
        tokenizerFactory = PTBTokenizerFactory.newWordTokenizerFactory("");
      }
    }

    public String apply(String o) {
      StringBuilder taggedSentence = new StringBuilder();
      int outputStyle;
      boolean tokenize;
      if (config != null) {
        outputStyle = PlainTextDocumentReaderAndWriter.asIntOutputFormat(config.getOutputFormat());
        tokenize = config.getTokenize();
      } else {
        outputStyle = PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_SLASH_TAGS;
        tokenize = true;
      }
      if (tokenize) {
        Reader r = new StringReader(o);
        List<Sentence<? extends HasWord>> l = tokenizeText(r, tokenizerFactory);

        for (Sentence<? extends HasWord> s : l) {
          Sentence<TaggedWord> taggedSentenceTok =  ts.tagSentence(s);
          if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_TSV) {
            taggedSentence.append(getTsvWords(taggedSentenceTok));
          } else if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_XML ||
            outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_INLINE_XML) {
            taggedSentence.append(getXMLWords(taggedSentenceTok, sentNum++));
          } else { // if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_SLASH_TAGS) {
              taggedSentence.append(taggedSentenceTok.toString(false)).append(' ');
          }
        }
      } else {
        Sentence<Word> sent = Sentence.toSentence(Arrays.asList(o.split("\\s+")));
        ts.tagSentence(sent);

        if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_TSV) {
          taggedSentence.append(getTsvWords(ts.getTaggedSentence()));
        } else if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_XML ||
                   outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_INLINE_XML) {
          taggedSentence.append(getXMLWords(ts.getTaggedSentence(), sentNum++));
        } else { // if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_SLASH_TAGS) {
          taggedSentence.append(ts.getTaggedNice()).append(' ');
        }
      }
      return taggedSentence.toString();
    }

  } // end static class TaggerWrapper


  private static String getXMLWords(Sentence<TaggedWord> s, int sentNum) {
    StringBuilder sb = new StringBuilder();
    sb.append("<sentence id=\"").append(sentNum).append("\">\n");
    for (int i = 0, sz = s.size(); i < sz; i++) {
      String word = s.get(i).word();
      String tag = s.get(i).tag();
      sb.append("  <word wid=\"").append(i).append("\" pos=\"").append(XMLUtils.escapeAttributeXML(tag)).append("\">").append(XMLUtils.escapeElementXML(word)).append("</word>\n");
    }
    sb.append("</sentence>\n");
    return sb.toString();
  }

  private static String getTsvWords(Sentence<TaggedWord> s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, sz = s.size(); i < sz; i++) {
      String word = s.get(i).word();
      String tag = s.get(i).tag();
      sb.append(word).append('\t').append(tag).append('\n');
    }
    sb.append('\n');
    return sb.toString();
  }

  /**
   * Takes a tagged sentence and writes out the xml version.
   *
   * @param w Where to write the output to
   * @param s A tagged sentence
   * @param sentNum The sentence index for XML printout
   */
  private static void writeXMLSentence(Writer w, Sentence<TaggedWord> s, int sentNum) {
    try {
      w.write(getXMLWords(s, sentNum));
    } catch (IOException e) {
      System.err.println("Error writing sentence " + sentNum + ": " + s.toString(false));
      throw new RuntimeIOException(e);
    }
  }

  private static void tagFromXML(TaggerConfig config) {
    InputStream is = null;
    Writer w = null;
    try {
      is = new BufferedInputStream(new FileInputStream(config.getFile()));
      String outFile = config.getOutputFile();
      if (outFile.length() > 0) {
        w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), config.getEncoding()));
      } else {
        w = new PrintWriter(System.out);
      }
      TransformXML<String> txml = new TransformXML<String>();
      txml.transformXML(config.getXMLInput(), new TaggerWrapper(config), is, w, new TaggerSaxInterface<String>());
    } catch (FileNotFoundException e) {
      System.err.println("Input file not found: " + config.getFile());
      e.printStackTrace();
    } catch (IOException ioe) {
      System.err.println("tagFromXML: mysterious IO Exception");
      ioe.printStackTrace();
    } finally {
      IOUtils.closeIgnoringExceptions(is);
      IOUtils.closeIgnoringExceptions(w);
    }
  }

  /**
   * Runs the tagger when we're in TAG mode.
   *
   * @param config The configuration parameters for the run.
   */
  @SuppressWarnings({"unchecked", "UnusedDeclaration"})
  private static void runTagger(TaggerConfig config) {
    if (config.getVerbose()) {
      Date now = new Date();
      System.err.println("## tagger invoked at " + now + " with arguments:");
      config.dump();
    }
    BufferedWriter writer = null;
    try {
      MaxentTagger tagger = new MaxentTagger(config.getModel(), config);

      Timing t = new Timing();
      String sentenceDelimiter = null;
      final TokenizerFactory<? extends HasWord> tokenizerFactory; // initialized immediately below
      if (config.getTokenize() && config.getTokenizerFactory().trim().length() != 0) {
        Class<TokenizerFactory<? extends HasWord>> clazz = (Class<TokenizerFactory<? extends HasWord>>) Class.forName(config.getTokenizerFactory().trim());
        Method factoryMethod = clazz.getMethod("newTokenizerFactory");
        tokenizerFactory = (TokenizerFactory<? extends HasWord>) factoryMethod.invoke(null);
      } else if (config.getTokenize()){
        tokenizerFactory = PTBTokenizerFactory.newWordTokenizerFactory(config.getTokenizerOptions());
      } else {
        tokenizerFactory = WhitespaceTokenizer.factory();
        sentenceDelimiter = "\n";
      }
      final DocumentPreprocessor docProcessor = new DocumentPreprocessor(tokenizerFactory);
      docProcessor.setEncoding(config.getEncoding());

      //Counts
      int numWords = 0;
      int numSentences = 0;
      String outFile = config.getOutputFile();

      if (outFile.length() > 0) {
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), config.getEncoding()));
      } else {
        writer = new BufferedWriter(new OutputStreamWriter(System.out, config.getEncoding()));
      }

      String[] xmlInput = config.getXMLInput();
      if (xmlInput.length > 0) {
        if(xmlInput.length > 1 || !xmlInput[0].equals("null")) {
          tagFromXML(config);
          return;
        }
      }
      boolean stdin = config.getFile().trim().equalsIgnoreCase("stdin");

      while (true) {
        //Now determine if we're tagging from stdin or from a file
        BufferedReader br;
        if (!stdin) {
          br = IOUtils.readReaderFromString(config.getFile(), config.getEncoding());
        } else {
          System.err.println("Type some text to tag, then EOF.");
          System.err.println("  (For EOF, use Return, Ctrl-D on Unix; Enter, Ctrl-Z, Enter on Windows.)");
          br = new BufferedReader(new InputStreamReader(System.in));
        }

        int outputStyle = PlainTextDocumentReaderAndWriter.asIntOutputFormat(config.getOutputFormat());
        if (config.getSGML()) {
          // this uses NER codebase technology to read/write SGML-ish files
          PlainTextDocumentReaderAndWriter readerAndWriter = new PlainTextDocumentReaderAndWriter();
          ObjectBank<List<CoreLabel>> ob = new ObjectBank<List<CoreLabel>>(new ReaderIteratorFactory(br), readerAndWriter);
          PrintWriter pw = new PrintWriter(writer);
          for (List<CoreLabel> sentence : ob) {
            Sentence<CoreLabel> s = new Sentence<CoreLabel>(sentence);
            numWords += s.length();
            Sentence<TaggedWord> taggedSentence = MaxentTagger.tagSentence(s);
            Iterator<CoreLabel> origIter = sentence.iterator();
            for (TaggedWord tw : taggedSentence) {
              CoreLabel cl = origIter.next();
              cl.set(CoreAnnotations.AnswerAnnotation.class, tw.tag());
            }
            readerAndWriter.printAnswers(sentence, pw, outputStyle, true);
          }
        } else {
          //Now we do everything through the doc preprocessor
          List<List<? extends HasWord>> document;
          if ((config.getTagInside() != null && !config.getTagInside().equals(""))) {
            document = docProcessor.getSentencesFromXML(br, config.getTagInside(), null, false);
          } else if (stdin) {
            document = docProcessor.getSentencesFromText(new StringReader(br.readLine()));
          } else {
            document = docProcessor.getSentencesFromText(br, sentenceDelimiter);
          }

          for (List<? extends HasWord> sentence : document) {
            numWords += sentence.size();
            Sentence<TaggedWord> taggedSentence = MaxentTagger.tagSentence(sentence);

            if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_TSV) {
              writer.write(getTsvWords(taggedSentence));
            } else if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_XML) {
              writeXMLSentence(writer, taggedSentence, numSentences);
            } else { // if (outputStyle == PlainTextDocumentReaderAndWriter.OUTPUT_STYLE_SLASH_TAGS) {
              writer.write(taggedSentence.toString(false));
              writer.newLine();
            }
            if (stdin) {
              writer.newLine();
              writer.flush();
            }
            numSentences++;
          }
        }
        if (!stdin) break;
      }
      long millis = t.stop();
      printErrWordsPerSec(millis, numWords);
    } catch (Exception e) {
      System.err.println("An error occurred while tagging.");
      e.printStackTrace();
    } finally {
      IOUtils.closeIgnoringExceptions(writer);
    }
  }


  /**
   * Command-line tagger interface.
   * Can be used to train or test taggers, or to tag text, taking input from
   * stdin or a file.
   * See class documentation for usage.
   *
   * @param args Command-line arguments
   * @throws IOException If any file problems
   */
  public static void main(String[] args) throws IOException {
    TaggerConfig config = new TaggerConfig(args);

    if (config.getMode() == TaggerConfig.Mode.TRAIN) {
      runTraining(config);
    } else if (config.getMode() == TaggerConfig.Mode.TAG) {
      runTagger(config);
    } else if (config.getMode() == TaggerConfig.Mode.TEST) {
      runTest(config);
    } else if (config.getMode() == TaggerConfig.Mode.CONVERT) {
      convertToSingleFileFormat(config);
    } else if (config.getMode() == TaggerConfig.Mode.DUMP) {
      dumpModel(config);
    } else {
      System.err.println("Impossible: nothing to do. None of train, tag, test, or convert was specified.");
    }
  } // end main()

}
