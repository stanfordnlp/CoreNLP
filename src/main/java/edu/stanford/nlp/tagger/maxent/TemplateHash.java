/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Stanford University<p>
 */
package edu.stanford.nlp.tagger.maxent; 

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
// import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Map;


public class TemplateHash  {

  // /** A logger for this class */
  // private static final Redwood.RedwoodChannels log = Redwood.channels(TemplateHash.class);

  // the positions of the feature extractors
  private final Map<Pair<Integer,String>,ListInstances> tempHash = Generics.newHashMap();

  private final MaxentTagger maxentTagger;

  public TemplateHash(MaxentTagger maxentTagger) {
    this.maxentTagger = maxentTagger;
  }

  protected void addPositions(int start, int end, FeatureKey fK) {
    Pair<Integer, String> key = new Pair<>(fK.num, fK.val);
    tempHash.get(key).addPositions(start, end);
  }

  protected int[] getPositions(FeatureKey s) {
    Pair<Integer, String> p = new Pair<>(s.num, s.val);
    return tempHash.get(p).getPositions();
  }

  //public void init() {
//    cdm 2008: stringNums isn't used anywhere, so we now don't do any init.
//    int num = maxentTagger.extractors.getSize() + maxentTagger.extractorsRare.getSize();
//    //log.info("A total of "+num+" features in TemplateHash");
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
    int general = maxentTagger.extractors.size();

    if (nFeatFrame < general) {
      wT = new Pair<>(nFeatFrame, maxentTagger.extractors.extract(nFeatFrame, history));
    } else {
      wT = new Pair<>(nFeatFrame, maxentTagger.extractorsRare.extract(nFeatFrame - general, history));
    }

    if (tempHash.containsKey(wT)) {
      ListInstances li = tempHash.get(wT);
      // TODO: can we clean this call up somehow?  perhaps make the
      // TemplateHash aware of the TaggerExperiments if we need to, or vice-versa?
      if (TaggerExperiments.isPopulated(nFeatFrame, li.getNum(), maxentTagger)) {
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
    int general = maxentTagger.extractors.size();

    if (nFeatFrame < general) {
      wT = new Pair<>(nFeatFrame, maxentTagger.extractors.extract(nFeatFrame, history));
    } else {
      wT = new Pair<>(nFeatFrame, maxentTagger.extractorsRare.extract(nFeatFrame - general, history));
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
  public void save(DataOutputStream rf) {
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

  // Read a string representation of a Pair from a DataStream.
  // This might not work correctly unless the pair of objects are of type
  // [@code String}.
  //
  public static Pair<String, String> readStringPair(DataInputStream in) {
    Pair<String, String> p = new Pair<>();
    try {
      p.first = in.readUTF();
      p.second = in.readUTF();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return p;
  }

  */


  /**
   * Hash the instances on the things that the features look at.
   *
   * @author Kristina Toutanova
   * @version 1.0
   */
  static class ListInstances {

    private final ArrayList<Integer> v = new ArrayList<>();
    private int[] positions; // = null;
    private int num; // = 0;

    ListInstances() { }

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
  public void save(DataOutputStream rf) {
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

  public void read(DataInputStream rf) {
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

}
