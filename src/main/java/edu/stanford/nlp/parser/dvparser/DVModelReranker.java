package edu.stanford.nlp.parser.dvparser;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.Reranker;
import edu.stanford.nlp.parser.lexparser.RerankerQuery;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.trees.DeepTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.Generics;

public class DVModelReranker implements Reranker {
  private final Options op;
  private final DVModel model;

  public DVModelReranker(DVModel model) {
    this.op = model.op;
    this.model = model;
  }

  public DVModel getModel() {
    return model;
  }

  public Query process(List<? extends HasWord> sentence) {
    return new Query();
  }

  public List<Eval> getEvals() {
    Eval eval = new UnknownWordPrinter(model);
    return Collections.singletonList(eval);
  }

  public class Query implements RerankerQuery {
    private final TreeTransformer transformer;
    private final DVParserCostAndGradient scorer;

    private List<DeepTree> deepTrees;

    public Query() {
      this.transformer = LexicalizedParser.buildTrainTransformer(op);
      this.scorer = new DVParserCostAndGradient(null, null, model, op);
      this.deepTrees = Generics.newArrayList();
    }

    public double score(Tree tree) {
      IdentityHashMap<Tree, SimpleMatrix> nodeVectors = Generics.newIdentityHashMap();
      Tree transformedTree = transformer.transformTree(tree);
      if (op.trainOptions.useContextWords) {
        Trees.convertToCoreLabels(transformedTree);
        transformedTree.setSpans();
      }
      double score = scorer.score(transformedTree, nodeVectors);
      deepTrees.add(new DeepTree(tree, nodeVectors, score));
      return score;
    }

    public List<DeepTree> getDeepTrees() {
      return deepTrees;
    }
  }

  private static final long serialVersionUID = 7897546308624261207L;

}
