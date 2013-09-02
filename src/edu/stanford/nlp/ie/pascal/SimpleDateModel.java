/* -*- indent-tabs-mode: nil -*- */
package edu.stanford.nlp.ie.pascal;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;


/**
 * Simple Date Model
 * 
 * @author Chris Cox
 */
public class SimpleDateModel implements RelationalModel {

  public static String INVALID_DATE = "1/1/1000";
  
  public static final double nullMultipliers[] = {.9825,.88,.785,.9525};
  
  private static SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
  
  private static final String nullDateStats = 
    "workshopdate workshoppapersubmissiondate workshopnotificationofacceptancedate workshopcamerareadycopydate\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.0189873417721519\n" +
    "0.0189873417721519\n" +
    "0.00316455696202532\n" +
    "0.00316455696202532\n" +
    "0.00632911392405063\n" +
    "0.0886075949367089\n" +
    "0.0189873417721519\n" +
    "0.0854430379746835\n" +
    "0.734177215189873\n";

  Prior priors;

  public static class InvalidDateException extends java.lang.Exception {

        /**
     * 
     */
    private static final long serialVersionUID = 7336785414776655140L;

        public InvalidDateException(String mesg) {
            super(mesg);
        }
  }
  public SimpleDateModel(String filename) {
    try {
      priors = new Prior(new BufferedReader(new StringReader(nullDateStats)));
    } catch(IOException e) {
      // shouldn't happen
      throw new RuntimeException(e);
    }
  }

  public double computeProb(DateTemplate temp) {
    
    return computeProb(temp.subdate,temp.noadate,temp.crcdate,temp.workdate);
    
  }
  
  public double computeProb(PascalTemplate temp) {
    
    String wsdate = temp.getValue("workshopdate");
    if( wsdate == null ) wsdate = INVALID_DATE;
    String camdate = temp.getValue("workshopcamerareadycopydate");
    if( camdate == null ) camdate = INVALID_DATE;
    String notedate = temp.getValue("workshopnotificationofacceptancedate");
    if( notedate == null ) notedate = INVALID_DATE;
    String subdate = temp.getValue("workshoppapersubmissiondate");
    if( subdate == null ) subdate = INVALID_DATE;
    
    return computeProb(subdate,notedate,camdate,wsdate);
  }
  
  public double computeProb(String subdate, String notedate, String camdate,
                            String wsdate){
    
    String dateFields [] = {subdate,notedate,camdate,wsdate};
    boolean nullFields[] = new boolean[4];
    long dates[] = new long[4];
    double prob = 1.0;

    HashSet presentFields = new HashSet();
    
    // find which fields are present
    for(int i = 0; i < dateFields.length;i++) {
      if(dateFields[i].trim().equals(INVALID_DATE)){
        dateFields[i]=null;
        nullFields[i]=true;
      }else{
        presentFields.add(PascalTemplate.fields[i]);
        nullFields[i]=false;
        try{
          Date dateDate = dateFormat.parse(dateFields[i]);
          Calendar date = Calendar.getInstance();
          date.setTime(dateDate);
          dates[i] = date.getTimeInMillis();
        }catch(ParseException e) {
          prob = 0;
          //System.err.println("PARSE EXCEPTION:");
        }

      } 
    }

    // set the prior based on presence/absence of fields
    prob *= priors.get(presentFields);
    
    for(int i = 0; i < 4; i++) {
      if(!nullFields[i]) {
        for(int j=i+1;j<4;j++) {
          if(!nullFields[j]) {
            //penalize for matching dates
            if(dates[i]==dates[j])
              prob *= .05;
            //penalize for out of order dates 
            //(except cameraready < workshopdate)
            if((dates[i] > dates[j]) && i!=2) 
              prob *= .05;
          }
        }
      }
    }
     
    return prob;
        
  }
}
  
