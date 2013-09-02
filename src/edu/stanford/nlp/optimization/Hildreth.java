package edu.stanford.nlp.optimization;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;

/**
 * Hildreth quadratic programming solver
 *
 * This code can solve any optimization problem of the form:
 *
 * max_x x'Cx + d'x
 *
 * subject to Gx <= h
 *
 * Procedure:
 *
 * Let: A  = (-1/4) G C^-1 G'
 *      b' = (1/2)  d 'C^-1 G' + h
 *
 * Solve min_z = z' A z + b'z
 *
 * subject to z >= 0
 *
 * Solved with really simple Gauss-Seidel like solver with
 * clipping of components of z at 0 after each update
 *
 * After solving the above, recover x with
 *
 * x = 1/2 C^-1(G'z - d)
 *
 * The procedure is described in Clifford Hildreth's
 * A quadratic programming procedure in Naval
 * Research Logistics Quarterly 4(1) 1957
 *
 * There is additional discussion in and generalization to arbitrary
 * convex functions with continuous derivatives in D.A. D'Esopo paper
 * A convex programming procedure Naval Research Logistics Quarterly 6(1) 1959
 *
 * However, the current implementation comes directly from Hildreth.
 *
 * Notes: This is an absolutely ancient algorithm that is still popular
 * within the machine learning community for solving
 * quadratic programming (QP) problems. It's continued usage is in
 * no-doubt due to the extreme simplicity of the algorithm.
 *
 * Variants of the procedure are used by SVMLight's default QP
 * solver and the MIRA implementation packaged with the MSTParser.
 *
 * @author danielcer (http://dmcer.net)
 *
 */
public class Hildreth {
    public static final double DEFAULT_EPSILON = 1e-6;
    public static final int    DEFAULT_MAX_ITERATIONS = 100000;
    public static final boolean VERBOSE = true;

    final double epsilon;
    final int maxIterations;

    public Hildreth() {
      epsilon = DEFAULT_EPSILON;
      maxIterations = DEFAULT_MAX_ITERATIONS;
    }

    public Hildreth(double epsilon) {
      this.epsilon = epsilon;
      maxIterations = DEFAULT_MAX_ITERATIONS;
    }

    public Hildreth(double epsilon, int maxIterations) {
      this.epsilon = epsilon;
      this.maxIterations = maxIterations;
    }

    public static String matrixToPrettyString(RealMatrix m) {
      return m.toString().replaceAll("\\{\\{", "\n").replaceAll("\\}\\}", "\n").replaceAll("\\},\\{", "\n").replaceAll(",", "\t").replaceAll("OpenMapRealMatrix", "");
    }

    /**
     * Solve the quadratic programming problem argmax_x x C x + d subject to G x <= h
     *
     * Optimization/gotcha note - Be sure to pass in C^-1 for invC, rather than the actual matrix C.
     * This are done this way since sometimes it's easy to construct C^-1 directly. In these cases
     * we don't want to do all the work of inverting a matrix if we don't have to.
     *
     * @param invC
     * @param d
     * @param G
     * @param h
     */
    public RealVector solve(RealMatrix invC, RealVector d, RealMatrix G, RealVector h) {
      if (VERBOSE) {
        System.out.println("Constructing Gt");
      }
      RealMatrix Gt = G.transpose();
      // A  = (-1/4) G C^-1 G'
      if (VERBOSE) {
        System.out.println("Constructing A");
      }

      if (VERBOSE) {
        System.out.println("G invC:");
        System.out.println(matrixToPrettyString(G.multiply(invC)));

        System.out.println("Gt");
        System.out.println(matrixToPrettyString(Gt) );
      }
      RealMatrix A = G.multiply(invC).multiply(Gt).scalarMultiply(-1./4);
      //  b' = (1/2)  d 'C^-1 G' + h
      if (VERBOSE) {
        System.out.println("Constructing b");
      }
      RealVector bt = Gt.preMultiply(invC.preMultiply(d)).mapMultiply(1./2).add(h);


      int numRows = A.getRowDimension();
      RealVector z = new ArrayRealVector(A.getColumnDimension());

      if (VERBOSE) {
        System.out.println("\nSolving Dual:");
        System.out.printf("\targmin_z z A z + b z\n");
        System.out.printf("A:\n%s\n", matrixToPrettyString(A));
        System.out.printf("b:\n%s\n", bt);
        System.out.println("\tSubject to z_i >= 0 for all i");
      }
      double lastObj = Double.POSITIVE_INFINITY;
      System.out.println("Optimizing QP");
      for (int iter = 0; iter < maxIterations; iter++) {
        double obj = A.preMultiply(z).dotProduct(z) + bt.dotProduct(z);
        double objDiff = lastObj - obj;

        System.out.printf("iter %d objective value %e (diff: %e, diff/obj: %e)\n", iter, obj, objDiff, objDiff/Math.abs(obj));

        if (objDiff/Math.abs(obj) < epsilon) {
          break;
        }
        lastObj = obj;
        for (int i = 0; i < numRows; i++) {
          double Aii = A.getEntry(i, i);
          double Ai = A.getRowVector(i).dotProduct(z);
          double bi = bt.getEntry(i);
          if (Ai == bi/2) continue;
          if (Aii == 0) {
            // throw new RuntimeException("Diagonal of A contains zeros: " + matrixToPrettyString(A));
            continue;
          }
          double wi = -(1/Aii)*(Ai -  Aii* z.getEntry(i) + bi/2);
          z.setEntry(i, Math.max(0, wi));
        }
      }
      RealVector x = invC.scalarMultiply(1./2).operate(Gt.operate(z).subtract(d));
      return x;
   }

