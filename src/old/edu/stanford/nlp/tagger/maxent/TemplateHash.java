/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Hash the instances on the things that the features look at.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
class ListInstances {

  private final ArrayList<Integer> v = new ArrayList<Integer>();
  private int[] positions = null;
  private int num = 0;

  ListInstances() {
  }

  protected void add(int x) {
    v.add(x);
  }

  protected void addPositions(int s, int e) {
    positions = new int[2];
    positions[0] = s;
    positions[1] = e;

  }

  public int[] getPositions() {
    return positions;
  }

  protected void inc() {
    num++;
  }


  public int getNum() {
    return num;
  }


  public int[] getInstances() {
    int[] arr = new int[v.size()];
    Integer[] arr1 = new Integer[v.size()];
    v.toArray(arr1);
    for (int i = 0; i < v.size(); i++) {
      arr[i] = arr1[i];
    }
    return arr;
  }

  /*
  Methods unused: commented for now.
  public void save(OutDataStreamFile rf) {
    try {
      rf.writeInt(v.size());
      int[] arr = getInstances();
      for (int i = 0; i < v.size(); i++) {
        rf.writeInt(arr[i]);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void read(InDataStreamFile rf) {
    try {
      int len = rf.readInt();
      for (int i = 0; i < len; i++) {
        int x = rf.readInt();
        add(x);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }// end read
  */

}

public class TemplateHash {

  // the positions of the feature extractors
  private final HashMap<Pair<Integer,String>,ListInstances> tempHash = new HashMap<Pair<Integer,String>,ListInstances>();

  public TemplateHash() {
  }

  protected void addPositions(int start, int end, FeatureKey fK) {
    Pair<Integer, String> key = new Pair<Integer, String>(fK.num, fK.val);
    tempHash.get(key).addPositions(start, end);
  }

  protected int[] getPositions(FeatureKey s) {
    Pair<Integer, String> p = new Pair<Integer, String>(s.num, s.val);
    return tempHash.get(p).getPositions();
  }

  //public void init() {
//    cdm 2008: stringNums isn't used anywhere, so we now don't do any init.
//    int num = GlobalHolder.extractors.getSize() + GlobalHolder.extractorsRare.getSize();
//    //System.err.println("A total of "+num+" features in TemplateHash");
//    stringNums = new String[num];
//    for (int i = 0; i < num; i++) {
//      stringNums[i] = String.valueOf(i);
//    }
  //}

  protected void release() {
    tempHash.clear();
  }

  protected void add(int nFeatFrame, History history, int number) {
    Pair<Integer,String> wT;
    int general = GlobalHolder.extractors.getSize();

    if (nFeatFrame < general) {
      wT = new Pair<Integer,String>(nFeatFrame, GlobalHolder.extractors.extract(nFeatFrame, history));
    } else {
      wT = new Pair<Integer,String>(nFeatFrame, GlobalHolder.extractorsRare.extract(nFeatFrame - general, history));
    }

    if (tempHash.containsKey(wT)) {
      ListInstances li = tempHash.get(wT);
      if (TaggerExperiments.populated(nFeatFrame, li.getNum())) {
        li.add(number);
      }
    } else {
      ListInstances li = new ListInstances();
      li.add(number);
      tempHash.put(wT, li);
    }
  }


  protected void addPrev(int nFeatFrame, History history) {
    Pair<Integer,String> wT;
    int general = GlobalHolder.extractors.getSize();

    if (nFeatFrame < general) {
      wT = new Pair<Integer,String>(nFeatFrame, GlobalHolder.extractors.extract(nFeatFrame, history));
    } else {
      wT = new Pair<Integer,String>(nFeatFrame, GlobalHolder.extractorsRare.extract(nFeatFrame - general, history));
    }
    if (tempHash.containsKey(wT)) {
      (tempHash.get(wT)).inc();
    } else {
      ListInstances li = new ListInstances();
      li.inc();
      tempHash.put(wT, li);
    }
  }


  protected int[] getXValues(Pair<Integer, String> key) {
    if (tempHash.containsKey(key)) {
      return tempHash.get(key).getInstances();
    }
    return null;
  }

  /* Methods unused. Commented for now.
  public void save(OutDataStreamFile rf) {
    try {
      Pair[] keys = new Pair[tempHash.keySet().size()];
      tempHash.keySet().toArray(keys);
      rf.writeInt(keys.length);
      for (Pair key : keys) {
        //rf.writeInt(s.length());
        //rf.write(s.getBytes());
        key.save(rf);
        tempHash.get(key).save(rf);
      } // for

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void read(InDataStreamFile rf) {
    try {
      int numElem = rf.readInt();
      for (int i = 0; i < numElem; i++) {
        //int strLen=rf.readInt();
        //byte[] buff=new byte[strLen];
        //rf.read(buff);
        //String s=new String(buff);
        Pair<String,String> sWT = Pair.readStringPair(rf);
        Pair<Integer,String> wT = new Pair<Integer,String>(Integer.parseInt(sWT.first()), sWT.second());
        ListInstances li = new ListInstances();
        li.read(rf);
        tempHash.put(wT, li);
      }// for
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void print() {
    Object[] arr = tempHash.keySet().toArray();
    for (int i = 0; i < arr.length; i++) {
      System.out.println(arr[i]);
    }
  }

  public static void main(String[] args) {
    TemplateHash hT = new TemplateHash();
    Pair<Integer,String> p = new Pair<Integer,String>(0, "0");
    ListInstances li = new ListInstances();
    li.add(14);
    hT.tempHash.put(p, new ListInstances());
    if (hT.tempHash.containsKey(p)) {
      System.out.println(hT.tempHash.get(p));
    }
  }

  */

}
