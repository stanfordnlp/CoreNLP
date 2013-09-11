package edu.stanford.nlp.classify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.BasicDataCollection;
import edu.stanford.nlp.ling.DataCollection;
import edu.stanford.nlp.ling.Datum;
//import edu.stanford.nlp.ling.Datum; will need this later if we decide to use a combination of BasicDatum and RVFDatum.
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author nmramesh@cs.stanford.edu
 *
 * 1/2009.
 *
 * <p>Class that learns a mixture of multiple logistic regression models.
 * Can be used when an example can be represented as a union of disjoint subsets of features where
 * each subset corresponds to a component of the mixture model, with its own logistic classifier.
 * </p>
 *
 * <p>For example each document can be represented by disjoint subsets of features such as
 * body, anchor text, title, urls, etc, each of which contributes to the label of the document.
 * The <code>MixtureOfLogistics</code> classifier allows an independent classifier for each feature subset,
 * while also learning the relative importance of each feature subset through the mixture probabilities.
 * </p>
 *
 * <p> This model is expected to be better than both a flat classifier and also a meta classifier.
 * Better than flat classifier because it prevents one feature from dominating others, and better than
 * meta classifier because the learning of component-wise classifiers and weights of each component is joint.
 * </p>
 *
 * <p>GeneralDataset corresponding to each component is allowed to be <code>RVFDataset</code> or <code>Dataset</code>.
 * </p>
 */

public class MixtureOfLogistics<L,F> {
  private List<GeneralDataset<L,F>> datasets;
  private double[][] logisticWeights;
  private double[] logMixtureWeights; //model mixture distribution over components
  private double[][] dataWeights; // transpose of the posterior distribution over components for each training example
  private int maxIter = 1000; // maximum number of EM steps
  private double convergence = 1e-4; //convergence criterion for iterations.
  private int numComponents;
  private int dataSize;
  private double priorScale;
  private Index<L> labelIndex;
  private static final int FOLD_COUNT = 10;
  /**
   * Constructor. Initializes the model.
   * @param datasets an array of datasets, where each dataset corresponds to one component (feature-subset)
   */
  public MixtureOfLogistics(List<GeneralDataset<L,F>> datasets,int maxIter, double convergence, double priorScale){
    this.datasets = datasets;
    boolean matched = matchDatasets();
    if(!matched)System.exit(0);
    this.maxIter = maxIter;
    this.convergence = convergence;
    this.dataSize = datasets.get(0).size();
    this.numComponents = datasets.size();
    this.labelIndex = datasets.get(0).labelIndex;
    this.priorScale = priorScale;
    initializeModel();
  }

  /**
   * Method to train the mixture model until convergence without cross validation.
   * @param beta is a parameter for tempering. Default value is 1.
   */
  public void train(double beta){
    double lhood = 0,old_lhood = -9999999999.99,fracImprov=1;
    for(int iter = 0; iter < maxIter; iter++){
      runMstep1(datasets,dataWeights);
      lhood = runEstep(0,dataSize,dataWeights,beta);
      runMstep2();
      if(iter>0){
        fracImprov = -(lhood - old_lhood)/old_lhood;
        // negative sign because old_lhood is always negative.
        if(iter%1 == 0)
          System.out.printf("iter: %d log-likelihood: %f old-likelihood: %f fractional Improvement: %f\n",iter,lhood,old_lhood,fracImprov);
      }
      if(fracImprov < 0){
        System.err.printf("WARNING: log likelihood went down at iteration %d. old_lhood: %f, new_lhood: %f\n",iter,old_lhood,lhood);
      }
      else if(fracImprov < convergence)
        break;
      old_lhood = lhood;
    }
  }
/**
 * method to runEM for a fixed number of iterations without cross validation.
 * This method can be used to tune the maxIter value at development time.
 * @param beta parameter for tempering.
 */

