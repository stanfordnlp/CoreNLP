/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tagger.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import old.edu.stanford.nlp.ling.CategoryWordTag;
import old.edu.stanford.nlp.trees.DiskTreebank;
import old.edu.stanford.nlp.trees.PennTreeReaderFactory;
import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.TreeVisitor;
import old.edu.stanford.nlp.trees.Treebank;
import vn.hus.nlp.tagger.IConstants;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Oct 7, 2009, 3:41:13 PM
 * <p>
 * This utility is used for searching for closed class tags of the Vietnamese.
 */
public class ClosedTagDistribution {
	
	private Map<String, Set<String>> tagToWords = new HashMap<String, Set<String>>();
	
	private static int THRESHOLD = 35;
	
	/**
	 * Collects tags (preterminal nodes)
	 * @param treebankFilename
	 */
	public void collectTags(String treebankFilename) {
		// create the treebank object
		// using the Vietnamese tree reader
//		Treebank treebank = new DiskTreebank(new VietnameseTreeReaderFactory());
		// use the Penn tree reader for collecting punctuations since
		// the Vietnamese tree reader strips them out.
		Treebank treebank = new DiskTreebank(new PennTreeReaderFactory());
		CategoryWordTag.suppressTerminalDetails = true;
		// load the treebank
		treebank.loadPath(treebankFilename);
		// create a height collector
		TagCollector categoryCollector = this.new TagCollector();
		// collect the categories
		treebank.apply(categoryCollector);
	}
	
	public void printTags() {
		StringBuffer buffer = new StringBuffer(1024);
		StringBuffer closedTags = new StringBuffer("Closed tags = ");
		buffer.append("Tag (#words)\n\n");
		for (String tag : tagToWords.keySet()) {
			int size = tagToWords.get(tag).size();
			if (size < THRESHOLD) {
				closedTags.append(tag);
				closedTags.append("  ");
			}
			buffer.append(tag);
			buffer.append(" (");
			buffer.append(size);
//			if (size < 10) {
//				buffer.append(tagToWords.get(tag));
//			}
			buffer.append(")\n");
		}
		System.out.println(buffer.toString());
		System.out.println();
		System.out.println(closedTags.toString());
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ClosedTagDistribution ctd = new ClosedTagDistribution();
		System.out.println("Collecting tags...");
		ctd.collectTags(IConstants.TREEBANK);
		ctd.printTags();
	}
	
	/**
	 * @author LE HONG Phuong, phuonglh@gmail.com
	 * <p>
	 * Oct 7, 2009, 3:50:59 PM
	 * <p>
	 * Collect tags and its associated different words.
	 */
	class TagCollector implements TreeVisitor {
		/* (non-Javadoc)
		 * @see edu.stanford.nlp.trees.TreeVisitor#visitTree(edu.stanford.nlp.trees.Tree)
		 */
		public void visitTree(Tree t) {
			for (Tree node : t) {
				String tag = null;
				if (node.isPreTerminal()) {
					String word = node.children()[0].label().toString();
					tag = basicCategory(node.label().toString());
					if (tagToWords.containsKey(tag)) {
						tagToWords.get(tag).add(word);
					} else {
						tagToWords.put(tag, new HashSet<String>());
					}
				}
			}
		}

		private String basicCategory(String string) {
			int index = string.indexOf('-');
			if (index > 0) {
				return string.substring(0, index);
			}
			return string;
		}
		
	}

}
