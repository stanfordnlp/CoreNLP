/**
 * 
 */
package vn.hus.nlp.tokenizer.tools;

import java.io.File;

import vn.hus.nlp.utils.FileIterator;
import vn.hus.nlp.utils.TextFileFilter;
import vn.hus.nlp.utils.UTF8FileUtility;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <br>
 * Jul 16, 2009, 8:37:30 PM
 * <br>
 * This utility is to used to convert a tokenized corpus to a raw corpus.
 */
public class TokenizedToRawConverter {

	
	private TokenizedToRawConverter() {}
	
	/**
	 * Post process a string.
	 * @param string
	 * @return a processed string
	 */
	private static String postProcess(String string) {
		// remove spaces before punctuations . , ! ? :
		String result = string;
		result = result.replaceAll("\\s+", " ");
		result = result.replaceAll("\\s+\\.", "\\.");
		result = result.replaceAll("\\s+,", ",");
		result = result.replaceAll("\\s+\\!", "\\!");
		result = result.replaceAll("\\s+\\?", "\\?");
		result = result.replaceAll("\\s+:", ":");
		result = result.replaceAll("\\s+\\)", "\\)");
		result = result.replaceAll("\\(\\s+", "\\(");
		result = result.replaceAll("\\s+”", "”");
		result = result.replaceAll("“\\s+", "“");
		
		return result;
	}

	/**
	 * This method removes spaces at the beginning and at the end of quotations 
	 * in a sentence. For example, it converts {" a b c "} to {"a b c"}, or {" a b c " mn " x y} to {"a b c" mn "x y}.
	 * Note that the number of quotation marks in the sentence can be even or odd. 
	 * @param string a sentence
	 * @return standardized sentence
	 */
	private static String postProcessQuotation(String string) {
		StringBuffer result = new StringBuffer(string.length());
		
		String[] substrings = string.split("\"");

		for (int i = 0; i < substrings.length; i++) {
			if (i % 2 == 0) {
				result.append(substrings[i]);
			} else {
				result.append("\"");
				result.append(substrings[i].trim());
				if (i < substrings.length - 1)
					result.append("\"");
			}
		}
		return result.toString();
	}
	
	
	public static void convertFile(String fileInp, String fileOut) {
		String[] taggedSents = UTF8FileUtility.getLines(fileInp);
		UTF8FileUtility.createWriter(fileOut);
		String sent = "";
		for (String taggedSent : taggedSents) {
			// replace _ by spaces
			sent = taggedSent.replaceAll("_", " ");
			sent = postProcessQuotation(postProcess(sent));
			UTF8FileUtility.write(sent + "\n");
		}
		UTF8FileUtility.closeWriter();
	}
	
	
	public static void convertDirectory(String dirInp, String dirOut) {
		TextFileFilter fileFilter = new TextFileFilter();
		File[] taggedFiles = FileIterator.listFiles(new File(dirInp), fileFilter);
		for (File file : taggedFiles) {
			convertFile(file.getAbsolutePath(), dirOut + File.separator + file.getName());
		}
		System.out.println("Converted " + taggedFiles.length + " files.");
	}
	
	public static void main(String[] args) {
		// corpus 1
//		String dirInp = "data/VTB-20090712/VTB-20090712-10K-TOK";
//		String dirOut = "data/VTB-20090712/VTB-20090712-10K-RAW";
		// corpus 2
		String dirInp = "data/VTB-20090712/VTB-20090712-TOK";
		String dirOut = "data/VTB-20090712/VTB-20090712-RAW";
		convertDirectory(dirInp, dirOut);
	}

}