  public void runEM(int numIter, double beta){
    for(int iter = 0; iter < numIter; iter++){
      runMstep1(datasets, dataWeights);
      runEstep(0,dataSize,dataWeights, beta);
      runMstep2();
    }
  }


  /**
   * method to reset model and start from scratch again without reallocating memory.
   * Can be useful when it is required to retrain the model after tuning on development set.
   */
  public void reset(){
    //initialize the weights of each logistic function to zeroes.
    for(int j = 0; j <numComponents; j++){
      Arrays.fill(logisticWeights[j], 0);
    }

    //initialize the mixture to a uniform distribution
    Arrays.fill(logMixtureWeights, 0.0);
    ArrayMath.logNormalize(logMixtureWeights);
    assert arrayIsLogDistribution(logMixtureWeights) : "logMixtureWeights not properly initialized.\n";

    //initialize the transpose of the posterior distribution for each example to uniform.
    for(int j = 0; j < numComponents; j++){
      Arrays.fill(dataWeights[j], 1.0/numComponents);
    }
  }

  public void reset(double priorScale){
    this.priorScale = priorScale;
    reset();
  }

  public void reset(double priorScale, int maxIter){
    this.maxIter = maxIter;
    reset(priorScale);
  }

  /**
   * method to test data collections.
   * Assumes that all the components of each example are <code>RVFDatum</code>s, but can be relaxed later.
   * @param beta is a parameter to temper the model. default=1.
   * @return accuracy.
   */
  public double test(List<DataCollection<L,F>> dataCollections, double beta){
    boolean matched = matchCollections(dataCollections);
    if(!matched){
      System.out.println("Returning 0 without evaluating the test data");
      return 0;
    }
    double accuracy = 0;
    int testDataSize = dataCollections.get(0).size();
    for(int i = 0; i < testDataSize; i++){
      L trueLabel = dataCollections.get(0).get(i).label();
      int trueLabelIndex = labelIndex.indexOf(trueLabel);
      List<Datum<L,F>> mixtureDatum = new ArrayList<Datum<L,F>>(numComponents);
      for(int j = 0; j < numComponents; j++){
        Datum<L,F> datum = dataCollections.get(j).getDatum(i);
        mixtureDatum.add(datum);
      }
      L sysLabel = classOf(mixtureDatum,beta);
      int sysLabelIndex = labelIndex.indexOf(sysLabel);
      if(sysLabelIndex == trueLabelIndex)accuracy++;
    }
    return accuracy/testDataSize;
  }

  /**
   * @param beta is for tempering. usually set to 1.
   */
  public L classOf(List<Datum<L,F>> mixtureDatum, double beta){
    double marginalLogLhood = 0.0;
    for(int j = 0; j < numComponents; j++){
      double logCompLhood = 0;
      Datum<L,F> datum = mixtureDatum.get(j);
      if(datum instanceof BasicDatum<?,?>)
        logCompLhood = scoreOf(datum.asFeatures(),1,j,beta);
      else if(datum instanceof RVFDatum<?,?>)
        logCompLhood = scoreOf(((RVFDatum<L,F>) datum).asFeaturesCounter(),1,j,beta);
      if(j==0)
        marginalLogLhood = logCompLhood;
      else
        marginalLogLhood = SloppyMath.logAdd(marginalLogLhood, logCompLhood);
    }
    if(Math.exp(marginalLogLhood) >= 0.5)
      return datasets.get(0).labelIndex.get(1);
    return datasets.get(0).labelIndex.get(0);
  }

  /**
   *
   * @param c a collection of features
   * @param label the label whose score is being computed
   * @param component the component for which the score is being computed
   * @return the score for <code>label</code> and <code>component</code>
   */
  private double scoreOf(Collection<F> c, int label, int component, double beta){
    double sum = 0;
    for(F feature : c){
      int f = datasets.get(component).featureIndex.indexOf(feature);
      if(f>=0){
        sum+=logisticWeights[component][f];
      }
    }
    if(label == 1)
      sum = -sum;
    return beta*(-Math.log(1.0+Math.exp(sum)))+logMixtureWeights[component];
  }

