package edu.stanford.nlp.service;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.BindException;

import edu.stanford.cs.ra.RA;
import edu.stanford.cs.ra.arguments.Argument;
import edu.stanford.nlp.util.Function;

/**
 * A SocketService listens on a port, sending serialized answers in
 * responses to serialized queries, where actual computation is performed
 * by an instance of Service.  All incoming connections are routed to
 * the same Service instance.  If multiple requests come in simultaneously,
 * they are processed by the provided ExecutorService, which defaults
 * to ExecutorService.newCachedThreadPool().
 * 
 * @author dramage
 */
public class SocketService implements Runnable {
  
  //
  // static control methods
  //
  
  /**
   * Returns true if the service at the given address is alive.
   */
  public static boolean ping(InetAddress host, int port) {
    try {
      SocketClient<Object,Object> client = SocketClient.connect(host,port);
      Object response = client.apply(SocketSessionControlMessage.PING);
      client.disconnect();
      return response == SocketSessionControlResponse.PONG;
    } catch (ServiceException e) {
      return false;
    }
  }
  
  /**
   * Returns true if the service at the given address is alive.
   */
  public static boolean ping(String host, int port) {
    try {
      return ping(InetAddress.getByName(host), port);
    } catch (UnknownHostException e) {
      throw new ServiceException(e);
    }
  }
  
  /**
   * Stops the service at the given address if one is running. Returns
   * true if a service was actually stopped.
   */
  public static boolean stop(InetAddress host, int port) {
    try {
      SocketClient<Object,Object> client = SocketClient.connect(host, port);
      Object response = client.apply(SocketSessionControlMessage.STOP);
      client.disconnect();
      return response == SocketSessionControlResponse.BYE;
    } catch (ServiceException e) {
      return false;
    }
  }
  
  /**
   * Stops the service at the given address if one is running. Returns
   * true if a service was actually stopped.
   */
  public static boolean stop(String host, int port) {
    try {
      return stop(InetAddress.getByName(host), port);
    } catch (UnknownHostException e) {
      throw new ServiceException(e);
    }
  }

  //
  // Actual class
  //
  
  /** Executor for evaluating futures (incoming requests) */
  private final ExecutorService executor;
  
  /** Listener for incoming connections on a port */
  private final ServerSocket listener;
  
  /** The actual service instance that does the computation. */
  private final Function<Object,Object> service;
  
  /** run() loops while active.  Stop with stop() */
  private boolean active = true;
  
  /**
   * Answer incoming requests on the given port with the given provider.
   * Call run() (possibly in a new thread) to begin listening.  Uses
   * ExecutorService.newCachedThreadPool() as the default execution policy
   * for multiple simultaneous requests.
   */
  public <K,V> SocketService(Function<K,V> service, int port) {
    this(service, port, Executors.newCachedThreadPool());
  }
  
  /**
   * Answer incoming requests on the given port with the given provider.
   * Call run() to begin listening.
   */
  @SuppressWarnings("unchecked")
  public <K,V> SocketService(Function<K,V> service, int port, ExecutorService executor)
    throws ServiceException {
    
    ServerSocket initListener = null;
    
    int tries = 0;
    while (true) {
      tries += 1;
        try {
          initListener = new ServerSocket(port);
          break;
          
        } catch (BindException e) {
   //       if (tries > 10) {
            throw new ServiceException(e);
 //         } else {            
 //           port = port + 1;       // try another port or at most 10 times
  //        }
        } catch (IOException e) { // if there are any unspecific IOExceptions, throw these
          throw new ServiceException(e);
        } 
    }
    this.listener = initListener;
    this.service = (Function)service;
    this.executor = executor;
  }
  
  /**
   * Runs the service; does not return until the stop() has been called.
   * Use new Thread(new Service(provider, port)).run() to run the service in
   * a new thread.
   */
  public void run() {
    while (active) {
      try {
        // blocks this thread until accept or the listener is closed
        Socket socket = listener.accept();
        new Thread(new SocketServiceSession(socket)).start();
      } catch (IOException e) {
        if (active) {
          System.err.println("Service Error: failed establishing connection");
        }
      }
    }
  }
  
