// TaggerExperiments -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2008 Leland Stanford Junior University
//
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tagger.shtml

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.maxent.Experiments;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;


/**
 * This class represents the training samples. It can return statistics of
 * them, for example the frequency of each x or y in the training data.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class TaggerExperiments extends Experiments {

  private static final boolean DEBUG = true;
  private static final String zeroSt = "0";

  private final TaggerFeatures feats;
  private final Set<FeatureKey> sTemplates = Generics.newHashSet();
  private final HistoryTable tHistories = new HistoryTable();

  private final int numFeatsGeneral;
  private final int numFeatsAll;

  private final MaxentTagger maxentTagger;

  private final TemplateHash tFeature;

  private byte[][] fnumArr;



  // This constructor is only used by unit tests.
  TaggerExperiments(MaxentTagger maxentTagger) {
    this.maxentTagger = maxentTagger;
    this.tFeature = new TemplateHash(maxentTagger);
    numFeatsGeneral = maxentTagger.extractors.getSize();
    numFeatsAll = numFeatsGeneral + maxentTagger.extractorsRare.getSize();
    feats = new TaggerFeatures(maxentTagger.tags, this);
  }

  /** This method gets feature statistics from a training file found in the TaggerConfig. */
  protected TaggerExperiments(TaggerConfig config, MaxentTagger maxentTagger) throws IOException {
    this(maxentTagger);

    System.err.println("TaggerExperiments: adding word/tags");
    PairsHolder pairs = new PairsHolder();
    ReadDataTagged c = new ReadDataTagged(config, maxentTagger, pairs);
    vArray = new int[c.getSize()][2];

    initTemplatesNew();
    System.err.println("Featurizing tagged data tokens...");
    for (int i = 0, size = c.getSize(); i < size; i++) {

      DataWordTag d = c.get(i);
      String yS = d.getY();
      History h = d.getHistory();
      int indX = tHistories.add(h);
      int indY = d.getYInd();
      addTemplatesNew(h, yS);
      addRareTemplatesNew(h, yS);
      vArray[i][0] = indX;
      vArray[i][1] = indY;

      if (i > 0 && (i % 10000) == 0) {
        System.err.printf("%d ", i);
        if (i % 100000 == 0) { System.err.println(); }
      }
    }
    System.err.println();
    System.err.println("Featurized " + c.getSize() + " data tokens [done].");
    c.release();
    ptilde();
    maxentTagger.xSize = xSize;
    maxentTagger.ySize = ySize;
    System.err.println("xSize [num Phi templates] = " + xSize + "; ySize [num classes] = " + ySize);

    hashHistories();

    // if we'll look at occurring tags only, we need the histories and pairs still
    if (!maxentTagger.occurringTagsOnly && !maxentTagger.possibleTagsOnly) {
      tHistories.release();
      pairs.clear();
    }

    getFeaturesNew();
  }


  public TaggerFeatures getTaggerFeatures() {
    return feats;
  }


  /** Adds a FeatureKey to the set of known FeatureKeys.
   *
   * @param s The feature key to be added
   * @return Whether the key was already known (false) or added (true)
   */
  protected boolean add(FeatureKey s) {
    if ((sTemplates.contains(s))) {
      return false;
    }
    sTemplates.add(s);
    return true;
  }

  byte[][] getFnumArr() {
    return fnumArr;
  }

  /** This method uses and deletes a file tempXXXXXX.x in the current directory! */
  private void getFeaturesNew() {
    // todo: Change to rethrow a RuntimeIOException.
    // todo: can fnumArr overflow?
    try {
      System.err.println("TaggerExperiments.getFeaturesNew: initializing fnumArr.");
      fnumArr = new byte[xSize][ySize]; // what is the maximum number of active features
      File hFile = File.createTempFile("temp",".x", new File("./"));
      RandomAccessFile hF = new RandomAccessFile(hFile, "rw");
      System.err.println("  length of sTemplates keys: " + sTemplates.size());
      System.err.println("getFeaturesNew adding features ...");
      int current = 0;
      int numFeats = 0;
      final boolean VERBOSE = false;
      for (FeatureKey fK : sTemplates) {
        int numF = fK.num;
        int[] xValues;
        Pair<Integer, String> wT = new Pair<Integer, String>(numF, fK.val);
        xValues = tFeature.getXValues(wT);
        if (xValues == null) {
          System.err.println("  xValues is null: " + fK.toString()); //  + " " + i
          continue;
        }
        int numEvidence = 0;
        int y = maxentTagger.tags.getIndex(fK.tag);
        for (int xValue : xValues) {

          if (maxentTagger.occurringTagsOnly) {
            //check whether the current word in x has occurred with y
            String word = ExtractorFrames.cWord.extract(tHistories.getHistory(xValue));
            if (maxentTagger.dict.getCount(word, fK.tag) == 0) {
              continue;
            }
          }
          if (maxentTagger.possibleTagsOnly) {
            String word = ExtractorFrames.cWord.extract(tHistories.getHistory(xValue));
            String[] tags = maxentTagger.dict.getTags(word);
            Set<String> s = Generics.newHashSet(Arrays.asList(maxentTagger.tags.deterministicallyExpandTags(tags)));
            if(DEBUG)
              System.err.printf("possible tags for %s: %s\n", word, Arrays.toString(s.toArray()));
            if(!s.contains(fK.tag))
              continue;
          }
          numEvidence += this.px[xValue];
        }

        if (populated(numF, numEvidence)) {
          int[] positions = tFeature.getPositions(fK);
          if (maxentTagger.occurringTagsOnly || maxentTagger.possibleTagsOnly) { // TODO
            positions = null;
          }

          if (positions == null) {
            // write this in the file and create a TaggerFeature for it
            //int numElem
            int numElements = 0;

            for (int x : xValues) {
              if (maxentTagger.occurringTagsOnly) {
                //check whether the current word in x has occurred with y
                String word = ExtractorFrames.cWord.extract(tHistories.getHistory(x));
                if (maxentTagger.dict.getCount(word, fK.tag) == 0) {
                  continue;
                }
              }
              if(maxentTagger.possibleTagsOnly) {
                String word = ExtractorFrames.cWord.extract(tHistories.getHistory(x));
                String[] tags = maxentTagger.dict.getTags(word);
                Set<String> s = Generics.newHashSet(Arrays.asList(maxentTagger.tags.deterministicallyExpandTags(tags)));
                if(!s.contains(fK.tag))
                  continue;
              }
              numElements++;

              hF.writeInt(x);
              fnumArr[x][y]++;
            }
            TaggerFeature tF = new TaggerFeature(current, current + numElements - 1, fK,
                                                 maxentTagger.tags, this);
            tFeature.addPositions(current, current + numElements - 1, fK);
            current = current + numElements;
            feats.add(tF);
            if (VERBOSE) {
              System.err.println("  added feature with key " + fK.toString() + " has support " + numElements);
            }
          } else {

            for(int x : xValues) {
              fnumArr[x][y]++;
            }
            // this is the second time to write these values
            TaggerFeature tF = new TaggerFeature(positions[0], positions[1], fK,
                                                 maxentTagger.tags, this);
            feats.add(tF);
            if (VERBOSE) {
              System.err.println("  added feature with key " + fK.toString() + " has support " + xValues.length);
            }
          }

          // TODO: rearrange some of this code, such as not needing to
          // look up the tag # in the index
          if (maxentTagger.fAssociations.size() <= fK.num) {
            for (int i = maxentTagger.fAssociations.size(); i <= fK.num; ++i) {
              maxentTagger.fAssociations.add(Generics.<String, int[]>newHashMap());
            }
          }
          Map<String, int[]> fValueAssociations = maxentTagger.fAssociations.get(fK.num);
          int[] fTagAssociations = fValueAssociations.get(fK.val);
          if (fTagAssociations == null) {
            fTagAssociations = new int[ySize];
            for (int i = 0; i < ySize; ++i) {
              fTagAssociations[i] = -1;
            }
            fValueAssociations.put(fK.val, fTagAssociations);
          }
          fTagAssociations[maxentTagger.tags.getIndex(fK.tag)] = numFeats;

          numFeats++;
        }

      } // foreach FeatureKey fK
      // read out the file and put everything in an array of ints stored in Feats
      tFeature.release();
      feats.xIndexed = new int[current];
      hF.seek(0);
      int current1 = 0;
      while (current1 < current) {
        feats.xIndexed[current1] = hF.readInt();
        current1++;
      }
      System.err.println("  total feats: " + sTemplates.size() + ", populated: " + numFeats);
      hF.close();
      hFile.delete();

      // what is the maximum number of active features per pair
      int max = 0;
      int maxGt = 0;
      int numZeros = 0;
      for (int x = 0; x < xSize; x++) {
        int numGt = 0;
        for (int y = 0; y < ySize; y++) {
          if (fnumArr[x][y] > 0) {
            numGt++;
            if (max < fnumArr[x][y]) {
              max = fnumArr[x][y];
            }
          } else {
            // if 00
            numZeros++;
          }
        }
        if (maxGt < numGt) {
          maxGt = numGt;
        }
      } // for x

      System.err.println("  Max features per x,y pair: " + max);
      System.err.println("  Max non-zero y values for an x: " + maxGt);
      System.err.println("  Number of non-zero feature x,y pairs: " +
          (xSize * ySize - numZeros));
      System.err.println("  Number of zero feature x,y pairs: " + numZeros);
      System.err.println("end getFeaturesNew.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  private void hashHistories() {
    int fAll = maxentTagger.extractors.getSize() + maxentTagger.extractorsRare.getSize();
    int fGeneral = maxentTagger.extractors.getSize();
    System.err.println("Hashing histories ...");
    for (int x = 0; x < xSize; x++) {
      History h = tHistories.getHistory(x);
      if (x > 0 && x % 10000 == 0) {
        System.err.printf("%d ",x);
        if (x % 100000 == 0) { System.err.println(); }
      }
      int fSize = (maxentTagger.isRare(ExtractorFrames.cWord.extract(h)) ? fAll : fGeneral);
      for (int i = 0; i < fSize; i++) {
        tFeature.addPrev(i, h);
      }
    } // for x
    // now for the populated ones
    System.err.println();
    System.err.println("Hashed " + xSize + " histories.");
    System.err.println("Hashing populated histories ...");
    for (int x = 0; x < xSize; x++) {
      History h = tHistories.getHistory(x);
      if (x > 0 && x % 10000 == 0) {
        System.err.print(x + " ");
        if (x % 100000 == 0) { System.err.println(); }
      }
      int fSize = (maxentTagger.isRare(ExtractorFrames.cWord.extract(h)) ? fAll : fGeneral);
      for (int i = 0; i < fSize; i++) {
        tFeature.add(i, h, x); // write this to check whether to add
      }
    } // for x
    System.err.println();
    System.err.println("Hashed populated histories.");
  }


  protected boolean populated(int fNo, int size) {
    return isPopulated(fNo, size, maxentTagger);
  }

  protected static boolean isPopulated(int fNo, int size, MaxentTagger maxentTagger) {
    // Feature number 0 is hard-coded as the current word feature, which has a special threshold
    if (fNo == 0) {
      return (size > maxentTagger.curWordMinFeatureThresh);
    } else if (fNo < maxentTagger.extractors.getSize()) {
      return (size > maxentTagger.minFeatureThresh);
    } else {
      return (size > maxentTagger.rareWordMinFeatureThresh);
    }
  }

  private void initTemplatesNew() {
    maxentTagger.dict.setAmbClasses(maxentTagger.ambClasses, maxentTagger.veryCommonWordThresh, maxentTagger.tags);
  }


  // Add a new feature key in a hashtable of feature templates
  private void addTemplatesNew(History h, String tag) {
    // Feature templates general

    for (int i = 0; i < numFeatsGeneral; i++) {
      String s = maxentTagger.extractors.extract(i, h);
      if (s.equals(zeroSt)) {
        continue;
      } //do not add the feature
      //iterate over tags in dictionary
      if (maxentTagger.alltags) {
        int numTags = maxentTagger.tags.getSize();
        for (int j = 0; j < numTags; j++) {

          String tag1 = maxentTagger.tags.getTag(j);

          FeatureKey key = new FeatureKey(i, s, tag1);

          if (!maxentTagger.extractors.get(i).precondition(tag1)) {
            continue;
          }

          add(key);
        }
      } else {
        //only this tag
        FeatureKey key = new FeatureKey(i, s, tag);

        if (!maxentTagger.extractors.get(i).precondition(tag)) {
          continue;
        }

        add(key);
      }
    }
  }


  private void addRareTemplatesNew(History h, String tag) {
    // Feature templates rare

    if (!(maxentTagger.isRare(ExtractorFrames.cWord.extract(h)))) {
      return;
    }
    int start = numFeatsGeneral;
    for (int i = start; i < numFeatsAll; i++) {
      String s = maxentTagger.extractorsRare.extract(i - start, h);

      if (s.equals(zeroSt)) {
        continue;
      } //do not add the feature
      if (maxentTagger.alltags) {
        int numTags = maxentTagger.tags.getSize();
        for (int j = 0; j < numTags; j++) {

          String tag1 = maxentTagger.tags.getTag(j);

          FeatureKey key = new FeatureKey(i, s, tag1);

          if (!maxentTagger.extractorsRare.get(i - start).precondition(tag1)) {
            continue;
          }

          add(key);
        }
      } else {
        //only this tag
        FeatureKey key = new FeatureKey(i, s, tag);

        if (!maxentTagger.extractorsRare.get(i - start).precondition(tag)) {
          continue;
        }

        add(key);
      }
    }
  }

  HistoryTable getHistoryTable() {
    return tHistories;
  }


  /*
  public String getY(int index) {
    return maxentTagger.tags.getTag(vArray[index][1]);
  }
  */

  /*
  public static void main(String[] args) {
    int[] hPos = {0, 1, 2, -1, -2};
    boolean[] isTag = {false, false, false, true, true};
    maxentTagger.init();
    TaggerExperiments gophers = new TaggerExperiments("trainhuge.txt", null);
    //gophers.ptilde();
  }
  */

}
