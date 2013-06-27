package edu.stanford.nlp.classify;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.ArrayUtils;

import java.io.Serializable;


/**
 * A Prior for functions.  Immutable.
 *
 * @author Galen Andrew
 */
public class LogPrior  implements Serializable {
  
  /**
   * 
   */
  private static final long serialVersionUID = 7826853908892790965L;

  public enum LogPriorType { NULL, QUADRATIC, HUBER, QUARTIC, COSH, ADAPT, MULTIPLE_QUADRATIC }

  public static LogPriorType getType(String name) {
    if (name.equalsIgnoreCase("null")) { return LogPriorType.NULL; }
    else if (name.equalsIgnoreCase("quadratic")) { return LogPriorType.QUADRATIC; }
    else if (name.equalsIgnoreCase("huber")) { return LogPriorType.HUBER; }
    else if (name.equalsIgnoreCase("quartic")) { return LogPriorType.QUARTIC; }
    else if (name.equalsIgnoreCase("cosh")) { return LogPriorType.COSH; }
//    else if (name.equalsIgnoreCase("multiple")) { return LogPriorType.MULTIPLE; }
    else { throw new RuntimeException("Unknown LogPriorType: "+name); }
  }

  // these fields are just for the ADAPT prior -
  // is there a better way to do this?
  private double[] means = null;
  private LogPrior otherPrior = null;

  public static LogPrior getAdaptationPrior(double[] means, LogPrior otherPrior) {
    LogPrior lp = new LogPrior(LogPriorType.ADAPT);
    lp.means = means;
    lp.otherPrior = otherPrior;
    return lp;
  }

  public LogPriorType getType() {
    return type;
  }

  public final LogPriorType type;

  public LogPrior() {
    this(LogPriorType.QUADRATIC);
  }

  public LogPrior(int intPrior) {
    this(intPrior, 1.0, 0.1);
  }

  public LogPrior(LogPriorType type) {
    this(type, 1.0, 0.1);
  }

  // why isn't this functionality in enum?
  private static LogPriorType intToType(int intPrior) {
    LogPriorType[] values = LogPriorType.values();
    for (LogPriorType val : values) {
      if (val.ordinal() == intPrior) {
        return val;
      }
    }
    throw new IllegalArgumentException(intPrior + " is not a legal LogPrior.");
  }

  public LogPrior(int intPrior, double sigma, double epsilon) {
    this(intToType(intPrior), sigma, epsilon);
  }

  public LogPrior(LogPriorType type, double sigma, double epsilon) {
    this.type = type;
    if (type != LogPriorType.ADAPT) {
      setSigma(sigma);
      setEpsilon(epsilon);
    }
  }


  // this is the C variable in CSFoo's MM paper C = 1/\sigma^2
//  private double[] regularizationHyperparameters = null;
  
  private double[] sigmaSqM = null;
  private double[] sigmaQuM = null;
  

//  public double[] getRegularizationHyperparameters() {
//    return regularizationHyperparameters;
//  }
//
//  public void setRegularizationHyperparameters(
//      double[] regularizationHyperparameters) {
//    this.regularizationHyperparameters = regularizationHyperparameters;
//  }

  /**
   * IMPORTANT NOTE: This constructor allows non-uniform regularization, but it
   * transforms the inputs C (like the machine learning people like) to sigma
   * (like we NLP folks like).  C = 1/\sigma^2
   */
  public LogPrior(double[] C) {
    this.type = LogPriorType.MULTIPLE_QUADRATIC;
    double[] sigmaSqM = new double[C.length];
    for (int i=0;i<C.length;i++){
      sigmaSqM[i] = 1./C[i];
    }
    this.sigmaSqM = sigmaSqM;
    setSigmaSquaredM(sigmaSqM);
//    this.regularizationHyperparameters = regularizationHyperparameters;
  }

  
//  private double sigma;
  private double sigmaSq;
  private double sigmaQu;
  private double epsilon;

  public double getSigma() {
    if (type == LogPriorType.ADAPT) {
      return otherPrior.getSigma();
    } else {
      return Math.sqrt(sigmaSq);
    }
  }

  public double getSigmaSquared() {
    if (type == LogPriorType.ADAPT) {
      return otherPrior.getSigmaSquared();
    } else {
      return sigmaSq;
    }
  }

  public double[] getSigmaSquaredM() {
    if (type == LogPriorType.MULTIPLE_QUADRATIC) {
      return sigmaSqM;
    } else {
      throw new RuntimeException("LogPrior.getSigmaSquaredM is undefined for any prior but MULTIPLE_QUADRATIC" + this);
    }
  }  
  
  
  public double getEpsilon() {
    if (type == LogPriorType.ADAPT) {
      return otherPrior.getEpsilon();
    } else {
      return epsilon;
    }
  }

  public void setSigma(double sigma) {
    if (type == LogPriorType.ADAPT) { otherPrior.setSigma(sigma); }
    else {
//    this.sigma = sigma;
      this.sigmaSq = sigma * sigma;
      this.sigmaQu = sigmaSq * sigmaSq;
    }
  }

//  public void setSigmaM(double[] sigmaM) {
//    if (type == LogPriorType.MULTIPLE_QUADRATIC) {
//      //    this.sigma = Math.sqrt(sigmaSq);
//      double[] sigmaSqM = new double[sigmaM.length];
//      double[] sigmaQuM = new double[sigmaM.length];
//      
//      for (int i = 0;i<sigmaM.length;i++){
//        sigmaSqM[i] = sigmaM[i] * sigmaM[i];  
//      }      
//      this.sigmaSqM = sigmaSqM;
//      
//      for (int i = 0;i<sigmaSqM.length;i++){
//        sigmaQuM[i] = sigmaSqM[i] * sigmaSqM[i];  
//      }
//      this.sigmaQuM = sigmaQuM;
//      
//    } else {
//      throw new RuntimeException("LogPrior.getSigmaSquaredM is undefined for any prior but MULTIPLE_QUADRATIC" + this);
//    }      
//  }  
  
  
  public void setSigmaSquared(double sigmaSq) {
    if (type == LogPriorType.ADAPT) { otherPrior.setSigmaSquared(sigmaSq); }
    else {
//    this.sigma = Math.sqrt(sigmaSq);
      this.sigmaSq = sigmaSq;
      this.sigmaQu = sigmaSq * sigmaSq;
    }
  }
  
