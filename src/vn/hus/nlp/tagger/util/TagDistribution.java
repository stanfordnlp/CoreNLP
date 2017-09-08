package vn.hus.nlp.tagger.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import old.edu.stanford.nlp.stats.Counter;
import old.edu.stanford.nlp.stats.Counters;
import old.edu.stanford.nlp.stats.IntCounter;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Jan 25, 2010, 1:52:13 PM
 * <p>
 * This utility counts the distribution of tags given in the VCL dictionary.
 */
public class TagDistribution {
	
	private Map<String, Set<String>> map = new TreeMap<String, Set<String>>();
	private final String dictionary = "data/vcl.csv";
	
	public TagDistribution() {
		String[] lines = UTF8FileUtility.getLines(dictionary);
		int position = -1;
		String previousWord = null;
		String word = null;
		String tag = null;
		for (String line : lines) {
			position = line.indexOf(';');
			if (position > 0) {
				if (word != null && !word.equals("NULL")) {
					previousWord = word;
				}
				word = line.substring(0, position).trim();
				tag = line.substring(position+1).trim();
				if (word.equals("NULL")) {
					// update an entry of the map
					map.get(previousWord).add(tag);
				} else {
					// make a new entry for the map
					Set<String> tags = new HashSet<String>();
					tags.add(tag);
					map.put(word, tags);
				}
			}
		}
	}

	public void show() {
		// print out ten random entries of the map
		for (int i = 0; i < 10; i++) {
			int randIndex = (int) (Math.random() * map.size());
			String word = map.keySet().toArray(new String[map.keySet().size()])[randIndex];
			System.out.print(word);
			System.out.println(map.get(word));
		}
	}
	
	public void statistics() {
		System.out.println("Number of distinct entries = " + map.keySet().size());
		/*
		for (String word : map.keySet()) {
			System.out.println(word + ";" + map.get(word));
		}
		*/
		// get the counter of entry and its different pos count
		Counter<String> counter = new IntCounter<String>();
		int[] counts = new int[6]; 
		for (String e : map.keySet()) {
			int s = map.get(e).size();
			counts[s]++;
			counter.incrementCount(e, s);
		}
		System.out.println("Max POS counts = " + Counters.max(counter)  + " at " + Counters.argmax(counter));
		System.out.println("Mean POS counts = " + Counters.mean(counter));
		for (int j = 1; j < counts.length; j++) {
			System.out.println(j + " : " + counts[j]);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TagDistribution td = new TagDistribution();
		td.statistics();
		td.show();
	}

}
