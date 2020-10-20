package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.util.Generics;

/**
 * Maps LDC-provided Bies mappings to the Universal POS tag set described in
 *  
 *   Slav Petrov, Dipanjan Das and Ryan McDonald. "A Universal Part-of-Speech Tagset."
 * <p>
 * Includes optional support for adding morphological annotations via the setup method.
 * 
 * @author Spence Green
 *
 */
public class UniversalPOSMapper extends LDCPosMapper  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(UniversalPOSMapper.class);

  private final Map<String,String> universalMap;
  private final MorphoFeatureSpecification morphoSpec;
  
  public UniversalPOSMapper(){
    super(false); //Don't add the determiner split
    
    universalMap = Generics.newHashMap();
    morphoSpec = new ArabicMorphoFeatureSpecification();
  }
  
  /**
   * First map to the LDC short tags. Then map to the Universal POS. Then add
   * morphological annotations.
   */
  @Override
  public String map(String posTag, String terminal) {
    String rawTag = posTag.trim();

    String shortTag = tagsToEscape.contains(rawTag) ? rawTag : tagMap.get(rawTag);
    if( shortTag == null ) {
      System.err.printf("%s: No LDC shortened tag for %s%n", this.getClass().getName(), rawTag);
      return rawTag;
    }
    
    String universalTag = universalMap.get(shortTag);
    if( ! universalMap.containsKey(shortTag)) {
      System.err.printf("%s: No universal tag for LDC tag %s%n", this.getClass().getName(),shortTag);
      universalTag = shortTag;
    }
   
    MorphoFeatures feats = new MorphoFeatures(morphoSpec.strToFeatures(rawTag));
    
    String functionalTag = feats.getTag(universalTag);
    
    return functionalTag;
  }
  
  @Override
  public void setup(File path, String... options) {
    //Setup the Bies tag mapping
    super.setup(path, new String[0]);
    
    for(String opt : options) {
      String[] optToks = opt.split(":");
      if(optToks[0].equals("UniversalMap") && optToks.length == 2) {
        loadUniversalMap(optToks[1]);
      
      } else {
        //Maybe it's a morphological feature
        //Both of these calls will throw exceptions if the feature is illegal/invalid
        MorphoFeatureType feat = MorphoFeatureType.valueOf(optToks[0]);
        List<String> featVals = morphoSpec.getValues(feat);
        morphoSpec.activate(feat);
      }
    }
  }

  private void loadUniversalMap(String path) {
    
    LineNumberReader reader = null;
    try {
      reader = new LineNumberReader(new FileReader(path));
      
      for(String line; (line = reader.readLine()) != null;) {
        if(line.trim().equals("")) continue;
        
        String[] toks = line.trim().split("\\s+");
        if(toks.length != 2)
          throw new RuntimeException("Invalid mapping line: " + line);
        
        universalMap.put(toks[0], toks[1]);
      }
      
      reader.close();
    
    } catch (FileNotFoundException e) {
      System.err.printf("%s: File not found %s%n", this.getClass().getName(),path);
    
    } catch (IOException e) {
      int lineId = (reader == null) ? -1 : reader.getLineNumber();
      System.err.printf("%s: Error at line %d%n", this.getClass().getName(),lineId);
      e.printStackTrace();
    }
  }
}
