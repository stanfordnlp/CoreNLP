package edu.stanford.nlp.international.arabic;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatures;

/**
 * Extracts morphosyntactic features from BAMA/SAMA analyses. Compatible with both the
 * long tags in the ATB and the output of MADA.
 *
 * @author Spence Green
 *
 */
public class ArabicMorphoFeatureSpecification extends MorphoFeatureSpecification {

  private static final long serialVersionUID = 4448045447200922076L;

  private static final String[] defVals = {"I", "D"};
  private static final String[] caseVals = {"NOM","ACC","GEN"};
  private static final String[] genVals = {"M","F"};
  private static final String[] numVals = {"SG","DU","PL"};
  private static final String[] perVals = {"1","2","3"};
  private static final String[] possVals = {"POSS"};
  private static final String[] voiceVals = {"ACT","PASS"};
  private static final String[] moodVals = {"I","S","J"};
  private static final String[] tenseVals = {"PAST","PRES","IMP"};

  // Standard feature tuple (e.g., "3MS", "1P", etc.)
  private static final Pattern pFeatureTuple = Pattern.compile("(\\d\\p{Upper}\\p{Upper}?)");

  // Demonstrative pronouns do not have number
  private static final Pattern pDemPronounFeatures = Pattern.compile("DEM_PRON(.+)");

  //Verbal patterns
  private static final Pattern pVerbMood = Pattern.compile("MOOD|SUBJ");
  private static final Pattern pMood = Pattern.compile("_MOOD:([ISJ])");
  private static final Pattern pVerbTenseMarker = Pattern.compile("IV|PV|CV");
  private static final Pattern pNounNoMorph = Pattern.compile("PROP|QUANT");

  @Override
  public List<String> getValues(MorphoFeatureType feat) {
    if(feat == MorphoFeatureType.DEF)
      return Arrays.asList(defVals);
    else if(feat == MorphoFeatureType.CASE) {
      throw new RuntimeException(this.getClass().getName() + ": Case is presently unsupported!");
//      return Arrays.asList(caseVals);
    } else if(feat == MorphoFeatureType.GEN)
      return Arrays.asList(genVals);
    else if(feat == MorphoFeatureType.NUM)
      return Arrays.asList(numVals);
    else if(feat == MorphoFeatureType.PER)
      return Arrays.asList(perVals);
    else if(feat == MorphoFeatureType.POSS)
      return Arrays.asList(possVals);
    else if(feat == MorphoFeatureType.VOICE)
      return Arrays.asList(voiceVals);
    else if(feat == MorphoFeatureType.MOOD)
      return Arrays.asList(moodVals);
    else if(feat == MorphoFeatureType.TENSE)
      return Arrays.asList(tenseVals);
    else
      throw new IllegalArgumentException("Arabic does not support feature type: " + feat.toString());
  }

  /**
   * Hand-written rules to convert SAMA analyses to feature structures.
   */
  @Override
  public MorphoFeatures strToFeatures(String spec) {
    MorphoFeatures features = new ArabicMorphoFeatures();

    // Check for the boundary symbol
    if(spec == null || spec.equals("")) {
      return features;
    }
    //Possessiveness
    if(isActive(MorphoFeatureType.POSS) && spec.contains("POSS")) {
      features.addFeature(MorphoFeatureType.POSS,possVals[0]);
    }

    //Nominals and pronominals. Mona ignores Pronominals in ERTS, but they seem to help...
    // NSUFF -- declinable nominals
    // VSUFF -- enclitic pronominals
    // PRON -- ordinary pronominals
    if(spec.contains("NSUFF") || spec.contains("NOUN") || spec.contains("ADJ")) {
      // Nominal phi feature indicators are different than the indicators
      // that we process with processInflectionalFeatures()
      if(isActive(MorphoFeatureType.NGEN)) {
        if(spec.contains("FEM")) {
          features.addFeature(MorphoFeatureType.NGEN, genVals[1]);
        } else if(spec.contains("MASC") || !pNounNoMorph.matcher(spec).find()) {
          features.addFeature(MorphoFeatureType.NGEN, genVals[0]);
        }
      }

      // WSGDEBUG -- Number for nominals only
      if(isActive(MorphoFeatureType.NNUM)) {
        if(spec.contains("DU")) {
          features.addFeature(MorphoFeatureType.NNUM, numVals[1]);
        } else if(spec.contains("PL")) {
          features.addFeature(MorphoFeatureType.NNUM, numVals[2]);
        } else if (!pNounNoMorph.matcher(spec).find()){ // (spec.contains("SG"))
          features.addFeature(MorphoFeatureType.NNUM, numVals[0]);
        }
      }

      //Definiteness
      if(isActive(MorphoFeatureType.DEF)) {
        if (spec.contains("DET")) {
          features.addFeature(MorphoFeatureType.DEF, defVals[1]);
        } else if (!pNounNoMorph.matcher(spec).find()){
          features.addFeature(MorphoFeatureType.DEF, defVals[0]);
        }
      }

      // Proper nouns (probably a stupid feature)
      if (isActive(MorphoFeatureType.PROP)) {
        if (spec.contains("PROP")) {
          features.addFeature(MorphoFeatureType.PROP,"");
        }
      }

    } else if(spec.contains("PRON") || (spec.contains("VSUFF_DO") && !pVerbMood.matcher(spec).find())) {
      if(spec.contains("DEM_PRON")) {
        features.addFeature(MorphoFeatureType.DEF, defVals[0]);
        Matcher m = pDemPronounFeatures.matcher(spec);
        if (m.find()) {
          spec = m.group(1);
          processInflectionalFeaturesHelper(features, spec);
        }

      } else {
        processInflectionalFeatures(features, spec);
      }

    // Verbs (marked for tense)
    } else if(pVerbTenseMarker.matcher(spec).find()) {

      // Tense feature
      if(isActive(MorphoFeatureType.TENSE)) {
        if(spec.contains("PV"))
          features.addFeature(MorphoFeatureType.TENSE, tenseVals[0]);
        else if(spec.contains("IV"))
          features.addFeature(MorphoFeatureType.TENSE, tenseVals[1]);
        else if(spec.contains("CV"))
          features.addFeature(MorphoFeatureType.TENSE, tenseVals[2]);
      }

      // Inflectional features
      processInflectionalFeatures(features, spec);

      if(isActive(MorphoFeatureType.MOOD)) {
        Matcher moodMatcher = pMood.matcher(spec);
        if(moodMatcher.find()) {
          String moodStr = moodMatcher.group(1);
          if(moodStr.equals("I"))
            features.addFeature(MorphoFeatureType.MOOD, moodVals[0]);
          else if(moodStr.equals("S"))
            features.addFeature(MorphoFeatureType.MOOD, moodVals[1]);
          else if(moodStr.equals("J"))
            features.addFeature(MorphoFeatureType.MOOD, moodVals[2]);
        }
      }

      if(isActive(MorphoFeatureType.VOICE)) {
        if(spec.contains("PASS")) {
          features.addFeature(MorphoFeatureType.VOICE, voiceVals[1]);
        } else {
          features.addFeature(MorphoFeatureType.VOICE, voiceVals[0]);
        }
      }
    }
    return features;
  }

