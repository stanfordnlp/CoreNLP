package edu.stanford.nlp.parser.eval;

import java.util.*;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;

/**
 * 
 * @author Spence Green
 *
 */
public class SimplePCFG {
  public final TwoDimensionalCounter<String,RuleRHS> ruleCtr;
  public final Counter<String> VnCtr;
  public final Counter<String> VtCtr;
  public final Set<Rule> ruleTypes;
  private final boolean SKIP_TERMINALS;
  private final TreeAnnotator ta;

  public SimplePCFG(TreebankLanguagePack tlp, boolean basicCat, boolean skipTerminals) {
    ruleCtr = new TwoDimensionalCounter<String,RuleRHS>();
    VnCtr = new ClassicCounter<String>();
    VtCtr = new ClassicCounter<String>();
    ruleTypes = new HashSet<Rule>();
    SKIP_TERMINALS = skipTerminals;
    ta = new TreeAnnotator(basicCat, tlp);
  }

  public void extract(Treebank tb) {
    System.out.print("Reading trees");
    int numTrees = 0;
    for(Tree t : tb) {
      numTrees++;
      t = ta.transformTree(t);
      
      for(Tree subtree : t) {
        String lhs = subtree.value();
        
        if(subtree.isLeaf()) {
          if( ! SKIP_TERMINALS) VtCtr.incrementCount(subtree.value());
          continue;
        
        } else {
          VnCtr.incrementCount(lhs);
          if(SKIP_TERMINALS && subtree.isPreTerminal()) 
            continue;
        }

        //RHS
        RuleRHS rhs = new RuleRHS(subtree.children());

        //LHS
        ruleCtr.incrementCount(lhs, rhs);
        
        ruleTypes.add(new Rule(lhs,rhs));
      }

      if((numTrees+1) % 200 == 0)
        System.out.print(".");
      if((numTrees+1) % 5000 == 0)
        System.out.println();
    }
    System.out.printf("\nRead %d trees\n",numTrees);
  }
  
  public void normalize() {
    for(String LHS : ruleCtr.firstKeySet())
      Counters.normalize(ruleCtr.getCounter(LHS));
  }

  public static class Rule {
    private final String rule;
    public Rule(String LHS, RuleRHS rhs) {
      StringBuilder sb = new StringBuilder();
      sb.append(LHS);
      for(String sym : rhs.getStates())
        sb.append(sym);
      rule = sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
      if(this == o) return true;
      else if( ! (o instanceof String)) return false;
      else return rule.equals((String) o);
    }
    
    @Override
    public int hashCode() { return rule.hashCode(); }
  }
  
  public static class RuleRHS {
    private final String[] m_rhs;
    private int m_hashcode = -1;

    public RuleRHS(Tree[] kids) {
      m_rhs = new String[kids.length];
      for(int i = 0; i < kids.length; i++) {
        m_rhs[i] = kids[i].value();
      }
    }

    public List<String> getStates() {
      return Arrays.asList(m_rhs);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for(int i = 0; i < m_rhs.length; i++)
        sb.append(m_rhs[i] + "\t");
      return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
      if(this == o) return true;
      else if( ! (o instanceof RuleRHS)) return false;
      else {
        RuleRHS otherRule = (RuleRHS) o;
        return Arrays.equals(m_rhs, otherRule.m_rhs);
      }
    }

    @Override
    public int hashCode() {
      if(m_hashcode == -1) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < m_rhs.length; i++)
          sb.append(m_rhs[i]);
        m_hashcode = sb.toString().hashCode();
      }
      return m_hashcode;
    }
  }

  private class TreeAnnotator implements TreeTransformer {

    private final boolean BASIC_CATEGORY;
    private final TreebankLanguagePack tlp;
    
    public TreeAnnotator(boolean basicCat, TreebankLanguagePack tlp) {
      BASIC_CATEGORY = basicCat;
      this.tlp = tlp;
    }
    
    /**
     * Does the annotation inplace.
     */
    public Tree transformTree(Tree t) {
      if(t.numChildren() != 1)
        throw new RuntimeException(String.format("Tree with more than one root substate\n%s",t.pennString()));

      transformTreeHelper(t.firstChild(), t.value());
      return t;
    }

    /**
     * Only annotates phrasal nodes.
     * 
     * @param t
     * @param parent
     */
    private void transformTreeHelper(Tree t, String parent) {
      if(t.isLeaf() || t.isPreTerminal()) return;

      if(t.isPhrasal()) 
        for(Tree kid : t.getChildrenAsList())
          transformTreeHelper(kid,t.value());

      String label = (BASIC_CATEGORY) ? tlp.basicCategory(t.value()) : t.value();
      String annotLabel = String.format("%s^%s",label,parent);

      t.setValue(annotLabel);
    }
  }

}
