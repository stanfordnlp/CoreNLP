package edu.stanford.nlp.trees.treebank;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.trees.tregex.tsurgeon.*;
import edu.stanford.nlp.util.Pair;

import java.io.*;
import java.util.*;


/**
 * Class for updating the OntoNotes data.  Filter out trees with undesirable tags,
 * update some labels.
 */

public class OntoNotesUDUpdater {

  /** Trees to filter out **/
  public static TregexPattern badLabelsPattern = TregexPattern.compile("/X|EDITED|EMBED/ < /.*/");

  public static List<Pair<TregexPattern, TsurgeonPattern>> updates = new ArrayList<>();
  static {
    // some OntoNotes sentences have `` outside the enclosing S
    updates.add(new Pair<>(TregexPattern.compile("__ !> __ <1 /``/=bad <2 S=good"),
                           Tsurgeon.parseOperation("[move bad >1 good]")));
    // also the final punct sometimes is outside the proper subtree
    updates.add(new Pair<>(TregexPattern.compile("__ !> __ <2 /[.]/=bad <1 /S|SQ|SINV|PP/=good"),
                           Tsurgeon.parseOperation("[move bad >-1 good]")));
  }

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

  public static TregexPattern weirdRootPattern =
    TregexPattern.compile("__ !> __ <2 __");

  public static void main(String[] args) throws IOException {

    // set up tree reader
    TreeFactory tf = new LabeledScoredTreeFactory();
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, tf);

    // iterate through trees, replace labels that need updating for UD
    Tree t = tr.readTree();
    while (t != null) {
      if (!badLabelsPattern.matcher(t).find()) {
        // this tool is used on both ontonotes and ewt trees
        // ewt can have blank top labels which get read in as null,
        // which is super confusing to tregex.
        // ontonotes has TOP at the root which gets replaced later
        if (t.value() == null) {
          t.label().setValue("");
        }

        for (Pair<TregexPattern, TsurgeonPattern> update : updates) {
          t = Tsurgeon.processPattern(update.first, update.second, t);
        }

        TregexMatcher replacementTreesMatcher = substitutionLabelsPattern.matcher(t);
        while (replacementTreesMatcher.find()) {
          Tree replacementTree = replacementTreesMatcher.getMatch();
          String newLabel = labelSubstitutions.get(replacementTree.value());
          replacementTree.label().setValue(newLabel);
          // ghetto tsurgeon
          replacementTreesMatcher = substitutionLabelsPattern.matcher(t);
        }

        TregexMatcher rootMatcher = weirdRootPattern.matcher(t);
        if (rootMatcher.find()) {
          System.err.println("Matched and eliminating \n" + t + "\nat\n" + rootMatcher.getMatch());
        } else {
          System.out.println(t);
        }
      }
      t = tr.readTree();
    }
  }

}
