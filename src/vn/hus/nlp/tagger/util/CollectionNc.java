/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tagger.util;

import java.util.Set;
import java.util.TreeSet;

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
 * Nov 26, 2009, 5:59:23 PM
 * <p>
 * This utility collects all the words that has Nc tags (classifiers) in the treebank.
 * 
 */
public class CollectionNc {

	private static final String targetTag = "Nc";
	
	private Set<String> set = new TreeSet<String>();
	
	
	/**
	 * Collects all the words which are tagged as Nc.
	 * @param treebankFilename
	 */
	public void collect(String treebankFilename) {
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
		TagCollectorNc categoryCollector = this.new TagCollectorNc();
		// collect the categories
		treebank.apply(categoryCollector);
	}
	
	public void print() {
		StringBuffer buffer = new StringBuffer(1024);
		StringBuffer line = new StringBuffer(1024);
		int i = 0;
		for (String w : set) {
			line.append("\"" +  w + "\", ");
			i++;
			if (i % 12 == 0) {
				buffer.append(line);
				buffer.append("\n");
				line = new StringBuffer(1024);
			}
		}
		System.out.println(buffer.toString());
		System.out.println();
		System.out.println("There are " + set.size() + " words.");
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CollectionNc ctd = new CollectionNc();
		System.out.println("Collecting words...");
		ctd.collect(IConstants.TREEBANK);
		ctd.print();
	}
	
	/**
	 * @author LE HONG Phuong, phuonglh@gmail.com
	 * <p>
	 * Nov 26, 2009, 6:02:59 PM
	 * <p>
	 */
	class TagCollectorNc implements TreeVisitor {
		/* (non-Javadoc)
		 * @see edu.stanford.nlp.trees.TreeVisitor#visitTree(edu.stanford.nlp.trees.Tree)
		 */
		public void visitTree(Tree t) {
			for (Tree node : t) {
				String tag = null;
				if (node.isPreTerminal()) {
					String word = node.children()[0].label().toString();
					tag = basicCategory(node.label().toString());
					if (tag.equals(targetTag) && word.indexOf('_') < 0) {
						set.add(word.toLowerCase());
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
