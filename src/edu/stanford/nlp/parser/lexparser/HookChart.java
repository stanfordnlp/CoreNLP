package edu.stanford.nlp.parser.lexparser;

import java.util.*;

/**
 * A HookChart is a chart data structure designed for use with the efficient
 * O(n^4) chart parsing mechanisms targetted at lexicalized parsing, which
 * were introduced by Eisner and Satta.
 *
 * @author Dan Klein
 */
class HookChart {

  private Map<ChartIndex,List<Hook>> registeredPreHooks = new HashMap<ChartIndex,List<Hook>>();
  private Map<ChartIndex,List<Hook>> registeredPostHooks = new HashMap<ChartIndex,List<Hook>>();
  private Map<ChartIndex,List<Edge>> registeredEdgesByLeftIndex = new HashMap<ChartIndex,List<Edge>>();
  private Map<ChartIndex,List<Edge>> registeredEdgesByRightIndex = new HashMap<ChartIndex,List<Edge>>();

  private Map<WeakChartIndex,List<Edge>> realEdgesByL = new HashMap<WeakChartIndex,List<Edge>>();
  private Map<WeakChartIndex,List<Edge>> realEdgesByR = new HashMap<WeakChartIndex,List<Edge>>();
  private Set<ChartIndex> builtLIndexes = new HashSet<ChartIndex>();
  private Set<ChartIndex> builtRIndexes = new HashSet<ChartIndex>();

  private Interner interner = new Interner();

  private static class ChartIndex {
    public int state;
    public int head;
    public int tag;
    public int loc;  // either the start or end of an edge

