/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tagger.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import old.edu.stanford.nlp.ling.CategoryWordTag;
import old.edu.stanford.nlp.trees.DiskTreebank;
import old.edu.stanford.nlp.trees.PennTreeReaderFactory;
import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.TreeVisitor;
import old.edu.stanford.nlp.trees.Treebank;
import old.edu.stanford.nlp.trees.Trees;
import vn.hus.nlp.tagger.IConstants;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Oct 7, 2009, 4:44:01 PM
 * <p>
 * This utility is used for converting parse sentences to tagged sentences.
 */
public class TreeToTaggedSentence {
	
	private List<String> taggedSentences = new ArrayList<String>();
	
	/**
	 * Collects tagged sentences from a treebank.
	 * @param treebankFilename a treebank
	 */
	public void collectSentences(String treebankFilename) {
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
	
	/**
	 * Prints the tagged sentences to a writer.
	 * @param pw a print writer.
	 */
	public void printTaggedSentences(PrintWriter pw) {
		for (String s : taggedSentences) {
			pw.append(s);
			pw.append("\n");
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TreeToTaggedSentence ctd = new TreeToTaggedSentence();
		System.out.println("Collecting tagged sentences...");
		ctd.collectSentences(IConstants.TREEBANK);
		PrintWriter pw;
		try {
			pw = new PrintWriter(new File(IConstants.CORPUS));
			ctd.printTaggedSentences(pw);
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		System.out.println("Done.");
	}
	
	
	/**
	 * @author LE HONG Phuong, phuonglh@gmail.com
	 * <p>
	 * Oct 7, 2009, 4:48:53 PM
	 * <p>
	 * Tagged sentences collector.
	 */
	class TagCollector implements TreeVisitor {
		/* (non-Javadoc)
		 * @see edu.stanford.nlp.trees.TreeVisitor#visitTree(edu.stanford.nlp.trees.Tree)
		 */
		public void visitTree(Tree t) {
			// get a list of tags of t
			List<Tree> tags = Trees.preTerminals(t);
			StringBuffer buffer = new StringBuffer(512);
			for (Tree node : tags) {
				String tag = node.label().toString();
				if (tag.equals("-NONE-")) {
					tag = "";
				}
				tag = basicCategory(tag);
				if (tag.length() > 0) {
					String word = node.children()[0].label().toString();
					// convert slash to back slash 
					if (tag.equals(IConstants.DEFAULT_MODEL_FILE)) {
						tag = "SLASH";
						word = "SLASH";
					}
					buffer.append(word);
					buffer.append(IConstants.DELIM);
					buffer.append(tag);
					buffer.append(" ");
				}
			}
			taggedSentences.add(buffer.toString().trim());
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
