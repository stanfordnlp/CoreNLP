package edu.stanford.nlp.ie.pascal;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.BestCliquesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BestFullAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IsURLAnnotation;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.util.StringUtils;

/**
 * Runs both phases of the task 3 pascal system,
 * usage:
 *
 * @author Jamie Nicolson
 * @author Chris Cox
 */

public class Phase2 {

  private static final int NUM_THREADS = 4;
  
  //private static final boolean USE_CLIQUE_SAMPLING = true;
  
  private static final boolean DEBUG = false;

  private List relationalModels = new ArrayList();

  /**
   * Run the full task 3 system on a file using a serialized {@link AbstractSequenceClassifier}
   * @param args <serializedClassifier> <testFile>
   * @throws Exception
   */

  public static void main(String args[]) throws Exception {
    Phase2 p2 = new Phase2();
    p2.run(args);
  }
  
  private static int DEFAULT_NUM_SAMPLES = 100;
  
  private static class SamplingJob implements Job {
    private String filename;
    private CMMSampler cmmSampler = null;
    private AbstractSequenceClassifier classifier;
    private int numSamples;
    private PrintWriter phase1Out;
    private PrintWriter phase2Out;
    private List<RelationalModel> relationalModels;
    private boolean sampleTemplates = true;

    private SamplingJob(String filename, AbstractSequenceClassifier classifier,
                       int numSamples, List<RelationalModel> relationalModels, 
                       PrintWriter phase1Out, PrintWriter phase2Out)
    {
      this.filename = filename;
      this.classifier = classifier;
      this.numSamples = numSamples;
      this.phase1Out = phase1Out;
      this.phase2Out = phase2Out;
      this.relationalModels = relationalModels;
    }
   
    
    /*
     * Returns the best template (according to the Relational models) by
     * processing each {@link CliqueTemplate} and combining those with the best overall
     * scores. */
    
    private PascalTemplate getBestTemplateFromCliques(CliqueTemplates ct){
      
      //!!! relationalModels must hold dateModel in position 0.
      SimpleDateModel dateModel = (SimpleDateModel)relationalModels.get(0);
      AcronymModel acronymModel = (AcronymModel)relationalModels.get(1);
      Counter dateCliqueProbs = 
        (Distribution.getDistribution(ct.dateCliqueCounter)).getCounter();
      
      //get date clique
      Iterator tempIter = ct.dateCliqueCounter.keySet().iterator();
      while(tempIter.hasNext() ) {
        DateTemplate dt = (DateTemplate)tempIter.next();
        double oldProb = dateCliqueProbs.getCount(dt);
        double newProb = dateModel.computeProb(dt);
        dateCliqueProbs.setCount(dt, oldProb*newProb);
        if(DEBUG){
          System.err.println("Testing date template : " + dt);
          System.err.print("Old prob: " + oldProb);
          System.err.print(" DateModelProb: " + newProb + " NewProb: " + oldProb*newProb+"\n");
        }
      }

      //get info clique
      Counter workshopInfoProbs=
        Distribution.getDistribution(ct.workshopInfoCliqueCounter).getCounter();

      tempIter = ct.workshopInfoCliqueCounter.keySet().iterator();
      
      while(tempIter.hasNext() ) {
        InfoTemplate it = (InfoTemplate)tempIter.next();
        double oldProb = workshopInfoProbs.getCount(it);
        double newProb = acronymModel.computeProb(it);
        workshopInfoProbs.setCount(it, oldProb*newProb);
        if(DEBUG){
          System.err.println("Testing info template : " + it.toString());
          System.err.print("Old prob: " + oldProb);
          System.err.println(" AcroModelProb: "+newProb+" NewProb: " + oldProb*newProb+"\n");
        }

      }

      return PascalTemplate.mergeCliqueTemplates((DateTemplate) Counters.argmax(dateCliqueProbs),
                                                 (String) Counters.argmax(ct.locationCliqueCounter),
                                                 (InfoTemplate) Counters.argmax(workshopInfoProbs));
    }
    /**
     * Returns the highest probability full template from the {@link CMMSampler}
     * according to the Relational Models
     */
    
