package edu.stanford.nlp.service;

import edu.stanford.nlp.util.Function;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class provides bookkeeping for which services are currently
 * available on which transports.
 * 
 * TODO: This is currently kind of broken and should be implemented
 * itself as a service.
 * 
 * @author dramage
 */
public class ServiceRegistry {
  //
  // static config variables
  //
  /** Path to central service registry */
  private static final File REGISTRY_PATH
    = new File("/u/nlp/javanlp/registry/services");

  /** Base port number for new services */
  private static final int BASE_PORT = 6600;

  //
  // instance variables
  //
  /** Registry path for this instance */
  private final File registryPath;

  /**
   * Creates a registry instance with the default REGISTRY_PATH
   */
  public ServiceRegistry() {
    this(REGISTRY_PATH);
  }
  
  /**
   * Creates a registry instance that queries the given registry path.
   */
  public ServiceRegistry(File registryPath) {
    this.registryPath = registryPath;
  }
  
  /** Loads the ServiceRegistry from the registry path */
  @SuppressWarnings("unchecked")
  private List<ServiceRegistryEntry> loadRegistry() {
    try {
      FileInputStream fileStream = null;
      
      try {
        if (registryPath.exists() && registryPath.length() == 0) {
          throw new FileNotFoundException();
        } else {
          fileStream = new FileInputStream(registryPath);
        }
      } catch (FileNotFoundException e) {
        // no registry exists .. create a default one
        saveRegistry(new ArrayList<ServiceRegistryEntry>());
        fileStream = new FileInputStream(registryPath);
      }
      ObjectInputStream objectStream = new ObjectInputStream(fileStream);
      List<ServiceRegistryEntry>registry = (List)objectStream.readObject();
      objectStream.close();
      fileStream.close();
      return registry;
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }
  
  /** Saves the ServiceRegistry from to the registry path */
  private void saveRegistry(List<ServiceRegistryEntry> registry) {
    try {
      FileOutputStream fileStream = new FileOutputStream(registryPath);
      ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
      objectStream.writeObject(registry);
      objectStream.close();
      fileStream.close();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }
  
  /**
   * Returns true if a service is registered for the given factory with
   * the given arguments.
   * 
   * @throws ServiceException if unable to load the registry (file
   *   not found, io error, etc).
   */
  public boolean hasService(String service, Object ... args)
    throws ServiceException {
    
    for (ServiceRegistryEntry entry : loadRegistry()) {
      if (entry.service.equals(service) && Arrays.equals(entry.args, args)) {
        return SocketService.ping(entry.host, entry.port);
      }
    }
    return false;
  }
  
 /**
  * Returns a ServiceSession for communicating with the given service,
  * as requested by fully qualified class name.
  * 
  * @throws ServiceException if no such service available
  */
  public <K,V> Function<K,V> getService(String service, Object ... args)
    throws ServiceException {
    
    for (ServiceRegistryEntry entry : loadRegistry()) {
      if (entry.service.equals(service) && Arrays.equals(entry.args, args)) {
        return SocketClient.connect(entry.host, entry.port);
      }
    }
    throw new ServiceException("Service not found");
  }
  
  /**
   * Stops the given service
  * 
  * @throws ServiceException if no such service available
   */
  public void stopService(String service, Object ... args) 
    throws ServiceException {
    
    List<ServiceRegistryEntry> entries = new ArrayList<ServiceRegistryEntry>();
    for (ServiceRegistryEntry entry : loadRegistry()) {
      if (entry.service.equals(service) && Arrays.equals(entry.args, args)) {
        SocketService.stop(entry.host, entry.port);
      } else {
        entries.add(entry);
      }
    }
    saveRegistry(entries);
    
    throw new ServiceException("Service not found");
  }
  
  /**
   * Registers the given service.
   */
  private void registerService(InetAddress host, int port,
      String service, Object ... args) {
    
    List<ServiceRegistryEntry> registry = loadRegistry();
    registry.add(new ServiceRegistryEntry(service, args, host, port));
    saveRegistry(registry);

    System.err.println("ServiceRegistry: registered "+service+" at "+
        host.getHostName()+":"+port);
  }
  
  /**
   * Registers and returns a SocketService for the given Service instance
   * on a free port on the local machine. The service is NOT started by
   * default.  Do <code>service.run()</code> or
   * <code>new Thread(service).run()</code> as necessary.
   * 
   * @param service Service to register
   * @param args Any arguments to be associated with the given service
   * 
   * @throws ServiceException If unable to discover local host or
   *   save the registry.
   */
  public <K,V> SocketService registerSocketService(
      Function<K,V> service, Object ... args) {
    
    InetAddress localhost = null;
    try {
      localhost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
     throw new ServiceException(e);
    }
    
    // find next available port on machine
    int port = BASE_PORT;
    for (ServiceRegistryEntry entry : loadRegistry()) {
      if (entry.host.equals(localhost) && entry.port >= port) {
        port = entry.port + 1;
      }
    }

    // may throw RuntimeException on error, which is cool
    SocketService socket = new SocketService(service, port);
    
    // registers only if service started successfully
    registerService(localhost, port, service.getClass().getName(), args);
    
    return socket;
  }
  
  /**
   * Unregister service by factory/args (where args may be null for standard
   * ServiceFactories).
   */
  public void unregisterSocketService(InetAddress host, int port) {
    List<ServiceRegistryEntry> updated = new ArrayList<ServiceRegistryEntry>();
    for (ServiceRegistryEntry entry : loadRegistry()) {
      if (!(entry.host.equals(host) && entry.port == port)) {
        updated.add(entry);
      }
    }
    saveRegistry(updated);
  }
  
  /**
   * Refreshes the registry, removing defunct services
   */
  private void cmdPurge() {
    List<ServiceRegistryEntry> active = new ArrayList<ServiceRegistryEntry>();
    
    // populate default list of none present
    if (!registryPath.exists()) {
      saveRegistry(active);
    }
    
    List<ServiceRegistryEntry> registered = loadRegistry();
    
    // read registry, connecting to each
    for (ServiceRegistryEntry entry : registered) {
      if (SocketService.ping(entry.host, entry.port)) { 
        active.add(entry);
      }
    }
    
    // update if changed
    if (!active.equals(registered)) {
      System.out.println("ServiceRegistry: purged "+(registered.size()-active.size())+" defunct");
      saveRegistry(active);
    }
  }
  
  /**
   * Lists services currently present in registry.
   */
  private void cmdList() {
    for (ServiceRegistryEntry entry : loadRegistry()) {
      System.out.println(SocketService.ping(entry.host, entry.port)
          ? entry : entry + " <defunct>");
    }
  }
  
  /** Single entry in the registry */
  public static class ServiceRegistryEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Class name of the ServiceFactory */
    public final String service;
    
    /** Factory arguments */
    public final Object[] args;
    
    /** Service host */
    public final InetAddress host;
    
    /** Service port */
    public final int port;
    
    public ServiceRegistryEntry(String factory, Object[] args, InetAddress host, int port) {
      this.service = factory;
      this.args = args;
      this.host = host;
      this.port = port;
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder()
        .append(host.getHostName()).append(":").append(port)
        .append(" ").append(service).append("(");
     
      for (int i = 0; i < args.length; i++) {
        sb.append(args[i]);
        if (i < args.length-1) {
          sb.append(",");
        }
      }
      
      sb.append(")");
      return sb.toString();
    }
  }
  
  /** Usage information for running ServiceRegistry as an entry point. */
  public static String USAGE = "ServiceRegistry {ls|purge|stop}\n" +
      "  ls                   - lists registered services\n" +
      "  purge                - removes defunct connections in service list\n" +
      "  stop host:port       - stops service running on host:port\n";
  
  /**
   * Main entry point.  See USAGE string for details.
   */
  public static void main(String[] args) {
    ServiceRegistry registry = new ServiceRegistry();
    try {
      if (args.length == 0) {
        throw new ServiceException(USAGE);
      }
      if (args[0].equals("ls")) {
        registry.cmdList();
      } else if (args[0].equals("purge")) {
        registry.cmdPurge();
        registry.cmdList();
      } else if (args[0].equals("stop")) {
        if (args.length != 2) {
          throw new ServiceException(USAGE);
        }
        String[] s = args[1].split(":");
        SocketService.stop(s[0], Integer.parseInt(s[1]));
      } else {
        throw new ServiceException(USAGE);
      }
    } catch (ServiceException e) {
      System.out.println(e.getMessage());
    } catch (Exception e) {
      System.out.println(USAGE);
      e.printStackTrace();
    }
  }
}
