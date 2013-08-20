package edu.stanford.nlp.sequences;

/**
 * Collection of features.  can be boolean or real valued.
 * arrays are NOT copied on input, so dont modify them unless
 * you want them modified.
 *
 * @author Jenny Finkel
 */

public class Features {

  private Features() {}
  
  public int[] features;  
  public float value(int featureIndex) { return 1.0f; }
  public boolean isBoolean() { return true; }
  public void setValues(float[] v) { throw new RuntimeException("Cannot set values for boolean feature set!"); }
  
  public static Features valueOf(int[] features) {
    Features f = new Features();
    f.features = features;
    return f;
  }

  public static Features valueOf(final int[] features, final float[] values) {
    if (features.length != values.length) {
      throw new RuntimeException("Array size mis-match: "+features.length+" "+values.length);
    }

    Features f = new Features () {
        float[] values;
        @Override
        public float value(int featureIndex) { return values[featureIndex]; }
        @Override
        public boolean isBoolean() { return false; }
        @Override
        public void setValues(float[] v) { values = v; }
      };
    f.features = features;
    f.setValues(values);

    return f;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer("{");
    for (int i = 0; i < features.length; i++) {
      sb.append(features[i]).append("=").append(value(i));
      if (i != features.length -1) { sb.append(", "); }      
    }
    sb.append("}");
    return sb.toString();
  }
}
