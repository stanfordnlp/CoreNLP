package edu.stanford.nlp.math;

/**
 * The class {@code ADMath} was created to extend the
 * current calculations of gradient to automatically include a calculation of the
 * hessian vector product with another vector {@code v}.  It contains all the functions
 * for the DoubleAlgorithmicDifferentiation class. This is used with
 * Stochastic Meta Descent Optimization, but could be extended for use in any application
 * that requires an additional order of differentiation without explicitly creating the code.
 *
 * @author Alex Kleeman
 * @version 2006/12/06
 */

public class ADMath {

  private ADMath() {} // static methods

  public static DoubleAD mult(DoubleAD a,DoubleAD b){
    DoubleAD c = new DoubleAD();
    c.setval( a.getval() * b.getval() );
    c.setdot(a.getdot()*b.getval() + b.getdot()*a.getval());
    return c;
  }

  public static DoubleAD multConst(DoubleAD a,double b){
    DoubleAD c = new DoubleAD();
    c.setval( a.getval() * b );
    c.setdot(a.getdot()*b);
    return c;
  }


  public static DoubleAD divide(DoubleAD a, DoubleAD b){
    DoubleAD c = new DoubleAD();
    c.setval(a.getval()/b.getval());
    c.setdot( (a.getdot()/b.getval()) - a.getval()*b.getdot() / (b.getval()*b.getval()) );
    return c;
  }

  public static DoubleAD divideConst(DoubleAD a, double b){
    DoubleAD c = new DoubleAD();
    c.setval(a.getval()/b);
    c.setdot( a.getdot()/b  );
    return c;
  }


  public static  DoubleAD exp(DoubleAD a){
    DoubleAD c = new DoubleAD();
    c.setval( Math.exp(a.getval()));
    c.setdot(a.getdot() * Math.exp(a.getval()));
    return c;
  }

  public static  DoubleAD log(DoubleAD a){
    DoubleAD c = new DoubleAD();
    c.setval( Math.log(a.getval()));
    c.setdot( a.getdot()/a.getval());
    return c;
  }

  public static  DoubleAD plus(DoubleAD a, DoubleAD b){
    DoubleAD c = new DoubleAD();
    c.setval( a.getval() + b.getval() );
    c.setdot( a.getdot() + b.getdot());
    return c;
  }

  public static  DoubleAD plusConst( DoubleAD a,double b){
    DoubleAD c = new DoubleAD();
    c.setval(  a.getval() + b );
    c.setdot( a.getdot());
    return c;
  }


  public static  DoubleAD minus(DoubleAD a, DoubleAD b){
    DoubleAD c = new DoubleAD();
    c.setval( a.getval() - b.getval() );
    c.setdot( a.getdot() - b.getdot());
    return c;
  }

  public static  DoubleAD minusConst( DoubleAD a,double b){
    DoubleAD c = new DoubleAD();
    c.setval(  a.getval() - b );
    c.setdot( a.getdot());
    return c;
  }


  public static DoubleAD logSum(DoubleAD[] logInputs) {
    return logSum(logInputs,0,logInputs.length);
  }

  // Some of this might need to change for optimal AD


  public static DoubleAD logSum(DoubleAD[] logInputs, int fromIndex, int toIndex) {
    if (logInputs.length == 0)
      throw new IllegalArgumentException();
    if(fromIndex >= 0 && toIndex < logInputs.length && fromIndex >= toIndex)
      return new DoubleAD(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);
    int maxIdx = fromIndex;
    double max = logInputs[fromIndex].getval();
    double maxdot = logInputs[fromIndex].getdot();

    for (int i = fromIndex+1; i < toIndex; i++) {
      if (logInputs[i].getval() > max) {
        maxIdx = i;
        maxdot = logInputs[i].getdot();
        max = logInputs[i].getval();
      }
    }

    DoubleAD ret = new DoubleAD();
    boolean haveTerms = false;
    double intermediate = 0.0;
    double intermediateDot = 0.0;
    double cutoff = max - SloppyMath.LOGTOLERANCE;
    // we avoid rearranging the array and so test indices each time!
    for (int i = fromIndex; i < toIndex; i++) {
      if (i != maxIdx && logInputs[i].getval() > cutoff) {
        haveTerms = true;
        double curEXP = Math.exp(logInputs[i].getval() - max);
        intermediate += curEXP;
        intermediateDot += curEXP*logInputs[i].getdot();
      }
    }
    if (haveTerms) {
      ret.setval(max + Math.log(1.0 + intermediate));
      ret.setdot((maxdot + intermediateDot)/(1.0 + intermediate));
    } else {
      ret.setval(max);
      ret.setdot(maxdot);
    }
    return ret;
  }

}
