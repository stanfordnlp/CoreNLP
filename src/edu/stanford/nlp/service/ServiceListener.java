package edu.stanford.nlp.service;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Pair;

public class ServiceListener<A,B,C> implements Function<Pair<String,Integer>, A> {

  private A initInfo;
//  public final Set<Function<B,C>> clients = Collections.synchronizedSet(new HashSet());
  private Function<Function<B,C>,Boolean> serviceProcessor;
  
  public ServiceListener(A initInfo, int port, Function<Function<B,C>,Boolean> serviceProcessor) {
    this.initInfo = initInfo;
    SocketService service = new SocketService(this, port);
    this.serviceProcessor = serviceProcessor;
    new Thread(service).start();
  }

  public A apply(Pair<String,Integer> hostAndPort) {

    try {
      System.err.print("connectiong ... ");
      SocketClient<B,C> service =
        SocketClient.connect(hostAndPort.first(), hostAndPort.second());
//      System.err.print("done\nadding "+service+" to all services ... ");
      System.err.print("done\nprocessing "+service+" ... ");
//      clients.add(service);
      System.err.println("return value: "+serviceProcessor.apply(service));
//      System.err.println("client added");
      
      return initInfo;
    } catch (Exception e) {
      e.printStackTrace();
      return null;        
    }
  }
}
