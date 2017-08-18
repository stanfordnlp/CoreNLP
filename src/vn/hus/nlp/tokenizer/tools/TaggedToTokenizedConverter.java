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
 * Jul 16, 2009, 8:08:12 PM
 * <br>
 * This utility is used to convert a tagged corpus to tokenized corpus.
 */
public class TaggedToTokenizedConverter {
	private static String TAGGED_FILE_EXTENSION = ".pos";
	private static String TOKENIZED_FILE_EXTENSION = ".txt";
	
	private TaggedToTokenizedConverter() {}
	
	/**
	 * Post process a string.
	 * @param string
	 * @return a processed string
	 */
	private static String postProcess(String string) {
		// remove spaces before punctuations . , ! ? :
		String result = string;
		result = result.replaceAll("\\*E\\*", "");
		result = result.replaceAll("\\*T\\*", "");
		result = result.replaceAll("\\*E", "");
		result = result.replaceAll("E\\*", "");
		result = result.replaceAll("\\*T", "");
		result = result.replaceAll("T\\*", "");
		return result;
	}

	public static void convertFile(String fileInp, String fileOut) {
		String[] taggedSents = UTF8FileUtility.getLines(fileInp);
		UTF8FileUtility.createWriter(fileOut);
		for (String taggedSent : taggedSents) {
			StringBuffer buffer = new StringBuffer();
			String[] wts = taggedSent.split("\\s+");
			for (String wt : wts) {
				String[] pairs = wt.split("/");
				if (pairs.length > 0) {
					buffer.append(pairs[0]);
					buffer.append(" ");
				}
			}
			UTF8FileUtility.write(postProcess(buffer.toString().trim()) + "\n");
		}
		UTF8FileUtility.closeWriter();
	}
	
	
	public static void convertDirectory(String dirInp, String dirOut) {
		TextFileFilter fileFilter = new TextFileFilter(TAGGED_FILE_EXTENSION);
		File[] taggedFiles = FileIterator.listFiles(new File(dirInp), fileFilter);
		String filename = "";
		String fileOut = "";
		for (File file : taggedFiles) {
			filename = file.getName();
			int id = filename.indexOf('.');
			fileOut = (id > 0) ? dirOut + File.separator + filename.substring(0, id) + TOKENIZED_FILE_EXTENSION : filename + TOKENIZED_FILE_EXTENSION;
			convertFile(file.getAbsolutePath(), fileOut);
		}
		System.out.println("Converted " + taggedFiles.length + " files.");
	}
	
	public static void main(String[] args) {
		// corpus 1
		String dirInp = "data/VTB-20090712/VTB-20090712-POS";
		String dirOut = "data/VTB-20090712/VTB-20090712-TOK";
		// corpus 2
//		String dirInp = "data/VTB-20090712/VTB-20090712-10K-POS";
//		String dirOut = "data/VTB-20090712/VTB-20090712-10K-TOK";
		convertDirectory(dirInp, dirOut);
	}
}
