package old.edu.stanford.nlp.trees;

import java.util.Comparator;

import old.edu.stanford.nlp.ling.HasTag;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import old.edu.stanford.nlp.util.CoreMap;
import old.edu.stanford.nlp.util.Filter;

/** Utilities for Dependency objects.
 *
 *  @author Christopher Manning
 */
public class Dependencies {

  private Dependencies() {} // only static methods


  public static class DependentPuncTagRejectFilter<G extends Label,D extends Label,N> implements Filter<Dependency<G, D, N>> {

    private Filter<String> tagRejectFilter;

    public DependentPuncTagRejectFilter(Filter<String> trf) {
      tagRejectFilter = trf;
    }

    public boolean accept(Dependency<G, D, N> d) {
      if (d == null) {
        return false;
      }
      if ( ! (d.dependent() instanceof HasTag)) {
        return false;
      }
      String tag = ((HasTag) d.dependent()).tag();
      return tagRejectFilter.accept(tag);
    }

    private static final long serialVersionUID = -7732189363171164852L;

  } // end class DependentPuncTagRejectFilter


  public static class DependentPuncWordRejectFilter<G extends Label,D extends Label,N> implements Filter<Dependency<G, D, N>> {

    /**
     *
     */
    private static final long serialVersionUID = 1166489968248785287L;
    private final Filter<String> wordRejectFilter;

    /** @param wrf A filter that rejects punctuation words.
     */
    public DependentPuncWordRejectFilter(Filter<String> wrf) {
      // System.err.println("wrf is " + wrf);
      wordRejectFilter = wrf;
    }

    public boolean accept(Dependency<G, D, N> d) {
      if (d == null) {
        return false;
      }
      if ( ! (d.dependent() instanceof HasWord)) {
        return false;
      }
      String word = ((HasWord) d.dependent()).word();
      // System.err.println("Dep: kid is " + ((MapLabel) d.dependent()).toString("value{map}"));
      return wordRejectFilter.accept(word);
    }

  } // end class DependentPuncWordRejectFilter


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class ComparatorHolder {

    private ComparatorHolder() {}

    private static class DependencyIdxComparator implements Comparator<Dependency> {

      public int compare(Dependency dep1, Dependency dep2) {
        CoreMap dep1lab = (CoreMap) dep1.dependent();
        CoreMap dep2lab = (CoreMap) dep2.dependent();
        int dep1idx = dep1lab.get(IndexAnnotation.class);
        int dep2idx = dep2lab.get(IndexAnnotation.class);
        return dep1idx - dep2idx;
      }

    }

    private static final Comparator<Dependency> dc = new DependencyIdxComparator();

  }

  public static Comparator<Dependency> dependencyIndexComparator() {
    return ComparatorHolder.dc;
  }

}
