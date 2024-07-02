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
 *
 * @author John Bauer
 */
public class SplitWord extends SsurgeonEdit {
  public static final String LABEL = "splitWord";

  final String node;
  final List<Pattern> nodeRegex;
  final int headIndex;
  final GrammaticalRelation relation;

  public SplitWord(String node, List<String> nodeRegex, Integer headIndex, GrammaticalRelation relation) {
    if (node == null) {
      throw new SsurgeonParseException("SplitWord expected -node with the name of the matched node to split");
    }
    this.node = node;

    if (nodeRegex == null || nodeRegex.size() == 0) {
      throw new SsurgeonParseException("SplitWord expected -regex with regex to determine which pieces to split the word into");
    }
    if (nodeRegex.size() == 1) {
      throw new SsurgeonParseException("SplitWord expected at least two -regex");
    }
    this.nodeRegex = new ArrayList<>();
    for (int i = 0; i < nodeRegex.size(); ++i) {
      this.nodeRegex.add(Pattern.compile(nodeRegex.get(i)));
    }

    if (headIndex == null) {
      throw new SsurgeonParseException("SplitWord expected a -headIndex, 0-indexed for the word piece to use when chopping up the word");
    }
    this.headIndex = headIndex;

    if (relation == null) {
      throw new SsurgeonParseException("SplitWord expected a -reln to represent the dependency to use for the new words");
    }
    this.relation = relation;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    buf.write("\t");
    buf.write("-node " + node + "\t");
    for (Pattern regex : nodeRegex) {
      buf.write("-regex " + regex + "\t");
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
    List<String> words = new ArrayList<>();
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

    int matchedIndex = matchedNode.index();

    // at this point, we can make new words out of each of the patterns

    // move all words down by nodeRegex.size() - 1
    // then move the original word down by headIndex
    AddDep.moveNodes(sg, sm, x -> (x > matchedIndex), x -> x+nodeRegex.size() - 1, true);
    // the head node has its word replaced, and its index & links need
    // to be rearranged, but none of the links are added or removed
    if (headIndex > 0) {
      AddDep.moveNode(sg, sm, matchedNode, matchedIndex + headIndex);
    }
    matchedNode = sm.getNode(node);
    matchedNode.setWord(words.get(headIndex));
    matchedNode.setValue(words.get(headIndex));

    for (int i = 0; i < nodeRegex.size(); ++i) {
      if (i == headIndex)
        continue;

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
    }
    return true;
  }
}
