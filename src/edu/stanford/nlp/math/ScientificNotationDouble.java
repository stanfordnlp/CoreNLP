package edu.stanford.nlp.math;

import java.util.StringTokenizer;

/**
 * ScientificNotationDouble allows very small and very large numbers to be multiplied without causing a floating point underflow/overflow.
 * has a base (which is a double x s.t. 1<=x<10), and an exponent such that the double value of ScientificNotationDouble is base * 10^exponent
 * note: ScientificNotationDouble does not handle negative numbers.  this is to increase the speed.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */

public class ScientificNotationDouble extends Number {
  /**
   * 
   */
  private static final long serialVersionUID = 3920695442203179910L;
  private double base;
  private int exponent;

  /**
   * creates a new ScientificNotationDouble with value 0
   */

  public ScientificNotationDouble() {
    base = 0;
    exponent = 0;
  }

  /**
   * constructs ScientificNotationDouble from a standard int
   */

  public ScientificNotationDouble(int i) {
    set(i);
    /*
    this();
    if (i!=0) {
      String s=Integer.toString(i);
      int magnitude=s.length();
      exponent=magnitude-1;
      base=(i*Math.pow(10, -exponent));
      }*/
  }

  /**
   * constructs ScientificNotationDouble from a standard double
   */

  public ScientificNotationDouble(double d) {
    set(d);
    /*    this();
    if (d!=0) {
      String s=Double.toString(d);
      
      if (s.indexOf('E')!=-1) {
	StringTokenizer st=new StringTokenizer(s, "E");
	base=Double.parseDouble(st.nextToken());
	exponent=Integer.parseInt(st.nextToken());
      }
      else {
	base=d;
	ensureProperBase();
      }
      }*/
  }

  /**
   * constructs ScientificNotationDouble given a base <code>b</code> and an exponent <code>e</code>
   */

  public ScientificNotationDouble(double b, int e) {
    base = b;
    exponent = e;
  }


  /**
   * constructs ScientificNotationDouble from another ScientificNotationDouble
   */

  public ScientificNotationDouble(ScientificNotationDouble other) {
    base = other.getBase();
    exponent = other.getExponent();
  }

  public double getBase() {
    return base;
  }

  public int getExponent() {
    return exponent;
  }

  public void setBase(double b) {
    base = b;
  }

  public void setExponent(int e) {
    exponent = e;
  }

  public void set(double b, int e) {
    base = b;
    exponent = e;
  }

  /**
   * sets to zero
   */

  public void reset() {
    set(0, 0);
  }

  public void set(double d) {
    reset();
    if (d != 0) {
      String s = Double.toString(d);

      if (s.indexOf('E') != -1) {
        StringTokenizer st = new StringTokenizer(s, "E");
        base = Double.parseDouble(st.nextToken());
        exponent = Integer.parseInt(st.nextToken());
      } else {
        base = d;
        ensureProperBase();
      }
    }
  }

  public void set(int i) {
    reset();
    if (i != 0) {
      String s = Integer.toString(i);
      int magnitude = s.length();
      exponent = magnitude - 1;
      base = (i * Math.pow(10, -exponent));
    }
  }

  /**
   * returns a ScientificNotationdouble whose base is scaled so that it = base * 10^e
   * the base may no longer be >=1 and <10
   * does not change <code>this</code>
   */

  public ScientificNotationDouble scale(int e) {
    int difference = exponent - e;
    double b = base * Math.pow(10, difference);
    return new ScientificNotationDouble(b, e);
  }

  /**
   * ensures that the base of the ScientificNotationDouble is >=1 and <10.
   * used within addition, multiplication, and division routines
   */

  public void ensureProperBase() {

    /*-------------------------------------------------------*/
    /*              check to see if the base is 0            */
    /*-------------------------------------------------------*/

    if (base == 0) {
      base = 0;
      exponent = 0;
    }
    //test code
    else {
      while (base < 1) {
        base = (base * 10);
        exponent -= 1;
      }
      while (base >= 10) {
        base = (base / 10);
        exponent += 1;
      }
    }
  }

  /**
   * returns product of this with multiplicand
   */

  public ScientificNotationDouble multiply(ScientificNotationDouble multiplicand) {
    ScientificNotationDouble product = new ScientificNotationDouble();
    product.setBase(base * multiplicand.getBase());
    product.setExponent(exponent + multiplicand.getExponent());
    product.ensureProperBase();
    return product;
  }

  public ScientificNotationDouble divide(ScientificNotationDouble divisor) {
    ScientificNotationDouble quotient = new ScientificNotationDouble();
    quotient.setBase(base / divisor.getBase());
    quotient.setExponent(exponent - divisor.getExponent());
    quotient.ensureProperBase();
    return quotient;
  }

  /**
   * returns the sum of this and addend
   */

  public ScientificNotationDouble add(ScientificNotationDouble addend) {
    ScientificNotationDouble scaledAddend;
    ScientificNotationDouble sum = new ScientificNotationDouble();
    
    /*-----------------------------------------------------*/
    /*  if one of the addends are 0, return the other one  */
    /*-----------------------------------------------------*/
    if (base == 0) {
      return addend;
    } else if (addend.getBase() == 0) {
      return this;
    }

    if (exponent >= addend.getExponent()) {
      sum.setExponent(exponent);
      scaledAddend = addend.scale(exponent);
      sum.setBase(base + scaledAddend.getBase());
    } else {
      sum.setExponent(addend.getExponent());
      scaledAddend = scale(addend.getExponent());
      sum.setBase(addend.getBase() + scaledAddend.getBase());
    }

    sum.ensureProperBase();
    return sum;
  }

  @Override
  public double doubleValue() {
    double td = base * (Math.pow(10, exponent));
    return td;
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
    if (exponent < -3 || exponent > 3) {
      return base + "E" + exponent;
    }
    return String.valueOf(doubleValue());
  }

  /**
   * set numbers which are smaller than 10^tolerance = 0
   * the tolerance should usually be a large negative number
   */

  public void setSmallToZero(int tolerance) {
    if (exponent < tolerance) {
      base = 0;
      exponent = 0;
    }
  }


}
