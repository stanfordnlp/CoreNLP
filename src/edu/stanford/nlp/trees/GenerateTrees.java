package edu.stanford.nlp.trees;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Generates trees based on simple grammars.
 * <br>
 * To run this script, run with an input file, an output file, and a
 * number of trees specified.
 * <br>
 * A more complete example is as follows:
 * <pre>

# This grammar produces trees that look like
# (S A (V B C)) -&gt; (S X (V Y Z))
# (S D E F) -&gt; (S X Y Z)

nonterminals
ROOT S
S A V
V B C
S D E F

terminals
A avocet albatross artichoke
B barium baseball brontosaurus
C canary cardinal crow
D delphinium dolphin dragon
E egret emu estuary
F finch flock finglonger

tsurgeon
S &lt;&lt; /A|D/=n1 &lt;&lt; /B|E/=n2 &lt;&lt; /C|F/=n3

relabel n1 X
relabel n2 Y
relabel n3 Z

</pre>
 *
 * <br>
 * You then run the program with
 * <br>
 * <code>java edu.stanford.nlp.trees.GenerateTrees input.txt output.txt 100</code>
 *
 * @author John Bauer
 */
public class GenerateTrees {
  static enum Section {
    TERMINALS, NONTERMINALS, TSURGEON
  }

  Map<String, Counter<List<String>>> nonTerminals = Generics.newHashMap();
  Map<String, Counter<String>> terminals = Generics.newHashMap();
  List<Pair<TregexPattern, TsurgeonPattern>> tsurgeons = new ArrayList<>();

  Random random = new Random();
  
  LabeledScoredTreeFactory tf = new LabeledScoredTreeFactory();
  
  TregexPatternCompiler compiler = new TregexPatternCompiler();

  TreePrint tp = new TreePrint("penn");

  public void readGrammar(String filename) {
    try {
      FileReader fin = new FileReader(filename);
      BufferedReader bin = new BufferedReader(fin);
      readGrammar(bin);
      bin.close();
      fin.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
  public void readGrammar(BufferedReader bin) {
    try {
      String line;
      Section section = Section.TERMINALS;
      while ((line = bin.readLine()) != null) {
        line = line.trim();

        if (line.equals("")) {
          continue;
        } 

        if (line.length() > 0 && line.charAt(0) == '#') {
          // skip comments
          continue;
        }

        try {
          Section newSection = Section.valueOf(line.toUpperCase(Locale.ROOT));
          section = newSection;
          if (section == Section.TSURGEON) {
            // this will tregex pattern until it has eaten a blank
            // line, then read tsurgeon until it has eaten another
            // blank line.
            Pair<TregexPattern, TsurgeonPattern> operation = Tsurgeon.getOperationFromReader(bin, compiler);
            tsurgeons.add(operation);
          }
          continue;
        } catch (IllegalArgumentException e) {
          // never mind, not an enum
        }
        
        String[] pieces = line.split(" +");
        switch(section) {
        case TSURGEON: {
          throw new RuntimeException("Found a non-empty line in a tsurgeon section after reading the operation");
        }
        case TERMINALS: {
          Counter<String> productions = terminals.get(pieces[0]);
          if (productions == null) {
            productions = new ClassicCounter<>();
            terminals.put(pieces[0], productions);
          }
          for (int i = 1; i < pieces.length; ++i) {
            productions.incrementCount(pieces[i]);
          }
          break;
        }
        case NONTERMINALS: {
          Counter<List<String>> productions = nonTerminals.get(pieces[0]);
          if (productions == null) {
            productions = new ClassicCounter<>();
            nonTerminals.put(pieces[0], productions);
          }
          String[] sublist = Arrays.copyOfRange(pieces, 1, pieces.length);
          productions.incrementCount(Arrays.asList(sublist));
        }
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
  public void produceTrees(String filename, int numTrees) {
    try {
      FileWriter fout = new FileWriter(filename);
      BufferedWriter bout = new BufferedWriter(fout);
      PrintWriter pout = new PrintWriter(bout);
      produceTrees(pout, numTrees);
      pout.close();
      bout.close();
      fout.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
  public void produceTrees(PrintWriter pout, int numTrees) {
    for (int i = 0; i < numTrees; ++i) {
      Tree tree = produceTree("ROOT");
      Tsurgeon.processPatternsOnTree(tsurgeons, tree);
      tp.printTree(tree, pout);
    }
  }
  
  public Tree produceTree(String state) {
    Counter<String> terminal = terminals.get(state);
    if (terminal != null) {
      // found a terminal production.  make a leaf with a randomly
      // chosen expansion and make a preterminal with that one leaf
      // as a child.
      String label = Counters.sample(terminal, random);
      Tree child = tf.newLeaf(label);
      List<Tree> children = Collections.singletonList(child);
      Tree root = tf.newTreeNode(state, children);
      return root;
    }
    
    Counter<List<String>> nonTerminal = nonTerminals.get(state);
    if (nonTerminal != null) {
      // found a nonterminal production.  produce a list of
      // recursive expansions, then attach them all to a node with
      // the expected state
      List<String> labels = Counters.sample(nonTerminal, random);
      List<Tree> children = new ArrayList<>();
      for (String childLabel : labels) {
        children.add(produceTree(childLabel));
      }
      Tree root = tf.newTreeNode(state, children);
      return root;
    }
    
    throw new RuntimeException("Unknown state " + state);
  }

  public static void help() {
    System.out.println("Command line should be ");
    System.out.println("  edu.stanford.nlp.trees.GenerateTrees <input> <output> <numtrees>");
  }
  
  public static void main(String[] args) {
    if (args.length == 0 || args[0].equals("-h")) {
      help();
      System.exit(0);
    }
    GenerateTrees grammar = new GenerateTrees();
    grammar.readGrammar(args[0]);
    int numTrees = Integer.valueOf(args[2]);
    grammar.produceTrees(args[1], numTrees);
  }
}
