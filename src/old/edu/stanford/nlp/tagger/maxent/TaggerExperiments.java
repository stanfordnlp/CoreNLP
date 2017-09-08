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

package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.maxent.Experiments;
import old.edu.stanford.nlp.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;
import java.util.HashSet;
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

  private final TaggerFeatures feats = new TaggerFeatures();
  private final Set<FeatureKey> sTemplates = new HashSet<FeatureKey>();
  private final HistoryTable tHistories = new HistoryTable();

  private final int numFeatsGeneral = GlobalHolder.extractors.getSize();
  private final int numFeatsAll = numFeatsGeneral + GlobalHolder.extractorsRare.getSize();


  public TaggerExperiments() {
  }

  protected TaggerExperiments(TaggerConfig config) throws IOException {
    System.err.println("TaggerExperiments: adding word/tags");
    PairsHolder pairs = new PairsHolder();
    ReadDataTagged c = new ReadDataTagged(config, pairs);
    vArray = new int[c.getSize()][2];

    initTemplatesNew();
    System.err.println("Featurizing tagged data tokens...\n");
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
        System.err.printf("%d ",i);
        if (i % 100000 == 0) { System.err.println(); }
      }
    }
    System.err.println();
    System.err.println("Featurized " + c.getSize() + " data tokens [done].");
    c.release();
    ptilde();
    GlobalHolder.xSize = xSize;
    GlobalHolder.ySize = ySize;
    System.err.println("xSize [num Phi templates] = " + xSize + "; ySize [num classes] = " + ySize);

    hashHistories();

    // if we'll look at occurring tags only, we need the histories and pairs still
    if (!GlobalHolder.occuringTagsOnly && !GlobalHolder.possibleTagsOnly) {
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

  /** This method uses and deletes a file tempXXXXXX.x in the current directory! */
  private void getFeaturesNew() {

    try {
      System.out.println("TaggerExperiments.getFeaturesNew: initializing fnumArr.");
      GlobalHolder.fnumArr = new byte[xSize][ySize]; // what is the maximum number of active features
      File hFile = File.createTempFile("temp",".x", new File("./"));
      RandomAccessFile hF = new RandomAccessFile(hFile, "rw");
      System.out.println("  length of sTemplates keys: " + sTemplates.size());
      System.out.println("getFeaturesNew adding features ...");
      int current = 0;
      int numFeats = 0;
      final boolean VERBOSE = false;
      for (FeatureKey fK : sTemplates) {
        int numF = fK.num;
        int[] xValues;
        Pair<Integer, String> wT = new Pair<Integer, String>(numF, fK.val);
        xValues = GlobalHolder.tFeature.getXValues(wT);
        if (xValues == null) {
          System.out.println("  xValues is null: " + fK.toString()); //  + " " + i
          continue;
        }
        int numEvidence = 0;
        int y = GlobalHolder.tags.getIndex(fK.tag);
        for (int xValue : xValues) {

          if (GlobalHolder.occuringTagsOnly) {
            //check whether the current word in x has occurred with y
            String word = ExtractorFrames.cWord.extract(tHistories.getHistory(xValue));
            if (GlobalHolder.dict.getCount(word, fK.tag) == 0) {
              continue;
            }
          }
          if (GlobalHolder.possibleTagsOnly) {
            String word = ExtractorFrames.cWord.extract(tHistories.getHistory(xValue));
            String[] tags = GlobalHolder.dict.getTags(word);
            Set<String> s = new HashSet<String>(Arrays.asList(GlobalHolder.tags.deterministicallyExpandTags(tags, word)));
            if(DEBUG)
              System.err.printf("possible tags for %s: %s\n", word, Arrays.toString(s.toArray()));
            if(!s.contains(fK.tag))
              continue;
          }
          numEvidence += this.px[xValue];
        }

        if (populated(numF, numEvidence)) {
          int[] positions = GlobalHolder.tFeature.getPositions(fK);
          if (GlobalHolder.occuringTagsOnly || GlobalHolder.possibleTagsOnly) { // TODO
            positions = null;
          }

          if (positions == null) {
            // write this in the file and create a TaggerFeature for it
            //int numElem
            int numElements = 0;

            for (int x : xValues) {
              if (GlobalHolder.occuringTagsOnly) {
                //check whether the current word in x has occurred with y
                String word = ExtractorFrames.cWord.extract(tHistories.getHistory(x));
                if (GlobalHolder.dict.getCount(word, fK.tag) == 0) {
                  continue;
                }
              }
              if(GlobalHolder.possibleTagsOnly) {
                String word = ExtractorFrames.cWord.extract(tHistories.getHistory(x));
                String[] tags = GlobalHolder.dict.getTags(word);
                Set<String> s = new HashSet<String>(Arrays.asList(GlobalHolder.tags.deterministicallyExpandTags(tags, word)));
                if(!s.contains(fK.tag))
                  continue;
              }
              numElements++;

              hF.writeInt(x);
              GlobalHolder.fnumArr[x][y]++;
            }
            TaggerFeature tF = new TaggerFeature(current, current + numElements - 1, fK);
            GlobalHolder.tFeature.addPositions(current, current + numElements - 1, fK);
            current = current + numElements;
            feats.add(tF);
            if (VERBOSE) {
              System.out.println("  added feature with key " + fK.toString() + " has support " + numElements);
            }
          } else {

            for(int x : xValues) {
              GlobalHolder.fnumArr[x][y]++;
            }
            // this is the second time to write these values
            TaggerFeature tF = new TaggerFeature(positions[0], positions[1], fK);
            feats.add(tF);
            if (VERBOSE) {
              System.out.println("  added feature with key " + fK.toString() + " has support " + xValues.length);
            }
          }

          GlobalHolder.fAssociations.put(fK, numFeats);
          numFeats++;
        }

      } // foreach FeatureKey fK
      // read out the file and put everything in an array of ints stored in Feats
      GlobalHolder.tFeature.release();
      TaggerFeatures.xIndexed = new int[current];
      hF.seek(0);
      int current1 = 0;
      while (current1 < current) {
        TaggerFeatures.xIndexed[current1] = hF.readInt();
        current1++;
      }
      System.out.println("  total feats: " + sTemplates.size() + ", populated: " + numFeats);
      hF.close();
      hFile.delete();

      // what is the maximum number of active features per pair
      int max = 0;
      int maxGt = 0;
      int numZeros = 0;
      for (int x = 0; x < xSize; x++) {
        int numGt = 0;
        for (int y = 0; y < ySize; y++) {
          if (GlobalHolder.fnumArr[x][y] > 0) {
            numGt++;
            if (max < GlobalHolder.fnumArr[x][y]) {
              max = GlobalHolder.fnumArr[x][y];
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

      System.out.println("  Max features per x,y pair: " + max);
      System.out.println("  Max non-zero y values for an x: " + maxGt);
      System.out.println("  Number of non-zero feature x,y pairs: " +
          (xSize * ySize - numZeros));
      System.out.println("  Number of zero feature x,y pairs: " + numZeros);
      System.out.println("end getFeaturesNew.");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  private void hashHistories() {
    int fAll = GlobalHolder.extractors.getSize() + GlobalHolder.extractorsRare.getSize();
    int fGeneral = GlobalHolder.extractors.getSize();
    System.err.println("Hashing histories ...");
    for (int x = 0; x < xSize; x++) {
      History h = tHistories.getHistory(x);
      if (x > 0 && x % 10000 == 0) {
        System.err.printf("%d ",x);
        if (x % 100000 == 0) { System.err.println(); }
      }
      int fSize = (GlobalHolder.isRare(ExtractorFrames.cWord.extract(h)) ? fAll : fGeneral);
      for (int i = 0; i < fSize; i++) {
        GlobalHolder.tFeature.addPrev(i, h);
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
      int fSize = (GlobalHolder.isRare(ExtractorFrames.cWord.extract(h)) ? fAll : fGeneral);
      for (int i = 0; i < fSize; i++) {
        GlobalHolder.tFeature.add(i, h, x); // write this to check whether to add
      }
    } // for x
    System.err.println();
    System.err.println("Hashed populated histories.");
  }


  protected static boolean populated(int fNo, int size) {

    // Feature number 0 is hard-coded as the current word feature, which has a special threshold
    if (fNo == 0) {
      return (size > GlobalHolder.curWordMinFeatureThresh);
    } else if (fNo < GlobalHolder.extractors.getSize()) {
      return (size > GlobalHolder.minFeatureThresh);
    } else {
      return (size > GlobalHolder.rareWordMinFeatureThresh);
    }
  }


  private void initTemplatesNew() {
    GlobalHolder.dict.setAmbClasses();
  }


  // Add a new feature key in a hashtable of feature templates
  private void addTemplatesNew(History h, String tag) {
    // Feature templates general

    for (int i = 0; i < numFeatsGeneral; i++) {
      String s = GlobalHolder.extractors.extract(i, h);
      if (s.equals(zeroSt)) {
        continue;
      } //do not add the feature
      //iterate over tags in dictionary
      if (GlobalHolder.alltags) {
        int numTags = GlobalHolder.tags.getSize();
        for (int j = 0; j < numTags; j++) {

          String tag1 = GlobalHolder.tags.getTag(j);

          FeatureKey key = new FeatureKey(i, s, tag1);

          if (!GlobalHolder.extractors.get(i).precondition(tag1)) {
            continue;
          }

          add(key);
        }
      } else {
        //only this tag
        FeatureKey key = new FeatureKey(i, s, tag);

        if (!GlobalHolder.extractors.get(i).precondition(tag)) {
          continue;
        }

        add(key);
      }
    }
  }


  private void addRareTemplatesNew(History h, String tag) {
    // Feature templates rare

    if (!(GlobalHolder.isRare(ExtractorFrames.cWord.extract(h)))) {
      return;
    }
    int start = numFeatsGeneral;
    for (int i = start; i < numFeatsAll; i++) {
      String s = GlobalHolder.extractorsRare.extract(i - start, h);

      if (s.equals(zeroSt)) {
        continue;
      } //do not add the feature
      if (GlobalHolder.alltags) {
        int numTags = GlobalHolder.tags.getSize();
        for (int j = 0; j < numTags; j++) {

          String tag1 = GlobalHolder.tags.getTag(j);

          FeatureKey key = new FeatureKey(i, s, tag1);

          if (!GlobalHolder.extractorsRare.get(i - start).precondition(tag1)) {
            continue;
          }

          add(key);
        }
      } else {
        //only this tag
        FeatureKey key = new FeatureKey(i, s, tag);

        if (!GlobalHolder.extractorsRare.get(i - start).precondition(tag)) {
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
    return GlobalHolder.tags.getTag(vArray[index][1]);
  }
  */

  /*
  public static void main(String[] args) {
    int[] hPos = {0, 1, 2, -1, -2};
    boolean[] isTag = {false, false, false, true, true};
    GlobalHolder.init();
    TaggerExperiments gophers = new TaggerExperiments("trainhuge.txt", null);
    //gophers.ptilde();
  }
  */

}
