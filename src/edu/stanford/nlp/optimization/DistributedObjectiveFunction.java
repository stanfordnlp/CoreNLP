package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.service.ServiceListener;
import edu.stanford.nlp.service.ServiceException;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.math.ArrayMath;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**     
 * @author Jenny Finkel
 */
public class DistributedObjectiveFunction<K>  extends AbstractStochasticCachingDiffFunction {

  private enum DistributionType { NETWORK, THREADED };
  private DistributionType distributionType = null;
  
  private Set<Function<Pair<Integer,double[]>, Pair<Double,double[]>>> allServices = Collections.synchronizedSet(new HashSet());
  private Queue<Function<Pair<Integer,double[]>, Pair<Double,double[]>>> freeServices = null;
  
  private final int dataDimension;
  private final int domainDimension;

  Factory<DistributedObjectiveFunctionService<K>> factory = null;
  private K objFuncArgs = null;
  
  private Function<double[], Boolean> monitor = null;
  
  /**
   * This constructor is for when the funciton is being distributed across the network.
   */
  public DistributedObjectiveFunction(K objFuncArgs, int port, Function<double[], Boolean> monitor, int dataDimension, int domainDimension) {
    this.dataDimension = dataDimension;
    this.domainDimension = domainDimension;
    this.monitor = monitor;

    distributionType = DistributionType.NETWORK;
    
    ServiceListener<K, Pair<Integer,double[]>, Pair<Double,double[]>> listener = new ServiceListener(objFuncArgs, port, new ServiceAdder());
  }

  /**
   * This constructor is for when you want multiple threads on the same machine.
   */
  public DistributedObjectiveFunction(Factory<DistributedObjectiveFunctionService<K>> factory, int numThreads, K objFuncArgs, Function<double[], Boolean> monitor, int dataDimension, int domainDimension) {
    this.dataDimension = dataDimension;
    this.domainDimension = domainDimension;
    this.monitor = monitor;
    this.factory = factory;
    this.objFuncArgs = objFuncArgs;

    distributionType = DistributionType.THREADED;
    
    for (int i = 0; i < numThreads; i++) {
      DistributedObjectiveFunctionService<K> service = factory.create();
      service.init(objFuncArgs);
      allServices.add(service);
    }
  }

  @Override
  public int dataDimension() {
    return dataDimension;
  }

  @Override
  public int domainDimension() {
    return domainDimension;
  }

  @Override
  protected void calculate(double[] x) {
    SortedSet<Integer> toBeAssigned = new TreeSet<Integer>();
    for (int i=0; i < dataDimension(); i++) {
      toBeAssigned.add(i);
    }
    doComputation(toBeAssigned, x);

    if (monitor != null) {
      monitor.apply(x);
    }
        
  }

  // v is ignored
  @Override
  public void calculateStochastic(double[] x, double[] v, int[] batch) {
    if (monitor != null) {
      monitor.apply(x);
    }

    SortedSet<Integer> toBeAssigned = new TreeSet<Integer>();
    for (int i : batch) { toBeAssigned.add(i); }
    doComputation(toBeAssigned, x);
  }
  
  private void doComputation(SortedSet<Integer> toBeAssigned, double[] x) {
    
    Output output = new Output();
    freeServices = new ConcurrentLinkedQueue(allServices);
    output.awaitingContributionFrom = new LinkedList<Integer>(toBeAssigned);
    output.value = 0.0;
    output.derivative = derivative;
    Arrays.fill(derivative, 0.0);

    while (true) {
      try {
        output.semaphore.acquire();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (output.awaitingContributionFrom.isEmpty()) {
        output.semaphore.release();
        break;
      }
      if (!freeServices.isEmpty()) {        
        int index = output.awaitingContributionFrom.poll();
        output.awaitingContributionFrom.offer(index);
        output.semaphore.release();
        
        Function service = freeServices.poll();
        Pair<Integer,double[]> input = new Pair<Integer,double[]>(index, x);
        ServiceThread serviceThread = new ServiceThread(service, input, output);
        serviceThread.start();
      } else {
        output.semaphore.release();
        try {
          Thread.sleep(10);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    value = output.value;
    derivative = output.derivative;
  }
  
  private static class Output {
    public Queue<Integer> awaitingContributionFrom;
    public Semaphore semaphore = new Semaphore(1);
    public double value;
    public double[] derivative;
  }

  private class ServiceThread extends Thread {

    private Function<Pair<Integer,double[]>, Pair<Double,double[]>> service;
    private Pair<Integer,double[]> input = null;
    private Output output = null;
    
    public ServiceThread(Function<Pair<Integer,double[]>, Pair<Double,double[]>> service, Pair<Integer,double[]> input, Output output) {

      this.service = service;      
      this.input = input;
      this.output = output;
    }
    
    @Override
    public void run() {

      try {
        Timing t = new Timing();
        t.start();
        Pair<Double,double[]> result = service.apply(input);
        output.semaphore.acquire();
        if (output.awaitingContributionFrom.remove(input.first())) {
          if (result.second() == null) {
            output.awaitingContributionFrom.offer(input.first());
            t.stop(" [error, but recovering] "+"\t("+service+")");
          } else {
//            t.stop(input.first()+": "+result.first()+"\t("+service+")");
            output.value += result.first();
            ArrayMath.pairwiseAddInPlace(output.derivative, result.second());
          }
          output.semaphore.release();
          freeServices.add(service);
          service = null;
          input = null;
          output = null;
        } else {
          output.semaphore.release();
        }
      } catch (ServiceException e) {
        output.semaphore.release();
        freeServices.remove(service);
        if (allServices.remove(service)) {
          System.err.println("lost a client: "+allServices.size()+" clients remain.");
        }
      } catch (Exception e) {
        output.semaphore.release();
        freeServices.remove(service);
        if (allServices.remove(service)) {
          System.err.println("lost a client: "+allServices.size()+" clients remain.");
          if (distributionType == DistributionType.THREADED) {
            DistributedObjectiveFunctionService<K> service = factory.create();
            service.init(objFuncArgs);
            allServices.add(service);
            if (freeServices != null) { freeServices.add(service); }
          }
        }
        e.printStackTrace();
      }
    }
  }

  
  private class ServiceAdder implements Function<Function<Pair<Integer,double[]>, Pair<Double,double[]>>, Boolean>{

    public Boolean apply(Function<Pair<Integer,double[]>, Pair<Double,double[]>> newService) {
      if (freeServices != null) {
        freeServices.add(newService);
      }
      allServices.add(newService);
      System.err.println("num clients: "+allServices.size());
      return true;
    }
  }
}
