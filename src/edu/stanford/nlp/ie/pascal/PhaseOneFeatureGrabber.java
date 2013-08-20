package edu.stanford.nlp.ie.pascal;

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EntityRuleAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EntityTypeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IsURLAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LastTaggedAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;

import java.io.*;
import java.util.*;

/**
 * Writes key-gate XML documents to one-token-per-line columns.
 * Usage: PhaseOneFeatureGrabber <filename>
 *
 * @author Chris Cox
 * @author Jamie Nicolson
 */

public class PhaseOneFeatureGrabber extends DefaultHandler {

  private static HashMap labelTags;
  private static HashMap entityTags; 
  private PrintWriter output; 
  private List<CoreLabel> outputArray;
  private static final String BACKGROUND = PascalTemplate.BACKGROUND_SYMBOL;
  //GLOBAL MARKERS
  private String pascalTag = BACKGROUND;
  private String lastTagged = BACKGROUND;
  private String wrapper;
  private String entityRule = "null";
  private CoreLabel currentToken;
  private boolean wrapperInsideToken=false;
  private boolean isNewLine=false;
  private boolean inURL=false;
  //USAGE OPTIONS
  private boolean tagURLs=true;  /*whether or not to tag consecutive 
                                   *URL components*/ 
  private boolean DEBUG=false;   
  private String EMPTY_VALUE = "null"; /* written to columns for                                                        * empty attributes.*/
  private boolean COLLAPSE_DATES = true;
  
  /*
   * COLUMN FORMAT:
   * -------------
   * WORD TAG SHAPE ENTITY_TYPE ISURL ENTITY_RULE NORMALIZED_DATE GOLD_ANSWER LAST_TAGGED
   *
   */
   public PhaseOneFeatureGrabber(PrintWriter output){
    this(output,true);
  }
  
  public PhaseOneFeatureGrabber(PrintWriter output,boolean tagURLs) {
        this.output = output;
        this.tagURLs= tagURLs;
        labelTags = new HashMap();
        labelTags.put("workshopname", null);
        labelTags.put("workshopacronym", null);
        labelTags.put("workshopdate", null);
        labelTags.put("workshophomepage", null);
        labelTags.put("workshoplocation", null);
        labelTags.put("workshoppapersubmissiondate", null);
        labelTags.put("workshopnotificationofacceptancedate", null);
        labelTags.put("workshopcamerareadycopydate", null);
        labelTags.put("conferencename", null);
        labelTags.put("conferenceacronym", null);
        labelTags.put("conferencehomepage", null);
        entityTags=new HashMap();
        entityTags.put("Date",null);
        entityTags.put("Location",null);
        entityTags.put("Person",null);
   
   
  }

  public void setOutput(PrintWriter output) {
    this.output = output;
  }

    @Override
    public void startElement(String uri, String localName, String qName,
        Attributes attributes)
    {
      
      if(DEBUG) System.err.println("starting element: " + qName);
      String kind = attributes.getValue("kind");
      if(kind==null)kind=EMPTY_VALUE;
      String string = EMPTY_VALUE;
      String category = EMPTY_VALUE;
      String orth = EMPTY_VALUE;
      //if the element is a space token we change its string to "*NewLine*"
      if(qName.equalsIgnoreCase("spacetoken")){
        inURL=false;
        if(kind.equals("control")) {
          isNewLine=true;
          string = "*NewLine*";
        }
      }
      //if the element is a token we save its attributes to the buffer
      if( (qName.equalsIgnoreCase("token") || isNewLine)) {
        assert(currentToken==null);
        //this line allows newline characters to enter the data
        if(qName.equalsIgnoreCase("token")){
          string = attributes.getValue("string");
          if(string==null)string=EMPTY_VALUE;
          if(tagURLs && 
             (string.equalsIgnoreCase("http") || 
              string.equalsIgnoreCase("www"))){
            inURL=true;
          }
          category = attributes.getValue("category");
          if(category==null)category=EMPTY_VALUE;
          orth = attributes.getValue("orth");
          if(orth==null)orth=EMPTY_VALUE;
        }
        currentToken = new CoreLabel();
        currentToken.set(TextAnnotation.class,string);
        currentToken.set(PartOfSpeechAnnotation.class,category);
        currentToken.set(ShapeAnnotation.class,orth);
        currentToken.set(LastTaggedAnnotation.class,lastTagged);
        if(tagURLs) {
          currentToken.set(IsURLAnnotation.class,(inURL ? "isURL":"isnotURL"));
        }                            
        //if the element is a pascal tag, we fill the tag string.
      } else if(labelTags.containsKey(qName) ) {
        pascalTag = qName;           
        //if the element is a wrapper, we fill the wrapper String.
      } else if(entityTags.containsKey(qName)){
        assert(wrapper.equals(EMPTY_VALUE));
        entityRule = attributes.getValue("rule1");
        if(entityRule==null ||
           entityRule.trim().equals("")) entityRule = EMPTY_VALUE;
        //if we're already inside a <token>, it means the <wrapper> is inside.  We want to
        //hold onto this wrapper value, so we set the wrapperInsidetoken value accordingly.
        if(currentToken!=null) {
          if(DEBUG)System.err.println("found " + qName+ " inside buffer");
          wrapperInsideToken=true;
        }
        if(DEBUG)System.err.println("Setting wrapper to:" +qName);
        wrapper=qName;
      }
      
    }
  
