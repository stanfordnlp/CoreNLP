package edu.stanford.nlp.trees; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Generics;

/** Utilities for Dependency objects.
 *
 *  @author Christopher Manning
 */
public class Dependencies  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Dependencies.class);

  private Dependencies() {} // only static methods


  public static class DependentPuncTagRejectFilter<G extends Label,D extends Label,N> implements Predicate<Dependency<G, D, N>>, Serializable {

    private Predicate<String> tagRejectFilter;

    public DependentPuncTagRejectFilter(Predicate<String> trf) {
      tagRejectFilter = trf;
    }

    @Override
    public boolean test(Dependency<G, D, N> d) {
      /*
      log.info("DRF: Checking " + d + ": hasTag?: " +
                         (d.dependent() instanceof HasTag) + "; value: " +
                         ((d.dependent() instanceof HasTag)? ((HasTag) d.dependent()).tag(): null));
      */
      if (d == null) {
        return false;
      }
      if ( ! (d.dependent() instanceof HasTag)) {
        return false;
      }
      String tag = ((HasTag) d.dependent()).tag();
      return tagRejectFilter.test(tag);
    }

    private static final long serialVersionUID = -7732189363171164852L;

  } // end class DependentPuncTagRejectFilter


  public static class DependentPuncWordRejectFilter<G extends Label,D extends Label,N> implements Predicate<Dependency<G, D, N>>, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1166489968248785287L;
    private final Predicate<String> wordRejectFilter;

    /** @param wrf A filter that rejects punctuation words.
     */
    public DependentPuncWordRejectFilter(Predicate<String> wrf) {
      // log.info("wrf is " + wrf);
      wordRejectFilter = wrf;
    }

    @Override
    public boolean test(Dependency<G, D, N> d) {
      /*
      log.info("DRF: Checking " + d + ": hasWord?: " +
                         (d.dependent() instanceof HasWord) + "; value: " +
                         ((d.dependent() instanceof HasWord)? ((HasWord) d.dependent()).word(): d.dependent().value()));
      */
      if (d == null) {
        return false;
      }
      String word = null;
      if (d.dependent() instanceof HasWord) {
        word = ((HasWord) d.dependent()).word();
      }
      if (word == null) {
        word = d.dependent().value();
      }
      // log.info("Dep: kid is " + ((MapLabel) d.dependent()).toString("value{map}"));
      return wordRejectFilter.test(word);
    }

  } // end class DependentPuncWordRejectFilter


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class ComparatorHolder {

    private ComparatorHolder() {}

    private static class DependencyIdxComparator implements Comparator<Dependency> {

      @Override
      public int compare(Dependency dep1, Dependency dep2) {
        HasIndex dep1lab = (HasIndex) dep1.dependent();
        HasIndex dep2lab = (HasIndex) dep2.dependent();
        int dep1idx = dep1lab.index();
        int dep2idx = dep2lab.index();
        return dep1idx - dep2idx;
      }

    }

    private static final Comparator<Dependency> dc = new DependencyIdxComparator();

  }

  public static Map<IndexedWord,List<TypedDependency>> govToDepMap(List<TypedDependency> deps) {
    Map<IndexedWord,List<TypedDependency>> govToDepMap = Generics.newHashMap();
    for (TypedDependency dep : deps) {
      IndexedWord gov = dep.gov();

      List<TypedDependency> depList = govToDepMap.get(gov);
      if (depList == null) {
        depList = new ArrayList<>();
        govToDepMap.put(gov, depList);
      }
      depList.add(dep);
    }
    return govToDepMap;
  }

  private static Set<List<TypedDependency>> getGovMaxChains(Map<IndexedWord,List<TypedDependency>> govToDepMap, IndexedWord gov, int depth) {
    Set<List<TypedDependency>> depLists = Generics.newHashSet();
    List<TypedDependency> children = govToDepMap.get(gov);

    if (depth > 0 && children != null) {
      for (TypedDependency child : children) {
        IndexedWord childNode = child.dep();
        if (childNode == null) continue;
        Set<List<TypedDependency>> childDepLists = getGovMaxChains(govToDepMap, childNode, depth-1);
        if (childDepLists.size() != 0) {
          for (List<TypedDependency> childDepList : childDepLists) {
            List<TypedDependency> depList = new ArrayList<>(childDepList.size() + 1);
            depList.add(child);
            depList.addAll(childDepList);
            depLists.add(depList);
          }
        } else {
          depLists.add(Arrays.asList(child));
        }
      }
    }
    return depLists;
  }

  public static Counter<List<TypedDependency>> getTypedDependencyChains(List<TypedDependency> deps, int maxLength) {
    Map<IndexedWord,List<TypedDependency>> govToDepMap = govToDepMap(deps);
    Counter<List<TypedDependency>> tdc = new ClassicCounter<>();
    for (IndexedWord gov : govToDepMap.keySet()) {
      Set<List<TypedDependency>> maxChains = getGovMaxChains(govToDepMap, gov, maxLength);
      for (List<TypedDependency> maxChain : maxChains) {
         for (int i = 1; i <= maxChain.size(); i++) {
           List<TypedDependency> chain = maxChain.subList(0, i);
           tdc.incrementCount(chain);
         }
      }
    }
    return tdc;
  }

  /** A Comparator for Dependencies based on their dependent annotation.
   *  It will only work if the Labels at the ends of Dependencies have
   *  an index().
   *
   *  @return A Comparator for Dependencies
   */
  public static Comparator<Dependency> dependencyIndexComparator() {
    return ComparatorHolder.dc;
  }

}
