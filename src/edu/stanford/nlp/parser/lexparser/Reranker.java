package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.metrics.Eval;

/**
 * A scorer which the RerankingParserQuery can use to rescore
 * sentences.  process(sentence) will be called with the words in the
 * sentence before score(tree) is called for any candidate trees for
 * that sentence.
 * <br>
 * For example, TaggerReranker is a Reranker that adds a score based
 * on how well a tree sentence matches the result of running a tagger,
 * although this does not help the basic parser.
 * <br>
 * We want the interface to be threadsafe, so process() should return
 * a RerankerQuery in a threadsafe manner.  The resulting
 * RerankerQuery should store any needed temporary data about the
 * sentence, etc.  For example, the TaggerReranker returns a
 * RerankerQuery which stores the output of the tagger.  This way,
 * subsequent calls to process() will not clobber existing data, and
 * the RerankerQuery can potentially have RerankerQuery.score() called
 * for different trees from different threads.
 * <br>
 * getEvals should return a list of Eval objects specific to this reranker.
 *
 * @author John Bauer
 */
public interface Reranker extends Serializable {
  RerankerQuery process(List<? extends HasWord> sentence);

  List<Eval> getEvals();
}