  /**
   *
   * @param c a counter of features
   * @param label the label whose score is being computed
   * @param component the component for which the score is being computed
   * @param beta the tempering paramter. usually <= 1. == 1 by default.
   * @return the score for <code>label</code> and <code>component</code>
   */
  private double scoreOf(Counter<F> c, int label, int component, double beta){
    double sum = 0;
    for(F feature : c.keySet()){
      int f = datasets.get(component).featureIndex.indexOf(feature);
      if(f>=0){
        double val = c.getCount(feature);
        sum+=logisticWeights[component][f]*val;
      }
    }
    if(label == 1)
      sum = -sum;
    return beta*(-Math.log(1.0+Math.exp(sum)))+logMixtureWeights[component];
  }


 /**
  * trains the model using cross validation until convergence.
  * @param beta the tempering parameter.
  */
  public void trainCrossValidation(double beta){
    double lhood = 0,old_lhood = -9999999999.99,fracImprov=1;
    for(int iter = 0; iter < maxIter; iter++){
      lhood = runEMCrossValidation(1,false,beta);
      if(iter>0){
        fracImprov = -(lhood - old_lhood)/old_lhood;
        // negative sign because old_lhood is always negative.
        if(iter%1 == 0)
          System.out.printf("iter: %d log-likelihood: %f old-likelihood: %f fractional Improvement: %f\n",iter,lhood,old_lhood,fracImprov);
      }
      if(fracImprov < 0){
        System.err.printf("WARNING: log likelihood went down at iteration %d. old_lhood: %f, new_lhood: %f\n",iter,old_lhood,lhood);
      }
      else if(fracImprov < convergence) {
        runMstep1(datasets,dataWeights);//train the component classifiers on all training data
        break;
      }
      old_lhood = lhood;
    }
    runMstep1(datasets,dataWeights); //train the component classifiers on all training data
  }


  public double runEMCrossValidation(int maxIter, boolean trainOnAllDataAtFinish,double beta){
    double[][] cvDataWeights = new double[numComponents][dataSize];
    double lhood = 0;
    for(int iter = 0; iter < maxIter; iter++){
      lhood = 0;
      int foldLen = dataSize / FOLD_COUNT;
      for(int fold = 0; fold < FOLD_COUNT; fold ++){
        List<GeneralDataset<L,F>> foldTrainDatasets = new ArrayList<GeneralDataset<L,F>>(numComponents);
        int foldStart = foldLen * fold;
        int foldEnd = foldLen * (fold + 1);
        double[][] foldDataWeights = new double[numComponents][dataSize-foldLen];
        for(int j = 0; j < numComponents; j++){
          Arrays.fill(foldDataWeights[j], -1);
          GeneralDataset<L,F> foldTrainData = null;
          if(datasets.get(j) instanceof Dataset<?,?>)
            foldTrainData = new Dataset<L,F>(dataSize-foldLen,datasets.get(j).featureIndex,
                datasets.get(j).labelIndex);
          else if(datasets.get(j) instanceof RVFDataset<?,?>)
            foldTrainData = new RVFDataset<L,F>(dataSize-foldLen,datasets.get(j).featureIndex,
                datasets.get(j).labelIndex);
          for(int i = 0; i < foldStart; i++){
            foldTrainData.add(datasets.get(j).getDatum(i));
            foldDataWeights[j][i] = dataWeights[j][i];
          }
          for(int i = foldEnd; i < dataSize; i ++){
            foldTrainData.add(datasets.get(j).getDatum(i));
            foldDataWeights[j][i-foldEnd+foldStart] = dataWeights[j][i];
          }
          foldTrainDatasets.add(foldTrainData);
        } //end loop over components
        runMstep1(foldTrainDatasets,foldDataWeights);
        lhood+=runEstep(foldStart, foldEnd, cvDataWeights,beta);
      } // end loop on folds
      for(int j = 0; j < numComponents; j++)
        System.arraycopy(cvDataWeights[j], 0, dataWeights[j], 0, dataSize);
      // at this point dataWeights are still unexponentiated. They will be below.
      runMstep2();
    } //end of iterations
    if(trainOnAllDataAtFinish)
      runMstep1(datasets,dataWeights);
    return lhood;
  }



