package edu.stanford.nlp.parser.lexparser;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.Tree;

/**
 * Gives a score to a Tree based on how well it matches the output of
 * a tagger.
 *
 * @author John Bauer
 */ 
public class TaggerReranker implements Reranker {
  MaxentTagger tagger;
  Options op;

  double weight = -1.0;

  public TaggerReranker(MaxentTagger tagger, Options op) {
    this.tagger = tagger;
    this.op = op;
  }

  public RerankerQuery process(List<? extends HasWord> sentence) {
    return new Query(tagger.tagSentence(sentence));
  }

  public List<Eval> getEvals() {
    return Collections.emptyList();
  }

  public class Query implements RerankerQuery {
    final List<TaggedWord> tagged;

    public Query(List<TaggedWord> tagged) {
      this.tagged = tagged;
    }

    public double score(Tree tree) {
      List<TaggedWord> yield = tree.taggedYield();
      int wrong = 0;
      int len = Math.min(yield.size(), tagged.size());
      for (int i = 0; i < len; ++i) {
        String yieldTag = op.langpack().basicCategory(yield.get(i).tag());
        if (!yieldTag.equals(tagged.get(i).tag())) {
          wrong++;
        }
      }

      return wrong * weight;
    }
  }

  private static final long serialVersionUID = 1;
}