  @Override
  public void endElement(String namespaceURI, String localName, String qName){
    
    if( labelTags.containsKey(qName) ) { //Check to see if the label is a pascal tag.
      assert(pascalTag.equals(qName));
      //  lastTagged = qName;
      pascalTag = BACKGROUND;
    } else if(qName.equalsIgnoreCase("token")||isNewLine){
      assert(currentToken!=null);
      currentToken.set(EntityTypeAnnotation.class, wrapper);
      currentToken.set(EntityRuleAnnotation.class, entityRule);
      if(DEBUG)System.err.println("found wrapper : " + wrapper);
      //if the wrapper was inside the token, we need to set it back to its default,
      //because we didn't when we hit the wrapper's end tag.
    
      if(wrapperInsideToken) {
        wrapper=EMPTY_VALUE;
        wrapperInsideToken=false;
        entityRule=EMPTY_VALUE;
      }
      
      currentToken.set(AnswerAnnotation.class,pascalTag);
      if(!pascalTag.equals(BACKGROUND)){ 
        lastTagged = pascalTag;
      }
      outputArray.add(currentToken);
      isNewLine=false;
      currentToken=null;
      
    } else if(entityTags.containsKey(qName)){
      assert(wrapper.equals(qName));
      if(DEBUG)System.err.println("Ending element: " + qName);
      /*if the wrapper is properly placed, we set it back to its default.  
       *otherwise, we hold onto its value to put inside the token.*/
      if(!wrapperInsideToken) {
        if(DEBUG)System.err.println("Resetting wrapper at p2");
        wrapper=EMPTY_VALUE;
        entityRule=EMPTY_VALUE;
      }
    }
  }
  
  @Override
  public void startDocument(){
    System.err.println("Starting new document");
    outputArray=new ArrayList<CoreLabel>();
    wrapper=EMPTY_VALUE;
    entityRule=EMPTY_VALUE;
    lastTagged="0";
  }
  
  @Override
  public void endDocument() {
    markupWordInfosWithNormalizedDateTags(outputArray);
    printWordInfosToOutput(outputArray, output);
    output.println();
    output.flush();
    outputArray=null;
  }
  
