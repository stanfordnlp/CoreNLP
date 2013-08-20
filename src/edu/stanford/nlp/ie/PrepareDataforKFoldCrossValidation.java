package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;



/**
 *  Separates data into k-parts with roughly equal no. of documents in each part.
 *  It then creates k files with 1 part each(for testing) and k files with (k-1) parts each
 *  for training.
 *
 *
 *   @author Vijay Krishnan
 */

public class PrepareDataforKFoldCrossValidation {

  private PrepareDataforKFoldCrossValidation() {
  }


  public static void prepareData(String fileName, int k) throws IOException{


    // Compute no. of documents in pass 1
    BufferedReader br = new BufferedReader(new FileReader(fileName));
    String line;

    int numDocs = 1;
    while ((line = br.readLine()) != null) {
      line = line.trim();

      if (line.equals(""))
        numDocs++;
    }
    br.close();

    System.out.println("Number of Docs= " + numDocs);
    // In pass 2, write into appropriate files

    br = new BufferedReader(new FileReader(fileName));
    int docNum =1;
    int foldNum =0;

    BufferedWriter[] bwTest = new BufferedWriter[k];
    BufferedWriter[] bwTrain = new BufferedWriter[k];

    for (int i=0;i<k;i++){
      bwTest[i] = new BufferedWriter(new FileWriter(fileName + "_Test" + i));
      bwTrain[i] = new BufferedWriter(new FileWriter(fileName + "_Train" + i));
    }


    while ((line = br.readLine()) != null) {

      // write line into 1 test doc and (k-1) train docs
      bwTest[foldNum].write(line +"\n");

      for (int i=0;i<k;i++){
        if (i == foldNum)
          continue;

      bwTrain[i].write(line +"\n");
      }
      
      line = line.trim();
      if (line.equals("")){
        docNum++;
        // update fold num
        foldNum = (docNum * k - 1)/ numDocs;
      }

    }

    br.close();
    for (int i=0;i<k;i++){
      bwTest[i].close();
      bwTrain[i].close();
    }


  }



  public static void main(String[] args) throws IOException {
    PrepareDataforKFoldCrossValidation.prepareData("/scr/kvijay/data/ner/column_data/CRF/eng_transformed_IO.train", 10);
  }

}
