package edu.stanford.nlp.parser.lexparser;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.util.Generics;

public class Lattice implements Serializable, Iterable<LatticeEdge> {

	private static final long serialVersionUID = 5135076134500512556L;
  
  private final List<ParserConstraint> constraints;
	
	private final List<LatticeEdge> edges;
	private final Set<Integer> nodes;
	private final Map<Integer,List<LatticeEdge>> edgeStartsAt;
	private int maxNode = -1;
	
	public Lattice() {
		edges = new ArrayList<>();
		nodes = Generics.newHashSet();
		constraints = new ArrayList<>();
		edgeStartsAt = Generics.newHashMap();
	}

	//TODO Do node normalization here
	public void addEdge(LatticeEdge e) { 
		nodes.add(e.start);
		nodes.add(e.end);
		edges.add(e); 
		if(e.end > maxNode)
		  maxNode = e.end;
		
		if(edgeStartsAt.get(e.start) == null) {
		  List<LatticeEdge> edges = new ArrayList<>();
		  edges.add(e);
		  edgeStartsAt.put(e.start, edges);
		} else {
		  edgeStartsAt.get(e.start).add(e);
		}
	}
	
	public void addConstraint(ParserConstraint c) { constraints.add(c); }
	
	public int getNumNodes() { return nodes.size(); }

	public List<ParserConstraint> getConstraints() {
	  return Collections.unmodifiableList(constraints);
	}
	
	public int getNumEdges() { return edges.size(); }
	
	public List<LatticeEdge> getEdgesOverSpan(int start, int end) {
	 
	  List<LatticeEdge> allEdges = edgeStartsAt.get(start);
	  List<LatticeEdge> spanningEdges = new ArrayList<>();
	  if(allEdges != null)
	    for(LatticeEdge e : allEdges)
	      if(e.end == end)
	        spanningEdges.add(e);
	  
	  return spanningEdges;
	}
	
	
	@Override
	public String toString() {
	  StringBuilder sb = new StringBuilder();
	  sb.append(String.format("[ Lattice: %d edges  %d nodes ]\n",edges.size(), nodes.size()));
	  for(LatticeEdge e : edges)
	    sb.append("  " + e.toString() + "\n");
		return sb.toString();
	}

	public void setEdge(int id, LatticeEdge e) { edges.set(id, e); }
	
	public Iterator<LatticeEdge> iterator() { return edges.iterator(); }

  public void addBoundary() {
    //Log prob of 0.0 since we have to take this transition
    LatticeEdge boundary = new LatticeEdge(Lexicon.BOUNDARY, 0.0, maxNode, maxNode + 1);
    addEdge(boundary);
  }

}
