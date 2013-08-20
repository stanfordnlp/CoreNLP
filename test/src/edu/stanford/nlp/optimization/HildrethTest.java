package edu.stanford.nlp.optimization;

import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;

import junit.framework.TestCase;

/**
 * Test Hildreth implementation with some simple constrained 
 * quadratic programming problems
 *  
 * @author daniel cer (http://dmcer.net)
 *
 */
public class HildrethTest extends TestCase {
  public void test1DInactiveConstraint() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1.}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4});
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1.}});
    RealVector h = new ArrayRealVector(new double[]{10});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);
    assertEquals(2., x.getEntry(0));
  }
  
  public void test1DActiveConstraint() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1.}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4});
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1.}});
    RealVector h = new ArrayRealVector(new double[]{-2.5});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);
    assertEquals(2.5, x.getEntry(0));
  }
  
  public void test2DInactiveConstraints() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4, 6});
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealVector h = new ArrayRealVector(new double[]{10,1});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);
    assertEquals(2., x.getEntry(0));
    assertEquals(3., x.getEntry(1));
    System.out.println("2  3");
  }
  
  public void test2DOneInactiveConstraintOneActiveConstraint() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4, 6});
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealVector h = new ArrayRealVector(new double[]{10,-3.1});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);
    assertEquals(2., x.getEntry(0));
    assertEquals(3.1, x.getEntry(1));
  }
  
  public void test2DTwoActiveConstraints() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4, 6});
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealVector h = new ArrayRealVector(new double[]{-100000,-3.1});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);
    assertEquals(100000., x.getEntry(0));
    assertEquals(3.1, x.getEntry(1));
  }
  
  public void test2DEqualityConstraintAsTwoInequalityConstraints() {    
    RealMatrix C = new Array2DRowRealMatrix(new double[][]{{-1., 0}, {0, -1}});
    RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();
    RealVector d = new ArrayRealVector(new double[]{4, 6});
    // constraint 1  x1 <= x2
    // constraint 2  x2 <= x1
    RealMatrix G = new Array2DRowRealMatrix(new double[][]{{-1, 1}, {1, -1}});
    RealVector h = new ArrayRealVector(new double[]{0,0});
    Hildreth hildreth = new Hildreth();
    RealVector x = hildreth.solve(invC, d, G, h);    
    assertEquals(2.5, x.getEntry(0));
    assertEquals(2.5, x.getEntry(1));
  }
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("edu.stanford.nlp.optimization.HildrethTest");
  }
}
