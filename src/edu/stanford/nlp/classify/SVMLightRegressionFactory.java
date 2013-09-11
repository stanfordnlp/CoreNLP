package edu.stanford.nlp.classify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.SystemUtils;

public class SVMLightRegressionFactory<F> implements RegressionFactory<F> {
  private String learnCommand;
  private String classifyCommand;
  private Double cost;
  private KernelType kernelType;
  private Integer degree;
  private Double gamma;
  private File modelFile = null;
  
  
  
  public enum KernelType {
    Linear(0),
    Polynomial(1),
    RBF(2),
    Sigmoid(3);
    
    private int value;

    KernelType(int value) {
      this.value = value;
    }
    
    public int getValue() {
      return this.value;
    }
  }

  public SVMLightRegressionFactory(String learnCommand, String classifyCommand) {
    this.learnCommand = learnCommand;
    this.classifyCommand = classifyCommand;
  }
  
  public SVMLightRegressionFactory() {
    this("svm_learn", "svm_classify");
  }
  
  public SVMLightRegressionFactory(String svmLightPath) {
    this(svmLightPath+"/svm_learn", svmLightPath+"/svm_classify");
  }
  
  /**
   * Set C, the cost of misclassification.
   * 
   * @param cost The new C value. 
   */
  public void setCost(Double cost) {
    this.cost = cost; 
  }
  
  /**
   * Set the type of kernel to be used.
   * 
   * @param kernelType The new kernel type.
   */
  public void setKernelType(KernelType kernelType) {
    this.kernelType = kernelType;
  }
  
  /**
   * Set the degree of the polynomial kernel. Ignored for other kernels.
   * 
   * @param degree The polynomial degree.
   */
  public void setDegree(Integer degree) {
    this.degree = degree;
  }
  
  /**
   * Set the gamma parameter of the RBF kernel. Ignored for other kernels.
   * 
   * @param gamma The polynomial degree.
   */
  public void setGamma(Double gamma) {
    this.gamma = gamma;
  }
  
  /**
   * Train an SVM-light regression, writing the model to the given file.
   * 
   * @param data      The data on which the SVM should be trained.
   * @param modelFile The location where the model should be written.
   * @return          The learned Classifier.
   */
  public Regressor<F> train(GeneralDataset<Double, F> data, File modelFile) {
    // write the training data to a file
    File trainFile;
    try {
      trainFile = File.createTempFile("svm-light-learn", ".input");
      SVMLightRegression.writeSVMLightFormat(data, trainFile);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    
    // assemble the SVM-light learn command and run it
    try {
      List<String> command = new ArrayList<String>();
      command.add(this.learnCommand);
      // regression, not classification
      command.addAll(Arrays.asList("-z", "r"));
      // not verbose
      command.addAll(Arrays.asList("-v", "0"));
      // cost of misclassification, if specified
      if (this.cost != null) {
        command.addAll(Arrays.asList("-c", String.valueOf(this.cost)));
      }
      // kernel type
      if (this.kernelType != null) {
        int value = this.kernelType.getValue();
        command.addAll(Arrays.asList("-t", String.valueOf(value)));
      }
      // kernel parameters, if specified
      if (this.degree != null) {
        command.addAll(Arrays.asList("-d", String.valueOf(this.degree)));
      }
      if (this.gamma != null) {
        command.addAll(Arrays.asList("-g", String.valueOf(this.gamma)));
      }
      // train and mode files
      command.add(trainFile.getPath());
      command.add(modelFile.getPath());
      // execute command
      SystemUtils.run(new ProcessBuilder(command));
    }
    
    // clean up temporary train file
    finally {
      trainFile.delete();
    }
    return new SVMLightRegression<F>(modelFile, data.featureIndex(), this.classifyCommand);
  }

  public void setModelFile(File file){
  	this.modelFile = file;
  }
  
	public Regressor<F> train(GeneralDataset<Double, F> dataset) {				
			return train(dataset,modelFile);
  }

}
