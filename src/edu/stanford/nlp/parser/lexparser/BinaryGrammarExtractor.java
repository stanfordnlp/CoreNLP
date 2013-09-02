package edu.stanford.nlp.parser.lexparser;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

public class BinaryGrammarExtractor extends AbstractTreeExtractor<Pair<UnaryGrammar,BinaryGrammar>> {

  protected Index<String> stateIndex;
  private ClassicCounter<UnaryRule> unaryRuleCounter = new ClassicCounter<UnaryRule>();
  private ClassicCounter<BinaryRule> binaryRuleCounter = new ClassicCounter<BinaryRule>();
  protected ClassicCounter<String> symbolCounter = new ClassicCounter<String>();
  private Set<BinaryRule> binaryRules = new HashSet<BinaryRule>();
  private Set<UnaryRule> unaryRules = new HashSet<UnaryRule>();

  //  protected void tallyTree(Tree t, double weight) {
  //    super.tallyTree(t, weight);
  //    System.out.println("Tree:");
  //    t.pennPrint();
  //  }

  public BinaryGrammarExtractor(Options op, Index<String> index) {
    super(op);
    this.stateIndex = index;
  }


  @Override
  protected void tallyInternalNode(Tree lt, double weight) {
    if (lt.children().length == 1) {
      UnaryRule ur = new UnaryRule(stateIndex.indexOf(lt.label().value(), true),
                        stateIndex.indexOf(lt.children()[0].label().value(),
                                           true));
      symbolCounter.incrementCount(stateIndex.get(ur.parent), weight);
      unaryRuleCounter.incrementCount(ur, weight);
      unaryRules.add(ur);
    } else {
      BinaryRule br = new BinaryRule(stateIndex.indexOf(lt.label().value(), true),
                         stateIndex.indexOf(lt.children()[0].label().value(),
                                            true),
                         stateIndex.indexOf(lt.children()[1].label().value(),
                                            true));
      symbolCounter.incrementCount(stateIndex.get(br.parent), weight);
      binaryRuleCounter.incrementCount(br, weight);
      binaryRules.add(br);
    }
  }

  @Override
  public Pair<UnaryGrammar,BinaryGrammar> formResult() {
    stateIndex.indexOf(Lexicon.BOUNDARY_TAG, true);
    BinaryGrammar bg = new BinaryGrammar(stateIndex);
    UnaryGrammar ug = new UnaryGrammar(stateIndex);
    // add unaries
    for (UnaryRule ur : unaryRules) {
      ur.score = (float) Math.log(unaryRuleCounter.getCount(ur) / symbolCounter.getCount(stateIndex.get(ur.parent)));
      if (op.trainOptions.compactGrammar() >= 4) {
        ur.score = (float) unaryRuleCounter.getCount(ur);
      }
      ug.addRule(ur);
    }
    // add binaries
    for (BinaryRule br : binaryRules) {
      br.score = (float) Math.log((binaryRuleCounter.getCount(br) - op.trainOptions.ruleDiscount) / symbolCounter.getCount(stateIndex.get(br.parent)));
      if (op.trainOptions.compactGrammar() >= 4) {
        br.score = (float) binaryRuleCounter.getCount(br);
      }
      bg.addRule(br);
    }
    return new Pair<UnaryGrammar,BinaryGrammar>(ug, bg);
  }

} // end class BinaryGrammarExtractor
