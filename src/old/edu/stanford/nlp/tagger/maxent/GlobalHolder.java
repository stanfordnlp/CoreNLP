//GlobalHolder -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2008 Leland Stanford Junior University


//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml

package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.io.InDataStreamFile;
import old.edu.stanford.nlp.io.OutDataStreamFile;
import old.edu.stanford.nlp.io.PrintFile;
import old.edu.stanford.nlp.maxent.iis.LambdaSolve;
import old.edu.stanford.nlp.util.StringUtils;
import old.edu.stanford.nlp.util.Timing;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


/** This class holds many global variables and other things that are used by
 *  the Stanford MaxEnt Part-of-speech Tagger package.
 *
 *  @author Kristina Toutanova
 *  @author Anna Rafferty
 *  @author Michel Galley
 *  @version 1.1
 */
public class GlobalHolder {

  private GlobalHolder() {}

  static TaggerExperiments domain;
  static final Dictionary dict = new Dictionary();
  static TTags tags;

  static byte[][] fnumArr;
  static LambdaSolveTagger prob;
  static HashMap<FeatureKey,Integer> fAssociations = new HashMap<FeatureKey,Integer>();
  static final TemplateHash tFeature = new TemplateHash();
  //static PairsHolder pairs = new PairsHolder();
  static Extractors extractors;
  static Extractors extractorsRare;
  static final AmbiguityClasses ambClasses = new AmbiguityClasses();
  static final CollectionTaggerOutputs collectionTaggers = new CollectionTaggerOutputs(0);
  static final boolean alltags = false;
  static final HashMap<String, HashSet<String>> tagTokens = new HashMap<String, HashSet<String>>();

  static final int RARE_WORD_THRESH = 5;
  static final int MIN_FEATURE_THRESH = 5;
  static final int CUR_WORD_MIN_FEATURE_THRESH = 2;
  static final int RARE_WORD_MIN_FEATURE_THRESH = 10;
  static final int VERY_COMMON_WORD_THRESH = 250;

  static final boolean OCCURRING_TAGS_ONLY = false;
  static final boolean POSSIBLE_TAGS_ONLY = false;

  static double defaultScore;

  /**
   * Determines which words are considered rare.  All words with count
   * in the training data strictly less than this number (standardly, &lt; 5) are
   * considered rare.
   */
  private static int rareWordThresh = RARE_WORD_THRESH;

  /**
   * Determines which features are included in the model.  The model
   * includes features that occurred strictly more times than this number
   * (standardly, &gt; 5) in the training data.  Here I look only at the
   * history (not the tag), so the history appearing this often is enough.
   */
  static int minFeatureThresh = MIN_FEATURE_THRESH;

  /**
   * This is a special threshold for the current word feature.
   * Only words that have occurred strictly &gt; this number of times
   * in total will generate word features with all of their occurring tags.
   * The traditional default was 2.
   */
  static int curWordMinFeatureThresh = CUR_WORD_MIN_FEATURE_THRESH;

  /**
   * Determines which rare word features are included in the model.
   * The features for rare words have a strictly higher support than
   * this number are included. Traditional default is 10.
   */
  static int rareWordMinFeatureThresh = RARE_WORD_MIN_FEATURE_THRESH;

  /**
   * If using tag equivalence classes on following words, words that occur
   * strictly more than this number of times (in total with any tag)
   * are sufficiently frequent to form an equivalence class
   * by themselves. (Not used unless using equivalence classes.)
   */
  static int veryCommonWordThresh = VERY_COMMON_WORD_THRESH;


  static int xSize;
  static int ySize;
  static boolean occuringTagsOnly = OCCURRING_TAGS_ONLY;
  static boolean possibleTagsOnly = POSSIBLE_TAGS_ONLY;

  private static boolean initted = false;

  static final boolean VERBOSE = false;



  public static LambdaSolve getLambdaSolve() {
    return prob;
  }

  protected static void init() {
    init(null);
  }

