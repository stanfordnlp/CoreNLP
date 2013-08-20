/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.InDataStreamFile;
import edu.stanford.nlp.io.OutDataStreamFile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;


/**
 * A class for reading data files in Weka (ARFF) format.
 * <h4>Data format</h4>
 * Datasets in WEKA have to be formatted according to the arff
 * format. Examples of arff files can be found in $WEKAHOME/data
 * (/u/nlp/src/weka/data).
 * What follows is a short description of the file format.
 * <p/>
 * A dataset has to start with a declaration of its name:
 * <blockquote>
 * &#64;relation name
 * </blockquote>
 * followed by a list of all the attributes in the dataset (including
 * the class attribute). These declarations have the form
 * <blockquote>
 * &#64;attribute attribute_name specification
 * </blockquote>
 * If an attribute is nominal, specification contains a list of the
 * possible attribute values in curly brackets:
 * <blockquote>
 * &#64;attribute nominal_attribute {first_value, second_value, third_value}
 * </blockquote>
 * If an attribute is numeric, specification is replaced by the keyword
 * numeric: (Integer values are treated as real numbers in WEKA.)
 * <blockquote>
 * &#64;attribute numeric_attribute numeric
 * </blockquote>
 * In addition to these two types of attributes, there also exists a
 * string attribute type. This attribute provides the possibility to
 * store a comment or ID field for each of the instances in a dataset:
 * <blockquote>
 * &#64;attribute string_attribute string
 * </blockquote>
 * After the attribute declarations, the actual data is introduced by a
 * <blockquote>
 * &#64;data
 * </blockquote>
 * tag, which is followed by a list of all the instances. The instances
 * are listed in comma-separated format, with a question mark
 * representing a missing value. Comments are lines starting with %.
 * <p/>
 * The function of this class is to read the data from some file in Weka format
 * and to assign unique Ids to the instances and to the outcomes. It must keep
 * hashTables for the nominal attributes to know their int correspondences.
 * It knows the number of attributes, the types, etc.
 * It can also be passed as a String a text instances and it will
 * convert it to the form necessary.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class ReadDataWeka {

  int numAttributes;
  int numClasses;
  private static final boolean binary = false;
  ArrayList<HashMap<String, Integer>> hashMaps = new ArrayList<HashMap<String, Integer>>();
  ArrayList<String> attrNames = new ArrayList<String>();
  ArrayList<String> attrTypes = new ArrayList<String>();
  String[] classNames;
  String[][] attrNamesArr; //atrrNo attrVal
  /**
   * Will contain elements of type data.
   */
  ArrayList<DataDouble> v = new ArrayList<DataDouble>();
  int[][] vArray;


  public ReadDataWeka() {
  }


  public ReadDataWeka(String wekaFileName) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(wekaFileName));
    String s;
    //first read the specifications
    while ((s = br.readLine()) != null) {
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
    }

    // Now read the instances
    if (numAttributes == 0 || numClasses == 0) {
      throw new Exception("Incorrect data format");
    }

    while ((s = br.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      v.add(readData(s));
    }

    vArray = new int[v.size()][2];
    for (int i = 0; i < v.size(); i++) {
      vArray[i][0] = i;
      vArray[i][1] = v.get(i).getYNo();
    }
    br.close();
  }


  public ReadDataWeka(String wekaFileName, ReadDataWeka train) throws Exception {
    Exception e1 = new Exception("Incorrect data format");
    BufferedReader in = new BufferedReader(new FileReader(wekaFileName));
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

    while ((s = in.readLine()) != null) {
      if (ignore(s)) {
        continue;
      }
      v.add(readData(s));
    }

    vArray = new int[v.size()][2];
    for (int i = 0; i < v.size(); i++) {
      vArray[i][0] = i;
      vArray[i][1] = v.get(i).getYNo();
    }


  }


  public DataDouble readData(String s) throws Exception {
    // comma separted things also spaces might have
    double[] x = new double[numAttributes];
    StringTokenizer st = new StringTokenizer(s, " \t,");
    String token;
    for (int a = 0; a < numAttributes; a++) {
      token = st.nextToken();
      if (numeric(a)) {
        x[a] = Double.parseDouble(token);
      } else {
        if (hashMaps.get(a).containsKey(token)) {
          x[a] = (hashMaps.get(a).get(token)).intValue();
        } else {
          HashMap<String, Integer> hM = hashMaps.get(a);
          x[a] = hM.size();
          hM.put(token, Integer.valueOf(hM.size()));
        }
      }
      if (binary) {
        if (x[a] > 0) {
          x[a] = 1.0;
        }
      }
    }//for a
    token = st.nextToken();
    int y = -1;
    if (token != null) {
      Object o = (hashMaps.get(numAttributes).get(token));
      if (o != null) {
        y = ((Integer) o).intValue();
      }
    } else {
      y = -1;
    }
    DataDouble d = new DataDouble(x, y);

    return d;

  }

  public boolean numeric(int aNo) {

    return (this.attrTypes.get(aNo)).equals("numeric");

  }

  public boolean nominal(int aNo) {
    return (this.attrTypes.get(aNo)).equals("nominal");

  }

  public boolean ignore(String s) {
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
    return v.get(exampleNo).getX(aNo);

  }

  public int getClass(int exampleNo) {

    return v.get(exampleNo).getYNo();

  }


  /**
   * Parse an attribute specification
   */
  public void readAttribute(String s) {
    boolean isClass = false;
    StringTokenizer st = new StringTokenizer(s, " \t,{}");
    String token;
    token = st.nextToken();// read out the attribute part
    token = st.nextToken(); // this is the name of the attribute
    this.attrNames.add(token);
    if (token.equalsIgnoreCase("class")) {
      isClass = true;
    }
    token = st.nextToken(); // this should be either the name |real|numeric or aomething strating with {
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

    HashMap<String,Integer> clss = (this.hashMaps.get(numAttributes));
    Integer keyNo = (clss.get(nameclass));
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

  public String getAttrName(int aNo, int val) {
    if (val == -1) {
      return "-1";
    }
    return attrNamesArr[aNo][val];

  }


  public DataDouble getData(int index) {

    return v.get(index);
  }

  public int numSamples() {
    return v.size();

  }

  public void save(String filename) {
    //save all the necessary stuff to a file, to be able to test later
    try {
      OutDataStreamFile oF = new OutDataStreamFile(filename);
      oF.writeInt(numAttributes);
      oF.writeInt(numClasses);
      for (int j = 0; j < numAttributes + 1; j++) {
        HashMap<String,Integer> hM = (hashMaps.get(j));
        oF.writeInt(hM.size());
        Object[] keys = hM.keySet().toArray();
        for (int v = 0; v < keys.length; v++) {
          String key = (String) (keys[v]);
          int val = hM.get(key).intValue();
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
