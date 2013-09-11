package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

/**
 * A general purpose class that prints the confusion matrices of label agreement
 * at the token level, entity level, and between entity subsequences and
 * entity supersequences. These stats can be obtained both within documents
 * and across the whole corpus. In addition, it has methods to print the
 * majority label of all occurrences of the token, the entity, as well as all
 * entity subsequences and entity supersequences. As before, these can be
 * computed both within a document and across the whole corpus.
 *
 *
 * @author Vijay Krishnan
 */

public class NamedEntityConfusionMatrices {

  // numEntities = no. of distinct entities + 1(to account for O).
  int numEntities = 5;

  /*  Pass a BufferedReader pointing to the line where you want to start reading.
   * CorpusLevel=true will make this method scan till the end of the file.
   * CorpusLevel=false makes it stop at the end of the document
   *
   *
   */

  // based on  IOB1 labeling...


  public static boolean sameEntity(String prev, String current,boolean entityLevel){
    // Setting entityLevel to false makes this method always return false
    // useful if we want single token majorities
    if (!entityLevel)
      return false;


    if (current.equalsIgnoreCase("O"))
      return false;

    String[] prevSplit = prev.split("-");
    String[] currentSplit = current.split("-");
    if ( (prevSplit.length < 2) ||  (currentSplit.length < 2))
     return false;
//    return (prev.equals(current));
    return ((prevSplit[1].equals(currentSplit[1])) && (!currentSplit[0].equals("B")));
  }


 /*
  public boolean sameEntity(String prev, String current){
      return false;
  }
 */

  // Arrays passed by reference. So its fine.
  public static String numToName(int num){
    if (num ==0)
      return "ORG";
    if (num ==1)
      return "GPE";
      //return "MISC";
    if (num ==2)
      return "PERSON";
    if (num == 3)
      return "LOC";
    return "O";
  }

  public static void incrementEntityLabelCount(int[] counts, String entityLabel){
    if (entityLabel.contains("ORG"))
      counts[0]++;
    //else if (entityLabel.contains("MISC"))
    else if (entityLabel.contains("GPE"))
      counts[1]++;
    else if (entityLabel.contains("PERSON"))
      counts[2]++;
    else if (entityLabel.contains("LOC"))
      counts[3]++;
    else
      counts[4]++;
  }


  public static int labelToNum(int[] counts, String entityLabel){
    if (entityLabel.contains("ORG"))
      return 0;
    //else if (entityLabel.contains("MISC"))
    else if (entityLabel.contains("GPE"))
      return 1;
    else if (entityLabel.contains("PERSON"))
      return 2;
    else if (entityLabel.contains("LOC"))
      return 3;
    else
      return 4;

  }


  public String entryAtColumnNumber(String line,int col){
    StringTokenizer st = new StringTokenizer(line);
    for (int i=1;i < col; i++){
      if (st.hasMoreTokens())
        st.nextToken();
    }
    if (st.hasMoreTokens())
      return st.nextToken();
    else
      return "";
  }


  public static void updateEntities(HashMap<String, int[]> entities, String entityTokensRaw,String entityLabel,
      boolean ignoreCase){

    String entityTokens ="";
    if (ignoreCase)
      entityTokens = entityTokensRaw.toLowerCase();
    else
      entityTokens = entityTokensRaw;

    if  (entities.containsKey(entityTokens)){
      int[] counts = entities.get(entityTokens);
      incrementEntityLabelCount(counts,entityLabel);
      entities.put(entityTokens,counts);
    }
    else{
      // modified to accommodate "O"
      int[] counts = new int[5];
      incrementEntityLabelCount(counts,entityLabel);
      entities.put(entityTokens,counts);
    }

  }