  /**
   * learns the logistic regression models for each component.
   */
  private void runMstep1(List<GeneralDataset<L,F>> foldDatasets, double[][] foldDataWeights){
    QNMinimizer minim;
    for(int j = 0; j < numComponents; j++){
      LogisticObjectiveFunction lof = null;
      if(foldDatasets.get(j) instanceof Dataset<?,?>){
        Dataset<L,F> dj = (Dataset<L,F>)foldDatasets.get(j);
        lof = new LogisticObjectiveFunction(dj.numFeatureTypes(), dj.getDataArray(), dj.getLabelsArray(),double2float(foldDataWeights[j]));
      }
      else if(foldDatasets.get(j) instanceof RVFDataset<?,?>){
        RVFDataset<L,F> dj =  (RVFDataset<L,F>)foldDatasets.get(j);
        lof = new LogisticObjectiveFunction(dj.numFeatureTypes(), dj.getDataArray(), dj.getValuesArray(), dj.getLabelsArray(),double2float(foldDataWeights[j]));
      }
      minim = new QNMinimizer(lof);
      logisticWeights[j]= minim.minimize(lof, convergence, new double[foldDatasets.get(j).numFeatureTypes()]);
    }
  }

  /**
   * computes the posterior over components for each example, based on the prior and
   * the logistic conditional.
   * @return likelihood of the data
   */
  private double runEstep(int startExample, int endExample, double[][] cvDataWeights, double beta){
    double logLhood = 0.0;
    double[] logPosterior = new double[numComponents];
    //as a convention, use i to loop over data, and j over components.
    for(int i = startExample; i < endExample; i++){
      double datumMarginalLogLhood = 0;
      for(int j=0; j < numComponents; j++){
        if(datasets.get(j) instanceof Dataset<?,?>)
          logPosterior[j] = scoreOf(datasets.get(j).getDatum(i).asFeatures(),
              datasets.get(j).getLabelsArray()[i],j,beta);
        else if(datasets.get(j) instanceof RVFDataset<?,?>)
          logPosterior[j] = scoreOf(datasets.get(j).getRVFDatum(i).asFeaturesCounter(),
              datasets.get(j).getLabelsArray()[i],j,beta);
        //this is unnormalized posterior at this point.
        if(j==0)
          datumMarginalLogLhood = logPosterior[j];
        else
          datumMarginalLogLhood = SloppyMath.logAdd(datumMarginalLogLhood, logPosterior[j]);
      }
      logLhood+=datumMarginalLogLhood;
      ArrayMath.logNormalize(logPosterior);
      for(int j = 0; j < numComponents; j++){
        cvDataWeights[j][i] = logPosterior[j];
      }//data weights need to be exponentiated later in the code.
      assert arrayIsLogDistribution(logPosterior) : "logPosterior of example "+i+" not properly normalized in E-step.\n";
    }
    return logLhood;
  }

  /**
   * Computes the prior weights based on the posterior for each example.
   */
  private void runMstep2(){
    for(int j = 0; j < numComponents; j++){
      logMixtureWeights[j] = ArrayMath.logSum(dataWeights[j]);
      logMixtureWeights[j] = SloppyMath.logAdd(logMixtureWeights[j], Math.log(priorScale/numComponents));
      ArrayMath.expInPlace(dataWeights[j]);
    }
    ArrayMath.logNormalize(logMixtureWeights);
  }

  private float[] double2float(double[] a){
    float[] b = new float[a.length];
    for(int i = 0; i < a.length; i++)
      b[i] = (float)a[i];
    return b;
  }

