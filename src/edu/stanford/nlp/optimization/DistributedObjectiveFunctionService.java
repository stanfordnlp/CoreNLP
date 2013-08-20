package edu.stanford.nlp.optimization;

import edu.stanford.nlp.process.Killable;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.service.SocketService;
import edu.stanford.nlp.service.SocketClient;
import edu.stanford.nlp.service.ServiceException;

import java.net.InetAddress;
import java.net.ConnectException;
import java.util.*;

/**
 * This class takes a Chart and sets the possible labels and local scores.
 */

public abstract class DistributedObjectiveFunctionService<K> implements Function<Pair<Integer,double[]>, Pair<Double,double[]>> {

  private AbstractStochasticCachingDiffFunction objectiveFunction;
  private K objFuncArgs;
  
  protected abstract AbstractStochasticCachingDiffFunction makeNewObjectiveFunction(K objFuncArgs) ;
  private MainThread mainThread = null;
  
  public void init(K objFuncArgs) {
    this.objFuncArgs = objFuncArgs;
    objectiveFunction = makeNewObjectiveFunction(objFuncArgs);
    mainThread = new MainThread();
    mainThread.start();
    System.err.println("init returning");
  }

  private List<Pair<Integer,double[]>> requests = Collections.synchronizedList(new LinkedList());
  
  public Pair<Double,double[]> apply (Pair<Integer,double[]> request) {
    requests.add(request);
    Pair<Double,double[]> output = inputOutputMap.get(request);

    if (objectiveFunction != null && objectiveFunction instanceof Killable && requests.size() > 1) {
      System.err.println("killing: "+this);
      ((Killable)objectiveFunction).kill();
      objectiveFunction = makeNewObjectiveFunction(objFuncArgs);
    }

    while (output == null) {
      output = inputOutputMap.remove(request);
      if (mainThread != null && mainThread.exception != null) {
        System.err.println("has exception: "+this);
        throw new RuntimeException(mainThread.exception);
      }
      try {
        Thread.sleep(10);
      } catch (Exception e) {}
    }
    return output;
  }

  private Map<Pair<Integer,double[]>, Pair<Double,double[]>> inputOutputMap = Collections.synchronizedMap(new HashMap());
  private Pair<Double,double[]> killOutput = new Pair(0.0, null);

  private class MainThread extends Thread {

    public Throwable exception = null;
    
    @Override
    public void run() {
      try {
        while (objectiveFunction == null) {}
        
        while (true) {
          if (requests.isEmpty()) { continue; }        
          
          Pair<Integer,double[]> request = requests.remove(0);
          
          while (!requests.isEmpty()) {
            inputOutputMap.put(request, killOutput);
            request = requests.remove(0);
          }
          
          int index = request.first();
          double[] weights = request.second();
          int[] batch = new int[]{ index };
          objectiveFunction.calculateStochastic(weights, null, batch);
          if (objectiveFunction instanceof Killable && ((Killable)objectiveFunction).killed()) {
            inputOutputMap.put(request, killOutput);
          } else {
            double value;
            double[] derivative;
            value = objectiveFunction.lastValue();
            derivative = objectiveFunction.lastDerivative();
            
            inputOutputMap.put(request, new Pair(value, derivative));
          }
        }
      } catch (Throwable e) {
        System.err.println("catching throwable: "+this);
        e.printStackTrace();
        this.exception = e;
      }
    }
  }
    
  
  public static void run(DistributedObjectiveFunctionService service, String[] args) {

    Properties prop = StringUtils.argsToProperties(args);

    String server = prop.getProperty("server");
    if (server == null) {
      System.err.println("You must specify a server with the -server flag.");
      return;
    }

    int serverPort = Integer.parseInt(prop.getProperty("serverPort", "4242"));
    int clientPort = Integer.parseInt(prop.getProperty("clientPort", "1234"));

    SocketService socketService = new SocketService(service, clientPort);
    System.err.println("Running service on port: "+clientPort);
    new Thread(socketService).start();

    boolean connected = false;

    while (!connected) {
      try {
        // tell server about client
        System.err.print("connecting ... ");    
        SocketClient<Pair<String,Integer>,Object> client = SocketClient.connect(server, serverPort);
        System.err.print("done.\ngetting constructor arguments ... ");    
        Object constructorInfo = client.apply(new Pair<String,Integer>(InetAddress.getLocalHost().getHostName(), clientPort));
        client.disconnect();
        System.err.print("done.\ninit ... ");    
        service.init(constructorInfo);
        System.err.println("done");    
        connected = true;
      } catch (ServiceException e1) {
        if (e1.getCause() instanceof ConnectException) {
          System.err.println("error connecting to: "+server+":"+serverPort+".  Trying again in 10 seconds.");
          try {
            Thread.sleep(10000);
          } catch (Exception e2) {
            e2.printStackTrace();
            return;
//            throw new RuntimeException(e2);
          }
        } else {
          e1.printStackTrace();
          return;
//          throw new RuntimeException(e1);
        }
      } catch (Exception e3) {
        e3.printStackTrace();
        return;
//        throw new RuntimeException(e3);
      }
    }
    
  }
 
}
