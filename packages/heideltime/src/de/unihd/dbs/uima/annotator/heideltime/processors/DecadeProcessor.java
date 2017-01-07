package de.unihd.dbs.uima.annotator.heideltime.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.types.heideltime.Timex3;

public class DecadeProcessor extends GenericProcessor {

	/**
	 * Constructor just calls the parent constructor here.
	 */
	public DecadeProcessor() {
		super();
	}
	

	/**
	 * not needed here 
	 */
	public void initialize(UimaContext aContext) {
		return;
	}
	
	/**
	 * all the functionality was put into evaluateCalculationFunctions().
	 */
	public void process(JCas jcas) {
		evaluateFunctions(jcas);
	}
	
	
	/**
	 * This function replaces function calls from the resource files with their TIMEX value.
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param jcas
	 */
	public void evaluateFunctions(JCas jcas) {

		// build up a list with all found TIMEX expressions
		List<Timex3> linearDates = new ArrayList<Timex3>();
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();

		// Create List of all Timexes of types "date" and "time"
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if (timex.getTimexType().equals("DATE")) {
				linearDates.add(timex);
			}
		}
		
		
		//////////////////////////////////////////////
		// go through list of Date and Time timexes //
		//////////////////////////////////////////////
		//compile regex pattern for validating commands/arguments
		Pattern cmd_p = Pattern.compile("(\\w\\w\\w\\w)-(\\w\\w)-(\\w\\w)\\s+decadeCalc\\((\\d+)\\)");

		Matcher cmd_m;
		String year;
		String valueNew;
		String argument;
		
		for (int i = 0; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();
			cmd_m = cmd_p.matcher(value_i);
			valueNew = value_i;
		
			if(cmd_m.matches()) {
				year = cmd_m.group(1);
				argument = cmd_m.group(4);
				
				valueNew = year.substring(0, Math.min(2, year.length())) + argument.substring(0, 1);
			}
						
			t_i.removeFromIndexes();
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
		}
	}
}
