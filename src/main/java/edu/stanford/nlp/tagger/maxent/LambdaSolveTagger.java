package edu.stanford.nlp.tagger.maxent; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.maxent.Feature;
import edu.stanford.nlp.maxent.Problem;
import edu.stanford.nlp.maxent.iis.LambdaSolve;

import java.text.NumberFormat;
import java.io.DataInputStream;


/**
 * This module does the working out of lambda parameters for binary tagger
 * features.  It can use either IIS or CG.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class LambdaSolveTagger extends LambdaSolve  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LambdaSolveTagger.class);

  /**
   * Suppress extraneous printouts
   */
  //@SuppressWarnings("unused")
  //private static final boolean VERBOSE = false;


  LambdaSolveTagger(Problem p1, double eps1, byte[][] fnumArr) {
    p = p1;
    eps = eps1;
    // newtonerr = nerr1;
    lambda = new double[p1.fSize];
    // lambda_converged = new boolean[p1.fSize];
    // cdm 2008: Below line is memory hog. Is there anything we can do to avoid this square array allocation?
    probConds = new double[p1.data.xSize][p1.data.ySize];
    this.fnumArr = fnumArr;
    zlambda = new double[p1.data.xSize];
    ftildeArr = new double[p.fSize];
    initCondsZlambdaEtc();
    super.setBinary();
  }


  /* Unused.
  @SuppressWarnings({"UnusedDeclaration"})
  private void readOldLambdas(String filename, String oldfilename) {
    double[] lambdaold;
    lambdaold = read_lambdas(oldfilename);
    HashMap<FeatureKey,Integer> oldAssocs = GlobalHolder.readAssociations(oldfilename);
    HashMap<FeatureKey,Integer> newAssocs = GlobalHolder.readAssociations(filename);
    for (FeatureKey fk : oldAssocs.keySet()) {
      int numOld = GlobalHolder.getNum(fk, oldAssocs);
      int numNew = GlobalHolder.getNum(fk, newAssocs);
      if ((numOld > -1) && (numNew > -1)) {
        lambda[numNew] = lambdaold[numOld];
        updateConds(numNew, lambdaold[numOld]);
      }
    }
  }
  */

  /* --- unused
  LambdaSolveTagger(String filename) {
    this.readL(filename);
    super.setBinary();
  }
  --- */


  /** Initialize a trained LambdaSolveTagger.
   *  This is the version used when loading a saved tagger.
   *  Only the lambda array is used, and the rest is irrelevant, CDM thinks.
   *
   *  @param dataStream Stream to load lambda parameters from.
   */
  LambdaSolveTagger(DataInputStream dataStream) {
    lambda = read_lambdas(dataStream);
    super.setBinary();
  }

  /** Initialize a trained LambdaSolveTagger.
   *  This is the version used when creating a LambdaSolveTagger from
   *  a condensed lambda array.
   *  Only the lambda array is used, and the rest is irrelevant, CDM thinks.
   *
   *  @param lambda Array used as the lambda parameters (directly; no safety copy is made).
   */
  LambdaSolveTagger(double[] lambda) {
    this.lambda = lambda;
    super.setBinary();
  }

  private void initCondsZlambdaEtc() {
    // init pcond
    for (int x = 0; x < p.data.xSize; x++) {
      for (int y = 0; y < p.data.ySize; y++) {
        probConds[x][y] = 1.0 / p.data.ySize;
      }
    }
    log.info(" pcond initialized ");
    // init zlambda
    for (int x = 0; x < p.data.xSize; x++) {
      zlambda[x] = p.data.ySize;
    }
    log.info(" zlambda initialized ");
    // init ftildeArr
    for (int i = 0; i < p.fSize; i++) {
      ftildeArr[i] = p.functions.get(i).ftilde();
      if (ftildeArr[i] == 0) {
        log.info(" Empirical expectation 0 for feature " + i);
      }
    }
    log.info(" ftildeArr initialized ");
  }


  /* --- unused
   *
   * Iteration for lambda[index].
   *
   * @return true if this lambda hasn't converged.
   *
  boolean iterate(int index, double err, MutableDouble ret) {
    double deltaL = 0.0;
    deltaL = newton(deltaL, index, err);
    lambda[index] = lambda[index] + deltaL;
    if (!(deltaL == deltaL)) {
      log.info(" NaN " + index + ' ' + deltaL);
    }
    ret.set(deltaL);
    return (Math.abs(deltaL) >= eps);
  }
  --- */

  /* --- unused:
   *
   * Finds the root of an equation by Newton's method. This is my
   * implementation. It might be improved if we looked at some official
   * library for numerical methods.
   *
  double newton(double lambda0, int index, double err) {
    double lambdaN = lambda0;
    int i = 0;
    do {
      i++;
      double lambdaP = lambdaN;
      double gPrimeVal = gprime(lambdaP, index);
      if (!(gPrimeVal == gPrimeVal)) {
        log.info("gPrime of " + lambdaP + ' ' + index + " is NaN " + gPrimeVal);
      }
      double gVal = g(lambdaP, index);
      if (gPrimeVal == 0.0) {
        return 0.0;
      }
      lambdaN = lambdaP - gVal / gPrimeVal;
      if (!(lambdaN == lambdaN)) {
        log.info("the division of " + gVal + ' ' + gPrimeVal + ' ' + index + " is NaN " + lambdaN);
        return 0;
      }
      if (Math.abs(lambdaN - lambdaP) < err) {
        return lambdaN;
      }
      if (i > 100) {
        if (Math.abs(gVal) > 1) {
          return 0;
        }
        return lambdaN;
      }
    } while (true);
  }
  --- */

 /* --- unused:
   *
   * This method updates the conditional probabilities in the model, resulting from the
   * update of lambda[index] to lambda[index]+deltaL .
   *
  void updateConds(int index, double deltaL) {
    //  for each x that (x,y)=true / exists y
    //  recalculate pcond(y,x) for all y
    int yTag = ((TaggerFeature) (p.functions.get(index))).getYTag();
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      // update for this x
      double s = 0;
      int x = (p.functions.get(index)).getX(i);
      double zlambdaX = zlambda[x] + pcond(yTag, x) * zlambda[x] * (Math.exp(deltaL) - 1);
      for (int y = 0; y < p.data.ySize; y++) {
        probConds[x][y] = (probConds[x][y] * zlambda[x]) / zlambdaX;
        s = s + probConds[x][y];
      }
      s = s - probConds[x][yTag];
      probConds[x][yTag] = probConds[x][yTag] * Math.exp(deltaL);
      s = s + probConds[x][yTag];
      zlambda[x] = zlambdaX;
    }
  }
  --- */

  /* --- unused:
  double pcondCalc(int y, int x) {
    double zlambdaX;
    zlambdaX = 0.0;
    for (int y1 = 0; y1 < p.data.ySize; y1++) {
      double s = 0.0;
      for (int i = 0; i < p.fSize; i++) {
        s = s + lambda[i] * p.functions.get(i).getVal(x, y1);
      }
      zlambdaX = zlambdaX + Math.exp(s);
    }
    double s = 0.0;
    for (int i = 0; i < p.fSize; i++) {
      s = s + lambda[i] * p.functions.get(i).getVal(x, y);
    }
    return (1 / zlambdaX) * Math.exp(s);
  }


  double fnumCalc(int x, int y) {
    double s = 0.0;
    for (int i = 0; i < p.fSize; i++) {
      //this is slow
      s = s + p.functions.get(i).getVal(x, y);
    }
    return s;
  }
  --- */

  double g(double lambdaP, int index) {
    double s = 0.0;
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = ((TaggerFeature) p.functions.get(index)).getYTag();
      int x = (p.functions.get(index)).getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x) * 1 * Math.exp(lambdaP * fnum(x, y));
    }
    s = s - ftildeArr[index];

    return s;
  }

  /* --- unused
  double gprime(double lambdaP, int index) {
    double s = 0.0;
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = ((TaggerFeature) (p.functions.get(index))).getYTag();
      int x = (p.functions.get(index)).getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x) * 1 * Math.exp(lambdaP * fnum(x, y)) * fnum(x, y);
    }
    return s;
  }
  --- */

  double fExpected(Feature f) {
    TaggerFeature tF = (TaggerFeature) f;
    double s = 0.0;
    int y = tF.getYTag();
    for (int i = 0; i < f.len(); i++) {
      int x = tF.getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x);
    }
    return s;
  }


  /** Works out whether the model expectations match the empirical
   *  expectations.
   *  @return Whether the model is correct
   */
  @Override
  public boolean checkCorrectness() {
    log.info("Checking model correctness; x size " + p.data.xSize + ' ' + ", ysize " + p.data.ySize);

    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(4);
    boolean flag = true;
    for (int f = 0; f < lambda.length; f++) {
      if (Math.abs(lambda[f]) > 100) {
        log.info(" Lambda too big " + lambda[f]);
        log.info(" empirical " + ftildeArr[f] + " expected " + fExpected(p.functions.get(f)));
      }
    }

    for (int i = 0; i < ftildeArr.length; i++) {
      double exp = Math.abs(ftildeArr[i] - fExpected(p.functions.get(i)));
      if (exp > 0.001) {
        flag = false;
        log.info("Constraint " + i + " not satisfied emp " + nf.format(ftildeArr[i]) + " exp " + nf.format(fExpected(p.functions.get(i))) + " diff " + nf.format(exp) + " lambda " + nf.format(lambda[i]));
      }
    }
    for (int x = 0; x < p.data.xSize; x++) {
      double s = 0.0;
      for (int y = 0; y < p.data.ySize; y++) {
        s = s + probConds[x][y];
      }
      if (Math.abs(s - 1) > 0.0001) {
        for (int y = 0; y < p.data.ySize; y++) {
          log.info(y + " : " + probConds[x][y]);
        }
        log.info("probabilities do not sum to one " + x + ' ' + (float) s);
      }
    }
    return flag;
  }

  /* --- unused
  double ZAlfa(double alfa, Feature f, int x) {
    double s = 0.0;
    for (int y = 0; y < p.data.ySize; y++) {
      s = s + pcond(y, x) * Math.exp(alfa * f.getVal(x, y));
    }
    return s;
  }
  --- */

  /* ---
  private static double[] read_lambdas(String modelFilename) {
    if (VERBOSE) {
      log.info(" entering read");
    }
    try {
      double[] lambdaold;
//      InDataStreamFile rf=new InDataStreamFile(modelFilename+".holder.prob");
//      int xSize=rf.readInt();
//      int ySize=rf.readInt();
//      if (VERBOSE) log.info("x y "+xSize+" "+ySize);
//      //rf.seek(rf.getFilePointer()+xSize*ySize*8);
//      int funsize=rf.readInt();
//      lambdaold=new double[funsize];
//      byte[] b=new byte[funsize*8];
//      rf.read(b);
//      lambdaold=Convert.byteArrToDoubleArr(b);
//      rf.close();
      DataInputStream dis = new DataInputStream(new FileInputStream(modelFilename + ".holder.prob"));
      int xSize = dis.readInt();
      int ySize = dis.readInt();
      if (VERBOSE) {
        log.info("x y " + xSize + ' ' + ySize);
      }
      int funsize = dis.readInt();
      byte[] b = new byte[funsize * 8];
      if (dis.read(b) != funsize * 8) { log.info("Rewrite read_lambdas!"); }
      lambdaold = Convert.byteArrToDoubleArr(b);
      dis.close();
      return lambdaold;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
  --- */

}

