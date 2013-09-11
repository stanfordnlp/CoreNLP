package edu.stanford.nlp.ie.pascal;



/**
 * PascalDate
 * ----------
 * Designed to hold lots of features for some Date field in a Pascal document.
 *
 * @author Chris Cox
 */
public class PascalDate {

    public String date = null;

    public int orderOnPageIndex = -1; // which instance of ANY date is this in the document.

    public int occurrenceIndex = -1; // which instance of THIS date is this in the document.

    public int orderOfFirst = -1; // what was the orderOnPageIndex of the
        // first instance of this date in the document

    public int tokenIndex = -1; // how far into the document is this Date, in tokens.

    public int temporalOrderIndex = -1; // the index of this date on the page, temporally.

    public double numOccurrences = -1; //number of times this date appeared on the page.
    
    public String documentName = null;

    public String posTag = null;

    public String pascalTag = null;

    public boolean isRange = false;

    public String[] prevTokens; /* In order of appearance, i.e. prevTokens[prevTokens.size] is the token
                                 * adjacent to the Date.  Size of this array is specified in the
                                 * numTokensToCache field of DateGrabber.
                                 */
    public PascalDate(){
     //   System.out.println("Pascal Date Created.");
    }

    public void print(){
        System.out.println("Document:\t" + documentName);
        System.err.println("Date:\t\t"+date);
        System.out.println("isRange:\t"+isRange);
        System.out.println("PrevTokens:\t"+ prevTokens[0] +" "+ prevTokens[1] +" "+ prevTokens[2]);
        System.err.println("pascalTag:\t"+pascalTag);
        System.out.println("orderOnPage / occurrenceNum / orderOfFirst / " +
            "tokenNum / temporalOrder :\t" + orderOnPageIndex +" / "+occurrenceIndex + " / " +
            orderOfFirst +" / "+tokenIndex + " / " +temporalOrderIndex +"\n\n");
	System.err.println("NumOccurrences:\t" + numOccurrences);
    }


}
