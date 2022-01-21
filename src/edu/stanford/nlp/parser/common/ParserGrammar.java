package edu.stanford.nlp.parser.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.Timing;
// TODO: it would be nice to move these to common, but that would
// wreck all existing models
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;

/**
 * An interface for the classes which store the data for a parser.
 * Objects which inherit this interface have a way to produce
 * ParserQuery objects, have a general Options object, and return a
 * list of Evals to perform on a parser.  This helps classes such as
 * {@link edu.stanford.nlp.parser.lexparser.EvaluateTreebank}
 * analyze the performance of a parser.
 *
 * TODO: it would be nice to actually make this an interface again.
 * Perhaps Java 8 will allow that
 *
 * @author John Bauer
 */
public abstract class ParserGrammar implements Function<List<? extends HasWord>, Tree>, ParserQueryFactory {

  private static Redwood.RedwoodChannels logger = Redwood.channels(ParserGrammar.class);

  public abstract ParserQuery parserQuery();

  /**
   * Parses the list of HasWord.  If the parse fails for some reason,
   * an X tree is returned instead of barfing.
   *
   * @param words The input sentence (a List of words)
   * @return A Tree that is the parse tree for the sentence.  If the parser
   *         fails, a new Tree is synthesized which attaches all words to the
   *         root.
   */
  @Override
  public Tree apply(List<? extends HasWord> words) {
    return parse(words);
  }

  /**
   * Tokenize the text using the parser's tokenizer
   */
  public List<? extends HasWord> tokenize(String sentence) {
    TokenizerFactory<? extends HasWord> tf = treebankLanguagePack().getTokenizerFactory();
    Tokenizer<? extends HasWord> tokenizer = tf.getTokenizer(new StringReader(sentence));
    List<? extends HasWord> tokens = tokenizer.tokenize();
    return tokens;
  }

  /**
   * Will parse the text in <code>sentence</code> as if it represented
   * a single sentence by first processing it with a tokenizer.
   */
  public Tree parse(String sentence) {
    List<? extends HasWord> tokens = tokenize(sentence);
    if (getOp().testOptions.preTag) {
      Function<List<? extends HasWord>, List<TaggedWord>> tagger = loadTagger();
      tokens = tagger.apply(tokens);
    }
    return parse(tokens);
  }

  private transient Function<List<? extends HasWord>, List<TaggedWord>> tagger;
  private transient String taggerPath;

  public Function<List<? extends HasWord>, List<TaggedWord>> loadTagger() {
    Options op = getOp();
    if (op.testOptions.preTag) {
      synchronized(this) { // TODO: rather coarse synchronization
        if (!op.testOptions.taggerSerializedFile.equals(taggerPath)) {
          taggerPath = op.testOptions.taggerSerializedFile;
          tagger = ReflectionLoading.loadByReflection("edu.stanford.nlp.tagger.maxent.MaxentTagger", taggerPath);
        }
        return tagger;
      }
    } else {
      return null;
    }
  }

  public List<CoreLabel> lemmatize(String sentence) {
    List<? extends HasWord> tokens = tokenize(sentence);
    return lemmatize(tokens);
  }

  /**
   * Only works on English, as it is hard coded for using the
   * Morphology class, which is English-only
   */
  public List<CoreLabel> lemmatize(List<? extends HasWord> tokens) {
    List<TaggedWord> tagged;
    if (getOp().testOptions.preTag) {
      Function<List<? extends HasWord>, List<TaggedWord>> tagger = loadTagger();
      tagged = tagger.apply(tokens);
    } else {
      Tree tree = parse(tokens);
      tagged = tree.taggedYield();
    }
    Morphology morpha = new Morphology();
    List<CoreLabel> lemmas = Generics.newArrayList();
    for (TaggedWord token : tagged) {
      CoreLabel label = new CoreLabel();
      label.setWord(token.word());
      label.setTag(token.tag());
      morpha.stem(label);
      lemmas.add(label);
    }
    return lemmas;
  }

  /**
   * Parses the list of HasWord.  If the parse fails for some reason,
   * an X tree is returned instead of barfing.
   *
   * @param words The input sentence (a List of words)
   * @return A Tree that is the parse tree for the sentence.  If the parser
   *         fails, a new Tree is synthesized which attaches all words to the
   *         root.
   */
  public abstract Tree parse(List<? extends HasWord> words);
  
  /**
   * Similar to parse(), but instead of returning an X tree on failure, returns null.
   */
  public abstract Tree parseTree(List<? extends HasWord> words);

  /**
   * Returns a list of extra Eval objects to use when scoring the parser.
   */
  public abstract List<Eval> getExtraEvals();

  /**
   * Return a list of Eval-style objects which care about the whole
   * ParserQuery, not just the finished tree
   */
  public abstract List<ParserQueryEval> getParserQueryEvals();

  public abstract Options getOp();

  public abstract TreebankLangParserParams getTLPParams();

  public abstract TreebankLanguagePack treebankLanguagePack();

  /**
   * Returns a set of options which should be set by default when used
   * in corenlp.  For example, the English PCFG/RNN models want
   * -retainTmpSubcategories, and the ShiftReduceParser models may
   * want -beamSize 4 depending on how they were trained.
   * <br>
   * TODO: right now completely hardcoded, should be settable as a training time option
   */
  public abstract String[] defaultCoreNLPFlags();

  public abstract void setOptionFlags(String ... flags);

  /**
   * The model requires text to be pretagged
   */
  public abstract boolean requiresTags();

  public static ParserGrammar loadModel(String path, String ... extraFlags) {
    ParserGrammar parser;
    try {
      Timing timing = new Timing();
      parser = IOUtils.readObjectFromURLOrClasspathOrFileSystem(path);
      timing.done(logger, "Loading parser from serialized file " + path);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
    if (extraFlags.length > 0) {
      parser.setOptionFlags(extraFlags);
    }
    return parser;
  }

  public static ParserGrammar loadModelFromZip(String zipFilename,
                                               String modelName) {
    Object object = null;
    try {
      File file = new File(zipFilename);
      if (file.exists()) {
        try (ZipFile zin = new ZipFile(file)) {
          ZipEntry zentry = zin.getEntry(modelName);
          if (zentry != null) {
            InputStream in = zin.getInputStream(zentry);
            // gunzip it if necessary
            if (modelName.endsWith(".gz")) {
              in = new GZIPInputStream(in);
            }
            ObjectInputStream ois = new ObjectInputStream(in);
            object = ois.readObject();

            ois.close();
            in.close();
          }
        }
      } else {
        throw new FileNotFoundException("Could not find " + modelName +
                                        " inside " + zipFilename);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    if (object instanceof ParserGrammar) {
      return (ParserGrammar) object;
    }
    throw new ClassCastException("Wanted LexicalizedParser, got " +
                                 object.getClass());
  }


}