  /**
   * Initializes all the model parameters.
   *
   */
  private void initializeModel(){
    logisticWeights = new double[numComponents][];
    //initialize the weights of each logistic function to zeroes.
    for(int j = 0; j <numComponents; j++){
      int numFeatures = datasets.get(j).numFeatures();
      logisticWeights[j] = new double[numFeatures];
      Arrays.fill(logisticWeights[j], 0);
    }

    //initialize the mixture to a uniform distribution
    logMixtureWeights = new double[numComponents];
    Arrays.fill(logMixtureWeights, 0.0);
    ArrayMath.logNormalize(logMixtureWeights);
    assert arrayIsLogDistribution(logMixtureWeights) : "logMixtureWeights not properly initialized.\n";

    //initialize the transpose of the posterior distribution for each example to uniform.
    dataWeights = new double[numComponents][dataSize];
    for(int j = 0; j < numComponents; j++){
      Arrays.fill(dataWeights[j], 1.0/numComponents);
    }
  }

  /**
   *
   * @return get a copy of the current model.
   */
  public MixtureOfLogistics<L,F> getCopy(){
    MixtureOfLogistics<L,F> newMol = new MixtureOfLogistics<L,F>(datasets,maxIter,convergence,priorScale);
    for(int j = 0; j <numComponents; j++){
      System.arraycopy(this.logisticWeights[j], 0, newMol.logisticWeights[j], 0, this.logisticWeights[j].length);
      System.arraycopy(this.dataWeights[j], 0, newMol.dataWeights[j], 0, this.dataWeights[j].length);
    }
    System.arraycopy(this.logMixtureWeights, 0, newMol.logMixtureWeights, 0, this.logMixtureWeights.length);
    return newMol;
  }

  /**
   * verifies in the log domain if the argument array sums to one.
   * @return true iff it sums to one within a tolerance level.
   */
  private static boolean arrayIsLogDistribution(double[] a){
    double tol = 1e-8;
    double norm = ArrayMath.logSum(a);
    if(Math.abs(norm) < tol)
      return true;
    return false;
  }

  /**
   * method to check if:
   * (a) all the datasets are of the same size and
   * (b) each corresponding example across all datasets shares the same label.
   */
  private boolean matchDatasets(){
    int numComp = datasets.size();
    int dataSize = datasets.get(0).size();
    for(int i = 0; i < numComp;i++){
      if(datasets.get(i).size() != dataSize){
        System.err.println("Error: datasets are not of the same size! Quitting.");
        return false;
      }
    }
    for(int i = 0; i < dataSize; i++){
      int label = datasets.get(0).getLabelsArray()[i];
      for(int j = 1; j < numComp; j++){
        int jlabel = datasets.get(j).getLabelsArray()[i];
        if(jlabel != label){
          System.err.println("Error: label mismatch between corresponding examples across components in training data:\n" +
              "Details: for example "+i+", component "+j+ ", label is '"+jlabel+"' whereas it should have been '"+label+"'.");
          return false;
        }
      }
    }
    return true;
  }


  /**
   * method to check if:
   * (a) all the data collections are of the same size and
   * (b) each corresponding example across all data collections shares the same label.
   */
  private boolean matchCollections(List<DataCollection<L,F>> dataCollections){
    int numComp = dataCollections.size();
    if(numComp != numComponents){
      System.err.println("Error: numComponents in testCollection does not match with training set! Quitting.");
      return false;
    }
    int testDataSize = dataCollections.get(0).size();
    for(int j = 0; j < numComp;j++){
      if(dataCollections.get(j).size() != testDataSize){
        System.err.println("Error: test dataCollections are not of the same size! Quitting.");
        return false;
      }
    }
    for(int i = 0; i < testDataSize; i++){
      L label = dataCollections.get(0).labels().get(i);
      int labelId = labelIndex.indexOf(label);
      for(int j = 1; j < numComp; j++){
        L jlabel = dataCollections.get(j).labels().get(i);
        int jlabelId = labelIndex.indexOf(jlabel);
        if(jlabelId != labelId){
          System.err.println("Error: label mismatch between corresponding examples across components in test data:\n" +
              "Details: for example "+i+", component "+j+ ", label is '"+jlabel+"' whereas it should have been '"+label+"'.");
          return false;
        }
      }
    }
    return true;
  }