  public void setSigmaSquaredM(double[] sigmaSq) {
    if (type == LogPriorType.ADAPT) { otherPrior.setSigmaSquaredM(sigmaSq); }
    if (type == LogPriorType.MULTIPLE_QUADRATIC) {
      //    this.sigma = Math.sqrt(sigmaSq);
      this.sigmaSqM = sigmaSq.clone();
      double[] sigmaQuM = new double[sigmaSq.length];
      for (int i = 0;i<sigmaSq.length;i++){
        sigmaQuM[i] = sigmaSqM[i] * sigmaSqM[i];  
      }
      this.sigmaQuM = sigmaQuM;
      
    } else {
      throw new RuntimeException("LogPrior.getSigmaSquaredM is undefined for any prior but MULTIPLE_QUADRATIC" + this);
    }         
  }
    
  public void setEpsilon(double epsilon) {
    if (type == LogPriorType.ADAPT) { otherPrior.setEpsilon(epsilon); }
    else {
      this.epsilon = epsilon;
    }
  }

  public double computeStochastic(double[] x, double[] grad, double fractionOfData) {
    if (type == LogPriorType.ADAPT) {
      double[] newX = ArrayMath.pairwiseSubtract(x, means);
      return otherPrior.computeStochastic(newX, grad, fractionOfData);
    } else if (type == LogPriorType.MULTIPLE_QUADRATIC) {
      
      double[] sigmaSquaredOld = getSigmaSquaredM();
      double[] sigmaSquaredTemp = sigmaSquaredOld.clone(); 
      for (int i = 0; i < x.length; i++) {
        sigmaSquaredTemp[i] /= fractionOfData;
      }
      setSigmaSquaredM(sigmaSquaredTemp);
      
      double val = compute(x, grad);
      setSigmaSquaredM(sigmaSquaredOld);
      return val;
      
    } else {
      double sigmaSquaredOld = getSigmaSquared();
      setSigmaSquared(sigmaSquaredOld / fractionOfData);
      
      double val = compute(x, grad);
      setSigmaSquared(sigmaSquaredOld);
      return val;
    }
  }
  
  /**
   * Adjust the given grad array by adding the prior's gradient component
   * and return the value of the logPrior
   * @param x the input point
   * @param grad the gradient array
   * @return the value
   */
  public double compute(double[] x, double[] grad) {
    
    double val = 0.0;

    switch (type) {
      case NULL:
        return val;

      case QUADRATIC:
        for (int i = 0; i < x.length; i++) {
          val += x[i] * x[i] / 2.0 / sigmaSq;
          grad[i] += x[i] / sigmaSq;
        }
        return val;

      case HUBER:
        // P.J. Huber. 1973. Robust regression: Asymptotics, conjectures and
        // Monte Carlo. The Annals of Statistics 1: 799-821.
        // See also:
        // P. J. Huber. Robust Statistics. John Wiley & Sons, New York, 1981.
        for (int i = 0; i < x.length; i++) {
          if (x[i] < -epsilon) {
            val += (-x[i] - epsilon / 2.0) / sigmaSq;
            grad[i] += -1.0 / sigmaSq;
          } else if (x[i] < epsilon) {
            val += x[i] * x[i] / 2.0 / epsilon / sigmaSq;
            grad[i] += x[i] / epsilon / sigmaSq;
          } else {
            val += (x[i] - epsilon / 2.0) / sigmaSq;
            grad[i] += 1.0 / sigmaSq;
          }
        }
        return val;

      case QUARTIC:
        for (int i = 0; i < x.length; i++) {
          val += (x[i] * x[i]) * (x[i] * x[i]) / 2.0 / sigmaQu;
          grad[i] += x[i] / sigmaQu;
        }
        return val;

      case ADAPT:
        double[] newX = ArrayMath.pairwiseSubtract(x, means);
        val += otherPrior.compute(newX, grad);
        return val;

      case COSH:
        double norm = ArrayMath.norm_1(x) / sigmaSq;
        double d;
        if (norm > 30.0) {
          val = norm - Math.log(2);
          d = 1.0 / sigmaSq;
        } else {
          val = Math.log(Math.cosh(norm));
          d = (2 * (1 / (Math.exp(-2.0 * norm) + 1)) - 1.0) / sigmaSq;
        }
        for (int i=0; i < x.length; i++) {
          grad[i] += Math.signum(x[i]) * d;
        }
        return val;
      case MULTIPLE_QUADRATIC:
//        for (int i = 0; i < x.length; i++) {
//          val += x[i] * x[i]* 1/2 * regularizationHyperparameters[i];
//          grad[i] += x[i] * regularizationHyperparameters[i];
//        }
        
        for (int i = 0; i < x.length; i++) {
          val += x[i] * x[i] / 2.0 / sigmaSqM[i];
          grad[i] += x[i] / sigmaSqM[i];
        }
        
        
        return val;
      default:
        throw new RuntimeException("LogPrior.valueAt is undefined for prior of type " + this);
    }
  }


}
