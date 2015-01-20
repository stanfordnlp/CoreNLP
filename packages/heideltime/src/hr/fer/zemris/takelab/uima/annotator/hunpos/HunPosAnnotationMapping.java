package hr.fer.zemris.takelab.uima.annotator.hunpos;

import java.util.regex.Pattern;

public class HunPosAnnotationMapping {

	private Pattern pattern;
	
	private String translation;
	
	public HunPosAnnotationMapping(String pattern, String translation) {
		this.pattern = Pattern.compile(pattern);
		this.translation = translation;
	}
	
	public boolean match(String candidate) {
		return pattern.matcher(candidate).matches();
	}
	
	public String getTranslation() {
		return this.translation;
	}

}
