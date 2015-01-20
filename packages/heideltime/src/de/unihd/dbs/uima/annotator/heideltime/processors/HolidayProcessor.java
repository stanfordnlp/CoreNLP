package de.unihd.dbs.uima.annotator.heideltime.processors;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Timex3;
/**
 * Addition to HeidelTime to recognize several (mostly, but not
 * entirely christian) holidays.
 * @author Hans-Peter Pfeiffer
 *
 */
public class HolidayProcessor extends GenericProcessor {

	/**
	 * Constructor just calls the parent constructor here.
	 */
	public HolidayProcessor() {
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
		evaluateCalculationFunctions(jcas);
	}
	
	
	/**
	 * This function replaces function calls from the resource files with their TIMEX value.
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param jcas
	 */
	public void evaluateCalculationFunctions(JCas jcas) {

		// build up a list with all found TIMEX expressions
		List<Timex3> linearDates = new ArrayList<Timex3>();
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();

		// Create List of all Timexes of types "date" and "time"
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if ((timex.getTimexType().equals("DATE")) || (timex.getTimexType().equals("TIME"))) {
				linearDates.add(timex);
			}
		}
		
		
		//////////////////////////////////////////////
		// go through list of Date and Time timexes //
		//////////////////////////////////////////////
		//compile regex pattern for validating commands/arguments
		Pattern cmd_p = Pattern.compile("((\\w\\w\\w\\w)-(\\w\\w)-(\\w\\w))\\s+funcDateCalc\\((\\w+)\\((.+)\\)\\)");
		Pattern year_p = Pattern.compile("(\\d\\d\\d\\d)");
		Pattern date_p = Pattern.compile("(\\d\\d\\d\\d)-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])");
		Matcher cmd_m;
		Matcher year_m;
		Matcher date_m;
		String date;
		String year;
		String month;
		String day;
		String function;
		String args[];
		String valueNew;
		
