package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.*;
import java.util.*;

public class OptimalLogLikelihood
{
  static private double t  = 1.0; 
  public static  Pair<double[],Double>  optimalLogLikelihood(final double[] scores,final Object[] labels,final Object positiveLabel)
  {
    Arrays.sort(scores);
    final int n = scores.length;  //number of datums
    final int m = 2 * n + (n-1); // number of constraints
    final boolean[] positiveInstance = new boolean[n];
    
    for (int i=0; i < n; ++i)
    {
      positiveInstance[i] = labels[i].equals(positiveLabel) ;
    }
    
    // Object Function is t * log-likelihood + \sum_i - log(-f_i)  , for each ineq f_i < 0
    DiffFunction df = new AbstractCachingDiffFunction()
      {
        @Override
        public int domainDimension() { return n; }

        @Override
        public void calculate(double[] p)
        {
          value = 0.0;
          System.out.print("\np: ");
          for (int i=0; i < derivative.length; ++i) 
            {
              if (p[i] < 0 || p[i] > 1) 
                {
                  value = Double.POSITIVE_INFINITY;
                  return;
                }
              System.out.print(p[i]+ " ");
            }
          System.out.println();

          for (int i=0; i < p.length; ++i)
          {
            boolean pos = positiveInstance[i];
            value -= t * Math.log( (pos ? p[i] : 1 - p[i]) ); 
            
            // For each constraint f_i < 0 we add - log(-f_i)
            
            // -p_i < 0 and p_i - 1 < 0 constraints
            value -= Math.log (p[i]);
            value -= Math.log((1 - p[i]));    
            
            // p_i - p_i+1 < 0
            if (i < p.length - 1)
            {
              value -= Math.log(p[i+1] - p[i]);
            }
          }

          for (int i=0; i < p.length;++i)
          {
            boolean pos = positiveInstance[i];
            derivative[i] = (pos ? -1.0 / p[i] : 1.0 / (1.0 - p[i]));
            
            // contribution from each f_i < 0 is -grad(f_i) / f_i 
            // since grad(-log(-f_i)) = - grad(f_i) / f_i
            
            // So need to sum over constraints involving p_i
            
            // -p_i < 0 -> 1 / (-p_i)
            derivative[i] +=  -1.0 / p[i];
            // p_i - 1 < 0  -> -1 / (p_i - 1)
            derivative[i] +=  1.0 / (1.0-p[i]);
            if (i < p.length - 1)
            {
              // p_i - p_i+1 < 0 -> (p_i+1 - 1) / (p_i - p-i+1)
             derivative[i] += 1.0 / (p[i+1] - p[i]);
            }
            if (i > 0)
            {
              // p_i-1 - p_i < 0-> (1 - p_i-1) / (p_i-1 - p_i)
              derivative[i] +=  1.0 / (p[i-1] - p[i]);
            }
            
          }

          System.out.println("Value: " + value);
          System.out.print("Derivative: ");
          for (int i=0; i < derivative.length; ++i) System.out.print(derivative[i]+ " ");
          System.out.println();
          //if (true) System.exit(0);
        }
      };
   
    Minimizer qnm = new QNMinimizer();
    double tol    = 1.0e-6;
    t = 1.0;
    double[] p = new double[n]; // feasible initial
    final double mu = 1.3;
    for (int i=0; i < n; ++i) p[i] = 1.0 - 1.0 / (i+2.0) ;
    for (int i=0; i < p.length; ++i) System.out.print(p[i]+" ");

    while ( m / t >= tol )
    {
      System.out.println("About to call qnm with t="+ t);
      p = qnm.minimize(df,tol,p);
      t *= mu; 
    }
        
    return new Pair<double[],Double>(p,df.valueAt(p));
  }

  public static void main(String[] args)
  {
    double  scores[] = {1.0};
    Object  labels[] = {"+1"};
    Pair<double[],Double>  pair = optimalLogLikelihood(scores,labels,"+1");
    System.out.println(pair.first()[0]);
  }
}


