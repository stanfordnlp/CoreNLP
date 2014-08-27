package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * We generate a USD-style dependency treebank from the original AnCora
 * dependency treebank using the HamleDT tool. This tool converts to
 * USD by first translating AnCora to Prague format, then from Prague
 * format to USD. As you might guess, the translation is lossy.
 *
 * This class synthesizes the HamleDT output (in CoNLL X format) with
 * the original AnCora data, re-inserting data from the AnCora treebank
 * that was lost in translation.
 *
 * @see <a href="http://ufal.mff.cuni.cz/hamledt">HamleDT homepage</a>
 * @author Jon Gauthier
 */
public class HamleDTCorrector {

  private String hamledtPath;
  private String ancoraPath;

  public HamleDTCorrector(String hamledtPath, String ancoraPath) {
    this.hamledtPath = hamledtPath;
    this.ancoraPath = ancoraPath;
  }

  public List<String> correct() {
    List<String> hamledtLines = IOUtils.linesFromFile(hamledtPath);
    List<String> ancoraLines = IOUtils.linesFromFile(ancoraPath);
    if (hamledtLines.size() != ancoraLines.size())
      throw new RuntimeException("Provided files do not have the same number of lines -- check " +
                                   "that these are the same treebank!");

    List<String> ret = new ArrayList<String>(ancoraLines.size());

    String hamledtLine = null, ancoraLine = null;
    for (Iterator<String> iHamledt = hamledtLines.iterator(), iAncora = ancoraLines.iterator();
      iHamledt.hasNext() && iAncora.hasNext();
      hamledtLine = iHamledt.next(), ancoraLine = iAncora.next()) {
      // TODO handle multi-word expressions

      ret.add(correctLine(hamledtLine, ancoraLine));
    }

    return ret;
  }

  private String correctLine(String hamledtLine, String ancoraLine) {
    if (hamledtLine.equals("") && ancoraLine.equals(""))
      return "";

    String[] hFields = hamledtLine.split("\t");
    String[] aFields = ancoraLine.split("\t");

    String hWord = hFields[1];
    String aWord = aFields[1];
    if (!hWord.equals(aWord))
      throw new RuntimeException(String.format(
        "Treebank line mismatch: HamleDT '%s' does not match AnCora line '%s'",
        hWord, aWord));

    String hDepRel = hFields[7];
    hFields[7] = correctDepRel(hDepRel, aFields);

    return StringUtils.join(hFields, "\t");
  }

  /**
   * Fix the HamleDT-produced dependency relation label given the
   * AnCora line from which it was sourced.
   */
  private String correctDepRel(String depRel, String[] ancoraFields) {
    // Original dependency relation label
    String aDepRel = ancoraFields[5];

    // Vocative
    if (aDepRel.equals("voc"))
      // TODO: Handle nmods nearby that HamleDT marks as "appos" -- these should probably be nmod
      // e.g. "Señor caballero" should yield `nmod(caballero, Señor)`
      return "vocative";

    // Distinguish dative and accusative objects
    if (depRel.equals("obj")) {
      if (isDative(ancoraFields))
        // "dative" annotation retained in Stanford output as "case=dat"
        return "nmod";
      else
        return "dobj";
    }

    return depRel;
  }

  private boolean isDative(String[] ancoraFields) {
    return ancoraFields[7].contains("case=dative");
  }

  private static final String usage = String.format(
    "Usage: java %s <hamledt_file> <ancora_file>%n%n" +
      "\t(where <hamledt_file> was produced directly from <ancora_file>,%n" +
      "\tso that the lines match up exactly)", HamleDTCorrector.class.getName());

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println(usage);
      System.exit(1);
    }

    HamleDTCorrector corrector = new HamleDTCorrector(args[0], args[1]);
    List<String> corrected = corrector.correct();

    for (String line : corrected)
      System.out.println(line);
  }

}
