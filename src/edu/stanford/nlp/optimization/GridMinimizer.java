package edu.stanford.nlp.optimization;

public class GridMinimizer implements LineSearcher { 

  public GridMinimizer() {
    valuesToTry = null;
  }

  private int stoppingNumber = Integer.MAX_VALUE; // stop after this number of consecutive increases. 

  public GridMinimizer(double[] valuesToTry) {
    this.valuesToTry = valuesToTry;
  }

  public GridMinimizer(double[] valuesToTry, int stoppingNumber) {
    this.valuesToTry = valuesToTry;
    this.stoppingNumber = stoppingNumber;
  }
  double[] valuesToTry;

  public double minimize(edu.stanford.nlp.util.Function<Double, Double> function, double[] valuesToTry) {
    this.valuesToTry = valuesToTry;
    return minimize(function);
  }

  public double minimize(edu.stanford.nlp.util.Function<Double, Double> function)
  {
    double[] values = new double[valuesToTry.length];
    double bestValue = Double.POSITIVE_INFINITY;
    int    bestIndex = 0;
    for (int i=0; i < valuesToTry.length; ++i)
      {
        values[i] = function.apply(valuesToTry[i]);
        if (values[i] < bestValue) 
          {
            bestIndex = i;
            bestValue = values[i];
          }
        if(i >= stoppingNumber) { 
          boolean giveUp = true;
          for(int j = i-1; j >= i - stoppingNumber; j--) {
            if(values[j] > values[i]){
              giveUp = false;
              break;
            }
          }
          if(giveUp) {
            //System.err.println("##giving up.");
            break;
          }
        }
      }
    return valuesToTry[bestIndex];
  }
  
}
