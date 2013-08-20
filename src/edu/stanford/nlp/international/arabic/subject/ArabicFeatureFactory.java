package edu.stanford.nlp.international.arabic.subject;

import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.international.arabic.ArabicWordLists;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.PaddedList;


public class ArabicFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  private static final long serialVersionUID = -8054065616648501985L;

  public ArabicFeatureFactory() {
    super();
  }

  private static final Pattern nounTagStem = Pattern.compile("NN[^P]|NN$");
  private static final Pattern verbTagStem = Pattern.compile("VB[^G]"); //Exclude maSdar
  private static final Pattern concordable = Pattern.compile("NN[^P]|JJ|NN$");
  private static final String adjTagStem = "JJ";
  private static final String adjNumTag = "ADJ_NUM";

  private Set<String> temporalNouns;
  private Set<String> innaSisters;
  private Set<String> kanSisters;
  private Set<String> dimirMunfasala;
  private Set<String> dimirMutasala;

  @Override
  public void init(SeqClassifierFlags flags)
  {
    super.init(flags);

    temporalNouns = ArabicWordLists.getTemporalNouns();
    innaSisters = ArabicWordLists.getInnaSisters();
    kanSisters = ArabicWordLists.getKanSisters();
    dimirMunfasala = ArabicWordLists.getDimirMunfasala();
    dimirMutasala = ArabicWordLists.getDimirMutasala();
  }


  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique)
  {
    Collection features = new ArrayList();

    if(clique == cliqueC)
    {
      Collection<String> c = featuresC(info, position);
      addAllInterningAndSuffixing(features, c, "C");
    }
    else if(clique == cliqueCpC)
    {
      Collection<String> c = featuresCpC(info, position);
      addAllInterningAndSuffixing(features, c, "CpC");
    }
    else if(clique == cliqueCpCp2C)
    {
      Collection<String> c = featuresCpCp2C(info, position);
      addAllInterningAndSuffixing(features, c, "CpCp2C");
    }

    return features;
  }

  //TODO Disable agreement on governed verbs
  protected String agreement(PaddedList<? extends CoreLabel> cInfo, int loc,int offset)
  {
    String nounGender = cInfo.get(loc).getString(CoreAnnotations.MorphoGenAnnotation.class);
    String nounNum = cInfo.get(loc).getString(CoreAnnotations.MorphoNumAnnotation.class);
    String nounCase = cInfo.get(loc).getString(CoreAnnotations.MorphoCaseAnnotation.class);

    String thisTag = cInfo.get(loc - offset).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
    String thisWord = cInfo.get(loc - offset).getString(CoreAnnotations.TextAnnotation.class);
    Matcher m = verbTagStem.matcher(thisTag);
    if(m.lookingAt() && !kanSisters.contains(thisWord) && !innaSisters.contains(thisWord))
    {

      if(flags.useSVO && (loc - offset > 0) && isSVO(cInfo, loc - offset))
          return Boolean.toString(false);

      //GEN can be determined from a stop list
      //GEN nouns cannot agree with anything
      if(nounCase.startsWith("GEN"))
        return Boolean.toString(false) + "-" + nounCase;

      String verbGender = cInfo.get(loc - offset).getString(CoreAnnotations.MorphoGenAnnotation.class);
      String verbNum = cInfo.get(loc - offset).getString(CoreAnnotations.MorphoNumAnnotation.class);
      String verbPers = cInfo.get(loc - offset).getString(CoreAnnotations.MorphoPersAnnotation.class);

      //Look for a definite sign of a verb-initial clause
      String pTag = cInfo.get(loc-offset-1).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      boolean verbInitialClause = (pTag.equals(flags.backgroundSymbol) || pTag.equals("CC") || pTag.equals("RP") || pTag.equals("IN"));

      if(verbGender.equals(nounGender) && verbNum.equals(nounNum))
        return Boolean.toString(verbInitialClause) + "-STRONG";
      else if(verbGender.equals(nounGender))
        return Boolean.toString(verbInitialClause) + "-WEAK";
      else if((nounNum.startsWith("PL") || nounGender.startsWith("FEM")) && (verbNum.equals("SG") && verbGender.equals("MASC") && verbPers.equals("3")))
        return Boolean.toString(verbInitialClause) + "-S-STRONG-PL";
      else if((nounNum.startsWith("PL") || nounGender.startsWith("FEM")) && (verbNum.equals("SG") && verbGender.equals("MASC")))
        return Boolean.toString(verbInitialClause) + "-STRONG-PL";
      else if((nounNum.startsWith("PL") || nounGender.startsWith("FEM")) && (verbNum.equals("SG")))
        return Boolean.toString(verbInitialClause) + "-WEAK-PL";
      else
        return Boolean.toString(false);
    }

    //Not a verb, so can't agree with anything
    return "";
  }

  protected boolean isSVO(PaddedList<? extends CoreLabel> cInfo, int verbPos) {
    String pWord = cInfo.get(verbPos - 1).getString(CoreAnnotations.TextAnnotation.class);
    String pTag = cInfo.get(verbPos - 1).getString(CoreAnnotations.PartOfSpeechAnnotation.class);

    //Verb preceded by a noun or a personal pronoun
    if(dimirMunfasala.contains(pWord) || innaSisters.contains(pWord))
      return true;
    else if(pTag.contains("NN"))
      return true;

    return false;
  }

  //1-clique feature design
  //Higher-order cliques should builds edges, i.e. shallow parsing
  protected void addTags(Collection<String> features, PaddedList<? extends CoreLabel> cInfo, int loc, int length)
  {
    int tagsToAdd = length;
    if(loc + 1 < cInfo.size()) {
      features.add(cInfo.get(loc + 1).getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-nTAG");
      --tagsToAdd;
    }

    String prefix = "";
    for(int offset = 0; (loc - offset) >= 0 && tagsToAdd > 0; offset++) {
      features.add(cInfo.get(loc - offset).getString(CoreAnnotations.PartOfSpeechAnnotation.class) + "-" + prefix + "TAG");
      --tagsToAdd;
      prefix += "p";
    }
  }

  protected void printMorphoFeatures(CoreLabel l)
  {
    String word = l.getString(CoreAnnotations.TextAnnotation.class);
    String tag = l.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
    String lCase = l.getString(CoreAnnotations.MorphoCaseAnnotation.class);
    String lGender = l.getString(CoreAnnotations.MorphoGenAnnotation.class);
    String lNum = l.getString(CoreAnnotations.MorphoNumAnnotation.class);
    String lPers = l.getString(CoreAnnotations.MorphoPersAnnotation.class);
    String lStem = l.getString(CoreAnnotations.StemAnnotation.class);

    System.err.printf(" (%s): %s %s %s %s %s %s\n",word,tag,lCase,lGender,lNum,lPers,lStem);
  }

  //WSGDEBUG: Designed for 2-cliques
  //TODO May want to skip punctuation that appears between the conjunction and its subject
  //Or, normalize punctuation
  private Pattern innaArgument = Pattern.compile("NN|PRP|CD");
  private Pattern specialSister = Pattern.compile("كان"); //Unvocalized has identical orthography to copular verb
  protected void addInna(Collection<String> features, PaddedList<? extends CoreLabel> cInfo, int loc)
  {
    String cTag = cInfo.get(loc).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
    String pWord = cInfo.get(loc - 1).getString(CoreAnnotations.TextAnnotation.class);
    String pTag = cInfo.get(loc - 1).getString(CoreAnnotations.PartOfSpeechAnnotation.class);

    boolean fireFeature = false;
    String featureSister = "";
    Matcher m = innaArgument.matcher(cTag);
    if(m.find())
    {
      Matcher specSister = specialSister.matcher(pWord);
      if(specSister.find() && pTag.equals("VBP"))
      {
        fireFeature = true;
        featureSister = specialSister.toString();
      }
      else if(innaSisters.contains(pWord))
      {
        fireFeature = true;
        featureSister = pWord;
      }
    }

    if(fireFeature)
      features.add(cTag + "+" + featureSister + "-INNA");
  }

  //TODO Set to the distortion limit
  private final static int pathLength = 10;

  protected void addPath(Collection<String> features, PaddedList<? extends CoreLabel> cInfo, int loc) {
    StringBuilder path = new StringBuilder();
    String cTag = cInfo.get(loc).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
    path.append(cTag);

    for(int i = 1; i <= pathLength && (loc - i) >= 0; i++) {
      String thisTag = cInfo.get(loc - i).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      path.append("-" + thisTag);
      Matcher m = verbTagStem.matcher(thisTag);
      if(m.lookingAt()) {
        features.add(path.toString() + "-PATH");
        break;
      }
    }
  }

  protected Collection<String> featuresC(PaddedList<? extends CoreLabel> cInfo, int loc)
  {
    CoreLabel c = cInfo.get(loc);
    CoreLabel n = (loc + 1 < cInfo.size()) ? cInfo.get(loc + 1) : null;
    CoreLabel p = (loc - 1 >= 0) ? cInfo.get(loc - 1) : null;

    String nWord = (n != null) ? n.getString(CoreAnnotations.TextAnnotation.class) : null;
    String cWord = (c != null) ? c.getString(CoreAnnotations.TextAnnotation.class) : null;
    String pWord = (p != null) ? p.getString(CoreAnnotations.TextAnnotation.class) : null;

    Collection<String> featuresC = new ArrayList<String>();

    if(flags.useWord)
      featuresC.add(cWord + "-WORD");
    if(flags.useWord1 && p != null)
      featuresC.add(pWord + "-pWORD");
    if(flags.useWordn && n != null)
      featuresC.add(nWord + "-nWORD");
    if(flags.useClassFeature)
      featuresC.add("#C#");
    if(flags.useTags)
    {
      addTags(featuresC, cInfo, loc, flags.numTags);
    }
    if(flags.useLastNgram)
    {
      if(cWord != null) {
        if(cWord.length() >= 5)
          featuresC.add(cWord.substring(cWord.length()-2, cWord.length()) + "-cL2GRAM");
        if(cWord.length() >= 3)
          featuresC.add(cWord.substring(cWord.length()-1, cWord.length()) + "-cL1GRAM");
      }
      if(pWord != null) {
        if(pWord.length() >= 5)
          featuresC.add(pWord.substring(pWord.length()-2, pWord.length()) + "-pL2GRAM");
        if(pWord.length() >= 3)
          featuresC.add(pWord.substring(pWord.length()-1, pWord.length()) + "-pL1GRAM");
      }
    }
    if(flags.useAccCase)
    {
      String cWordPrefix = "";
      String cWordSuffix = "";
      if(cWord.length() >= 2)
      {
        cWordPrefix = cWord.substring(0, 2);
        cWordSuffix = cWord.substring(cWord.length()-1,cWord.length());
      }
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);

      Matcher m = nounTagStem.matcher(cTag);
      if((m.find() || cTag.contains(adjTagStem)) && !cWordPrefix.equals("ال") && cWordSuffix.equals("ا"))
      {
        featuresC.add(cTag + "-cACASE");
      }
    }
    if(flags.useVB) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String stem = c.getString(CoreAnnotations.StemAnnotation.class);
      if(!stem.equals("NA") && !stem.equals(flags.backgroundSymbol))
        featuresC.add(cTag + "-true" + "-USEVB");
      else
        featuresC.add(cTag + "-false" + "-USEVB");

      Matcher m = verbTagStem.matcher(cTag);
      if(m.matches() && pWord != null && pWord.equals("س"))
        featuresC.add(cTag + "-USE_FUT_VB");
    }
    if(flags.useTemporalNN) {
      if(temporalNouns.contains(cWord))
        featuresC.add(cWord + "-CTEMPORAL_NN");
    }
    if(flags.usePath) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class).trim();
      if(cTag.contains("NN") || cTag.startsWith("PRP") || cTag.equals("IN"))
        addPath(featuresC,cInfo,loc);
    }
    if(flags.useAgreement)
    {
      boolean canAgree = true;
      if(flags.useAuxPairs && pWord != null)
      {
        if(kanSisters.contains(pWord)) {
          featuresC.add(pWord + "-pAUXVERB");
          canAgree = false;
        }
      }
      if(canAgree)
      {
        String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
        Matcher m = nounTagStem.matcher(cTag);
        if(m.find()) {
          for(int offset = 1; offset <= 5 && (loc - offset) >= 0; offset++) {
            String pTagi = cInfo.get(loc - offset).getString(CoreAnnotations.PartOfSpeechAnnotation.class);

            if(pTagi.contains("NN")) {
              break;
            } else {
              String agr = agreement(cInfo,loc,offset);
              if(!agr.equals("")) {
                featuresC.add(agr + "-AGREE");
                break;
              }
            }

          }
        }
      }
    }

    return featuresC;
  }

  protected Collection<String> featuresCpC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    Collection<String> featuresCpC = new ArrayList<String>();

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = (loc - 1 >= 0) ? cInfo.get(loc - 1) : null;

    if(flags.useClassFeature)
      featuresCpC.add("#CpC#");
    if(flags.useTagsCpC && p != null)
    {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      featuresCpC.add(cTag + "+" + pTag + "-TAG");
      //addTags(featuresCpC, cInfo, loc, flags.numTags);
    }
    if(flags.useConcord && p != null) //Noun concord
    {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String cWord = c.getString(CoreAnnotations.TextAnnotation.class);
      String pWord = p.getString(CoreAnnotations.TextAnnotation.class);

      Matcher m1 = concordable.matcher(cTag);
      Matcher m2 = concordable.matcher(pTag);
      if(m1.find() && cWord.length() > 1 && m2.find() && pWord.length() > 1) {

        //Direct observations
        String cGender = c.getString(CoreAnnotations.MorphoGenAnnotation.class);
        String cWordPrefix = (cWord.length() >= 2) ? cWord.substring(0, 2) : null;

        String pWordPrefix = (pWord.length() >= 2) ? pWord.substring(0, 2) : null;
        String pGender = p.getString(CoreAnnotations.MorphoGenAnnotation.class);


        //1 letter suffix (feminine)
        String c1Suff = cWord.substring(cWord.length()-1, cWord.length());
        String p1Suff = pWord.substring(pWord.length()-1, pWord.length());

        //2 letter
        String p2Suff = pWord.substring(pWord.length()-2, pWord.length());


        //DEFINITENESS
        if(cWordPrefix.equals("ال") && pWordPrefix.equals("ال"))
          featuresCpC.add(pTag + "-" + cTag + "-" + Boolean.toString(true) + "-CpCORTHO_DEF_CONCORD");
//        else if (cWordPrefix.equals("ال") || pWordPrefix.equals("ال"))
//          featuresCpC.add(pTag + "-" + cTag + "-" + Boolean.toString(false) + "-CpCORTHO_DEF_CONCORD");
        else if (cTag.equals("NN|JJ") && pTag.matches("NN|NNS"))
          featuresCpC.add(pTag + "-" + cTag + "-" + "-CpCPOS_INDEF_CONCORD");

        //GENDER
        if(cGender.equals(pGender)) {
          featuresCpC.add(cGender + "+" + pGender + "-CpCMADAGenMatch");
        }
        if((c1Suff.equals("ة") && p1Suff.equals("ة"))) {
          featuresCpC.add(c1Suff + "+" + p1Suff + "-CpCOrthoGenMatch");
        } else if((c1Suff.equals("ة") && p2Suff.equals("ات")) ||
                (c1Suff.equals("ى") && p2Suff.equals("ات"))) {
          featuresCpC.add(c1Suff + "+" + p2Suff + "-CpCOrthoGenMatch");
        }

        //CASE
//        else if((m1.find() || cTag.equals(adjNumTag) || cTag.startsWith(adjTagStem)) &&
//            (m2.find() || pTag.equals(adjNumTag) || pTag.startsWith(adjTagStem))) {
//          boolean concord = (cGender.equals(pGender));
//          featuresCpC.add(pTag + "-" + cTag + "-" + Boolean.toString(concord) + "-INDEFCONCORD");
//        }
      }
      //Idafa - No concord to check
    }
    if(flags.useConjBreak && p != null)
    {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String cWord = c.getString(CoreAnnotations.TextAnnotation.class);
      String pWord = p.getString(CoreAnnotations.TextAnnotation.class);

      //TODO - Re-factor using the regex compiler
      if(!cTag.matches(".*NN.*|.*CD.*|.*VB.*"))
        if(pTag.matches(".*CC.*"))
          featuresCpC.add(cWord + "+" + pWord + "-CBREAK");
    }
    if(flags.useInna)
    {
      addInna(featuresCpC,cInfo,loc);
    }
    if(flags.usePPVBPairs && p != null)
    {
      String cWord = c.getString(CoreAnnotations.TextAnnotation.class);
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(cTag.startsWith("IN"))
      {
        //TODO 10 covers all but 8% of NP subjects in the ATB (Green et al., 2009)
        for(int offset = 1; offset < 10 && (loc - offset) >= 0; offset++)
        {
          String pTag = cInfo.get(loc - offset).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
          if(pTag.startsWith("VB"))
          {
            String verbStem = cInfo.get(loc - offset).getString(CoreAnnotations.StemAnnotation.class);
            if(!verbStem.equals("NA"))
            {
              featuresCpC.add(cWord + "+" + verbStem + "-PPVBPair");
            }
            break;
          }
        }
      }
    }
    if(flags.useParenMatching)
    {
      String cWord = c.getString(CoreAnnotations.TextAnnotation.class);
      if(cWord.equals("\"")) {
        for(int i = 1; i <= 6 && (loc -i) >= 0; i++) {
          String pWord = cInfo.get(loc-i).getString(CoreAnnotations.TextAnnotation.class);
          if(pWord.equals("\""))
          {
            featuresCpC.add("-QUOTE-MATCH");
            break;
          }
        }
      } else if(cWord.equals(")")) {
        for(int i = 1; i <= 6 && (loc -i) >= 0; i++) {
          String pWord = cInfo.get(loc-i).getString(CoreAnnotations.TextAnnotation.class);
          if(pWord.equals("("))
          {
            featuresCpC.add("-PAREN-MATCH");
            break;
          }
        }
      }
    }
    if(flags.useAnnexing && p != null) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);

      if((cTag.startsWith("DTNN") || cTag.startsWith("DTJJ") || cTag.equals(adjNumTag))
          && (pTag.equals("NN") || pTag.equals("NNS")))
        featuresCpC.add(pTag + "-" + cTag + "-ANNEXING");
    }
    if(flags.useTemporalNN && p != null) {
      String cWord = c.getString(CoreAnnotations.TextAnnotation.class);
      String pWord = p.getString(CoreAnnotations.TextAnnotation.class);
      if(temporalNouns.contains(cWord) && temporalNouns.contains(pWord))
        featuresCpC.add(pWord + "-" + cWord + "-CpCTEMPORAL_NN");
    }
    if(flags.markProperNN && p != null) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(cTag.endsWith("NNP") && pTag.endsWith("NNP"))
        featuresCpC.add(cTag + "-" + pTag + "-CpCPROPERNN");
    }
    if(flags.markMasdar && p != null) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(cTag.equals("PRP$") && pTag.equals("VBG"))
        featuresCpC.add("-CpCMASDAR");
    }

    return featuresCpC;
  }

  protected Collection<String> featuresCpCp2C(PaddedList<? extends CoreLabel> cInfo, int loc)
  {
    Collection<String> featuresCpCp2C = new ArrayList<String>();

    CoreLabel c = cInfo.get(loc);
    CoreLabel p = (loc - 1 >= 0) ? cInfo.get(loc - 1) : null;
    CoreLabel p2 = (loc - 2 >= 0) ? cInfo.get(loc - 2) : null;

    if(flags.maxLeft > 1)
      if(flags.useLongSequences)
        featuresCpCp2C.add("#CpCp2C#");

    if (flags.useTagsCpCp2C && p != null && p2 != null) {
      String cTag = c.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String pTag = p.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
      String p2Tag = p2.getString(CoreAnnotations.PartOfSpeechAnnotation.class);

      featuresCpCp2C.add(String.format("%s+%s+%s-TAG", cTag, pTag, p2Tag));
    }

    return featuresCpCp2C;
  }

  //