  //TODO why isn't the last token of a document/corpus getting added to the HashMap
  // Stores entities spanning multiple words by separating with "~". May not be the best possible way.
  public HashMap<String, int[]> namedEntitiesLabelCounts(BufferedReader br, boolean corpusLevel,
      int tokenColumn,int labelColumn,boolean ignoreCase,boolean entityLevel) throws IOException {
 // Column numbers start from 1

   // This is a mapping from the "extended string" to  and int array of size 4(ORG, MISC, PER, LOC)
   // actually array of size 5... fifth one being "O".

    HashMap<String, int[]> entities = new HashMap<String, int[]>();
//    BufferedReader br = new BufferedReader(new FileReader(fileName));
    String line;

    String entitySoFar = "", prevEntityLabel = "";
    int linesScanned =0;

    while ((line = br.readLine()) != null) {
      if (!corpusLevel && line.trim().equals(""))
        break;

      linesScanned++;
  //    System.out.println(line);
      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
         // if (! (prevEntityLabel.equals("O")  || prevEntityLabel.equals("") ))
          // CHANGED to count O labels as singletons as well....
          if (! prevEntityLabel.equals("") )
            updateEntities(entities,entitySoFar,prevEntityLabel,ignoreCase);

         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
         // if (! (prevEntityLabel.equals("O")  || prevEntityLabel.equals("")))
          // CHANGED to count O labels as singletons as well....
          if (! prevEntityLabel.equals("") )
         updateEntities(entities,entitySoFar,prevEntityLabel,ignoreCase);

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }
    } // end of while loop

    updateEntities(entities,entitySoFar,prevEntityLabel,ignoreCase);

    if (linesScanned == 0)
      entities = null;

    return entities;
  }

  // Throw out entries which occur only once.
