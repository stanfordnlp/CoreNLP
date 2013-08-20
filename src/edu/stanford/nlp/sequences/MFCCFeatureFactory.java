package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.PaddedList;

public class MFCCFeatureFactory<IN extends CoreLabel> extends RVFFeatureFactory<IN> {

  private static final long serialVersionUID = 4927205169837335801L;

  public MFCCFeatureFactory() {}

  @Override
  @SuppressWarnings("unchecked")
  public ClassicCounter<String> getCliqueFeaturesRVF(PaddedList<IN> info, int position, Clique clique) {
    if (clique.equals(cliqueC)) {
      CoreLabel c = info.get(position);
      ClassicCounter<String> features = new ClassicCounter<String>();
      for (int i=0; i< keys.length; i++) {
        Class<? extends CoreAnnotation<Double>> key = (Class<? extends CoreAnnotation<Double>>) keys[i];
        Double dvalue = c.get(key);
        //  System.out.println(key + ": " + value);
        features.incrementCount(key.getSimpleName(), dvalue);
        features.incrementCount(key.getSimpleName()+"_squared", dvalue*dvalue);
      }
      features.incrementCount("one", 1);
      return features;
    } else {
      ClassicCounter features = new ClassicCounter();
      features.incrementCount("one", 1);
      return features;
    }
  }

  private Class<?>[] keys = {
      MFCC1.class,
      MFCC2.class,
      MFCC3.class,
      MFCC4.class,
      MFCC5.class,
      MFCC6.class,
      MFCC7.class,
      MFCC8.class,
      MFCC9.class,
      MFCC10.class,
      MFCC11.class,
      MFCC12.class,
      MFCC13.class,
      MFCC14.class,
      MFCC15.class,
      MFCC16.class,
      MFCC17.class,
      MFCC18.class,
      MFCC19.class,
      MFCC20.class,
      MFCC21.class,
      MFCC22.class,
      MFCC23.class,
      MFCC24.class,
      MFCC25.class,
      MFCC26.class,
      MFCC27.class,
      MFCC28.class,
      MFCC29.class,
      MFCC30.class,
      MFCC31.class,
      MFCC32.class,
      MFCC33.class,
      MFCC34.class,
      MFCC35.class,
      MFCC36.class,
      MFCC37.class,
      MFCC38.class,
      MFCC39.class,
  };

  private static class MFCC1 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC2 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC3 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC4 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC5 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC6 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC7 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC8 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC9 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC10 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC11 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC12 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC13 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC14 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC15 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC16 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC17 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC18 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC19 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC20 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC21 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC22 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC23 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC24 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC25 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC26 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC27 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC28 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC29 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC30 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC31 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC32 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC33 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC34 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC35 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC36 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC37 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC38 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

  private static class MFCC39 implements CoreAnnotation<Double> {
    public Class<Double> getType() {  return Double.class; } }

}
