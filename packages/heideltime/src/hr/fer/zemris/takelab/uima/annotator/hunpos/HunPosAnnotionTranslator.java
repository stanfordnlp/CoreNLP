package hr.fer.zemris.takelab.uima.annotator.hunpos;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;

public class HunPosAnnotionTranslator {
	
	private List<HunPosAnnotationMapping> mappings;
	
	public HunPosAnnotionTranslator() {
		mappings = new ArrayList<HunPosAnnotationMapping>();
		loadTranslations();
	}
	
	private void loadTranslations() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("croatian/TagTranslation.conf")));
			Pattern reRule = Pattern.compile("^\\s*\"([^\"]+)\"\\s*=\\s*\"([^\"]+)\"\\s*$");
			String line;
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) continue;
				
				Matcher m = reRule.matcher(line);
				if(!m.matches()) {
					Logger.printError("Error matching HunPos annotation translation rule : " + line);
					continue;
				}
				
				try {
					mappings.add(new HunPosAnnotationMapping(m.group(1), m.group(2)));
				} catch (Exception e) {
					Logger.printError("Invalid regex in HunPos annotation matching rule " + m.group(1));
					continue;
				}			
			}
		} catch (FileNotFoundException e) {
			Logger.printError("Cannot find the HunPos annotation translation rules file.");
		} catch (IOException e) {
			Logger.printError("Error reading HunPos annotation translation rules file.");
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				Logger.printError("An error occured while closing the file.");
			}
		}
		
	}
	
	public String translate(String annotation) {		
		for(HunPosAnnotationMapping mapping : this.mappings) {
			if(mapping.match(annotation)) {
				return mapping.getTranslation();
			}
		}
		
		//Welp, we failed, return it unchanged
		return annotation;
	}
}