/*
  public void pruneEntities(HashMap<String, int[]> entities){
    String entityTokens[] = (String[])entities.keySet().toArray(new String[0]);
    for (int i=0;i < entityTokens.length;i++){
      int counts[] = entities.get(entityTokens[i]);
    }
  } */

  public int[][] corpusLevelConfusionMatrix(String file, boolean ignoreCase,
      boolean entityLevel) throws IOException{

    BufferedReader br = new BufferedReader(new FileReader(file));
    HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,true,1,3,ignoreCase,entityLevel);
  br.close();
   int[][] confusionMatrix= new int[4][4];
   for (String entityToken : entities.keySet()) {
     int[] counts = entities.get(entityToken);
     // System.out.println(entityTokens[i] + " "
     //     + numToName(0) + counts[0] + " "
     //     + numToName(1) + counts[1] + " "
     //     + numToName(2) + counts[2] + " "
     //     + numToName(3) + counts[3] + " ");

     for (int j=0;j < 4;j++)
       for (int k=j;k<4;k++){
         confusionMatrix[j][k] += counts[j] * counts[k];
       }
   }

   for (int j=0;j < 4;j++){
     for (int k=j;k<4;k++){
       System.out.print(numToName(j) + ":" + numToName(k) + " " +confusionMatrix[j][k] + "\t");
     }
     System.out.println("");
   }

   return confusionMatrix;
  }


  public static void printMajority (HashMap<String, int[]> entities, String entityTokensRaw,String entityLabel,
      boolean ignoreCase){

    String entityTokens = "";
    if (ignoreCase)
      entityTokens = entityTokensRaw.toLowerCase();
    else
      entityTokens = entityTokensRaw;

    int label;
   // if ((entityLabel.equals("")) || (entityLabel.equals("O"))){
    // CHANGED TO allow O labels
    if (entityLabel.equals("")){
      System.out.println(entityLabel);
    return;
    }

      int[] counts = entities.get(entityTokens);

      //TODO remove this when bug with last token not getting added to HashMap is fixed
      if (counts == null){
        System.out.println(entityLabel);
        return;
      }

      int max = 0;
     int maxIndex = 0;
     for (int i=0;i<5;i++)
       if (counts[i] > max){
         max = counts[i];
         maxIndex = i;
       }

       int entityNum = labelToNum(counts, entityLabel);
       if (counts[entityNum] == max)
         label = entityNum;
       else
         label = maxIndex;

       //Now print the label
        StringTokenizer st = new StringTokenizer(entityTokens,"~");
        int numTokens = st.countTokens();

        //TODO ...currently hardcoded for IO.....may need to fix
        for (int i=0;i<numTokens;i++){
          if (numToName(label).equalsIgnoreCase("O"))
            System.out.println(numToName(label));
          else
            System.out.println("I-" + numToName(label));
        }



     }



  // read a results file and output the majority labels alone....
  public void CorpusLevelMajority(String file,boolean ignoreCase,
                                  boolean entityLevel) throws IOException{

    BufferedReader br = new BufferedReader(new FileReader(file));
    HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,true,1,3,ignoreCase,entityLevel);
    br.close();
    int tokenColumn = 1;
    int labelColumn  =3;
    br = new BufferedReader(new FileReader(file));
    String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo =0;

    while ((line = br.readLine()) != null) {

      lineNo++;
      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajority(entities,entitySoFar,prevEntityLabel,ignoreCase);

         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
 //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajority(entities,entitySoFar,prevEntityLabel,ignoreCase);
 //       else
  //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }
    } // end of while loop

    printMajority(entities,entitySoFar,prevEntityLabel,ignoreCase);

    br.close();
  }


  public void DocumentLevelMajority(String file,boolean ignoreCase,
      boolean entityLevel) throws IOException{

    // stores all hashmaps, one for each doc
    List<HashMap<String, int[]>> hashMapsofDocuments = new ArrayList<HashMap<String, int[]>>();
    BufferedReader br = new BufferedReader(new FileReader(file));
    while (true) {
      HashMap<String, int[]> entities = namedEntitiesLabelCounts(br, false, 1, 3, ignoreCase, entityLevel);
      if (entities == null) {
        break;
      } else {
        hashMapsofDocuments.add(entities);
      }
    }
    br.close();

    int tokenColumn = 1;
    int labelColumn = 3;
    br = new BufferedReader(new FileReader(file));
    String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo = 0;
    int docNo = 0;

    while ((line = br.readLine()) != null) {

      lineNo++;

      String word = entryAtColumnNumber(line, tokenColumn);
      String currEntityLabel = entryAtColumnNumber(line, labelColumn);

      if (currEntityLabel.equals("O") || currEntityLabel.equals("")) {
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1) {
          printMajority(hashMapsofDocuments.get(docNo), entitySoFar,
                        prevEntityLabel, ignoreCase);
        }

        entitySoFar = word;
        prevEntityLabel = currEntityLabel;
      } else if (sameEntity(prevEntityLabel, currEntityLabel, entityLevel)) {
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel = currEntityLabel;
      } else {
        // HashMap updated with latest entity
        //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1) {
          printMajority(hashMapsofDocuments.get(docNo), entitySoFar,
                        prevEntityLabel, ignoreCase);
        }
        //       else
        //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel = currEntityLabel;
      }

      if (line.trim().equals("")) {
        docNo++;
      }

    } // end of while loop

    printMajority(hashMapsofDocuments.get(hashMapsofDocuments.size() - 1),
                  entitySoFar, prevEntityLabel, ignoreCase);

    br.close();


  }


  // Write a printMajoritySuperString which does what's necessary in a blackbox way....
   public boolean isSubEntity(String superEntity, String subEntity){

     return (superEntity.startsWith(subEntity + "~") ||
         superEntity.endsWith("~" + subEntity) ||
         superEntity.contains("~" + subEntity + "~"));

   }


  public void printMajoritySuperString (HashMap<String, int[]> entities, String entityTokens,String entityLabel){

    int label;
   // if ((entityLabel.equals("")) || (entityLabel.equals("O"))){
    // CHANGED TO allow O labels
    if (entityLabel.equals("")){
      System.out.println(entityLabel);
    return;
    }

  /*
    int counts[] = entities.get(entityTokens);
      //TODO remove this when bug with last token not getting added to HashMap is fixed
      if (counts == null){
        System.out.println(entityLabel);
        return;
      }*/
    // Need to get the final counts here...
    int[] totalSuperstringCounts = new int[5];

    for (String entityToken : entities.keySet()) {
      if (isSubEntity(entityToken,entityTokens)){
            int[] counts = entities.get(entityToken);

            for (int j=0;j<totalSuperstringCounts.length;j++)
              totalSuperstringCounts[j] += counts[j];
    }
    }


      int max = 0;
     int maxIndex = 0;
     for (int i=0;i<5;i++)
       if (totalSuperstringCounts[i] > max){
         max = totalSuperstringCounts[i];
         maxIndex = i;
       }

       int entityNum = labelToNum(totalSuperstringCounts, entityLabel);
       if (totalSuperstringCounts[entityNum] == max)
         label = entityNum;
       else
         label = maxIndex;

       //Now print the label
        StringTokenizer st = new StringTokenizer(entityTokens,"~");
        int numTokens = st.countTokens();

        //TODO ...currently hardcoded for IO.....may need to fix
        for (int i=0;i<numTokens;i++){
          if (numToName(label).equalsIgnoreCase("O"))
            System.out.println(numToName(label));
          else
            System.out.println("I-" + numToName(label));
        }
     }



  public static HashMap<String, List<String>> subStringToSuperStringIndex(HashMap<String, int[]> entities,int superStringPosition){
    /* Note: superStringPosition = 0   =>  We look at all proper superStrings
     *   Note: superStringPosition = 1   =>  We look at proper superStrings that start at the given substring
     *   Note: superStringPosition = 2   =>  We look at proper superStrings with the substring strictly in the middle
     *   Note: superStringPosition = 3   =>  We look at all proper superStrings with the substring ending it.
     */


    // index stores mappings from substrings to vectors of superstrings
    HashMap<String, List<String>> index = new HashMap<String, List<String>>();

    // iterate over substrings of the particular entity tokens
    for (String entityTokens : entities.keySet()) {

      String[] eTokens = entityTokens.split("~");
      //  Now generate all possible substrings
      for (int i=0;i< eTokens.length;i++){
       String sub = "";

        for (int j=i;j< eTokens.length;j++){
          if (j >i)
            sub = sub + "~";

          sub = sub + eTokens[j];

          if (entityTokens.equals(sub))
            continue;

          // Check if superstring is "eligible" under the position constraint

          //TODO: This is one of the places where we don't do "toLowerCase"
          boolean superStringEligible;
          if (superStringPosition ==1){
            superStringEligible = entityTokens.startsWith(sub);
          }
          else if (superStringPosition ==2){
            superStringEligible = entityTokens.contains(sub) &&
            (!entityTokens.startsWith(sub))  && (!entityTokens.endsWith(sub));
          }
          else if (superStringPosition ==3){
            superStringEligible = entityTokens.endsWith(sub);
          }
          else{ // For other values, look at all superstrings
            superStringEligible = true;
          }

          // Do whatever you want with the substrings over here.....
          if (superStringEligible){
          if (index.containsKey(sub)){
            List<String> v = index.get(sub);
            v.add(entityTokens);
            index.put(sub,v);
          }
          else{
            List<String> v = new ArrayList<String>();
            v.add(entityTokens);
            index.put(sub,v);
          }
          }

        }
      }

    }
    return index;

  }


