package edu.stanford.nlp.parser.lexparser;

import java.util.*;
import java.io.Serializable;

import edu.stanford.nlp.util.Generics;

public class LatticeEdge implements Serializable {

	public final String word;
	public String label = null;
	public double weight;
	public final int start;
	public final int end;
	
	public final Map<String,String> attrs;

	public LatticeEdge(String word, double weight, int start, int end) {
		this.word = word;
		this.weight = weight;
		this.start = start;
		this.end = end;
		
		attrs = Generics.newHashMap();
	}

	public void setAttr(String key, String value) { attrs.put(key, value); }
	
	public String getAttr(String key) { return attrs.get(key); }
	
	public void setLabel(String l) { label = l; }
	
	public void setWeight(double w) { weight = w; }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[ " + word);
		sb.append(String.format(" start(%d) end(%d) wt(%f) ]", start,end,weight));
		if(label != null)
			sb.append(" / " + label);
		return sb.toString();
	}

	private static final long serialVersionUID = 4416189959485854286L;
}
