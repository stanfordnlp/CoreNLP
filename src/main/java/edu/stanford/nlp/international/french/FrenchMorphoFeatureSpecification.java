package edu.stanford.nlp.international.french; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatures;

/**
 * If MorphoFeatureType.OTHER is active, then the "CC tagset" is produced (see Tbl.2
 * of (Crabbe and Candito, 2008). Additional support exists for GEN, NUM, and PER, which
 * are (mostly) marked in the FTB annotation.
 * <p>
 * The actual CC tag is placed in the altTag field of the MorphoFeatures object.
 * 
 * @author Spence Green
 *
 */
public class FrenchMorphoFeatureSpecification extends MorphoFeatureSpecification  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FrenchMorphoFeatureSpecification.class);

  private static final long serialVersionUID = -58379347760106784L;

  public static final String[] genVals = {"M","F"};
  public static final String[] numVals = {"SG","PL"};
  public static final String[] perVals = {"1","2","3"};


  @Override
  public List<String> getValues(MorphoFeatureType feat) {
    if(feat == MorphoFeatureType.GEN)
      return Arrays.asList(genVals);
    else if(feat == MorphoFeatureType.NUM)
      return Arrays.asList(numVals);
    else if(feat == MorphoFeatureType.PER)
      return Arrays.asList(perVals);
    else
      throw new IllegalArgumentException("French does not support feature type: " + feat.toString());
  }

  @Override
  public MorphoFeatures strToFeatures(String spec) {
    MorphoFeatures feats = new MorphoFeatures();

    //Usually this is the boundary symbol
    if(spec == null || spec.equals(""))
      return feats;

    boolean isOtherActive = isActive(MorphoFeatureType.OTHER);
    
    if(spec.startsWith("ADV")) {
      feats.setAltTag("ADV");
      if(spec.contains("int")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "advint");
        }
        feats.setAltTag("ADVWH");
      }

    } else if(spec.startsWith("A")) {
      feats.setAltTag("ADJ");
      if(spec.contains("int")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "adjint");
        }
        feats.setAltTag("ADJWH");
      }
      
      addPhiFeatures(feats,spec);

    } else if(spec.equals("CC") || spec.equals("C-C")) {
      if (isOtherActive) {
        feats.addFeature(MorphoFeatureType.OTHER, "Cc");
      }
      feats.setAltTag("CC");

    } else if(spec.equals("CS") || spec.equals("C-S")) {
      if (isOtherActive) {
        feats.addFeature(MorphoFeatureType.OTHER, "Cs");
      }
      feats.setAltTag("CS");

    } else if(spec.startsWith("CL")) {
      feats.setAltTag("CL");
      if(spec.contains("suj") || spec.equals("CL-S-3fp")) {//"CL-S-3fp" is equivalent to suj
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER,"Sbj");
        }
        feats.setAltTag("CLS");

      } else if(spec.contains("obj")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Obj");
        }
        feats.setAltTag("CLO");

      } else if(spec.contains("refl")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Rfl");
        }
        feats.setAltTag("CLR");
      }

      addPhiFeatures(feats,spec);

    } else if(spec.startsWith("D")) {
      feats.setAltTag("DET");
      if(spec.contains("int")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "dint");
        }
        feats.setAltTag("DETWH");
      }

      addPhiFeatures(feats,spec);

    } else if(spec.startsWith("N")) {
      feats.setAltTag("N");//TODO These are usually N-card...make these CD?
      if(spec.contains("P")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Np");
        }
        feats.setAltTag("NPP");

      } else if(spec.contains("C")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Nc");
        }
        feats.setAltTag("NC");
      }

      addPhiFeatures(feats,spec);

    } else if(spec.startsWith("PRO")) {
      feats.setAltTag("PRO");
      if(spec.contains("int")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER,"Ni");
        }
        feats.setAltTag("PROWH");

      } else if(spec.contains("rel")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Nr");
        }
        feats.setAltTag("PROREL");
      }

      addPhiFeatures(feats,spec);

    } else if(spec.startsWith("V")) {
      feats.setAltTag("V");
      if(spec.contains("Y")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER,"Vp");
        }
        feats.setAltTag("VIMP");

      } else if(spec.contains("W")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Vf");
        }
        feats.setAltTag("VINF");
        
      } else if(spec.contains("S") || spec.contains("T")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Vs");
        }
        feats.setAltTag("VS");
        
      } else if(spec.contains("K")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Vp");
        }
        feats.setAltTag("VPP");
        
      } else if(spec.contains("G")) {
        if (isOtherActive) {
          feats.addFeature(MorphoFeatureType.OTHER, "Vr");
        }
        feats.setAltTag("VPR");
      }
      
      addPhiFeatures(feats,spec);
    
    } else if(spec.equals("P") || spec.equals("I")) { 
      feats.setAltTag(spec);
      
    } 
//    else {
//      log.info("Could not map spec: " + spec);
//    }

    return feats;
  }

  private void addPhiFeatures(MorphoFeatures feats, String spec) {
    String[] toks = spec.split("\\-+");

    String morphStr;
    if(toks.length == 3 && toks[0].equals("PRO") && toks[2].equals("neg"))
      morphStr = toks[1];
    else
      morphStr = toks[toks.length-1];

    //wsg2011: The analyses have mixed casing....
    morphStr = morphStr.toLowerCase();

    if(isActive(MorphoFeatureType.GEN)) {
      if(morphStr.contains("m"))
        feats.addFeature(MorphoFeatureType.GEN, genVals[0]);
      else if(morphStr.contains("f"))
        feats.addFeature(MorphoFeatureType.GEN, genVals[1]);
    }

    if(isActive(MorphoFeatureType.PER)) {
      if(morphStr.contains("1"))
        feats.addFeature(MorphoFeatureType.PER, perVals[0]);
      else if(morphStr.contains("2"))
        feats.addFeature(MorphoFeatureType.PER, perVals[1]);
      else if(morphStr.contains("3"))
        feats.addFeature(MorphoFeatureType.PER, perVals[2]);
    }

    if(isActive(MorphoFeatureType.NUM)) {
      if(morphStr.contains("s"))
        feats.addFeature(MorphoFeatureType.NUM, numVals[0]);
      else if(morphStr.contains("p"))
        feats.addFeature(MorphoFeatureType.NUM, numVals[1]);
    }
  }


  /**
   * For debugging
   * 
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.printf("Usage: java %s file%n", FrenchMorphoFeatureSpecification.class.getName());
      System.exit(-1);
    }

    try {
      BufferedReader br = new BufferedReader(new FileReader(args[0]));
      MorphoFeatureSpecification mfs = new FrenchMorphoFeatureSpecification();

      //Activate all features for debugging
      mfs.activate(MorphoFeatureType.GEN);
      mfs.activate(MorphoFeatureType.NUM);
      mfs.activate(MorphoFeatureType.PER);

      for(String line; (line = br.readLine()) != null;) {
        MorphoFeatures feats = mfs.strToFeatures(line);
        System.out.printf("%s\t%s%n", line.trim(),feats.toString());
      }

      br.close();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