    @Override
    public int hashCode() {
      return state ^ (head << 8) ^ (tag << 16) ^ (loc << 24);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof ChartIndex) {
        ChartIndex ci = (ChartIndex) o;
        return state == ci.state && head == ci.head && tag == ci.tag && loc == ci.loc;
      }
      return false;
    }

  } // end class ChartIndex

  private static class WeakChartIndex {
    public int state;
    public int loc;  // either the start or end of an edge

    @Override
    public int hashCode() {
      return state ^ (loc << 16);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof WeakChartIndex) {
        WeakChartIndex ci = (WeakChartIndex) o;
        return state == ci.state && loc == ci.loc;
      }
      return false;
    }
  }


  private static final Collection<Edge> empty = Collections.emptyList();
  private static final Collection<Hook> emptyHooks = Collections.emptyList();

  private ChartIndex tempIndex = new ChartIndex(); // used in many methods to decrease new's
  private WeakChartIndex tempWeakIndex = new WeakChartIndex();  // used to decrease new's


  public void registerEdgeIndexes(Edge edge) {
    tempIndex.state = edge.state;
    tempIndex.head = edge.head;
    tempIndex.tag = edge.tag;
    tempIndex.loc = edge.start;
    ChartIndex index = (ChartIndex) interner.intern(tempIndex);
    builtLIndexes.add(index);
    if (index == tempIndex) {
      tempIndex = new ChartIndex();
      tempIndex.state = edge.state;
      tempIndex.head = edge.head;
      tempIndex.tag = edge.tag;
    }
    //System.out.println("Edge registered: "+edge);
    tempIndex.loc = edge.end;
    index = (ChartIndex) interner.intern(tempIndex);
    if (index == tempIndex) {
      tempIndex = new ChartIndex();
    }
    builtRIndexes.add(index);
  }

  public void registerRealEdge(Edge edge) {
    tempWeakIndex.state = edge.state;
    tempWeakIndex.loc = edge.start;
    WeakChartIndex index = (WeakChartIndex) interner.intern(tempWeakIndex);
    insert(realEdgesByL, index, edge);
    if (index == tempWeakIndex) {
      tempWeakIndex = new WeakChartIndex();
      tempWeakIndex.state = edge.state;
    }
    tempWeakIndex.loc = edge.end;
    index = (WeakChartIndex) interner.intern(tempWeakIndex);
    insert(realEdgesByR, index, edge);
    if (index == tempWeakIndex) {
      tempWeakIndex = new WeakChartIndex();
    }
  }

  public boolean isBuiltL(int state, int start, int head, int tag) {
    tempIndex.state = state;
    tempIndex.head = head;
    tempIndex.tag = tag;
    tempIndex.loc = start;
    return builtLIndexes.contains(tempIndex);
  }

  public boolean isBuiltR(int state, int end, int head, int tag) {
    tempIndex.state = state;
    tempIndex.head = head;
    tempIndex.tag = tag;
    tempIndex.loc = end;
    return builtRIndexes.contains(tempIndex);
  }

  public Collection<Edge> getRealEdgesWithL(int state, int start) {
    tempWeakIndex.state = state;
    tempWeakIndex.loc = start;
    Collection<Edge> edges = realEdgesByL.get(tempWeakIndex);
    if (edges == null) {
      return empty;
    }
    return edges;
  }

  public Collection<Edge> getRealEdgesWithR(int state, int end) {
    tempWeakIndex.state = state;
    tempWeakIndex.loc = end;
    Collection<Edge> edges = realEdgesByR.get(tempWeakIndex);
    if (edges == null) {
      return empty;
    }
    return edges;
  }

  public Collection<Hook> getPreHooks(Edge edge) {
    tempIndex.state = edge.state;
    tempIndex.head = edge.head;
    tempIndex.tag = edge.tag;
    tempIndex.loc = edge.end;
    Collection<Hook> result = registeredPreHooks.get(tempIndex);
    if (result == null) {
      result = emptyHooks;
    }
    //System.out.println("For "+edge+" returning "+result.size()+" pre hooks");
    return result;
  }

  public Collection<Hook> getPostHooks(Edge edge) {
    tempIndex.state = edge.state;
    tempIndex.head = edge.head;
    tempIndex.tag = edge.tag;
    tempIndex.loc = edge.start;
    Collection<Hook> result = registeredPostHooks.get(tempIndex);
    if (result == null) {
      result = emptyHooks;
    }
    //System.out.println("For "+edge+" returning "+result.size()+" post hooks");
    return result;
  }

  public Collection<Edge> getEdges(Hook hook) {
    tempIndex.state = hook.subState;
    tempIndex.head = hook.head;
    tempIndex.tag = hook.tag;
    Collection<Edge> result;
    if (hook.isPreHook()) {
      tempIndex.loc = hook.start;
      result = registeredEdgesByRightIndex.get(tempIndex);
    } else {
      tempIndex.loc = hook.end;
      result = registeredEdgesByLeftIndex.get(tempIndex);
    }
    if (result == null) {
      result = empty;
    }
    //System.out.println("For "+hook+" returning "+result.size()+" edges");
    return result;
  }

  // This hacks up a CollectionValuedMap.  Maybe convert to using that class?
  private static <K,V> void insert(Map<K,List<V>> map, K index, V item) {
    List<V> list = map.get(index);
    if (list == null) {
      // make default size small: many only ever contain 1 or 2 items
      list = new ArrayList<V>(3);
      map.put(index, list);
    }
    list.add(item);
    // System.err.println("#### HookChart list length is " + list.size());
  }


  public void addEdge(Edge edge) {
    tempIndex.state = edge.state;
    tempIndex.head = edge.head;
    tempIndex.tag = edge.tag;
    // left index
    tempIndex.loc = edge.start;
    ChartIndex index = (ChartIndex) interner.intern(tempIndex);
    insert(registeredEdgesByLeftIndex, index, edge);
    if (index == tempIndex) {
      tempIndex = new ChartIndex();
      tempIndex.state = edge.state;
      tempIndex.head = edge.head;
      tempIndex.tag = edge.tag;
    }
    tempIndex.loc = edge.end;
    index = (ChartIndex) interner.intern(tempIndex);
    insert(registeredEdgesByRightIndex, index, edge);
    if (index == tempIndex) {
      tempIndex = new ChartIndex();
    }
  }

  public void addHook(Hook hook) {
    Map<ChartIndex,List<Hook>> map;
    tempIndex.state = hook.subState;
    tempIndex.head = hook.head;
    tempIndex.tag = hook.tag;
    if (hook.isPreHook()) {
      tempIndex.loc = hook.start;
      map = registeredPreHooks;
    } else {
      tempIndex.loc = hook.end;
      map = registeredPostHooks;
    }
    ChartIndex index = (ChartIndex) interner.intern(tempIndex);
    insert(map, index, hook);
    if (index == tempIndex) {
      tempIndex = new ChartIndex();
    }
  }

} // end class HookChart
