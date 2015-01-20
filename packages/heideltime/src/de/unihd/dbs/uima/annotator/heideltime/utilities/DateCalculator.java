package de.unihd.dbs.uima.annotator.heideltime.utilities;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.resources.NormalizationManager;
/**
 * 
 * This class contains methods that rely on calendar functions to calculate data.
 * @author jannik stroetgen
 *
 */
public class DateCalculator {
	
	public static String getXNextYear(String date, Integer x){
		
		// two formatters depending if BC or not
		SimpleDateFormat formatter   = new SimpleDateFormat("yyyy");
		SimpleDateFormat formatterBC = new SimpleDateFormat("GGyyyy");
		
		String newDate = "";
		Calendar c = Calendar.getInstance();
		
		try {
			// read the original date
			if (date.matches("^\\d.*")){
				c.setTime(formatter.parse(date));
			}
			else{
				c.setTime(formatterBC.parse(date));
			}
			// make calucaltion
			c.add(Calendar.YEAR, x);
			c.getTime();
			// check if new date is BC or AD for choosing formatter or formatterBC
			int newEra = c.get(Calendar.ERA);
			if (newEra > 0){
				newDate = formatter.format(c.getTime());
			}
			else{
				newDate = formatterBC.format(c.getTime());
			}
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}
	
	public static String getXNextDecade(String date, Integer x) {
		date = date + "0"; // deal with years not with centuries
		
		// two formatters depending if BC or not
		SimpleDateFormat formatter   = new SimpleDateFormat("yyyy");
		SimpleDateFormat formatterBC = new SimpleDateFormat("GGyyyy");
		
		String newDate = "";
		Calendar c = Calendar.getInstance();
		
		try {
			// read the original date
			if (date.matches("^\\d.*")){
				c.setTime(formatter.parse(date));
			}
			else{
				c.setTime(formatterBC.parse(date));
			}
			
			// make calucaltion
			c.add(Calendar.YEAR, x*10);
			c.getTime();
			
			// check if new date is BC or AD for choosing formatter or formatterBC
			int newEra = c.get(Calendar.ERA);
			if (newEra > 0){
				newDate = formatter.format(c.getTime()).substring(0, 3);
			}
			else{
				newDate = formatterBC.format(c.getTime()).substring(0, 5);
			}
			
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}
	
	
	public static String getXNextCentury(String date, Integer x) {
		date = date + "00"; // deal with years not with centuries
		int oldEra = 0;     // 0 if BC date, 1 if AD date
		
		// two formatters depending if BC or not
		SimpleDateFormat formatter   = new SimpleDateFormat("yyyy");
		SimpleDateFormat formatterBC = new SimpleDateFormat("GGyyyy");
		
		String newDate = "";
		Calendar c = Calendar.getInstance();
		
		try {
			// read the original date
			if (date.matches("^\\d.*")){
				c.setTime(formatter.parse(date));
				oldEra = 1;
			}
			else{
				c.setTime(formatterBC.parse(date));
			}
			
			// make calucaltion
			c.add(Calendar.YEAR, x*100);
			c.getTime();
			
			// check if new date is BC or AD for choosing formatter or formatterBC
			int newEra = c.get(Calendar.ERA);
			if (newEra > 0){
				if (oldEra == 0){
					// -100 if from BC to AD
					c.add(Calendar.YEAR, -100);
					c.getTime();
				}
				newDate = formatter.format(c.getTime()).substring(0, 2);
			}
			else{
				if (oldEra > 0){
					// +100 if from AD to BC
					c.add(Calendar.YEAR, 100);
					c.getTime();
				}
				newDate = formatterBC.format(c.getTime()).substring(0, 4);
			}
			
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}
	
	/**
	 * get the x-next day of date.
	 * 
	 * @param date given date to get new date from
	 * @param x type of temporal event to search for
	 * @return
	 */
	public static String getXNextDay(String date, Integer x) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		String newDate = "";
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			c.add(Calendar.DAY_OF_MONTH, x);
			c.getTime();
			newDate = formatter.format(c.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}

	/**
	 * get the x-next month of date
	 * 
	 * @param date current date
	 * @param x amount of months to go forward 
	 * @return new month
	 */
	public static String getXNextMonth(String date, Integer x) {

		// two formatters depending if BC or not
		SimpleDateFormat formatter   = new SimpleDateFormat("yyyy-MM");
		SimpleDateFormat formatterBC = new SimpleDateFormat("GGyyyy-MM");
		String newDate = "";
		Calendar c = Calendar.getInstance();

		try {
			// read the original date
			if (date.matches("^\\d.*")){
				c.setTime(formatter.parse(date));
			}
			else{
				c.setTime(formatterBC.parse(date));
			}
			// make calucaltion
			c.add(Calendar.MONTH, x);
			c.getTime();
			
			// check if new date is BC or AD for choosing formatter or formatterBC
			int newEra = c.get(Calendar.ERA);
			if (newEra > 0){
				newDate = formatter.format(c.getTime());
			}
			else{
				newDate = formatterBC.format(c.getTime());
			}
			
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}
	
	/**
	 * get the x-next week of date
	 * @param date current date
	 * @param x amount of weeks to go forward
	 * @return new week
	 */
	public static String getXNextWeek(String date, Integer x, Language language) {
		NormalizationManager nm = NormalizationManager.getInstance(language);
		String date_no_W = date.replace("W", "");
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-w");
		String newDate = "";
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date_no_W));
			c.add(Calendar.WEEK_OF_YEAR, x);
			c.getTime();
			newDate = formatter.format(c.getTime());
			newDate = newDate.substring(0,4)+"-W"+nm.getFromNormNumber(newDate.substring(5));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}

	/**
	 * Get the weekday of date
	 * 
	 * @param date current date
	 * @return day of week
	 */
	public static int getWeekdayOfDate(String date) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		int weekday = 0;
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			weekday = c.get(Calendar.DAY_OF_WEEK);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return weekday;
	}

	/**
	 * Get the week of date
	 * 
	 * @param date current date
	 * @return week of year
	 */
	public static int getWeekOfDate(String date) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		int week = 0;
		;
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			week = c.get(Calendar.WEEK_OF_YEAR);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return week;
	}
	
	/**
	 * takes a desired locale input string, iterates through available locales, returns a locale object
	 * @param locale String to grab a locale for, i.e. en_US, en_GB, de_DE
	 * @return Locale to represent the input String
	 */
	public static Locale getLocaleFromString(String locale) throws LocaleException {
		for(Locale l : Locale.getAvailableLocales()) {
			if(locale.toLowerCase().equals(l.toString().toLowerCase())) {
				return l;
			}
		}
		throw new LocaleException();
	}
}
