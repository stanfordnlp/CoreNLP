package edu.stanford.nlp.semgraph; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class contains only a main method, which prints out various
 * views of SemanticGraphs.  This method is separate from
 * SemanticGraph so that packages don't need to include the
 * LexicalizedParser in order to include SemanticGraph.
 */
public class SemanticGraphPrinter  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SemanticGraphPrinter.class);
  private SemanticGraphPrinter() {} // main method only

  public static void main(String[] args) {

    Treebank tb = new MemoryTreebank();
    Properties props = StringUtils.argsToProperties(args);
    String treeFileName = props.getProperty("treeFile");
    String sentFileName = props.getProperty("sentFile");
    String testGraph = props.getProperty("testGraph");
    if (testGraph == null) {
      testGraph = "false";
    }
    String load = props.getProperty("load");
    String save = props.getProperty("save");

    if (load != null) {
      log.info("Load not implemented!");
      return;
    }

    if (sentFileName == null && treeFileName == null) {
      log.info("Usage: java SemanticGraph [-sentFile file|-treeFile file] [-testGraph]");
      Tree t = Tree.valueOf("(ROOT (S (NP (NP (DT An) (NN attempt)) (PP (IN on) (NP (NP (NNP Andres) (NNP Pastrana) (POS 's)) (NN life)))) (VP (VBD was) (VP (VBN carried) (PP (IN out) (S (VP (VBG using) (NP (DT a) (JJ powerful) (NN bomb))))))) (. .)))");
      tb.add(t);
    } else if (treeFileName != null) {
      tb.loadPath(treeFileName);
    } else {
      String[] options = {"-retainNPTmpSubcategories"};
      LexicalizedParser lp = LexicalizedParser.loadModel("/u/nlp/data/lexparser/englishPCFG.ser.gz", options);
      BufferedReader reader = null;
      try {
        reader = IOUtils.readerFromString(sentFileName);
      } catch (IOException e) {
        throw new RuntimeIOException("Cannot find or open " + sentFileName, e);
      }
      try {
        System.out.println("Processing sentence file " + sentFileName);
        for  (String line; (line = reader.readLine()) != null; ) {
          System.out.println("Processing sentence: " + line);
          PTBTokenizer<Word> ptb = PTBTokenizer.newPTBTokenizer(new StringReader(line));
          List<Word> words = ptb.tokenize();
          Tree parseTree = lp.parseTree(words);
          tb.add(parseTree);
        }
        reader.close();
      } catch (Exception e) {
        throw new RuntimeException("Exception reading key file " + sentFileName, e);
      }
    }

    for (Tree t : tb) {
      SemanticGraph sg = SemanticGraphFactory.generateUncollapsedDependencies(t);
      System.out.println(sg.toString());
      System.out.println(sg.toCompactString());

      if (testGraph.equals("true")) {
        SemanticGraph g1 = SemanticGraphFactory.generateCollapsedDependencies(t);
        System.out.println("TEST SEMANTIC GRAPH - graph ----------------------------");
        System.out.println(g1.toString());
        System.out.println("readable ----------------------------");
        System.out.println(g1.toString(SemanticGraph.OutputFormat.READABLE));
        System.out.println("List of dependencies ----------------------------");
        System.out.println(g1.toList());
        System.out.println("xml ----------------------------");
        System.out.println(g1.toString(SemanticGraph.OutputFormat.XML));
        System.out.println("dot ----------------------------");
        System.out.println(g1.toDotFormat());
        System.out.println("dot (simple) ----------------------------");
        System.out.println(g1.toDotFormat("Simple", CoreLabel.OutputFormat.VALUE));

        // System.out.println(" graph ----------------------------");
        // System.out.println(t.allTypedDependenciesCCProcessed(false));
      }
    }

    if (save != null) {
      log.info("Save not implemented!");
    }
  } // end main

}