  public void printAll(){
    printPrior();
    printPosterior();
    printLogisticWeights();
    printLabels();
  }

  public void printPosterior(){
    System.out.println("*** PRINTING posterior distribution for each example row wise");
    for(int i = 0; i < dataSize; i++){
      for(int j = 0; j < numComponents; j++){
        System.out.printf("%f ",dataWeights[j][i]);
      }
      System.out.printf("\n");
    }
  }

  public void printLogisticWeights(){
    System.out.println("*** PRINTING logistic for each component row wise");
    for(int j = 0; j < numComponents; j++){
      for(int k = 0; k < logisticWeights[j].length; k++){
        System.out.printf("%s:%f ",datasets.get(j).featureIndex.get(k),logisticWeights[j][k]);
      }
      System.out.printf("\n");
    }
  }

  public void printPrior(){
    System.out.println("*** PRINTING prior distribution");
    for(int j = 0; j < numComponents; j++)
      System.out.printf("%f ",Math.exp(logMixtureWeights[j]));
    System.out.printf("\n");
  }

  public String priorToString(){
    String priorString = "Prior distribution:\n";
    for(int j = 0; j < numComponents; j++)
      priorString = priorString.concat(Math.exp(logMixtureWeights[j])+" ");
    priorString = priorString.concat("\n");
    return priorString;
  }

  public void printLabels(){
    System.out.println("*** PRINTING labels:");
    for(int k = 0; k < labelIndex.size(); k++){
      System.out.printf("%d:%s ", k,labelIndex.get(k));
    }
    System.out.printf("\n");
  }

  public static void main(String[] args) throws IOException{
    //read properties
    Properties prop = StringUtils.argsToProperties(args);
    String trainFile = prop.getProperty("train");
    String testFile = prop.getProperty("test");
    int numComponents = Integer.parseInt(prop.getProperty("numComponents"));
    int maxIter = Integer.parseInt(prop.getProperty("maxIter"));
    double convergence = Double.parseDouble(prop.getProperty("convergence"));
    double priorScale = Double.parseDouble(prop.getProperty("priorScale"));
    //System.out.println("trainFile:"+trainFile+" testFile:"+testFile+
      //  "\nnumComp:"+numComponents+" maxIter:"+maxIter+" converged:"+convergence);
    //read training data
    List<GeneralDataset<String,String>> datasets = new ArrayList<GeneralDataset<String,String>>();
    for(int j = 0; j < numComponents; j++){
      String compTrainFile = trainFile+"_"+j+".txt";
      //System.out.println("reading trainfile:"+compTrainFile);
      GeneralDataset<String,String> dataset = RVFDataset.readSVMLightFormat(compTrainFile);
      datasets.add(dataset);
    }

    //read test data
    List<DataCollection<String,String>> dataCollections = new ArrayList<DataCollection<String,String>>();
    for(int j = 0; j < numComponents; j++){
      DataCollection<String,String> dataCollection = BasicDataCollection.readSVMLightFormat(testFile+"_"+j+".txt");
      dataCollections.add(dataCollection);
    }

    //train the classifier
    MixtureOfLogistics<String,String> mol = new MixtureOfLogistics<String,String>(datasets,maxIter,convergence,priorScale);
    mol.runEMCrossValidation(maxIter,true,1.0);// 1 implies no tempering, standard EM.
    System.out.println("Finished training!");

    //test the classifier
    double accuracy = mol.test(dataCollections,1.0);
    System.out.printf("Accuracy = %2.2f\n",accuracy);

    //print model parameters
    mol.printAll();
  }
}


