package edu.stanford.nlp.service;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.util.Function;

/**
 * Establishes a connection to a remote {@link SocketService} - all
 * requests will be serialized and sent to the remote machine for processing,
 * which will return a serialized result.  The static connect methods
 * are used in preference to the constructor for this class because they
 * will infer the correct K,V types. 
 * 
 * @author dramage
 */
public class SocketClient<K,V> implements Function<K,V> {
  
  //
  // static constructor methods
  //
  
  /** Connects to the given host at the given port. */
  public static <K,V> SocketClient<K,V> connect(InetAddress host, int port) {
    return new SocketClient<K,V>(host, port);
  }
  
  /**
   * Connects to the given host at the given port, throwing a ServiceException
   * if there is an error connecting.
   */
  public static <K,V> SocketClient<K,V> connect(String host, int port) {
    try {
      return connect(InetAddress.getByName(host), port);
    } catch (UnknownHostException e) {
      throw new ServiceException(e);
    }
  }
  
  //
  // actual class
  //

  /** Address of host */
  private final InetAddress address;
  
  /** Port number on host */
  private final int port;

  /** Socket connection */
  private Socket socket;
  
  /** Output stream for sending objects to socket */
  private ObjectOutputStream out;
  
  /** Input stream for reading objects from socket */
  private ObjectInputStream in;
  
  /** Processed requests by id */
  private Map<Integer,V> processed =
    Collections.synchronizedMap(new HashMap<Integer,V>());
  
  /** Which request are we currently at - used only by sendRequest */
  private int requestID = 0;
  
  /**
   * Creates an instance of the SocketClient connected to a remote
   * SocketService.
   */
  private SocketClient(InetAddress address, int port) {
    this.address = address;
    this.port = port;
    connect();
  }
  
  /** Returns true if a socket connection is already established. */
  public boolean isConnected() {
    return socket != null;
  }
  
  /**
   * Connects to a running socket service or throws ServiceException
   * on error or if the connection is already open.
   */
  private void connect() throws ServiceException {
    if (isConnected()) {
      throw new ServiceException("Connection to " + address + ":" + port +
          " already established");
    }
    try {
      this.socket = new Socket(address, port);
      this.socket.setSoTimeout(0);
      this.out = new ObjectOutputStream(socket.getOutputStream());
      this.in = new ObjectInputStream(socket.getInputStream());
    } catch (IOException e) {
      this.socket = null;
      this.out = null;
      this.in = null;
      throw new ServiceException(e);
    }

    // start one thread to do all the reading
    Thread thread = new Thread(new Runnable() {
      @SuppressWarnings("unchecked")
      public void run() {
        while (isConnected()) {
          try {
            int id = in.readInt();
            V value = (V)in.readUnshared();
            if (id < 0) {
              // only happens with SocketService exceptions (not exceptions
              // while evaluating)
              throw new RuntimeException((Exception)value);
            }
            
            if (id >= 0) {
              // put answer into processed (or a class not found exception)
              processed.put(id, value);
              synchronized(processed) {
                processed.notifyAll();
              }
            }
          } catch (IOException e) {
            // expected disconnect
            disconnect();
          } catch (ClassNotFoundException e) {
            // this is a big woopsie on the programmer's part
            disconnect();
            throw new ServiceException(e);
          }
        }
      }
    });
    // must be a daemon thread so we don't keep jvm from exiting
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Disconnects from the service, but leaves it running on the server.
   */
  public void disconnect() {
    try {
      this.out.close();
      this.in.close();
    } catch (Exception e) {
      // do nothing      
    }
    try {
      this.socket.close();
    } catch (Exception e) {
      // do nothing      
    }
    this.socket = null;
  }
  
  /**
   * Stops service running on the server.
   */
  public void stop() {
    SocketService.stop(address, port);
    disconnect();
  }
  
  /**
   * Disconnect on finalization.
   */
  @Override
  protected void finalize() throws Throwable {
    disconnect();
    super.finalize();
  }
  
  /**
   * Sends a message and returns the given request id
   */
  private synchronized int sendRequest(K request) {
    requestID = requestID == Integer.MAX_VALUE ? 0 : requestID + 1;
    
    try {
      out.reset();
      out.writeInt(requestID);
      out.writeUnshared(request);
      out.flush();
    } catch (NotSerializableException e) {
      e.printStackTrace();
      System.exit(1);
    } catch (IOException e) {
        e.printStackTrace();
      // unexpected disconnect
      disconnect();
      throw new ServiceException("Unexpected disconnect or error while sending request");
    }
    
    return requestID;
  }
  
  /**
   * Processes the given request remotely.
   */
  public V apply(K request) {

    if (!isConnected()) {
      throw new ServiceException("Attempt to process request on disconnected session"); 
    }
    
    // wait until processed contains our request id
    
    Integer id = sendRequest(request);
    while (!processed.containsKey(id)) {
      synchronized(processed) {
        try {
          processed.wait();
        } catch (InterruptedException e) {
          // safe to ignore
        }
      }
      if (!isConnected()) {
        throw new ServiceException("Unexepcted disconnection from server"); 
      }
    }
    V rv = processed.remove(id);
    
    if (rv instanceof SocketService.ServiceEvaluationException) {
      throw ((SocketService.ServiceEvaluationException)rv).exception;
    }

    return rv;
  }
}
