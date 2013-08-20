/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.InDataStreamFile;
import edu.stanford.nlp.io.OutDataStreamFile;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.HashIndex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;


/**
 * The function of this class is to read the data from some file in Weka format and to assign unique Ids to
 * the instances and to the outcomes. It must keep hashTables for the nominal attributes to know their int correspondences
 * it knows the number of attributes, the types, etc. It can also be passed as a String a text instances and it will
 * convert it to the form necessary.
 * This is similar to ReadDataWeka except we want to be able to use
 * some shortcuts specifically for the data from the HPSG experiments
 * the features have values on R+ and are numeric rather than nominal
 * for a given data case, only the non-zero values of features can be
 * specified , in the form fNo:val, ..
 * it will actually create features and store them rather than the array of
 * DataDouble
 */
public class ReadDataHPSG {

  private int numAttributes;
  private int numClasses;
  private ArrayList<HashMap<String, Integer>> hashMaps = new ArrayList<HashMap<String, Integer>>();
  ArrayList<String> attrNames = new ArrayList<String>();
  ArrayList<String> attrTypes = new ArrayList<String>();
  String[] classNames;
  String[][] attrNamesArr; //atrrNo attrVal
  int[][] vArray;
  ArrayList<Integer>[] classinstances; //put the instances of class i in classintances[i] as an array of int
  int current = 0;
  Features feats;
  BufferedReader in;
  SparseDataDouble currentData;
  int maxY = 0;
  int currentSentence = -1;
  int numCurrentSentence = 0; // this shows how many y-s from the current sentence we have read already
  int sNoLast = -1;
  int[] maxYs;
  ArrayList<Integer> maxYsList = new ArrayList<Integer>();
  int maxFts = 200; // 200 nodes maximum
  ArrayList<Integer> classesInstances = new ArrayList<Integer>();
  Index<IntPair> instanceIndex;

  int objcount = 0;


  public int numClasses() {
    return numClasses;
  }

  int indexOf(int x, int y) {
    return instanceIndex.indexOf(new IntPair(x, y));

  }

  Index<IntPair> createIndex() {
    Index<IntPair> index = new HashIndex<IntPair>();
    if (maxYs == null) {
      maxYs = new int[maxYsList.size()];
      for (int i = 0; i < maxYsList.size(); i++) {
        maxYs[i] = maxYsList.get(i).intValue();
      }
    }
    for (int x = 0; x < maxYs.length; x++) {
      for (int y = 0; y < maxYs[x]; y++) {
        index.add(new IntPair(x, y));
      }
    }
    return index;
  }

  public Features features() {
    return feats;
  }

  double[][] valuesArr;
  ArrayList<IntDoubleTriple> valuesList = new ArrayList<IntDoubleTriple>(); // each entry is an arraylist of doub$    double[][] valuesArr;
  boolean hasValues = false; // keep track of whether there are values in comm$
  final boolean filter = true; // always filter b/e we want to have two passes
  int cutoff = 0; // do not add features less than or equal to this count
  int[] counts;
  int[] indices; // keep position in feature arrays


  public Experiments domain() {
    return feats.domain();
  }

  static class IntDoubleTriple {
    int index;
    int cNo;
    double value;

    IntDoubleTriple(int index, int cNo, double val) {
      this.index = index;
      this.cNo = cNo;
      value = val;

    }

    public void set(int no, double val) {
      if (no == 2) {
        this.value = val;
      }
    }
  }


  public ReadDataHPSG() {

  }