  /**
   * Stops the service if it has been run in a thread via:
   * new Thread(service).start() and shuts down the ExecutorService
   * that might be running any jobs.
   */
  public synchronized void stop() {
    this.active = false;
    try {
      this.listener.close();
    } catch (IOException e) {
      // ignore
    }
    executor.shutdown();
  }
  
  /**
   * Control messages
   */
  public enum SocketSessionControlMessage {
    STOP, // Stop the service
    PING; // If healthy, respond with a ping
  }
  
  /**
   * Control responses
   */
  public enum SocketSessionControlResponse {
    BYE,  // Service stopped
    PONG; // I been done pinged.
  }
  
  /**
   * Tracks an individual client conversation
   */
  private class SocketServiceSession implements Runnable {
    /** Socket connection to client */
    private Socket socket;
    
    /** The input stream from the client. */
    private ObjectInputStream in;

    /** The output stream to the client. */
    private ObjectOutputStream out;
    
    public SocketServiceSession(Socket socket) throws IOException {
      this.socket = socket;
      this.in = new ObjectInputStream(socket.getInputStream());
      this.out = new ObjectOutputStream(socket.getOutputStream());
    }

    public void run() {
      while (socket != null) {
        final int rid;
        final Object query;
        
        try {
          rid = in.readInt();
          query = in.readUnshared();
        } catch (ClassNotFoundException e) {
          // couldn't de-serialize - this is a bad error
          e.printStackTrace();
          sendMessage(-1, e);
          close();
          return;
        } catch (IOException e) {
          // problem reading or eof
          sendMessage(-1, e);
          close();
          return;
        }
        
        if (query instanceof SocketSessionControlMessage) {
          // process a control message
          switch ((SocketSessionControlMessage)query) {
          case STOP:
            SocketService.this.stop();
            sendMessage(rid, SocketSessionControlResponse.BYE);
            close();
            stop();
            return;
          case PING:
            System.err.println("SocketService: PINGED");
            if (!sendMessage(rid, SocketSessionControlResponse.PONG)) { return; }
            continue;
          }
        } else {
          // regular object: process and write output
          System.err.println("SocketService: "+ service.getClass().getSimpleName() +" READ "+query);
          
          // execute the query according to our service's executor
          executor.execute(new Runnable() {
            public void run() {
              Object rv;
              try {
                rv = service.apply(query);
              } catch (RuntimeException e) {
                // problem during evaluation, return wrapped exception
                e.printStackTrace();
                sendMessage(rid, new ServiceEvaluationException(e));
                return;
              }
              sendMessage(rid, rv);
            }
          });
        }

        // be nice
        Thread.yield();
      }
    }
    
    private synchronized boolean sendMessage(int rid, Object message) {
      try {
        out.reset();
        out.writeInt(rid);
        out.writeUnshared(message);
        out.flush();
      } catch (NotSerializableException e) {
        e.printStackTrace();
        System.exit(1);
//        throw new RuntimeException(e);
      } catch (IOException e) {
        // probably a client disconnected, which is ok 
        close();
        return false;
      }
      return true;
    }
    
    /** Close sockets and streams */
    public void close() {
      if (socket == null) {
        return;
      }
      
      try {
        in.close();
        out.close();
      } catch (IOException e) {
        // do nothing
      }
      try {
        socket.close();
      } catch (IOException e) {
        // do nothing
      }
      socket = null;
    }
  }
  
  /** Exception thrown when running Service instance */
  static class ServiceEvaluationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final RuntimeException exception;
    
    public ServiceEvaluationException(RuntimeException exception) {
      this.exception = exception;
    }
  }
  
  
  @Argument("Type of service to run")
  @Argument.Switch("--service")
  @Argument.Name("SocketService:SessionType")
  private static Class<? extends Function<?,?>> sessionType;
  
  @Argument("Port to run service on")
  @Argument.Switch("--port")
  @Argument.Name("SocketService:SessionPort")
  private static int port;
  
  /**
   * Entry to running a service.  See usage string for usage.
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] argv) throws Exception {
    RA.begin(argv, SocketService.class);
    
    Function service = sessionType.newInstance();
    RA.getArgumentPopulator().populate(service);
    
    new SocketService(service, port).run();
  }
}