  protected static void init(TaggerConfig config) {
    if (initted) return;

    extractors = new Extractors();
    extractorsRare = new Extractors();

    String lang, arch;
    String[] openClassTags, closedClassTags;

    if (config == null) {
      lang = "english";
      arch = "left3words";
      openClassTags = StringUtils.EMPTY_STRING_ARRAY;
      closedClassTags = StringUtils.EMPTY_STRING_ARRAY;
    } else {
      lang = config.getLang();
      arch = config.getArch();
      openClassTags = config.getOpenClassTags();
      closedClassTags = config.getClosedClassTags();

      if (((openClassTags.length > 0) && !lang.equals("")) || ((closedClassTags.length > 0) && !lang.equals("")) || ((closedClassTags.length > 0) && (openClassTags.length > 0))) {
        throw new RuntimeException("At least two of lang (\"" + lang + "\"), openClassTags (length " + openClassTags.length + ": " + Arrays.toString(openClassTags) + ")," +
            "and closedClassTags (length " + closedClassTags.length + ": " + Arrays.toString(closedClassTags) + ") specified---you must choose one!");
      } else if ((openClassTags.length == 0) && lang.equals("") && (closedClassTags.length == 0) && ! config.getLearnClosedClassTags()) {
        System.err.println("warning: no language set, no open-class tags specified, and no closed-class tags specified; assuming ALL tags are open class tags");
      }
    }

    if (openClassTags.length > 0) {
      tags = new TTags();
      tags.setOpenClassTags(openClassTags);
    } else if (closedClassTags.length > 0) {
      tags = new TTags();
      tags.setClosedClassTags(closedClassTags);
    } else {
      tags = new TTags(lang);
    }

    defaultScore = lang.equals("english") ? 1.0 : 0.0;

    if (config != null) {
      rareWordThresh = config.getRareWordThresh();
      minFeatureThresh = config.getMinFeatureThresh();
      curWordMinFeatureThresh = config.getCurWordMinFeatureThresh();
      rareWordMinFeatureThresh = config.getRareWordMinFeatureThresh();
      veryCommonWordThresh = config.getVeryCommonWordThresh();
      occuringTagsOnly = config.occuringTagsOnly();
      possibleTagsOnly = config.possibleTagsOnly();
      // System.err.println("occuringTagsOnly: "+occuringTagsOnly);
      // System.err.println("possibleTagsOnly: "+possibleTagsOnly);

      if(config.getDefaultScore() >= 0)
        defaultScore = config.getDefaultScore();
    }

    if (config != null && config.getMode() == TaggerConfig.Mode.TRAIN) {
      // initialize the extractors based on the arch variable
      // you only need to do this when training; otherwise they will be
      // restored from the serialized file
      extractors.init(ExtractorFrames.getExtractorFrames(arch));
      extractorsRare.init(ExtractorFramesRare.getExtractorFramesRare(arch));
    }

    initted = true;
  }

  protected static int getNum(FeatureKey s) {
    return getNum(s, fAssociations);
  }


  private static int getNum(FeatureKey s, HashMap<FeatureKey, Integer> fAssocs) {
    Integer num = fAssocs.get(s); // hprof: 15% effective running time
    if (num == null) {
      return -1;
    } else {
      return num;
    }
  }


  // serialize the ExtractorFrames and ExtractorFramesRare in filename
  private static void saveExtractors(OutputStream os) throws IOException {

    ObjectOutputStream out = new ObjectOutputStream(os);

    System.out.println(extractors.toString() + "\nrare" + extractorsRare.toString());
    out.writeObject(extractors);
    out.writeObject(extractorsRare);
  }

  // Read the extractors from a filename.
  private static void readExtractors(String filename) throws Exception {
    InputStream in = new BufferedInputStream(new FileInputStream(filename));
    readExtractors(in);
    in.close();
  }

  // Read the extractors from a stream.
  private static void readExtractors(InputStream file) throws IOException, ClassNotFoundException {

    ObjectInputStream in = new ObjectInputStream(file);
    edu.stanford.nlp.tagger.maxent.Extractors temp = (edu.stanford.nlp.tagger.maxent.Extractors) in.readObject();

    Extractor[] extractorsx = new Extractor[temp.size()];
    for (int i = 0; i < temp.getV().length; i++) {
      edu.stanford.nlp.tagger.maxent.Extractor extractor = temp.getV()[i];
      extractorsx[i]  = new Extractor(extractor.getPosition(), extractor.isTag());
    }

    extractors.init(extractorsx);

    edu.stanford.nlp.tagger.maxent.Extractors temp1 = (edu.stanford.nlp.tagger.maxent.Extractors) in.readObject();
    Extractor[] extractorsx1 = new Extractor[temp1.size()];
    for (int i = 0; i < temp1.getV().length; i++) {
      edu.stanford.nlp.tagger.maxent.Extractor extractor = temp1.getV()[i];
      extractorsx1[i]  = new Extractor(extractor.getPosition(), extractor.isTag());
    }

    extractorsRare.init(extractorsx1);
    extractors.initTypes();
    extractorsRare.initTypes();
    int left = extractors.leftContext();
    int left_u = extractorsRare.leftContext();
    if (left_u > left) {
      left = left_u;
    }
    TestSentence.leftContext = left;
    int right = extractors.rightContext();
    int right_u = extractorsRare.rightContext();
    if (right_u > right) {
      right = right_u;
    }
    TestSentence.rightContext = right;
  }