    private PascalTemplate getBestFullTemplate(CMMSampler cmmSampler) {
      Distribution phase1Distrib = cmmSampler.getDistribution();
      
      // read distribution probs into a counter
      Counter<PascalTemplate> probs = phase1Distrib.getCounter();

      // iterate over relationalModels, multiplying the prob from each
      //  model into the counter
      Iterator<RelationalModel> modelIter = relationalModels.iterator();
      while(modelIter.hasNext()) {
        RelationalModel model = modelIter.next();
        Iterator<PascalTemplate> tempIter = probs.keySet().iterator();
        while( tempIter.hasNext() ) {
          PascalTemplate temp = tempIter.next();
          double oldProb = probs.getCount(temp);
          double newProb = model.computeProb(temp);
          probs.setCount(temp, oldProb * newProb);
        }
      }
      return Counters.argmax(probs);
    }

    public void run() {
      System.out.println("Thread " + Thread.currentThread().getName() 
                         + " starting file " +filename);
      ArrayList<CoreLabel> testList;
      try {
        testList = CMMSampler.readWordInfos(filename);
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
      
      cmmSampler = new CMMSampler(testList, classifier, numSamples, sampleTemplates);
      cmmSampler.sampleDocuments();
      
      Distribution phase1Distrib = cmmSampler.getDistribution();

      OverloadedPascalTemplate goldAnswerTemplate = cmmSampler.getGoldAnswerTemplate();
      PascalTemplate bestRawFieldValuesTemplate = cmmSampler.getBestFieldValuesTemplate();

      //best full template before processing (sampling alone)
      PascalTemplate bestRawSingleTemplate = (PascalTemplate)phase1Distrib.argmax();

      //best template from processing cliques
      CliqueTemplates cliqueTemplates = cmmSampler.getCliqueTemplates();
      PascalTemplate bestProcessCliquesTemplate = getBestTemplateFromCliques(cliqueTemplates);



      System.err.println("Gold answer: \n"+ goldAnswerTemplate.toString());
      System.err.println("Best Process Cliques Template: \n" + bestProcessCliquesTemplate.toString());
      cmmSampler.printBestFieldValues();

      addMarkupToWordInfos(testList, bestRawFieldValuesTemplate, BestFullAnnotation.class,
                           null);
      
      addMarkupToWordInfos(testList, bestProcessCliquesTemplate, BestCliquesAnnotation.class,
                           cmmSampler);
      
      Iterator<CoreLabel> wiIter = testList.iterator();
      // we will use the lock on phase1Out to lock both phase1Out and phase2Out
      synchronized(phase1Out) {
        while(wiIter.hasNext()) {
          CoreLabel wi = wiIter.next();
          phase1Out.println(wi.word() + " " + wi.get(GoldAnswerAnnotation.class) +
                            " " + wi.get(BestFullAnnotation.class));
          phase2Out.println(wi.word() + " " + wi.get(GoldAnswerAnnotation.class) +
                            " " + wi.get(BestCliquesAnnotation.class));
        }
        phase1Out.println();
        phase2Out.println();
      }

      System.out.println("Thread " + Thread.currentThread().getName() + " finishing file " + filename);
    }
  }
  
  public void run(String args[]) throws Exception {
    
    Properties props = StringUtils.argsToProperties(args);
    
    String serializedClassifier = props.getProperty("serializedClassifier");
    String dateModelParamFile = props.getProperty("dateModelParamFile");
    String phase1OutFilename = props.getProperty("phase1OutFilename");
    String phase2OutFilename = props.getProperty("phase2OutFilename");
    String numSamplesProp = props.getProperty("numSamples");
    int numSamples = DEFAULT_NUM_SAMPLES;

    if (numSamplesProp != null) {
      numSamples = Integer.parseInt(numSamplesProp);
    }

    relationalModels.add(new SimpleDateModel(dateModelParamFile));
    relationalModels.add(new AcronymModel());

    AbstractSequenceClassifier classifier;
    try {
      classifier = CMMClassifier.getClassifier(serializedClassifier);
    } catch (java.lang.ClassCastException e) {
      classifier = CRFClassifier.getClassifier(serializedClassifier);
    }

    PrintWriter phase1Out = new PrintWriter(new FileOutputStream(phase1OutFilename));
    PrintWriter phase2Out = new PrintWriter(new FileOutputStream(phase2OutFilename));

    ThreadPool pool = new ThreadPool(NUM_THREADS);

    for (int inputIdx = 2; inputIdx < args.length; ++inputIdx) {
      pool.insertJob(new SamplingJob(args[inputIdx], classifier, numSamples, relationalModels, phase1Out, phase2Out));
    }
    pool.stopWhenEmpty();
    phase1Out.close();
    phase2Out.close();
  }
  
