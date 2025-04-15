package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Split a word into pieces based on the regex expressions provided by the -regex arguments
 * <br>
 * As an example of where this is useful, a tokenization dataset had "
 * stuck to each of the words.  We can separate that out by using two
 * regex, one which matches the " in a group, one which matches the
 * rest of the word without the "
 * <br>
 * Aside from the text and the dependency, the new node is rather bare bones.
 * Adding the -name argument allows for specifying a comma-separate list
 * of names which can be used to insert the new nodes into the SemgrexMatcher
 * as named nodes.  This will allow for further edits in the same edit step.
 * This list should be 0 indexed.
 * <br>
 * For example, this will split "foobar" and put the pos ADJ on the first word
 * <pre>
 * semgrex:
 *   {word:/foobar/}=split
 * ssurgeon:
 *   splitWord -node split -regex ^(foo)bar$ -regex ^foo(bar)$ -reln dep -headIndex 1 -name 0=asdf
 *   editNode -node asdf -pos ADJ
 * </pre>
 *
 * @author John Bauer
 */
public class SplitWord extends SsurgeonEdit {
  public static final String LABEL = "splitWord";

  final String node;
  final List<Pattern> nodeRegex;
  final List<String> exactPieces;
  final int headIndex;
  final GrammaticalRelation relation;
  final Map<Integer, String> nodeNames;

  public SplitWord(String node, List<String> nodePieces, Integer headIndex, GrammaticalRelation relation, String nodeNames, boolean exactSplit) {
    if (node == null) {
      throw new SsurgeonParseException("SplitWord expected -node with the name of the matched node to split");
    }
    this.node = node;

    if (nodePieces == null || nodePieces.size() == 0) {
      throw new SsurgeonParseException("SplitWord expected -exact or -regex with regex to determine which pieces to split the word into");
    }
    if (nodePieces.size() == 1) {
      throw new SsurgeonParseException("SplitWord expected at least two -exact or -regex");
    }
    if (exactSplit) {
      this.exactPieces = new ArrayList<>(nodePieces);
      this.nodeRegex = null;
    } else {
      this.nodeRegex = new ArrayList<>();
      for (int i = 0; i < nodePieces.size(); ++i) {
        this.nodeRegex.add(Pattern.compile(nodePieces.get(i)));
      }
      this.exactPieces = null;
    }

    if (headIndex == null) {
      throw new SsurgeonParseException("SplitWord expected a -headIndex, 0-indexed for the word piece to use when chopping up the word");
    }
    this.headIndex = headIndex;

    if (relation == null) {
      throw new SsurgeonParseException("SplitWord expected a -reln to represent the dependency to use for the new words");
    }
    this.relation = relation;

    if (nodeNames != null) {
      String[] namePieces = nodeNames.split(",");
      this.nodeNames = new HashMap<>();
      for (String namePiece : namePieces) {
        String[] pieces = namePiece.split("=", 2);
        if (pieces.length < 2) {
          throw new SsurgeonParseException("SplitWord got a -name parameter which did not have a number for one of the names.  Should look like 0=foo,1=bar");
        }
        int idx = Integer.valueOf(pieces[0]);
        if (idx >= nodePieces.size()) {
          throw new SsurgeonParseException("SplitWord got an index in -name which was larger than the largest possible split piece, " + idx + " (this is 0-indexed)");
        }
        this.nodeNames.put(idx, pieces[1]);
      }
    } else {
      this.nodeNames = Collections.emptyMap();
    }
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    buf.write("\t");
    buf.write("-node " + node + "\t");
    if (nodeRegex != null) {
      for (Pattern regex : nodeRegex) {
        buf.write("-regex " + regex + "\t");
      }
    } else {
      for (String piece : exactPieces) {
        buf.write("-exact " + piece + "\t");
      }
    }
    buf.write("-reln " + relation.toString() + "\t");
    buf.write("-headIndex " + headIndex);
    return buf.toString();
  }

  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord matchedNode = sm.getNode(node);
    String origWord = matchedNode.word();

    // first, iterate over the regex patterns we had at creation time
    //
    // each new word created will be the concatenation of all of the
    // matching groups from this pattern
    List<String> words;
    if (exactPieces != null) {
      words = new ArrayList<>(exactPieces);
    } else {
      words = new ArrayList<>();
      for (int i = 0; i < nodeRegex.size(); ++i) {
        Matcher regexMatcher = nodeRegex.get(i).matcher(origWord);
        if (!regexMatcher.matches()) {
          return false;
        }

        StringBuilder newWordBuilder = new StringBuilder();
        for (int j = 0; j < regexMatcher.groupCount(); ++j) {
          newWordBuilder.append(regexMatcher.group(j+1));
        }
        String newWord = newWordBuilder.toString();
        if (newWord.length() == 0) {
          return false;
        }
        words.add(newWord);
      }
    }

    int matchedIndex = matchedNode.index();

    // at this point, we can make new words out of each of the patterns

    // move all words down by nodeRegex.size() - 1
    // then move the original word down by headIndex
    SsurgeonUtils.moveNodes(sg, sm, x -> (x > matchedIndex), x -> x+words.size() - 1, true);
    // the head node has its word replaced, and its index & links need
    // to be rearranged, but none of the links are added or removed
    if (headIndex > 0) {
      SsurgeonUtils.moveNode(sg, sm, matchedNode, matchedIndex + headIndex);
    }
    matchedNode = sm.getNode(node);
    matchedNode.setWord(words.get(headIndex));
    matchedNode.setValue(words.get(headIndex));

    // TODO: update SpaceAfter in a reasonable manner
    for (int i = 0; i < words.size(); ++i) {
      if (i == headIndex) {
        if (nodeNames.containsKey(i)) {
          sm.putNode(nodeNames.get(i), matchedNode);
        }
        continue;
      }

      // otherwise, add a word with the appropriate index,
      // then connect it to matchedNode
      // TODO: add the ability to set more values, such as POS?
      IndexedWord newNode = new IndexedWord();
      newNode.setDocID(matchedNode.docID());
      newNode.setIndex(matchedIndex + i);
      newNode.setSentIndex(matchedNode.sentIndex());
      newNode.setWord(words.get(i));
      newNode.setValue(words.get(i));

      sg.addVertex(newNode);
      sg.addEdge(matchedNode, newNode, relation, 0.0, false);

      if (nodeNames.containsKey(i)) {
        sm.putNode(nodeNames.get(i), newNode);
      }
    }

    return true;
  }
}