   public static RealMatrix readMatrix(String filename) throws IOException {
     BufferedReader br = new BufferedReader(new FileReader(filename));
     List<List<Double>> rawValues = new ArrayList<List<Double>>();
     int rowLen = -1;
     for (String line = br.readLine(); line != null; line = br.readLine()) {
        String[] fields = line.split("\\s+");
        if (rowLen == -1) {
          rowLen = fields.length;
        } else if (rowLen != fields.length) {
          throw new RuntimeException("Expected "+rowLen+" columns, but read "+fields.length);
        }
        List<Double> row = new ArrayList<Double>(fields.length);
        for (String field : fields) {
          row.add(Double.parseDouble(field));
        }
        rawValues.add(row);
     }

     RealMatrix m = new Array2DRowRealMatrix(rawValues.size(), rowLen);
     for (int i = 0; i < m.getRowDimension(); i++) {
       for (int j = 0; j < m.getColumnDimension(); j++) {
          m.setEntry(i, j, rawValues.get(i).get(j));
       }
     }
     return m;
   }

   public static RealVector readVector(String filename) throws IOException {
     BufferedReader br = new BufferedReader(new FileReader(filename));
     List<Double> values = new ArrayList<Double>();
     for (String line = br.readLine(); line != null; line = br.readLine()) {
        values.add(Double.parseDouble(line));
     }
     RealVector v = new ArrayRealVector(values.size());

     for (int i = 0; i < v.getDimension(); i++) {
       v.setEntry(i, values.get(i));
     }
     return v;
   }

   public static void main(String[] args) throws IOException {
     if (args.length != 4) {
       System.err.println("Usage:\n\tjava ...Hildreth Cfn dfn Gfn hfn");
       System.err.println("\nSolves: ");
       System.err.println("\targmax x' C x + x d");
       System.err.println("\tsubject to G x <= h");
       System.exit(-1);
     }
     RealMatrix C = readMatrix(args[0]);
     RealVector d = readVector(args[1]);
     RealMatrix G = readMatrix(args[2]);
     RealVector h = readVector(args[3]);
     Hildreth hildreth = new Hildreth();
     RealMatrix invC = new LUDecompositionImpl(C).getSolver().getInverse();

     System.out.println("Solving:\n");
     System.out.printf("\targmax_x x' %s x + x %s\n", C, d);
     System.out.printf("\tsubject to %s x <= %s\n", G, h);
     System.out.printf("\ninvC: %s\n", invC);

     RealVector x = hildreth.solve(invC, d, G, h);
     double obj = C.preMultiply(x).dotProduct(x) + x.dotProduct(d);
     RealVector constViolations = G.operate(x).subtract(h);
     System.out.printf("\nSolution: %s\n", x);
     System.out.printf("Value x' C x + x d: %e\n", obj);
     System.out.printf("Constraint Violations\n");
     for (int i = 0; i < constViolations.getDimension(); i++) {
       System.out.printf("\t%s x <= %e violation: %e [unclipped %e]\n", Arrays.toString(G.getRow(i)), h.getEntry(i), Math.max(0, constViolations.getEntry(i)), constViolations.getEntry(i));
     }
   }
}
