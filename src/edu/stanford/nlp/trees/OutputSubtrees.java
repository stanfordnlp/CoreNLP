package edu.stanford.nlp.trees;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StringUtils;

/**
 * Output a tree and all of its subtrees.  Each subtree is on a
 * separate line, with the start of the line being the top node's
 * label and the rest of the line being the text of the leaves.
 * <br>
 * One specific use of this is to output the sentiment treebank
 * as a bunch of lines with no tree structure.  This is useful
 * for then building a phrase/sentence classifier using that
 * dataset.  See scripts/sentiment/convert_sentiment.sh
 *
 * @author John Bauer
 */
public class OutputSubtrees {
  @ArgumentParser.Option(name="input", gloss="The file to use as input.", required=true)
  private static String INPUT; // = null;

  @ArgumentParser.Option(name="output", gloss="Where to write output.  Will write to stdout if not set.", required=false)
  private static String OUTPUT; // = null;

  @ArgumentParser.Option(name="root_only", gloss="Output only the roots", required=false)
  private static boolean ROOT_ONLY = false;

  @ArgumentParser.Option(name="ignore_labels", gloss="Labels to ignore as a WS-separated list.", required=false)
  private static String IGNORE_LABELS; // = null;

  @ArgumentParser.Option(name="remap_labels", gloss="Remap labels: for sentiment, for example, write '1=0,2=-1,3=1,4=1' to make it binary.  Remapping , or = currently not supported.", required=false)
  private static String REMAP_LABELS; // = null;

  @ArgumentParser.Option(name="assert_binary", gloss="Barf on non-binary trees", required=false)
  private static boolean ASSERT_BINARY = false;  

  public static void main(String[] args) throws IOException {
    // Parse the arguments
    Properties props = StringUtils.argsToProperties(args, new HashMap<String, Integer>() {{
          put("ignore_labels", 1);
          put("remap_labels", 1);
        }});
    ArgumentParser.fillOptions(new Class[]{ArgumentParser.class, OutputSubtrees.class}, props);

    Set<String> ignored;
    if (IGNORE_LABELS != null) {
      ignored = new HashSet<>(StringUtils.split(IGNORE_LABELS));
      System.err.println("Ignoring the following labels: " + ignored);
    } else {
      ignored = Collections.emptySet();
    }

    Map<String, String> remap;
    if (REMAP_LABELS != null) {
      remap = StringUtils.mapStringToMap(REMAP_LABELS);
      System.err.println("Remapping labels as follows: " + remap);
    } else {
      remap = Collections.emptyMap();
    }

    MemoryTreebank treebank = new MemoryTreebank("utf-8");
    treebank.loadPath(INPUT, null);

    final Writer output;
    if (OUTPUT == null) {
      output = IOUtils.encodedOutputStreamWriter(System.out, "utf-8");
    } else {
      output = IOUtils.getPrintWriter(OUTPUT, "utf-8");
    }

    int treeNum = 0;
    for (Tree tree : treebank) {
      ++treeNum;
      if (ASSERT_BINARY && !tree.isBinary()) {
        throw new RuntimeException("Tree " + treeNum + " is not properly binary");
      }
      //System.out.println(tree);
      //System.out.println("--------------");
      Iterable<Tree> subtrees = (ROOT_ONLY) ? Collections.singletonList(tree) : tree;
      for (Tree subtree : subtrees) {
        if (subtree.isLeaf()) {
          continue;
        }
        String value = subtree.label().value();
        List<Tree> leaves = Trees.leaves(subtree);
        List<Label> labels = leaves.stream().map(x -> x.label()).collect(Collectors.toList());
        String text = SentenceUtils.listToString(labels);
        if (ignored.contains(value)) {
          continue;
        }
        if (remap.containsKey(value)) {
          value = remap.get(value);
        }

        output.write(value + "   " + text + "\n");
      }
      output.write("\n");
    }
    output.flush();
    if (OUTPUT != null) {
      output.close();
    }
  }
}