  private static class Field {
    String label;
    String[] tokens;
    int[] matchUpTo;
    boolean isDate;
    DateInstance date;
    boolean isURL;
    
    public Field(String label, String field) {
      this.label = label;
      this.isURL = (label.indexOf("homepage") > -1);
      if( field == null ) {
          field = "";
        }
        
        StringTokenizer tokenizer = new StringTokenizer(field);
        tokens = Collections.list(tokenizer).toArray(new String[0]);
        matchUpTo = new int[tokens.length];
        Arrays.fill(matchUpTo, 0);
        
    }

    public String getLabel() {
      return label;
    }
    public void nextToken(String tok) {
      if (matchUpTo.length == 0)
      // don't do anything if we are an empty field
      {
        return;
      }

      if (tok.equals("*NewLine*")) {
        for (int i = 0; i < matchUpTo.length - 1; ++i) {
          if (matchUpTo[i] > 0) {
            matchUpTo[i]++;
          }
        }
        // a newline at the end doesn't match anymore
        matchUpTo[matchUpTo.length - 1] = 0;
      } else {
        for (int cmpIdx = matchUpTo.length - 1; cmpIdx >= 0; --cmpIdx) {
          // We match up to cmpIdx if we match up to everything previous,
          // and the current token matches.
          matchUpTo[cmpIdx] = 0;
          if (tokens[cmpIdx].equalsIgnoreCase(tok)) {
            if (cmpIdx == 0) {
              matchUpTo[cmpIdx] = 1;
            } else if (matchUpTo[cmpIdx - 1] > 0) {
              matchUpTo[cmpIdx] = matchUpTo[cmpIdx - 1] + 1;
            }
          }
        }
      }
    }
    
    public boolean haveMatch() {
      // we have a match if we match up to the end
      return matchUpTo.length > 0 && matchUpTo[matchUpTo.length-1] > 0;
    }
    
    public int matchingTokens() {
      if( matchUpTo.length == 0 )
          return 0;
      else
        return matchUpTo[matchUpTo.length-1];
    }
  }
  