  /**
   * read in only the header to form the attributies
   */
  public ReadDataHPSG(String wekaFileName, boolean header) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(wekaFileName));
    String s;
    //first read the specifications
    while ((s = in.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      s = s.toLowerCase();
      if (s.startsWith("@relation")) {
        continue;
      }
      if (s.startsWith("@attribute") || s.startsWith("@ATTRIBUTE")) {
        readAttribute(s);
      }
      if (s.startsWith("@data") || s.startsWith("@DATA")) {
        break;
      }
    }//

  }


  public ReadDataHPSG(String wekaFileName, int cutoff) throws Exception {
    BufferedReader in = new BufferedReader(new FileReader(wekaFileName));
    String s;
    this.cutoff = cutoff;
    System.out.println("Feature count cutoff (strictly greater included) is " + cutoff);

    //first read the specifications
    while ((s = in.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      if (s.startsWith("@relation")) {
        continue;
      }
      if (s.startsWith("@attribute") || s.startsWith("@ATTRIBUTE")) {
        readAttribute(s);
      }
      if (s.startsWith("@data") || s.startsWith("@DATA")) {
        break;
      }
    }//

    // Now read the instances

    if (filter) {
      counts = new int[numAttributes + 1];
      indices = new int[numAttributes + 1];
      maxYsList = new ArrayList<Integer>();
      readInstances(in, true);
      System.out.println("max y is " + maxY);
      int numpass = 0;
      for (int j = 0; j < counts.length; j++) {
        System.out.println(j + "\t" + counts[j]);
        if (counts[j] > cutoff) {
          numpass++;
        }
      }

      System.out.println(" passing " + numpass + " out of " + (numAttributes + 1));
      //rewind
      in.close();
      in = new BufferedReader(new FileReader(wekaFileName));

      while ((s = in.readLine()) != null) {
        if (s.startsWith("@data") || s.startsWith("@DATA")) {
          break;
        }
      }//

    }
    readInstances(in, false);
  }

  public void readInstances(BufferedReader in, boolean collectCountsOnly) throws Exception {
    Exception e1 = new Exception("Incorrect data format");
    String s;
    if (numAttributes == 0) {
      throw e1;
    }
    if (numClasses == 0) {
      throw e1;
    }

    if (!collectCountsOnly) {
      feats = new Features();
      Experiments data = new Experiments(vArray, maxY);
      data.ySize = maxY;
      instanceIndex = createIndex();
      Feature emptyFeature = new Feature(data, new int[0], new double[0], instanceIndex);

      for (int i = 0; i < numAttributes + 1; i++) {

        if (counts[i] > cutoff) {

          feats.add(new Feature(data, counts[i], instanceIndex));

        } else {
          feats.add(emptyFeature);
        }

      }
    }

    classinstances = ErasureUtils.mkTArray(ArrayList.class, numClasses);
    for (int i = 0; i < numClasses; i++) {
      classinstances[i] = new ArrayList<Integer>();
    }

    currentSentence = -1;
    numCurrentSentence = 0;
    sNoLast = -1;

    boolean seenThisHundred = false;
    while ((s = in.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      if ((currentSentence % 100) == 0) {
        if (!seenThisHundred) {
          System.out.print("Have read " + currentSentence + " sentences;");
          System.out.println(" object count is " + objcount);
          seenThisHundred = true;
        }
      } else {
        seenThisHundred = false;
      }
      readData(s, collectCountsOnly);
    }
    //now also add the number of trees of the last sentence
    if (collectCountsOnly) {
      maxYsList.add(Integer.valueOf(numCurrentSentence));
    }

    if (collectCountsOnly) {
      int numAllInstances = 0;
      for (int i = 0; i < 1; i++) {
        numAllInstances += classinstances[i].size();
      }

      vArray = new int[numAllInstances][2];
      for (int i = 0; i < numClasses; i++) {

        for (int j = 0; j < classinstances[i].size(); j++) {
          int index = classinstances[i].get(j).intValue();
          vArray[index][0] = index;
          vArray[index][1] = classesInstances.get(index).intValue();// the first tree is the correct one

        }

      }

    }

    if (!collectCountsOnly) {
      setFeatures();
    }

  }


  /**
   * set the preference scores for ranking if any
   */
  public void setFeatures() {
    feats.get(0);
    Experiments data = Feature.domain;
    data.ySize = maxY;
    data.maxY = maxYs;
    System.out.println("added " + feats.size() + " features ");


    System.out.println("current is " + currentSentence);
    valuesArr = new double[currentSentence + 1][];


    for (int i = 0; i < currentSentence + 1; i++) {
      valuesArr[i] = new double[maxYs[i]];
      System.out.println(" the " + i + " th has " + maxYs[i] + " entries ");
    }


    for (int index = 0; index < valuesList.size(); index++) {

      IntDoubleTriple cstr = valuesList.get(index);
      valuesArr[cstr.index][cstr.cNo] = cstr.value;

    }

    if (valuesList.size() > 0) {
      hasValues = true;
    }
    if (hasValues) {
      data.values = valuesArr;
    }
    valuesList = null;

  }

  public ReadDataHPSG(String wekaFileName, ReadDataHPSG train) throws Exception {
    Exception e1 = new Exception("Incorrect data format");
    in = new BufferedReader(new FileReader(wekaFileName));
    String s;
    //skip the specifications

    while ((s = in.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      if (s.startsWith("@relation")) {
        continue;
      }
      if (s.startsWith("@attribute") || s.startsWith("@ATTRIBUTE")) {
        continue;
      }
      if (s.startsWith("@data") || s.startsWith("@DATA")) {
        break;
      }
    }//

    this.numAttributes = train.numAttributes;
    this.numClasses = train.numClasses;
    this.hashMaps = train.hashMaps;
    this.attrNames = train.attrNames;
    this.attrTypes = train.attrTypes;

    // Now read the instances
    if (numAttributes == 0) {
      throw e1;
    }
    if (numClasses == 0) {
      throw e1;
    }
    advanceData();
  }


  public boolean hasMoreData() {
    if (currentData == null) {
      return false;
    }
    //read in the next thing
    return true;


  }

  /**
   * read in the next data in currentData
   */
  public void advanceData() {
    try {
      String s = in.readLine();
      if (s == null) {
        currentData = null;
        return;
      }
      //double[] x=new double[numAttributes];
      int y = 0;
      currentData = new SparseDataDouble();

      double rank = 0;

      StringTokenizer st = new StringTokenizer(s, " \t,:");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int fNo = Integer.parseInt(token);
        if (st.hasMoreTokens()) { // the usual case fno:val
          String token1 = st.nextToken();
          if (token1.equals("%")) {
            rank = Double.parseDouble(st.nextToken());
            y = fNo;
            currentData.setYNo(y);
            break;

          }
          double val = Double.parseDouble(token1);
          //x[fNo]=val;
          currentData.setX(fNo, new Double(val));

        } else {   //this is the class
          y = fNo;
          currentData.setYNo(y);
        }

      }//while

      //currentData=new SparseDataDouble(x,y);

      currentData.setCost(rank);


    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public DataDouble getNextData() {

    DataDouble ret = currentData;
    advanceData();
    return ret;

  }


  /**
   * reads one data instance - one line of the file
   * adds the instance to the features which are non-zero for it
   * also adds it to the list of instances for its class
   */

  public void readData(String s, boolean collectCountsOnly) throws Exception {

    //if we are only collecting counts,add a count to the feature number,
    //else add an actual data point

    // comma separated things also spaces might have
    // nF:val is number of feature, value
    //all are like that except for the class which is the last one and is just class

    //first separate out the comment field if there is any

    double rank = 0;


    StringTokenizer st = new StringTokenizer(s, " \t,:");
    int totalcnt = 0;

    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      int fNo = Integer.parseInt(token);

      if (st.hasMoreTokens()) { // the usual case fno:val or class %
        String token1 = st.nextToken();

        if (token1.equals("%")) {
          //remember the class and get the value and break
          if (fNo == 1) {
            if (collectCountsOnly) {
              classesInstances.add(Integer.valueOf(numCurrentSentence));
            }

          }
          rank = Double.parseDouble(st.nextToken());
          break;
        }

        double val = Double.parseDouble(token1);

        if (fNo == 0) { // this is the sentence number
          int sNoC = (int) val;
          if (sNoC != this.sNoLast) {

            if ((currentSentence >= 0) && (collectCountsOnly)) {
              maxYsList.add(Integer.valueOf(numCurrentSentence));
            }
            currentSentence++;
            numCurrentSentence = 0;
            sNoLast = sNoC;
          }

        }

        if (collectCountsOnly) {
          counts[fNo]++;

        } else {
          if ((counts[fNo] > cutoff)) {
            int index = indexOf(this.currentSentence, this.numCurrentSentence);
            (feats.get(fNo)).setValue(indices[fNo]++, index, val);
            objcount++;
            totalcnt += val;
          }

        }

      } else {
        if (fNo == 1) {
          if (collectCountsOnly) {
            classesInstances.add(Integer.valueOf(numCurrentSentence));
          }

        }

      }

    }

    //add the class
    if (!collectCountsOnly) {
      if (rank != 0) {
        IntDoubleTriple pair1 = new IntDoubleTriple(this.currentSentence, this.numCurrentSentence, rank);
        valuesList.add(pair1);

      } else {
        if (numCurrentSentence == 0) {
          IntDoubleTriple pair2 = new IntDoubleTriple(this.currentSentence, this.numCurrentSentence, 1);
          valuesList.add(pair2);
        }
      }
    }

    if (numCurrentSentence == 0) {
      //features[features.length-1].add(new IntDoubleTriple(currentSentence,0,1));
      classinstances[0].add(Integer.valueOf(currentSentence));

    }


    if (maxY <= numCurrentSentence) {
      maxY = numCurrentSentence + 1;
    }
    numCurrentSentence++;


  }


  public boolean numeric(int aNo) {

    return (this.attrTypes.get(aNo)).equals("numeric");

  }

  public boolean nominal(int aNo) {
    return (this.attrTypes.get(aNo)).equals("nominal");

  }

  private static boolean ignore(String s) {
    if (s.length() == 0) {
      return true;
    }
    if (s.startsWith("%")) {
      return true;
    }
    if (s.startsWith("//")) {
      return true;
    }
    return false;
  }


  public int getNumAttributes() {

    return numAttributes;

  }


  public int getNumValues(int aNo) {
    if (!nominal(aNo)) {
      return -1;
    }
    return hashMaps.get(aNo).size();

  }


  public double getFValue(int aNo, int exampleNo) {
    return 0;//((DataDouble)v.get(exampleNo)).getX(aNo);

  }

  public int getClass(int exampleNo) {

    return 0;//((DataDouble)v.get(exampleNo)).getYNo();

  }


  /**
   * Parse an attribute specification
   */
  public void readAttribute(String s) {
    boolean isClass = false;
    StringTokenizer st = new StringTokenizer(s, " \t,{};");
    String token;
    String nameA = "empty";
    token = st.nextToken();// read out the attribute part
    token = st.nextToken(); // this is the name of the attribute

    if (token.equalsIgnoreCase("class")) {
      isClass = true;
      nameA = token;
    } else {


      //read some more until we get to something that is numeric
      nameA = token;
      token = st.nextToken();

      //System.out.println("token is "+token);

      while (!(token.equals("numeric") || token.equals("class"))) {
        nameA = nameA + " " + token;
        //System.out.println("token "+token+" is not numeric or class");
        token = st.nextToken();
      }

    }
    //System.out.println("attribute name is "+token);
    this.attrNames.add(nameA);
    if (token.equalsIgnoreCase("class")) {
      isClass = true;

      token = st.nextToken(); // this should be either the name |real|numeric or aomething strating with {
    }

    if (token.equalsIgnoreCase("real") || token.equalsIgnoreCase("numeric")) {
      numAttributes++;
      this.hashMaps.add(new HashMap<String, Integer>(1));
      attrTypes.add("numeric");
      return;
    }

    if (token.equalsIgnoreCase("nominal")) {
      this.hashMaps.add(new HashMap<String, Integer>());
      numAttributes++;
      attrTypes.add("nominal");
    } else if (token.equalsIgnoreCase("string")) {
      this.hashMaps.add(new HashMap<String, Integer>());
      numAttributes++;
      attrTypes.add("nominal");
    } else { // it is nominal but follows direct specification
      this.hashMaps.add(new HashMap<String, Integer>());
      numAttributes++;
      attrTypes.add("nominal");
      HashMap<String, Integer> hM = (this.hashMaps.get(numAttributes - 1));
      hM.put(token, Integer.valueOf(hM.size()));
    }


    while (st.hasMoreTokens()) {
      token = st.nextToken();
      HashMap<String, Integer> hM = (this.hashMaps.get(numAttributes - 1));
      hM.put(token, Integer.valueOf(hM.size()));
    }// while

    if (isClass) {
      numClasses = (this.hashMaps.get(numAttributes - 1)).size();
      numAttributes = numAttributes - 1;
    }

  }


  int getYIndex(String nameclass) {

    HashMap<String, Integer> clss = (this.hashMaps.get(numAttributes));
    Integer keyNo = clss.get(nameclass);
    if (keyNo == null) {
      return -1;
    }
    return keyNo.intValue();


  }

  void makeStringsClasses() {
    HashMap<String, Integer> clss = (this.hashMaps.get(numAttributes));
    Object[] keys = clss.keySet().toArray();
    this.classNames = new String[numClasses];
    for (int i = 0; i < keys.length; i++) {
      int id = clss.get(keys[i]).intValue();
      classNames[id] = (String) keys[i];
    }

    this.attrNamesArr = new String[numAttributes][];
    for (int a = 0; a < numAttributes; a++) {
      clss = (this.hashMaps.get(a));
      keys = clss.keySet().toArray();
      this.attrNamesArr[a] = new String[keys.length];
      for (int i = 0; i < keys.length; i++) {
        int id = clss.get(keys[i]).intValue();
        attrNamesArr[a][id] = (String) keys[i];
      }


    }


  }


  public String getYName(int yNo) {
    return classNames[yNo];

  }

  public String getAttributeName(int aNo) {
    return attrNames.get(aNo);

  }

  public String getAttrName(int aNo, int val) {
    if (val == -1) {
      return "-1";
    }
    return attrNamesArr[aNo][val];

  }


  public DataDouble getData(int index) {

    return null;//(DataDouble)v.get(index);
  }

  public int numSamples() {
    return vArray.length;

  }

  public void save(String filename) {
    //save all the necessary stuff to a file, to be able to test later
    try {
      OutDataStreamFile oF = new OutDataStreamFile(filename);
      oF.writeInt(numAttributes);
      oF.writeInt(numClasses);
      for (int j = 0; j < numAttributes + 1; j++) {
        HashMap<String, Integer> hM = (hashMaps.get(j));
        oF.writeInt(hM.size());
        for (String key: hM.keySet()) {
          int val = hM.get(key);
          oF.writeInt(key.length());
          oF.writeBytes(key);
          oF.writeInt(val);
        }//v

      }//j
      for (int j = 0; j < numAttributes + 1; j++) {
        String key = (attrTypes.get(j));
        oF.writeInt(key.length());
        oF.writeBytes(key);
      }//j

      oF.close();
    } catch (Exception e) {
      e.printStackTrace();
    }


  }


  public void read(String filename) {
    try {
      InDataStreamFile inF = new InDataStreamFile(filename);
      numAttributes = inF.readInt();
      numClasses = inF.readInt();
      for (int j = 0; j < numAttributes + 1; j++) {
        HashMap<String, Integer> hM = new HashMap<String, Integer>();
        int len = inF.readInt();
        for (int v = 0; v < len; v++) {
          int buffLen = inF.readInt();
          byte[] buff = new byte[buffLen];
          inF.read(buff);
          String key = new String(buff);
          int val = inF.readInt();
          hM.put(key, Integer.valueOf(val));
        }//v
        hashMaps.add(hM);
      }//j

      for (int j = 0; j < numAttributes + 1; j++) {

        int buffLen = inF.readInt();
        byte[] buff = new byte[buffLen];
        inF.read(buff);
        String key = new String(buff);
        attrTypes.add(key);
      }//j
      inF.close();
    } catch (Exception e) {
      e.printStackTrace();
    }


  }


}