  /**
   * Extract features from a standard phi feature specification.
   *
   * @param feats
   * @param spec
   */
  private void processInflectionalFeatures(MorphoFeatures feats, String spec) {
    // Extract the feature tuple
    Matcher m = pFeatureTuple.matcher(spec);
    if (m.find()) {
      spec = m.group(1);
      processInflectionalFeaturesHelper(feats, spec);
    }
  }

  private void processInflectionalFeaturesHelper(MorphoFeatures feats, String spec) {
    if(isActive(MorphoFeatureType.GEN)) {
      if(spec.contains("M"))
        feats.addFeature(MorphoFeatureType.GEN, genVals[0]);
      else if(spec.contains("F"))
        feats.addFeature(MorphoFeatureType.GEN, genVals[1]);
    }

    if(isActive(MorphoFeatureType.NUM)) {
      if(spec.endsWith("S"))
        feats.addFeature(MorphoFeatureType.NUM, numVals[0]);
      else if(spec.endsWith("D"))
        feats.addFeature(MorphoFeatureType.NUM, numVals[1]);
      else if(spec.endsWith("P"))
        feats.addFeature(MorphoFeatureType.NUM, numVals[2]);
    }

    if(isActive(MorphoFeatureType.PER)) {
      if(spec.contains("1"))
        feats.addFeature(MorphoFeatureType.PER, perVals[0]);
      else if(spec.contains("2"))
        feats.addFeature(MorphoFeatureType.PER, perVals[1]);
      else if(spec.contains("3"))
        feats.addFeature(MorphoFeatureType.PER, perVals[2]);
    }
  }

  /**
   * Converts features specifications to labels for tagging
   *
   * @author Spence Green
   *
   */
  public static class ArabicMorphoFeatures extends MorphoFeatures {

    private static final long serialVersionUID = -4611776415583633186L;

    @Override
    public MorphoFeatures fromTagString(String str) {
      String[] feats = str.split("\\-");
      MorphoFeatures mFeats = new ArabicMorphoFeatures();
      // First element is the base POS
//      String baseTag = feats[0];
      for(int i = 1; i < feats.length; i++) {
        String[] keyValue = feats[i].split(KEY_VAL_DELIM);
        if(keyValue.length != 2) continue;
        MorphoFeatureType fName = MorphoFeatureType.valueOf(keyValue[0].trim());
        mFeats.addFeature(fName, keyValue[1].trim());
      }
      return mFeats;
    }

    @Override
    public String getTag(String basePartOfSpeech) {
      StringBuilder sb = new StringBuilder(basePartOfSpeech);
      // Iterate over feature list so that features are added in the same order
      // for every feature spec.
      for (MorphoFeatureType feat : MorphoFeatureType.values()) {
        if (hasFeature(feat)) {
          sb.append(String.format("-%s:%s",feat,fSpec.get(feat)));
        }
      }
      return sb.toString();
    }
  }

  /**
   * For debugging. Converts a set of long tags (BAMA analyses as in the ATB) to their morpho
   * feature specification. The input file should have one long tag per line.
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 2) {
      System.err.printf("Usage: java %s filename feats%n", ArabicMorphoFeatureSpecification.class.getName());
      System.exit(-1);
    }

    MorphoFeatureSpecification fSpec = new ArabicMorphoFeatureSpecification();
    String[] feats = args[1].split(",");
    for(String feat : feats) {
      MorphoFeatureType fType = MorphoFeatureType.valueOf(feat);
      fSpec.activate(fType);
    }

    File fName = new File(args[0]);
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fName)));

      int nLine = 0;
      for(String line;(line = br.readLine()) != null; nLine++) {
        MorphoFeatures mFeats = fSpec.strToFeatures(line.trim());
        System.out.printf("%s\t%s%n", line.trim(), mFeats.toString());
      }
      br.close();
      System.out.printf("%nRead %d lines%n",nLine);

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