  private void printWordInfosToOutput(List<CoreLabel> outputArray, 
                                      PrintWriter output) {
    
    if(DEBUG)System.err.println("printing word infos to output");
    boolean inDate = false;
    String currentAnswer="#$%#$";
    CoreLabel last= null;
    String lastNonBackground = BACKGROUND;
    for(Iterator<CoreLabel> iter = outputArray.iterator(); iter.hasNext();){
      CoreLabel w = iter.next();
      if (COLLAPSE_DATES) {
        if ((w.get(EntityTypeAnnotation.class)).equalsIgnoreCase("Date")) {
          
          if(inDate){
            //if the answer tags split a date up, we label the entire date
            //as the later label.
            String thisTag = w.get(AnswerAnnotation.class);
            if(!thisTag.equals(currentAnswer)){
              currentAnswer = thisTag;
            }
            last = w;
            //do nothing
          }else{
            inDate=true;
            currentAnswer=w.get(AnswerAnnotation.class);
            lastNonBackground=w.get(LastTaggedAnnotation.class);
            last = w;
          }  
        } else {  //if its not a date we proceed normally.
          if(inDate){ //We print the previous date, if we were just in it.
            output.println(last.get(NormalizedNamedEntityTagAnnotation.class)+" "
                           +"collapsed_date null Date null "
                           + last.get(EntityRuleAnnotation.class)+" "+last.get(NormalizedNamedEntityTagAnnotation.class)+" "+ currentAnswer+
                           " "+ lastNonBackground);
          }
          inDate = false; 
          output.println(w.get(TextAnnotation.class) +" "+ w.get(PartOfSpeechAnnotation.class)+" "+w.get(ShapeAnnotation.class)+" "+
                         w.get(EntityTypeAnnotation.class)+" "+w.get(IsURLAnnotation.class)+" "+
                         w.get(EntityRuleAnnotation.class) +" "+ w.get(NormalizedNamedEntityTagAnnotation.class)+" "+w.get(AnswerAnnotation.class) +
                         " "+w.get(LastTaggedAnnotation.class));
        }
      }else{  //if we're not using COLLAPSE_DATES this gets called on all WordInfos.
        output.println(w.get(TextAnnotation.class) +" "+ w.get(PartOfSpeechAnnotation.class)+" "+w.get(ShapeAnnotation.class)+" "+
                       w.get(EntityTypeAnnotation.class)+" "+w.get(IsURLAnnotation.class)+" "+
                       w.get(EntityRuleAnnotation.class)+" "+w.get(NormalizedNamedEntityTagAnnotation.class)+" "+w.get(AnswerAnnotation.class)
                       +" "+ w.get(LastTaggedAnnotation.class));
        
      }
    }
  }
        
  
  private void markupWordInfosWithNormalizedDateTags(List<CoreLabel> l){
    
    if(DEBUG)System.err.println("marking up dates");
    boolean insideDate = false;
    
    for(int i = 0; i < l.size();i++){
      CoreLabel currWord = l.get(i);
      currWord.set(NormalizedNamedEntityTagAnnotation.class, "null");
      String curEntityType = currWord.get(EntityTypeAnnotation.class);
      if(curEntityType != null && curEntityType.equalsIgnoreCase("Date")) {
        insideDate=true;
        DateInstance di = new DateInstance();
        di.add(currWord.get(TextAnnotation.class));
        List<CoreLabel> constituentHolder = new ArrayList<CoreLabel>();
        constituentHolder.add(currWord);
        for(int j = i+1;insideDate;j++){
          CoreLabel nextWord = l.get(j);
          String entityType = nextWord.get(EntityTypeAnnotation.class);
          if(entityType != null && entityType.equalsIgnoreCase("Date")) {
            di.add(nextWord.get(TextAnnotation.class));
            constituentHolder.add(nextWord);
          } else {
            insideDate=false;
            String s = di.getDateString();
            //set all constituents to the right normalized date.
            for(Iterator<CoreLabel> iter = constituentHolder.iterator();iter.hasNext();){
              CoreLabel w = iter.next();
              w.set(NormalizedNamedEntityTagAnnotation.class, s);
            };
            i=j-1;
           
          }
        }
      }
    }
  
  }
  
  public static void main(String[] args) throws Exception {
    SAXParserFactory xmlParserFactory = SAXParserFactory.newInstance();
    SAXParser xmlParser = xmlParserFactory.newSAXParser();
    PhaseOneFeatureGrabber fs = new PhaseOneFeatureGrabber(new PrintWriter(System.out));
    String outPrefix = null;
    int startingIndex = 0;
    if( args[0].equalsIgnoreCase("-outPrefix") ) {
      outPrefix = args[1];
      startingIndex = 2;
    }
    for( int a = startingIndex; a < args.length; ++a) {
      System.err.println("Processing file " + a + ": " + args[a]);
      File infile = new File(args[a]);
      PrintWriter output = null;
      if( outPrefix != null ) {
        output = new PrintWriter(
          new FileWriter(args[a] + "." + outPrefix) );
        fs.setOutput(output);
      }
      try {
        xmlParser.parse(infile,fs);
        System.out.println();
      } catch (Exception e) {
        System.err.println("exception caught in file:" + args[a]);
        throw e;
      }
      if( outPrefix != null ) {
        output.close();
      }
    }
  }
  
}