  public static List generateFieldsToLabel(PascalTemplate template,
                                                boolean useStemming,
                                                CMMSampler cmmSampler) {
    List<Field> fields = new ArrayList<Field>();
    boolean useCMM=false;
    CliqueTemplates ct = null;
    if(cmmSampler !=null){ 
      useCMM = true;
      ct= cmmSampler.getCliqueTemplates();
    }
    /* useStemming:
     *for acronyms, we want a field for every possible acronym
     *that generated the template's stemmed acronym value. 
     *if we're adding Markups from cliqueSampling, we invoke the 
     *inverseAcronymMap.
     */
    
    /*
     * for conference and workshop names, we want to use jenny's 
     * PascalAnswers which stems these to an "abstract" representation.
     * we generate the representation of the best template's values for these
     * two fields, and iterate through the per field 
     */
    
    String wacro;
    String cacro;
    try{
      wacro = template.getValue("workshopacronym");
    } catch (NullPointerException e) { wacro = "!!!!!!!";}
    try{
      cacro = template.getValue("conferenceacronym");
    }catch (NullPointerException e) { cacro = "!!!!!!!";}
    
    //add URLS containg our chosen acronyms to the markup list.
        
    for(int fieldIdx = 0; fieldIdx < PascalTemplate.fields.length; 
        ++fieldIdx){
      String label = PascalTemplate.fields[fieldIdx];
      if(useStemming &&(label.indexOf("acronym")!=-1)) {
        String stemmedAcro=template.getValue(label);
        if(stemmedAcro!=null && !stemmedAcro.equals("null")){
          HashSet acroSet = (HashSet)
            ct.inverseAcronymMap.get(stemmedAcro);
          System.err.println("Rewriting acronym stem:" + stemmedAcro);
          for(Iterator iter = acroSet.iterator();iter.hasNext();){
            fields.add(new Field(label, (String)iter.next()));
          }
        }
      }else {
        if(!label.equals("1/1/1000")){
          fields.add( new Field( label, template.getValue(label) ) );
        }
      }
    }
    if(!useCMM) return fields;
    /*if we're using the cmm sampler, we can use the PascalAnswers workshop name generification
     *tool to iterate through candidate names and add those that match to the Fields to label.
     */


    addSimilarCandidatesToTargetLabels("conferencename", 
                                       cmmSampler.getCandidateFieldValues("conferencename"),
                                       template.getValue("conferencename"),
                                       fields);
    
    addSimilarCandidatesToTargetLabels("workshopname", 
                                       cmmSampler.getCandidateFieldValues("workshopname"),
                                       template.getValue("workshopname"),
                                       fields);
    return fields;
  }
    private static void addSimilarCandidatesToTargetLabels(String fieldName, Set<String> candidateFieldNames,
                                                           String targetFieldValue, List<Field> fields){ 
      System.err.println("Searching for match to:" + targetFieldValue);
      if(targetFieldValue!=null){
        List abstractName = PascalAnswers.process(targetFieldValue);
        for(Iterator<String> iter = candidateFieldNames.iterator(); iter.hasNext();){
          String candidate = iter.next();
          if (abstractName.equals(PascalAnswers.process(candidate))){
            fields.add(new Field(fieldName, candidate));
            System.err.println("Found match: "+ candidate);
          }
        }
      }
    }  
    // if(ct!=null && ct.urls!=null){
    // for(Iterator iter = ct.urls.iterator();iter.hasNext();){
    //   String url = (String)iter.next();
    //   if(url.indexOf(wacro)!=-1){
    //     fields.add(new Field("workshophomepage",url));
    //     System.err.println("added" + url + " to workshop homepage");
    //   }else if(url.indexOf(cacro)!=-1){
    //     fields.add(new Field("conferencehomepage",url));
    //     System.err.println("added" + url + " to workshop homepage");
    //   }
    // }
    //}
    
  public static void addMarkupToWordInfos(List wordInfos, 
                                          PascalTemplate template,
                                          Class<? extends CoreAnnotation<String>> attribName,
                                          CMMSampler cmmSampler)
  {
    
    boolean useStemming = false;
    if(attribName == BestCliquesAnnotation.class) useStemming=true;
    
    ArrayList fields = (ArrayList)generateFieldsToLabel(template,useStemming,
                                                        cmmSampler);
    
    for( int wiIdx = 0; wiIdx < wordInfos.size(); ++wiIdx ) {
      CoreLabel wi = (CoreLabel) wordInfos.get(wiIdx);
      for(int fieldIdx = 0; fieldIdx < fields.size(); ++fieldIdx ) {
        Field field = (Field) fields.get(fieldIdx);
        //if the word info is a URL, we only proceed if the field
        //to label is a URL.
        if(!wi.get(IsURLAnnotation.class).equals("isURL") || field.isURL){
          field.nextToken( wi.word() );
          if( field.haveMatch() ) {
            // System.err.println("Match field " + field.getLabel() 
            //                  + " tokens " + (wiIdx - field.matchingTokens() + 1)  
            //                  + " to " + wiIdx);
            for( int tagIdx = wiIdx - field.matchingTokens() + 1; 
                 tagIdx <= wiIdx; ++tagIdx ){
            CoreLabel toBeTagged = (CoreLabel) wordInfos.get(tagIdx);
            toBeTagged.set(attribName, field.getLabel());
            }
          }
        }
        
        
      }
    }
    
    
    
    for( int wiIdx = 0; wiIdx < wordInfos.size(); ++wiIdx ) {
      CoreLabel wi = (CoreLabel) wordInfos.get(wiIdx);
      if( wi.get(attribName).equals("")) {
        wi.set(attribName, "0");
      }
    }
  }
  
}