//  protected Collection<String> featuresCpCp2Cp3C(PaddedList<? extends CoreLabel> cInfo, int loc)
//  {
//    Collection<String> featuresCpCp2Cp3C = new ArrayList<String>();
//
//    if(flags.maxLeft > 2)
//      if(flags.useLongSequences)
//        featuresCpCp2Cp3C.add("#CpCp2Cp3C#");
//
//    if (flags.useTagsCpCp2Cp3C)
//    {
//      addTags(featuresCpCp2Cp3C, cInfo, loc, flags.numTags);
//
//    }
//    return featuresCpCp2Cp3C;
//  }
//
//  protected Collection<String> featuresCpCp2Cp3Cp4C(PaddedList<? extends CoreLabel> cInfo, int loc)
//  {
//    Collection<String> featuresCpCp2Cp3Cp4C = new ArrayList<String>();
//
//    if(flags.maxLeft > 3)
//      if(flags.useLongSequences)
//        featuresCpCp2Cp3Cp4C.add("#CpCp2Cp3Cp4C#");
//
//    if (flags.useTagsCpCp2Cp3Cp4C)
//    {
//      addTags(featuresCpCp2Cp3Cp4C, cInfo, loc, flags.numTags);
//    }
//    return featuresCpCp2Cp3Cp4C;
//  }
}
