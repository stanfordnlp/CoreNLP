package edu.stanford.nlp.math;

/**
 * The class {@code DoubleAD} was created to extend the
 * current calculations of gradient to automatically include a calculation of the
 * Hessian vector product with another vector {@code v}.  This is used with the
 * Stochastic Meta Descent Optimization, but could be extended for use in any application
 * that requires an additional order of differentiation without explicitly creating the code.
 *
 * @author Alex Kleeman
 * @version 2006/12/06
 */

public class DoubleAD extends Number {

  private static final long serialVersionUID = -5702334375099248894L;
  private double val;
  private double dot;


  public DoubleAD() {
    setval(0);
    setdot(1);
  }

  public DoubleAD(double initVal, double initDot) {
    val = initVal;
    dot = initDot;
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if ( ! (obj instanceof DoubleAD)) {
      return false;
    }
    DoubleAD b = (DoubleAD) obj;
    return b.getval() == val && b.getdot() == dot;
  }

  public boolean equals(double valToCompare, double dotToCompare) {
    return valToCompare == val && dotToCompare == dot;
  }

  public boolean equals(double valToCompare, double dotToCompare, double TOL) {
    return Math.abs(valToCompare - val) < TOL && Math.abs(dotToCompare - dot) < TOL;
  }

  public double getval(){
    return val;
  }

  public double getdot(){
    return dot;
  }

  public void set(double value, double dotValue){
    val = value;
    dot = dotValue;
  }

  public void setval(double a){
    val = a;
  }

  public void setdot(double a){
    dot = a;
  }

  public void plusEqualsConst(double a){
    setval(val + a);
  }



  public void plusEquals(DoubleAD a){
    setval(val + a.getval() );
    setdot(dot + a.getdot() );
  }

  public void minusEquals(DoubleAD a){
    setval(val - a.getval() );
    setdot(dot - a.getdot() );
  }

  public void minusEqualsConst(double a){
    setval(val - a);
  }


  @Override
  public double doubleValue() {
    return getval();
  }

  @Override
  public float floatValue() {
    return (float) doubleValue();
  }

  @Override
  public int intValue() {
    return (int) doubleValue();
  }

  @Override
  public long longValue() {
    return (long) doubleValue();
  }

  @Override
  public String toString() {
      return "Value= " + val + "; Dot= " + dot;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    temp = Double.doubleToLongBits(val);
    result = (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(dot);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

}
