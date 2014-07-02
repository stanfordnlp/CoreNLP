package edu.stanford.nlp.parser.common;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Function;
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
public abstract class ParserGrammar implements Function<List<? extends HasWord>, Tree> {
  public abstract ParserQuery parserQuery();

  /**
   * A convenience method which wraps the ParserQuery and returns a Tree
   */
  public abstract Tree apply(List<? extends HasWord> words);

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
      System.err.print("Loading parser from serialized file " + path + " ...");
      parser = IOUtils.readObjectFromURLOrClasspathOrFileSystem(path);
      timing.done();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
    if (extraFlags.length > 0) {
      parser.setOptionFlags(extraFlags);
    }
    return parser;
  }
  
}
