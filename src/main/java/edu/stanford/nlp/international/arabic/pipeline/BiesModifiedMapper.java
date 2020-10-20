package edu.stanford.nlp.international.arabic.pipeline;

import java.util.regex.Pattern;

public class BiesModifiedMapper extends LDCPosMapper {

	public BiesModifiedMapper() {
		mapping = Pattern.compile("(\\S+)\t(\\S+)");
	}
}
