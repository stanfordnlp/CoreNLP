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
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.Generics;


public class CombinedDVModelReranker implements Reranker {
  private final Options op;
  private final List<DVModel> models;
  
  public CombinedDVModelReranker(Options op, List<DVModel> models) {
    this.op = op;
    this.models = models;
  }

  public Query process(List<? extends HasWord> sentence) {
    return new Query();
  }

  public List<Eval> getEvals() {
    return Collections.emptyList();
  }

  public class Query implements RerankerQuery {
    private final TreeTransformer transformer;
    private final List<DVParserCostAndGradient> scorers;

    public Query() {
      this.transformer = LexicalizedParser.buildTrainTransformer(op);
      this.scorers = Generics.newArrayList();
      for (DVModel model : models) {
        this.scorers.add(new DVParserCostAndGradient(null, null, model, op));
      }
    }

    public double score(Tree tree) {
      double totalScore = 0.0;
      for (DVParserCostAndGradient scorer : scorers) {
        IdentityHashMap<Tree, SimpleMatrix> nodeVectors = Generics.newIdentityHashMap();
        Tree transformedTree = transformer.transformTree(tree);
        if (op.trainOptions.useContextWords) {
          Trees.convertToCoreLabels(transformedTree);
          transformedTree.setSpans();
        }
        double score = scorer.score(transformedTree, nodeVectors);
        totalScore += score;
        //totalScore = Math.max(totalScore, score);
      }
      return totalScore;
    }    
  }


}
