package edu.stanford.nlp.ie.pascal;

import java.util.Calendar;
import java.io.IOException;
import java.io.BufferedWriter;

/**
 * @author Chris Cox 
 * @author Jamie Nicolson
 */


public class PascalDocument {
    private DateInstance workshopDate = new DateInstance();
    private DateInstance workshopPaperSubmissionDate = new DateInstance();
    private DateInstance workshopNotificationOfAcceptanceDate= new DateInstance();
    private DateInstance workshopCameraReadyCopyDate = new DateInstance();

    public PascalDocument() {
	//        System.err.println("Pascal Document created");
    }

    public Calendar getWorkshopDate() { return workshopDate.getStartDate();}
    public Calendar getWorkshopPaperSubmissionDate() { return workshopPaperSubmissionDate.getStartDate(); }
    public Calendar getWorkshopNotificationOfAcceptanceDate() {return workshopNotificationOfAcceptanceDate.getStartDate();}
    public Calendar getWorkshopCameraReadyCopyDate() {return workshopCameraReadyCopyDate.getStartDate();}


    public void addDate(String dateType, String value) {
        //System.out.println("adding date : " + dateType + " " + value);

        if(dateType.equalsIgnoreCase("workshopdate"))
            workshopDate.add(value);
        else if(dateType.equalsIgnoreCase("workshoppapersubmissiondate"))
            workshopPaperSubmissionDate.add(value);
        else if(dateType.equalsIgnoreCase("workshopnotificationofacceptancedate"))
            workshopNotificationOfAcceptanceDate.add(value);
        else if(dateType.equalsIgnoreCase("workshopcamerareadycopydate"))
            workshopCameraReadyCopyDate.add(value);

    }

    public void printDates() {
        System.out.print("workshop date : ");
        workshopDate.printString();
        System.out.print("paper submission date : ");
        workshopPaperSubmissionDate.printString();
        System.out.print("notification of acceptance date : ");
        workshopNotificationOfAcceptanceDate.printString();
        System.out.print("camera ready copy date : ");
        workshopCameraReadyCopyDate.printString();

    }

    public void regularizeDateStrings() {
        int yearHolder;

        workshopDate.extractFields();
  //      if(!workshopDate.isRange()) {
   //       System.out.println("WD:");
    //        workshopDate.print();
     //       try{Thread.currentThread().sleep(4000);}catch(Exception e){}
      // }

        yearHolder = workshopDate.getYear();
        //we default all years to the year of the workshop date.

        workshopPaperSubmissionDate.setYear(yearHolder);
        workshopNotificationOfAcceptanceDate.setYear(yearHolder);
        workshopCameraReadyCopyDate.setYear(yearHolder);

        workshopNotificationOfAcceptanceDate.extractFields();
        workshopPaperSubmissionDate.extractFields();
        workshopCameraReadyCopyDate.extractFields();

      /*  if(workshopPaperSubmissionDate.isRange()) {
	    System.out.println("WPSD:");
	    workshopPaperSubmissionDate.print();
	    try{Thread.currentThread().sleep(4000);}catch(Exception e){}
	}
	if(workshopNotificationOfAcceptanceDate.isRange()) {
	    System.out.println("WNOAD:");
	    workshopNotificationOfAcceptanceDate.print();
	    try{Thread.currentThread().sleep(4000);}catch(Exception e){}
	}
	if(workshopCameraReadyCopyDate.isRange()) {
	    System.out.println("WCRCD:");
	    workshopCameraReadyCopyDate.print();
	    try{Thread.currentThread().sleep(4000);}catch(Exception e){}
	}
	    
	    */


    }

    public void sendDatesToFile(BufferedWriter fw) throws IOException{
        Calendar wd = this.getWorkshopDate();
        Calendar wpsd = this.getWorkshopPaperSubmissionDate();
        Calendar wnoad = this.getWorkshopNotificationOfAcceptanceDate();
        Calendar wcrcd = this.getWorkshopCameraReadyCopyDate();

        fw.write((wpsd.get(Calendar.MONTH)+1) +"/"+ wpsd.get(Calendar.DAY_OF_MONTH) +"/"+ wpsd.get(Calendar.YEAR) + " r:" + workshopPaperSubmissionDate.isRange()+"\t");
        fw.write((wnoad.get(Calendar.MONTH)+1) +"/"+ wnoad.get(Calendar.DAY_OF_MONTH) +"/"+ wnoad.get(Calendar.YEAR) +" r:" + workshopNotificationOfAcceptanceDate.isRange()+"\t");
        fw.write((wcrcd.get(Calendar.MONTH)+1) +"/"+ wcrcd.get(Calendar.DAY_OF_MONTH) +"/"+ wcrcd.get(Calendar.YEAR) +" r:" + workshopCameraReadyCopyDate.isRange() + "\t");
        fw.write((wd.get(Calendar.MONTH)+1) +"/"+ wd.get(Calendar.DAY_OF_MONTH) +"/"+ wd.get(Calendar.YEAR) +" r:" + workshopDate.isRange()+ "\n");


     }



}
