package edu.stanford.nlp.parser.lexparser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;


/** @author Dan Klein */
public class XBarGrammarProjection implements GrammarProjection {

    UnaryGrammar sourceUG;
    BinaryGrammar sourceBG;
    Index<String> sourceStateIndex;

    UnaryGrammar targetUG;
    BinaryGrammar targetBG;
    Index<String> targetStateIndex;

    int[] projection;

    public int project(int state) {
      return projection[state];
    }

    public UnaryGrammar sourceUG() {
      return sourceUG;
    }

    public BinaryGrammar sourceBG() {
      return sourceBG;
    }

    public UnaryGrammar targetUG() {
      return targetUG;
    }

    public BinaryGrammar targetBG() {
      return targetBG;
    }


    protected static String projectString(String str) {
      if (str.indexOf('@') == -1) {
        if (str.indexOf('^') == -1) {
          return str;
        }
        return str.substring(0, str.indexOf('^'));
      }
      StringBuilder sb = new StringBuilder();
      sb.append(str.substring(0, str.indexOf(' ')));
      // if (str.indexOf('^') > -1) {
        //sb.append(str.substring(str.indexOf('^'),str.length()));
      // }
      int num = -2;
      for (int i = 0; i < str.length(); i++) {
        if (str.charAt(i) == ' ') {
          num++;
        }
      }
      sb.append(" w ").append(num);
      return sb.toString();
    }

    protected void scanStates(Index<String> source, Index<String> target) {
      for (int i = 0; i < source.size(); i++) {
        String stateStr = source.get(i);
        String projStr = projectString(stateStr);
        projection[i] = target.indexOf(projStr, true);
      }
    }

    protected BinaryRule projectBinaryRule(BinaryRule br) {
      return new BinaryRule(projection[br.parent], projection[br.leftChild], projection[br.rightChild], br.score);
    }

    protected UnaryRule projectUnaryRule(UnaryRule ur) {
      return new UnaryRule(projection[ur.parent], projection[ur.child], ur.score);
    }

    public XBarGrammarProjection(BinaryGrammar bg, UnaryGrammar ug, Index<String> stateIndex) {
      Map<BinaryRule,BinaryRule> binaryRules = new HashMap<BinaryRule,BinaryRule>();
      Map<UnaryRule,UnaryRule> unaryRules = new HashMap<UnaryRule,UnaryRule>();
      sourceUG = ug;
      sourceBG = bg;
      sourceStateIndex = stateIndex;
      targetStateIndex = new HashIndex<String>();
      projection = new int[sourceStateIndex.size()];
      scanStates(sourceStateIndex, targetStateIndex);
      targetBG = new BinaryGrammar(targetStateIndex);
      targetUG = new UnaryGrammar(targetStateIndex);
      for (BinaryRule br : bg) {
        BinaryRule rule = projectBinaryRule(br);
        BinaryRule old = binaryRules.get(rule);
        if (old == null || rule.score > old.score) {
          binaryRules.put(rule, rule);
        }
      }
      for (BinaryRule br : binaryRules.keySet()) {
        targetBG.addRule(br);
        //System.out.println("BR: "+targetStateIndex.get(br.parent)+" -> "+targetStateIndex.get(br.leftChild)+" "+targetStateIndex.get(br.rightChild)+" %% "+br.score);
      }
      targetBG.splitRules();
      for (int parent = 0; parent < sourceStateIndex.size(); parent++) {
        for (Iterator<UnaryRule> urI = ug.ruleIteratorByParent(parent); urI.hasNext();) {
          UnaryRule sourceRule = urI.next();
          UnaryRule rule = projectUnaryRule(sourceRule);
          UnaryRule old = unaryRules.get(rule);
          if (old == null || rule.score > old.score) {
            unaryRules.put(rule, rule);
          }
          /*
            if (((UnaryRule)rule).child == targetStateIndex.indexOf("PRP") &&
              (sourceStateIndex.get(rule.parent)).charAt(0) == 'N') {
            System.out.println("Source UR: "+sourceRule+" %% "+sourceRule.score);
            System.out.println("Score of "+rule+"is now: "+((UnaryRule)unaryRules.get(rule)).score);
          }
          */
        }
      }
      for (UnaryRule ur : unaryRules.keySet()) {
        targetUG.addRule(ur);
        //System.out.println("UR: "+targetStateIndex.get(ur.parent)+" -> "+targetStateIndex.get(ur.child)+" %% "+ur.score);
      }
      targetUG.purgeRules();
      System.out.println("Projected " + sourceStateIndex.size() + " states to " + targetStateIndex.size() + " states.");
    }

  } // end class XBarGrammarProjection
