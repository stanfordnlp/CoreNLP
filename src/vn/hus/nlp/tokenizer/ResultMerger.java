/**
 * 
 */
package vn.hus.nlp.tokenizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author phuonglh
 * <p>
 * This is a post-processor of vnTokeninzer. It corrects the tokenization result
 * by performing some fine-tuning operations, for example, mergence of dates. 
 */
public class ResultMerger {
	
	private static String DAY_STRING_1 = "ngày";
	private static String DAY_STRING_2 = "Ngày";
	
	private static String MONTH_STRING_1 = "tháng";
	private static String MONTH_STRING_2 = "Tháng";
	
	private static String YEAR_STRING_1 = "năm";
	private static String YEAR_STRING_2 = "Năm";

	public ResultMerger() {
		
	}
	
	private TaggedWord mergeDateDay(TaggedWord day, TaggedWord nextToken) {
		TaggedWord taggedWord = null; 
		if (nextToken.isDateDay()) {
			String text = day.getText() + " " + nextToken.getText();
			taggedWord = new TaggedWord(nextToken.getRule(), text, nextToken.getLine(), day.getColumn());
		}
		return taggedWord;
	}
	
	private TaggedWord mergeDateMonth(TaggedWord month, TaggedWord nextToken) {
		TaggedWord taggedWord = null; 
		if (nextToken.isDateMonth()) {
			String text = month.getText() + " " + nextToken.getText();
			taggedWord = new TaggedWord(nextToken.getRule(), text, nextToken.getLine(), month.getColumn());
		}
		return taggedWord;
	}

	private TaggedWord mergeDateYear(TaggedWord year, TaggedWord nextToken) {
		TaggedWord taggedWord = null; 
		// merge the date year or a number
		if (nextToken.isDateYear() || nextToken.isNumber()) {
			String text = year.getText() + " " + nextToken.getText();
			taggedWord = new TaggedWord(nextToken.getRule(), text, nextToken.getLine(), year.getColumn());
		}
		return taggedWord;
	}

	
	/**
	 * @param token
	 * @param nextToken
	 * @return a lexer token merging from two tokens or <tt>null</tt>. 
	 */
	private TaggedWord mergeDate(TaggedWord token, TaggedWord nextToken) {
		if (token.getText().equals(DAY_STRING_1) || token.getText().equals(DAY_STRING_2)) {
			
			return mergeDateDay(token, nextToken);
		}
		if (token.getText().equals(MONTH_STRING_1) || token.getText().equals(MONTH_STRING_2)) {
			return mergeDateMonth(token, nextToken);
		}
		if (token.getText().equals(YEAR_STRING_1) || token.getText().equals(YEAR_STRING_2)) {
			return mergeDateYear(token, nextToken);
		}
		return null;
	}
	
	/**
	 * Merge the result of the tokenization.
	 * @param tokens
	 * @return a list of lexer tokens
	 */
	public List<TaggedWord> mergeList(List<TaggedWord> tokens) {
		List<TaggedWord> result = new ArrayList<TaggedWord>();
		TaggedWord token = new TaggedWord(""); // a fake start token
		Iterator<TaggedWord> it = tokens.iterator();
		while (it.hasNext()) {
			// get a token
			TaggedWord nextToken = it.next();
			// try to merge the two tokens
			TaggedWord mergedToken = mergeDate(token, nextToken);
			// if they are merged
			if (mergedToken != null) {
//				System.out.println(mergedToken.getText()); // DEBUG
				result.remove(result.size()-1);
				result.add(mergedToken);
			} else { // if they aren't merge
				result.add(nextToken);
			}
			token = nextToken;
		}
		return result;
	}
}
