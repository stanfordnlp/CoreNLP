package edu.stanford.nlp.trees.treebank;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;

import java.io.*;
import java.util.*;


/**
 * Class for updating the OntoNotes data.  Filter out trees with undesirable tags,
 * update some labels.
 */

public class OntoNotesUDUpdater {

  /** Trees to filter out **/
  public static TregexPattern badLabelsPattern = TregexPattern.compile("/X|EDITED|EMBEDq/ < /.*/");
  public static TregexPattern substitutionLabelsPattern =
      TregexPattern.compile("/-LCB-|-RCB-|-LSB-|-RSB-|TOP/ < /.*/");

  /** Label substitutions to make **/
  public static HashMap<String,String> labelSubstitutions = new HashMap<>();
  static {
    labelSubstitutions.put("-LCB-", "{");
    labelSubstitutions.put("-RCB-", "}");
    labelSubstitutions.put("-LSB-", "[");
    labelSubstitutions.put("-RSB-", "]");
    labelSubstitutions.put("TOP", "");
  }

  public static void main(String[] args) throws IOException {

    // set up tree reader
    TreeFactory tf = new LabeledScoredTreeFactory();
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, tf);

    // iterate through trees, replace labels that need updating for UD
    Tree t = tr.readTree();
    while(t != null) {
      if (!badLabelsPattern.matcher(t).find()) {
        TregexMatcher replacementTreesMatcher = substitutionLabelsPattern.matcher(t);
        while (replacementTreesMatcher.find()) {
          Tree replacementTree = replacementTreesMatcher.getMatch();
          String newLabel = labelSubstitutions.get(replacementTree);
          replacementTree.label().setValue(newLabel);
        }
        System.out.println(t);
      }
      t = tr.readTree();
    }
  }

}
