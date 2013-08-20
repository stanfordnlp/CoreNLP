package edu.stanford.nlp.parser.lexparser;

import java.util.HashSet;
import java.util.Arrays;

import edu.stanford.nlp.trees.TreebankLanguagePack;


class ChrisTagProjection implements TagProjection {

  private static final long serialVersionUID = -5768677575591827440L;

  TreebankLanguagePack tlp;

  @SuppressWarnings({"RedundantArrayCreation"})
  HashSet<String> goodGuys =
    new HashSet<String>(Arrays.asList(new String[]
      {"-SC", "-SCC", "-N", "-Q", "-T",
       "-HV", "-BE",
       "-TMP", "-A", "-B" }));

  public ChrisTagProjection(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }

  public String project(String tagStr) {
    StringBuilder basic =  new StringBuilder(tlp.basicCategory(tagStr));
    for (String keep : goodGuys) {
      if (tagStr.contains(keep)) {
        basic.append(keep);
      }
    }
    return basic.toString();
  }

}