  /* ----
   * NOT USED
   * This reads the .assoc file.  It is only used by LambdaSolveTagger.java
   * The same associations also appear in the main file, and are read by
   * read(), read_prev().
   *
   * @param modelFilename The string .assoc is appended and feature
   *    associations are then read from this file
   * @return The feature associations HashMap, or null if there is an error
   *
  protected static HashMap<FeatureKey, Integer> readAssociations(String modelFilename) {
    try {
      HashMap<FeatureKey, Integer> fAssocs = new HashMap<FeatureKey, Integer>();
      String filename = modelFilename + ".assoc";
      InDataStreamFile rf = new InDataStreamFile(filename);
      int sizeAssoc = rf.readInt();
      for (int i = 0; i < sizeAssoc; i++) {
        int numF = rf.readInt();
        FeatureKey fK = new FeatureKey();
        fK.read(rf);
        fAssocs.put(fK, numF);
      }
      rf.close();
      return fAssocs;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
  ---- */

  /* ----------- unused
  public static void release_mem() {
    // release dict
    dict.release();
    pairs.release();
    tHistories.release();
    tFeature.release();
    fAssociations.clear();
  }
  -------------- */

  protected static void saveModel(String filename, TaggerConfig config) {
    try {
      OutDataStreamFile file = new OutDataStreamFile(filename);
      config.saveConfig(file);
      file.writeInt(xSize);
      file.writeInt(ySize);
      dict.save(file);
      tags.save(file);

      saveExtractors(file);

      file.writeInt(fAssociations.size());
      for (Map.Entry<FeatureKey,Integer> item : fAssociations.entrySet()) {
        int numF = item.getValue();
        file.writeInt(numF);
        FeatureKey fk = item.getKey();
        fk.save(file);
      }

      LambdaSolve.save_lambdas(file, prob.lambda);
      file.close();
    } catch (IOException ioe) {
      System.err.println("Error saving tagger to file " + filename);
      ioe.printStackTrace();
    }
  }


  /**
   * This method is provided for backwards compatibility with the old tagger.  It reads
   * a tagger that was saved as multiple files into the current format and saves it back
   * out as a single file, newFilename.
   *
   * @param filename The name of the holder file, which is also used as a prefix for other filenames
   * @param newFilename The name of the new one-file model that will be written
   * @param config tagger configuration file
   * @return true (whether this operation succeeded; always true
   * @throws Exception ??
   */
  protected static boolean convertMultifileTagger(String filename, String newFilename, TaggerConfig config) throws Exception {
    InDataStreamFile rf = new InDataStreamFile(filename);
    GlobalHolder.init(config);
    if (VERBOSE) {
      System.err.println(" length of holder " + new File(filename).length());
    }

    xSize = rf.readInt();
    ySize = rf.readInt();
    dict.read(filename + ".dict");

    if (VERBOSE) {
      System.err.println(" dictionary read ");
    }
    tags.read(filename + ".tags");
    readExtractors(filename + ".ex");

    dict.setAmbClasses();

    int[] numFA = new int[extractors.getSize() + extractorsRare.getSize()];
    int sizeAssoc = rf.readInt();
    PrintFile pfVP = null;
    if (VERBOSE) {
      pfVP = new PrintFile("pairs.txt");
    }
    for (int i = 0; i < sizeAssoc; i++) {
      int numF = rf.readInt();
      FeatureKey fK = new FeatureKey();
      fK.read(rf);
      numFA[fK.num]++;
      fAssociations.put(fK, numF);
    }

    if (VERBOSE) {
      pfVP.close();
    }
    if (VERBOSE) {
      for (int k = 0; k < numFA.length; k++) {
        System.err.println(" Number of features of kind " + k + ' ' + numFA[k]);
      }
    }
    prob = new LambdaSolveTagger(filename + ".prob");
    if (VERBOSE) {
      System.err.println(" prob read ");
    }

    saveModel(newFilename, config);
    rf.close();
    return true;
  }