		for (int i = 0; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();
			cmd_m = cmd_p.matcher(value_i);
			valueNew = value_i;
		
			if(cmd_m.matches()) {
				date = cmd_m.group(1);
				year = cmd_m.group(2);
				month = cmd_m.group(3);
				day = cmd_m.group(4);
				function = cmd_m.group(5);
				args = cmd_m.group(6).split("\\s*,\\s*");
				
				//replace keywords in function with actual values
				for(int j=0; j<args.length; j++) {
					args[j] = args[j].replace("DATE", date);
					args[j] = args[j].replace("YEAR", year);
					args[j] = args[j].replace("MONTH", month);
					args[j] = args[j].replace("DAY", day);
				}
				
				if(function.equals("EasterSunday")) {
					year_m = year_p.matcher(args[0]);
					//check if args[0] is a valid YEAR value
					if(year_m.matches()) {
						
						//System.err.println("correct format");
						valueNew = this.getEasterSunday(Integer.valueOf(args[0]), Integer.valueOf(args[1]));
					
					}
					else{
						
						Logger.printError("wrong format");
						valueNew = "XXXX-XX-XX";
					
					}
				}
				else if(function.equals("WeekdayRelativeTo")) {
					date_m = date_p.matcher(args[0]);
					//check if args[0] is a valid DATE value
					if(date_m.matches()) {
						
						//System.err.println("correct format");
						valueNew = this.getWeekdayRelativeTo(args[0], Integer.valueOf(args[1]), Integer.valueOf(args[2]), Boolean.parseBoolean(args[3]));
						
					}
					else{

						Logger.printError("wrong format");
						valueNew = "XXXX-XX-XX";
						
					}
				}
				else if(function.equals("EasterSundayOrthodox")) {
					year_m = year_p.matcher(args[0]);
					//check if args[0] is a valid YEAR value
					if(year_m.matches()) {

						//System.err.println("correct format");
						valueNew = this.getEasterSundayOrthodox(Integer.valueOf(args[0]), Integer.valueOf(args[1]));

					}
					else{

						Logger.printError("wrong format");
						valueNew = "XXXX-XX-XX";

					}
				}
				else if(function.equals("ShroveTideOrthodox")) {
					year_m = year_p.matcher(args[0]);
					//check if args[0] is a valid YEAR value
					if(year_m.matches()) {

						//System.err.println("correct format");
						valueNew = this.getShroveTideWeekOrthodox(Integer.valueOf(args[0]));

					}
					else{

						Logger.printError("wrong format");
						valueNew = "XXXX-XX-XX";

					}
				}
				else{
					// if function call doesn't match any supported function
					Logger.printError("command not found");
					valueNew = "XXXX-XX-XX";
				}
			}
						
			t_i.removeFromIndexes();
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
		}
	}
	
	/**
	 * Get the date of a day relative to Easter Sunday in a given year. Algorithm used is from the "Physikalisch-Technische Bundesanstalt Braunschweig" PTB.
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param year
	 * @param days
	 * @return date
	 */
	public String getEasterSunday(int year, int days) {
		int K = year / 100;
		int M = 15 + ( ( 3 * K + 3 ) / 4 ) - ( ( 8 * K + 13 ) / 25 );
		int S = 2 - ( (3 * K + 3) / 4 );
		int A = year % 19;
		int D = ( 19 * A + M ) % 30;
		int R = ( D / 29) + ( ( D / 28 ) - ( D / 29 ) * ( A / 11 ) );
		int OG = 21 + D - R;
		int SZ = 7 - ( year + ( year / 4 ) + S ) % 7;
		int OE = 7 - ( OG - SZ ) % 7;
		int OS = OG + OE;
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		Calendar c = Calendar.getInstance();
		String date;
		
		if( OS <= 31 ) {
			date = String.format("%04d-03-%02d", year, OS);
		}
		else{
			date = String.format("%04d-04-%02d", year, ( OS - 31 ) );
		}		
		try{
			c.setTime(formatter.parse(date));
			c.add(Calendar.DAY_OF_MONTH, days);
			date = formatter.format(c.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date;
	}
	
	/**
	 * Get the date of Eastersunday in a given year
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param year
	 * @return date
	 */
	public String getEasterSunday(int year) {
		return getEasterSunday(year, 0);
	}
	
    /**
     * Get the date of a day relative to Easter Sunday in a given year. Algorithm used is from the http://en.wikipedia.org/wiki/Computus#cite_note-otheralgs-47.
     *
     * @author Elena Klyachko
     * @param year
     * @param days
     * @return date
     */
    public String getEasterSundayOrthodox(int year, int days) {
        int A = year%4;
        int B = year%7;
        int C = year%19;
        int D = (19*C+15)%30;
        int E = ((2*A + 4*B -D + 34))%7;
        int Month = (int)(Math.floor ((D + E + 114) / 31));
        int Day = ((D + E + 114)% 31) +1;

        /*

        int K = year / 100;
        int M = 15 + ( ( 3 * K + 3 ) / 4 ) - ( ( 8 * K + 13 ) / 25 );
        int S = 2 - ( (3 * K + 3) / 4 );
        int A = year % 19;
        int D = ( 19 * A + M ) % 30;
        int R = ( D / 29) + ( ( D / 28 ) - ( D / 29 ) * ( A / 11 ) );
        int OG = 21 + D - R;
        int SZ = 7 - ( year + ( year / 4 ) + S ) % 7;
        int OE = 7 - ( OG - SZ ) % 7;
        int OS = OG + OE; */

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        String date;


        date = String.format("%04d-%02d-%02d", year, Month, Day );

        try{
            c.setTime(formatter.parse(date));
            c.add(Calendar.DAY_OF_MONTH, days);
            c.add(Calendar.DAY_OF_MONTH, getJulianDifference(year));
            date = formatter.format(c.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }


    /**
     * Get the date of Eastersunday in a given year
     *
     * @author Elena Klyachko
     * @param year
     * @return date
     */
    public String getEasterSundayOrthodox(int year) {
        return getEasterSundayOrthodox(year, 0);
    }
    
    /**
     * Get the date of the Shrove-Tide week in a given year
     *
     * @author Elena Klyachko
     * @param year
     * @return date
     */

    public String getShroveTideWeekOrthodox(int year){
        String easterOrthodox = getEasterSundayOrthodox(year);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        try{
            Calendar calendar = Calendar.getInstance();
            Date date = formatter.parse(easterOrthodox);
            calendar.setTime(date);
            calendar.add(Calendar.DAY_OF_MONTH, -49);
            int shroveTideWeek =  calendar.get(Calendar.WEEK_OF_YEAR);
            if(shroveTideWeek<10){
                return year+"-W0"+shroveTideWeek;
            }
            return year+"-W"+shroveTideWeek;
        }
        catch (ParseException pe){
            Logger.printError("ParseException:"+pe.getMessage());
            return "unknown";
        }
    }
	
	
	
	/**
	 * Get the date of a weekday relative to a date, e.g. first Wednesday before 11-23
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param date
	 * @param weekday
	 * @param number
	 * @param count_itself
	 * @return
	 */
	public String getWeekdayRelativeTo(String date, int weekday, int number, boolean count_itself) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		Calendar c = Calendar.getInstance();
				
		int day;
		int add;
		
		if(number == 0) {
			try{
				c.setTime(formatter.parse(date));
				date = formatter.format(c.getTime());
			} catch (ParseException e) {
				e.printStackTrace();
			}
			return date;			
		}
		else{
			
			if(number<0) {
				number+=1;
			}
						
			try{
				c.setTime(formatter.parse(date));
				day = c.get(Calendar.DAY_OF_WEEK);
				if((count_itself && number>0) || (!count_itself && number <= 0)) {
					if(day<=weekday) {
						add = weekday - day;
					}
					else{
						add = weekday - day + 7;
					}	
				}
				else{
					if(day<weekday) {
						add = weekday - day;
					}
					else{
						add = weekday - day + 7;
					}	
				}
				add += (( number - 1) * 7);
				c.add(Calendar.DAY_OF_MONTH, add);
				date = formatter.format(c.getTime());
			} catch (ParseException e) {
				e.printStackTrace();
			}
			return date;
		}
	}
	
	
	/**
	 * Get the date of a the first, second, third etc. weekday in a month
	 * 
	 * @author Hans-Peter Pfeiffer
	 * @param number
	 * @param weekday
	 * @param month
	 * @param year
	 * @return date
	 */
	public String getWeekdayOfMonth(int number, int weekday, int month, int year) {
		return getWeekdayRelativeTo(String.format("%04d-%02d-01", year, month), weekday, number, true);
	}

    private int getJulianDifference(int year){
        //TODO: this is not entirely correct!
        int century = year/100 + 1;
        if(century<18){
            return 10;
        }
        if(century==18){
            return 11;
        }
        if(century==19){
            return 12;
        }
        if(century==20||century == 21){
            return 13;
        }
        if(century==22){
            return 14;
        }
        return 15;
    }

}