// Same as printMajoritySuperString except that it takes an extra argument
  // An index from substring to all its superstring for faster computation
  // TODO modify this method....to use the index....


  public static void printMajoritySuperStringIndexed(Map<String, int[]> entities, Map<String, List<String>> subToSuperIndex,
                                                     String entityTokensRaw, String entityLabel, boolean ignoreCase) {


    String entityTokens = "";
    if (ignoreCase) {
      entityTokens = entityTokensRaw.toLowerCase();
    } else {
      entityTokens = entityTokensRaw;
    }

    int label;
    // if ((entityLabel.equals("")) || (entityLabel.equals("O"))){
    // CHANGED TO allow O labels
    if (entityLabel.equals("")) {
      System.out.println(entityLabel);
      return;
    }

    /*
  int counts[] = entities.get(entityTokens);
    //TODO remove this when bug with last token not getting added to HashMap is fixed
    if (counts == null){
      System.out.println(entityLabel);
      return;
    }*/
    // Need to get the final counts here...
    int[] totalSuperstringCounts = new int[5];
    List<String> superEntities = subToSuperIndex.get(entityTokens);
    if (superEntities != null) {


      for (int i = 0; i < superEntities.size(); i++) {
        int[] counts = entities.get(superEntities.get(i));

        for (int j = 0; j < totalSuperstringCounts.length; j++) {
          totalSuperstringCounts[j] += counts[j];
        }
      }
    }


    int max = 0;
    int maxIndex = 0;
    for (int i = 0; i < 5; i++) {
      if (totalSuperstringCounts[i] > max) {
        max = totalSuperstringCounts[i];
        maxIndex = i;
      }
    }

    int entityNum = labelToNum(totalSuperstringCounts, entityLabel);
    if (totalSuperstringCounts[entityNum] == max) {
      label = entityNum;
    } else {
      label = maxIndex;
    }

    //Now print the label
    StringTokenizer st = new StringTokenizer(entityTokens, "~");
    int numTokens = st.countTokens();

    //TODO ...currently hardcoded for IO.....may need to fix
    for (int i = 0; i < numTokens; i++) {
      if (numToName(label).equalsIgnoreCase("O")) {
        System.out.println(numToName(label));
      } else {
        System.out.println("I-" + numToName(label));
      }
    }
  }


  //TODO check that corpus + doc level superstrings run after the indexing change...
  // read a results file and output the majority labels alone....
  public void CorpusLevelSuperstringMajority(String file,int superStringPosition,
      boolean ignoreCase) throws IOException{
    /* Note: superStringPosition = 0   =>  We look at all proper superStrings
     *   Note: superStringPosition = 1   =>  We look at proper superStrings that start at the given substring
     *   Note: superStringPosition = 2   =>  We look at proper superStrings with the substring strictly in the middle
     *   Note: superStringPosition = 3   =>  We look at all proper superStrings with the substring ending it.
     */
    boolean entityLevel = true;

    BufferedReader br = new BufferedReader(new FileReader(file));
    HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,true,1,3,ignoreCase,entityLevel);
    br.close();
    HashMap<String, List<String>> subToSuperIndex = subStringToSuperStringIndex(entities,superStringPosition);

    int tokenColumn = 1;
    int labelColumn  =3;
    br = new BufferedReader(new FileReader(file));
   String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo =0;

    while ((line = br.readLine()) != null) {

      lineNo++;
      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1){
          //printMajoritySuperString(entities,entitySoFar,prevEntityLabel);
          printMajoritySuperStringIndexed(entities,subToSuperIndex,entitySoFar,
              prevEntityLabel,ignoreCase);
        }
         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
 //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1) {
         // printMajoritySuperString(entities,entitySoFar,prevEntityLabel);
          printMajoritySuperStringIndexed(entities,subToSuperIndex,entitySoFar,
              prevEntityLabel,ignoreCase);
        }
          //       else
  //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }
    } // end of while loop

    //printMajoritySuperString(entities,entitySoFar,prevEntityLabel);
    printMajoritySuperStringIndexed(entities,subToSuperIndex,entitySoFar,
        prevEntityLabel,ignoreCase);

    br.close();
  }





  public void DocumentLevelSuperstringMajority(String file,int superStringPosition,
      boolean ignoreCase) throws IOException{
    /* Note: superStringPosition = 0   =>  We look at all proper superStrings
     *   Note: superStringPosition = 1   =>  We look at proper superStrings that start at the given substring
     *   Note: superStringPosition = 2   =>  We look at proper superStrings with the substring strictly in the middle
     *   Note: superStringPosition = 3   =>  We look at all proper superStrings with the substring ending it.
     */
    boolean entityLevel = true;

   // stores all hashmaps, one for each doc
    List<HashMap<String, int[]>> hashMapsofDocuments = Generics.newArrayList();
    List<Map<String, List<String>>> hashMapsofSubtoSuperStrings = Generics.newArrayList();
    BufferedReader br = new BufferedReader(new FileReader(file));
   while (true){
     HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,false,1,3,ignoreCase,entityLevel);
     if (entities == null)
       break;
     hashMapsofDocuments.add(entities);
     hashMapsofSubtoSuperStrings.add(subStringToSuperStringIndex(entities,superStringPosition));
   }
    br.close();

    int tokenColumn = 1;
    int labelColumn  =3;
    br = new BufferedReader(new FileReader(file));
   String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo =0;
    int docNo = 0;

    while ((line = br.readLine()) != null) {

      lineNo++;

      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1){
         // printMajoritySuperString(hashMapsofDocuments.get(docNo),entitySoFar,prevEntityLabel);
          printMajoritySuperStringIndexed(hashMapsofDocuments.get(docNo),
              hashMapsofSubtoSuperStrings.get(docNo),entitySoFar,prevEntityLabel,ignoreCase);

        }
         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
 //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1) {
          //printMajoritySuperString(hashMapsofDocuments.get(docNo),entitySoFar,prevEntityLabel);
          printMajoritySuperStringIndexed(hashMapsofDocuments.get(docNo),
              hashMapsofSubtoSuperStrings.get(docNo),entitySoFar,prevEntityLabel,ignoreCase);

        }
          //       else
  //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }

      if (line.trim().equals(""))
        docNo++;

    } // end of while loop

 //   printMajoritySuperString(hashMapsofDocuments.get(hashMapsofDocuments.size() - 1),
   //     entitySoFar,prevEntityLabel);
    printMajoritySuperStringIndexed(hashMapsofDocuments.get(hashMapsofDocuments.size() - 1),
        hashMapsofSubtoSuperStrings.get(hashMapsofDocuments.size() - 1),
        entitySoFar,prevEntityLabel,ignoreCase);


    br.close();


  }




  public void printMajoritySubString (Map<String, int[]> entities, String entityTokens,String entityLabel){

    int label;
   // if ((entityLabel.equals("")) || (entityLabel.equals("O"))){
    // CHANGED TO allow O labels
    if (entityLabel.equals("")){
      System.out.println(entityLabel);
    return;
    }

  /*
    int counts[] = entities.get(entityTokens);
      //TODO remove this when bug with last token not getting added to HashMap is fixed
      if (counts == null){
        System.out.println(entityLabel);
        return;
      }*/
    // Need to get the final counts here...
    int[] totalSubstringCounts = new int[5];
    String[] eTokens = entityTokens.split("~");
    //  Now generate all possible substrings
    for (int i=0;i< eTokens.length;i++){
     String sub = "";

      for (int j=i;j< eTokens.length;j++){
        if (j >i)
          sub = sub + "~";

        sub = sub + eTokens[j];

        if (entityTokens.equals(sub))
          continue;

        int[] counts = entities.get(sub);
        if (counts != null){
        for (int k=0;k<totalSubstringCounts.length;k++)
          totalSubstringCounts[k] += counts[k];
        }

      }
    }


      int max = 0;
     int maxIndex = 0;
     for (int i=0;i<5;i++)
       if (totalSubstringCounts[i] > max){
         max = totalSubstringCounts[i];
         maxIndex = i;
       }

       int entityNum = labelToNum(totalSubstringCounts, entityLabel);
       if (totalSubstringCounts[entityNum] == max)
         label = entityNum;
       else
         label = maxIndex;

       //Now print the label
        StringTokenizer st = new StringTokenizer(entityTokens,"~");
        int numTokens = st.countTokens();

        //TODO ...currently hardcoded for IO.....may need to fix
        for (int i=0;i<numTokens;i++){
          if (numToName(label).equalsIgnoreCase("O"))
            System.out.println(numToName(label));
          else
            System.out.println("I-" + numToName(label));
        }
     }



  // read a results file and output the majority labels alone....
  public void CorpusLevelSubstringMajority(String file,boolean ignoreCase) throws IOException{

    boolean entityLevel = true;

      BufferedReader br = new BufferedReader(new FileReader(file));
      HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,true,1,3,ignoreCase,entityLevel);
    br.close();
    int tokenColumn = 1;
    int labelColumn  =3;
    br = new BufferedReader(new FileReader(file));
   String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo =0;

    while ((line = br.readLine()) != null) {

      lineNo++;
      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajoritySubString(entities,entitySoFar,prevEntityLabel);

         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
 //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajoritySubString(entities,entitySoFar,prevEntityLabel);
 //       else
  //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }
    } // end of while loop

    printMajoritySubString(entities,entitySoFar,prevEntityLabel);

    br.close();
  }





  public void DocumentLevelSubstringMajority(String file,boolean ignoreCase) throws IOException{

    boolean entityLevel = true;

   // stores all hashmaps, one for each doc
    List<Map<String, int[]>> hashMapsofDocuments = Generics.newArrayList();
    BufferedReader br = new BufferedReader(new FileReader(file));
   while (true){
     HashMap<String, int[]> entities = namedEntitiesLabelCounts(br,false,1,3,ignoreCase,entityLevel);
     if (entities == null)
       break;
     hashMapsofDocuments.add(entities);
   }
    br.close();

    int tokenColumn = 1;
    int labelColumn  =3;
    br = new BufferedReader(new FileReader(file));
   String line;
    String entitySoFar = "", prevEntityLabel = "";
    int lineNo =0;
    int docNo = 0;

    while ((line = br.readLine()) != null) {

      lineNo++;

      String word = entryAtColumnNumber(line,tokenColumn);
      String  currEntityLabel = entryAtColumnNumber(line,labelColumn);

      if ( currEntityLabel.equals("O") || currEntityLabel.equals("")  ){
//        if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajoritySubString(hashMapsofDocuments.get(docNo),entitySoFar,prevEntityLabel);

         entitySoFar = word;
         prevEntityLabel =  currEntityLabel;
      }
      else if (sameEntity(prevEntityLabel,currEntityLabel,entityLevel)){
        entitySoFar = entitySoFar + "~" + word;
        prevEntityLabel =  currEntityLabel;
      }
      else{
        // HashMap updated with latest entity
 //       if (! (  entitySoFar.trim().equals("") || entitySoFar.trim().equals("~") ))
        if (lineNo > 1)
          printMajoritySubString(hashMapsofDocuments.get(docNo),entitySoFar,prevEntityLabel);
 //       else
  //        System.out.println("");

        entitySoFar = word;
        prevEntityLabel =  currEntityLabel;
      }

      if (line.trim().equals(""))
        docNo++;

    } // end of while loop

    printMajoritySubString(hashMapsofDocuments.get(hashMapsofDocuments.size() - 1),
        entitySoFar,prevEntityLabel);

    br.close();


  }


  public int[][] corpusLevelSubstringConfusionMatrix(String file, boolean ignoreCase) throws IOException {

    boolean entityLevel = true;

    BufferedReader br = new BufferedReader(new FileReader(file));
    HashMap<String, int[]> entities = namedEntitiesLabelCounts(br, true, 1, 3, ignoreCase, entityLevel);
    br.close();
    int[][] confusionMatrix = new int[5][5];
    int[][][] enhancedConfusionMatrix = new int[5][5][3];
    for (String entityToken : entities.keySet()) {
      int[] superCounts = entities.get(entityToken);

      //   get substrings of entityTokens[i]
      String[] eTokens = entityToken.split("~");
      //  Now generate all possible substrings
      for (int t = 0; t < eTokens.length; t++) {
        String sub = "";

        for (int j = t; j < eTokens.length; j++) {
          if (j > t) {
            sub = sub + "~";
          }
          sub = sub + eTokens[j];

          if (entityToken.equals(sub)) {
            continue;
          }

          int[] counts = entities.get(sub);
          if (counts != null) {
            // for (int k=0;k<counts.length;k++)
            // totalSubstringCounts[k] += counts[k];
            for (int x = 0; x < superCounts.length; x++) {
              for (int y = 0; y < counts.length; y++) {
                confusionMatrix[x][y] += superCounts[x] * counts[y];
                int temp = 1;

                if (entityToken.startsWith(sub)) {
                  temp = 0;
                }

                if (entityToken.endsWith(sub)) {
                  temp = 2;
                }

                enhancedConfusionMatrix[x][y][temp] += superCounts[x] * counts[y];
              }
            }
          }
        }
      }
    }

    /*  System.out.println(entityTokens[i] + " "
+ numToName(0) + counts[0] + " "
+ numToName(1) + counts[1] + " "
+ numToName(2) + counts[2] + " "
+ numToName(3) + counts[3] + " ");*/
     /*
     for (int j=0;j < 4;j++)
       for (int k=j;k<4;k++){
         confusionMatrix[j][k] += counts[j] * counts[k];
       }

   }*/

   for (int j=0;j< 4;j++){
     for (int k=0;k<4;k++){
       System.out.print(numToName(j) + ":" + numToName(k) + " " +confusionMatrix[j][k] + "\t");
     }
     System.out.println("");
   }
   // System.out.println("");

   // String[] st = new String[3];
   // st[0] = "start";
   // st[1] = "middle";
   // st[2] = "end";

   // for (int j=0;j < 5;j++){
   //   for (int k=0;k<5;k++){
   //     for (int l=0;l<3;l++){
   //     System.out.print(numToName(j) + ":" + numToName(k) +  ":" + st[l] +  " " +
   //         enhancedConfusionMatrix[j][k][l] + "     ");
   //     }
   //     System.out.println("");
   //     }
   //   System.out.println("");
   // }

   return confusionMatrix;
  }



  /**
   * @throws IOException
   */
  /* Note: superStringPosition = 0   =>  We look at all proper superStrings
   *   Note: superStringPosition = 1   =>  We look at proper superStrings that start at the given substring
   *   Note: superStringPosition = 2   =>  We look at proper superStrings with the substring strictly in the middle
   *   Note: superStringPosition = 3   =>  We look at all proper superStrings with the substring ending it.
   */
  public static void main(String[] args) throws IOException {

    NamedEntityConfusionMatrices n = new NamedEntityConfusionMatrices();


    //int temp[][] = n.corpusLevelSubstringConfusionMatrix(args[0]);
   String filename = args[0];
   int position =0;
   int majorityOption = Integer.parseInt(args[1]);
   switch (majorityOption){
   // Corpus Level entity Level Majority
   // case 1: n.CorpusLevelMajority(filename,true,true);
   case 1: n.corpusLevelConfusionMatrix(filename,true,true);
   break;
   // Document Level entity Level Majority
   // case 2: n.DocumentLevelMajority(filename,true,true);
   // break;
   // Corpus Level token Level Majority
   // case 3: n.CorpusLevelMajority(filename,true,false);
   // break;
   // Document Level token Level Majority
   // case 4: n.CorpusLevelMajority(filename,true,false);
   // break;
   // Corpus Level superstring majority
   // case 5: n.CorpusLevelSuperstringMajority(filename,position,true);
   case 2: n.corpusLevelSubstringConfusionMatrix(filename,true);
   break;
   // Document Level superstring majority
   // case 6: n.DocumentLevelSuperstringMajority(filename,position,true);
   // break;
   }


//   n.CorpusLevelSuperstringMajority(filename,position);
   //n.DocumentLevelSuperstringMajority(filename,position,true);
   // if (args.length > 0)
  //    n.CorpusLevelSubstringMajority(args[0]);
    /*
    else{
//   int temp[][] = n.corpusLevelConfusionMatrix("/scr/kvijay/data/ner/column_data/eng_transformed_IOB1.train");
   n.CorpusLevelMajority("/scr/kvijay/data/ner/column_data/CRF/results/original_expt_testb.res");
      }*/
  //  n.DocumentLevelMajority(args[0]);

  }

}