  /** This reads the complete tagger from a single model stored in a file, at a URL,
   *  or as a resource
   *  in a jar file, and inits the tagger using a
   *  combination of the properties passed in and parameters from the file.
   *  <p>
   *  <i>Note for the future:</i> This assumes that the TaggerConfig in the file
   *  has already been read and used.  This work is done inside the
   *  constructor of TaggerConfig.  It might be better to refactor
   *  things so that is all done inside this method, but for the moment
   *  it seemed better to leave working code alone [cdm 2008].
   *
   *  @param config The tagger config
   *  @param modelFileOrUrl The name of the model file. This routine opens and closes it.
   *  @throws Exception If I/O errors, etc.
   */
  protected static TaggerConfig readModelAndInit(TaggerConfig config, String modelFileOrUrl, boolean printLoading) throws Exception {
    // first check can open file ... or else leave with exception
    DataInputStream rf = config.getTaggerDataInputStream(modelFileOrUrl);

    // if (VERBOSE) {
    //   System.err.println(" length of model holder " + new File(modelFileOrUrl).length());
    // }

    TaggerConfig tc = readModelAndInit(config, rf, printLoading);
    rf.close();
    return tc;
  }



  /** This reads the complete tagger from a single model file, and inits
   *  the tagger using a combination of the properties passed in and
   *  parameters from the file.
   *  <p>
   *  <i>Note for the future: This assumes that the TaggerConfig in the file
   *  has already been read and used.  It might be better to refactor
   *  things so that is all done inside this method, but for the moment
   *  it seemed better to leave working code alone [cdm 2008].</i>
   *
   *  @param config The tagger config
   *  @param rf DataInputStream to read from.  It's the caller's job to open and close this stream.
   *  @throws IOException If I/O errors
   *  @throws ClassNotFoundException If serialization errors
   */
  protected static TaggerConfig readModelAndInit(TaggerConfig config, DataInputStream rf,
                                         boolean printLoading) throws IOException, ClassNotFoundException {
    Timing t = new Timing();
    if (printLoading) t.doing("Reading POS tagger model from " + config.getModel());
    // then init tagger
    init(config);
    TaggerConfig ret = TaggerConfig.readConfig(rf); // taggerconfig in file has already been put into config in constructor of TaggerConfig, so usually just read past it.

    xSize = rf.readInt();
    ySize = rf.readInt();
    dict.read(rf);

    if (VERBOSE) {
      System.err.println(" dictionary read ");
    }
    tags.read(rf);
    readExtractors(rf);
    dict.setAmbClasses();

    int[] numFA = new int[extractors.getSize() + extractorsRare.getSize()];
    int sizeAssoc = rf.readInt();
    // init the Hash at the right size for efficiency (avoid resizing ops)
    // mg2008: sizeAssoc defines the number of keys, whereas specifying
    // sizeAssoc as argument defines an initial size.
    // Unless load factor is >= 1, fAssociations is guaranteed to resize at least once.
    //fAssociations = new HashMap<FeatureKey,Integer>(sizeAssoc);
    fAssociations = new HashMap<FeatureKey,Integer>(sizeAssoc*2);
    if (VERBOSE) System.err.printf("Reading %d feature keys...\n",sizeAssoc);
    PrintFile pfVP = null;
    if (VERBOSE) {
      pfVP = new PrintFile("pairs.txt");
    }
    for (int i = 0; i < sizeAssoc; i++) {
      int numF = rf.readInt();
      FeatureKey fK = new FeatureKey();
      fK.read(rf);
      numFA[fK.num]++;
      fAssociations.put(fK, numF);
    }
    if (VERBOSE) {
      pfVP.close();
    }
    if (VERBOSE) {
      for (int k = 0; k < numFA.length; k++) {
        System.err.println(" Number of features of kind " + k + ' ' + numFA[k]);
      }
    }
    prob = new LambdaSolveTagger(rf);
    if (VERBOSE) {
      System.err.println(" prob read ");
    }
    if (printLoading) t.done();
    return ret;
  }


  protected static void dumpModel() {
    assert fAssociations.size() == prob.lambda.length;
    for (Map.Entry<FeatureKey,Integer> fk : fAssociations.entrySet()) {
      System.out.println(fk.getKey() + ": " + prob.lambda[fk.getValue()]);
    }
  }


  protected static boolean isRare(String word) {
    return dict.sum(word) < rareWordThresh;
  }

  public static TTags getTags() {
    return tags;
  }

}
