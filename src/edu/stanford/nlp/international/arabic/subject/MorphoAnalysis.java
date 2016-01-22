package edu.stanford.nlp.international.arabic.subject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.Label;

public class MorphoAnalysis {
  public static final String DEFAULT_VALUE = "NA";
  private static final int NUM_FEATURES = 5;

  private String gen = DEFAULT_VALUE;
  private String num = DEFAULT_VALUE;
  private String nounCase = DEFAULT_VALUE;
  private String pers = DEFAULT_VALUE;
  private String verbStem = DEFAULT_VALUE;

  public MorphoAnalysis() {}

  public MorphoAnalysis(String gender,String number,String nCase, String person, String vbStem) {
    gen = gender;
    num = number;
    nounCase = nCase;
    pers = person;
    verbStem = vbStem;
  }

  public static MorphoAnalysis parse(String s) {
    StringTokenizer st = new StringTokenizer(s);
    assert st.countTokens() == NUM_FEATURES;

    MorphoAnalysis m = new MorphoAnalysis();
    m.setGender(st.nextToken());
    m.setCase(st.nextToken());
    m.setNumber(st.nextToken());
    m.setPerson(st.nextToken());
    m.setVerbStem(st.nextToken());

    return m;
  }

  public void setGender(String s) {
    gen = s;
  }

  public void setNumber(String s) {
    num = s;
  }

  public void setCase(String s) {
    nounCase = s;
  }

  public void setPerson(String s) {
    pers = s;
  }

  public void setVerbStem(String s) {
    verbStem = s;
  }

  public String getVerbStem() {
    return verbStem;
  }

  @Override
  public String toString() {
    return String.format("%s\t%s\t%s\t%s\t%s",gen,nounCase,num,pers,verbStem);
  }

  
  public static String longFeatName(String shortName) {
    if(shortName.equals("M"))
      return "MASC";
    else if(shortName.equals("F"))
      return "FEM";
    else if(shortName.equals("S"))
      return "SG";
    else if(shortName.equals("D"))
      return "DU";
    else if(shortName.equals("P"))
      return "PL";
    else
      throw new RuntimeException(String.format("Bad tag input for (%s)",shortName));
  }

  private static final Pattern reGen = Pattern.compile("(FEM|MASC)");
  private static final Pattern reCase = Pattern.compile("(NOM|ACC|GEN)");
  private static final Pattern reNum = Pattern.compile("(SG|DU|PL)");
  private static final Pattern reAnal = Pattern.compile("([1-3]){1}([MF]){1}([SDP])?");
  private static final Pattern reVb = Pattern.compile("IV|PV|CV|PRON");
  
  public static List<String> parseGoldAnalyses(List<Label> analyses, List<String> madaFeats) {

    assert analyses.size() == madaFeats.size();

    List<String> feats = new ArrayList<String>();

    Iterator<Label> analItr = analyses.iterator();
    Iterator<String> madaItr = madaFeats.iterator();
    while(analItr.hasNext() && madaItr.hasNext()) {
      String analysis = analItr.next().toString().trim();
      String featString = madaItr.next().toString().trim();
      MorphoAnalysis morpho = new MorphoAnalysis();

      Matcher vbMatcher = reVb.matcher(analysis);
      if(vbMatcher.find()) {
        Matcher matchAnal = reAnal.matcher(analysis);
        if(matchAnal.find()) {
          if(matchAnal.groupCount() == 2) {
            morpho.setPerson(matchAnal.group(1));
            morpho.setGender(longFeatName(matchAnal.group(2)));
          } else if(matchAnal.groupCount() == 3) {
            morpho.setPerson(matchAnal.group(1));
            morpho.setGender(longFeatName(matchAnal.group(2)));
            morpho.setNumber(longFeatName(matchAnal.group(3)));            
          } else
            throw new RuntimeException(String.format("Malformed analysis (%s)",analysis));
        }

        MorphoAnalysis madaAnal = MorphoAnalysis.parse(featString);
        if(!madaAnal.getVerbStem().equals(MorphoAnalysis.DEFAULT_VALUE))
          morpho.setVerbStem(madaAnal.getVerbStem());

      } else {
        Matcher genMatcher = reGen.matcher(analysis);
        if(genMatcher.find())
          morpho.setGender(genMatcher.group().trim());

        Matcher numMatcher = reNum.matcher(analysis);
        if(numMatcher.find())
          morpho.setNumber(numMatcher.group().trim());

        Matcher caseMatcher = reCase.matcher(analysis);
        if(caseMatcher.find())
          morpho.setCase(caseMatcher.group().trim());
      }

      feats.add(morpho.toString());
    }

    return feats;
  }
}
