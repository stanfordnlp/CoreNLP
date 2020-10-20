package edu.stanford.nlp.parser.lexparser;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;


/** @author Dan Klein */
class ProjectionScorer implements Scorer {

  protected GrammarProjection gp;
  protected Scorer scorer;

  protected final Options op;

  protected Edge project(Edge edge) {
    Edge tempEdge = new Edge(op.testOptions.exhaustiveTest);
    tempEdge.start = edge.start;
    tempEdge.end = edge.end;
    tempEdge.state = gp.project(edge.state);
    tempEdge.head = edge.head;
    tempEdge.tag = edge.tag;
    return tempEdge;
  }

  protected Hook project(Hook hook) {
    Hook tempHook = new Hook(op.testOptions.exhaustiveTest);
    tempHook.start = hook.start;
    tempHook.end = hook.end;
    tempHook.state = gp.project(hook.state);
    tempHook.head = hook.head;
    tempHook.tag = hook.tag;
    tempHook.subState = gp.project(hook.subState);
    return tempHook;
  }

  public double oScore(Edge edge) {
    return scorer.oScore(project(edge));
  }

  public double iScore(Edge edge) {
    return scorer.iScore(project(edge));
  }

  public boolean oPossible(Hook hook) {
    return scorer.oPossible(project(hook));
  }

  public boolean iPossible(Hook hook) {
    return scorer.iPossible(project(hook));
  }

  public boolean parse(List<? extends HasWord> words) {
    return scorer.parse(words);
  }

  public ProjectionScorer(Scorer scorer, GrammarProjection gp, Options op) {
    this.scorer = scorer;
    this.gp = gp;
    this.op = op;
  }

} // end ProjectionScorer
