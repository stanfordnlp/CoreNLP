package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.StreamGobbler;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;


/**
 * A common base class for annotators that talk to other web servers.
 *
 * The important things to do to implement this is:
 *
 * <ol>
 *   <li>Implement {@link #annotateImpl(Annotation)} with the code to actually call the server.</li>
 *   <li>Implement {@link #ready(boolean initialTest)} with code to check if the server is available. {@link #ping(String)} may be useful for this.</li>
 *   <li>Optionally implement {@link #startCommand()} with a command to start a local server. If this is specified, we will start
 *       a local server before we start checking for readiness.
 *       Note that the {@link #ready(boolean initialTest)} endpoint does still have to point to this local server in that case, or else
 *       lifecycle won't be managed properly.
 *   </li>
 * </ol>
 *
 * @author <a href="mailto:gabor@eloquent.ai">Gabor Angeli</a>
 */

public abstract class WebServiceAnnotator implements Annotator {
  /** A logger from this class. */
  private static Redwood.RedwoodChannels log = Redwood.channels(WebServiceAnnotator.class);

  /** A timeout to wait for a server to boot up. Beyond this, we simply give up and throw an exception. */
  private static long CONNECT_TIMEOUT = Duration.ofMinutes(15).toMillis();


  /**
   * Thrown if we could not annotate, but there's hope to either reconnect or restart the server.
   * Will still only try to connect 3 times.
   * This is the usual exception.
   */
  public static class ShouldRetryException extends Exception {
    private static final long serialVersionUID = -4292922700733296864L;

    public ShouldRetryException() {}
  }


  /** An exception thrown if we could not connect to the server, and shouldn't retry / recreate the server. */
  @SuppressWarnings("unused")
  public static class PermanentlyFailedException extends Exception {
    private static final long serialVersionUID = 6812811056236924923L;

    public PermanentlyFailedException() {}
    public PermanentlyFailedException(Throwable t) {
      super(t);
    }
  }



  /**
   * A class encapsulating a running server process.
   */
  private class RunningProcess {
    /** The actual running process. */
    public final Process process;
    /** The output stream gobbler, redirecting the stream to stdout. */
    public final StreamGobbler stdout;
    /** The error stream gobbler, redirecting the stream to stderr. */
    public final StreamGobbler stderr;
    /** If true, the server is presumed ready to accept connections. */
    public boolean ready = false;
    /** A shutdown hook to clean up this process on shutdown. */
    private final Thread shutdownHoook;

    /** A straightforward constructor. */
    private RunningProcess(Process process) {
      this.process = process;
      Writer errWriter = new BufferedWriter(new OutputStreamWriter(System.err));
      this.stderr = new StreamGobbler(process.getErrorStream(), errWriter);
      this.stderr.start();
      Writer outWriter = new BufferedWriter(new OutputStreamWriter(System.out));
      this.stdout = new StreamGobbler(process.getErrorStream(), outWriter);
      this.stdout.start();
      this.shutdownHoook = new Thread(() -> {
        log.info("Killing process " + WebServiceAnnotator.this);
        this.stdout.kill();
        this.stderr.kill();
        if (this.process.isAlive()) {
          this.process.destroy();
        }
        this.ready = false;
      });
      Runtime.getRuntime().addShutdownHook(this.shutdownHoook);
    }

    /** Kills this process, and kills the stream gobblers waiting on it. */
    public void kill() {
      Runtime.getRuntime().removeShutdownHook(shutdownHoook);
      shutdownHoook.run();
    }


    /** Make sure we clean up this annotator! */
    @Override
    protected void finalize() throws Throwable {
      try {
        super.finalize();
      } finally {
        kill();
      }
    }
  }


  /** If true, we have connected to the server at some point. */
  protected boolean everLive      = false;

  /** If true, the server was active last time checked */
  protected boolean serverWasActive = false;

  /** The running server, if any. */
  private   Optional<RunningProcess> server = Optional.empty();


  /**
   * The command to run to start the server, if any.
   * If no command is given, we assume it's being managed by someone else (e.g., an external
   * running service).
   *
   * @return The command we should start, or {@link Optional#empty()} if we don't want CoreNLP
   * to manage the server.
   */
  protected abstract Optional<String[]> startCommand();

  /**
   * An optional command provided to run to shut down the server.
   */
  protected abstract Optional<String[]> stopCommand();


  /**
   * Check if the server is ready to accept annotations.
   * This client will wait until the ready endpoint returns true.
   *
   * @param initialTest testing a server that has just been started?
   *
   * @return True if the server is ready to accept documents to annotate.
   */
  protected abstract boolean ready(boolean initialTest);


  /**
   * Actually annotate a document with the server.
   *
   * @param ann The document to annotate.
   *
   * @throws ShouldRetryException Thrown if we could not annotate the document, but we could plausibly retry.
   * @throws PermanentlyFailedException Thrown if we could not annotate the document and should not retry.
   */
  protected abstract void annotateImpl(Annotation ann) throws ShouldRetryException, PermanentlyFailedException;


  /**
   * Check if the server is live. Can be overwritten if it differs from {@link #ready(boolean initialTest)}.
   *
   * @return True if the server is live.
   */
  protected boolean live() {
    return true;
  }


  /**
   * A utility to ping an endpoint. Useful for {@link #live()} and {@link #ready(boolean initialTest)}.
   *
   * @param uri The URL we are trying to ping.
   *
   * @return True if we got any non-5XX response from the endpoint.
   */
  protected boolean ping(String uri) {
    try {
      URL url = new URL(uri);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("Accept-Charset", "UTF-8");
      connection.setRequestMethod("GET");
      connection.connect();
      int code = connection.getResponseCode();
      return code < 500 || code >= 600;
    } catch (MalformedURLException e) {
      log.warn("Could not parse URL: " + uri);
      return false;
    } catch (ClassCastException e) {
      log.warn("Not an HTTP URI");
      return false;
    } catch (IOException e) {
      return false;
    }

  }


  /**
   * Start the actual server.
   *
   * @param command the command we are using to start the sever.
   *
   * @return True if the server was started; false otherwise.
   */
  private boolean startServer(String[] command) {
    ProcessBuilder proc = new ProcessBuilder(command);
    try {
      synchronized (this) {
        this.server = Optional.of(new RunningProcess(proc.start()));
      }
      log.info("Started server " + StringUtils.join(command));
      return true;

    } catch (IOException e) {
      log.error("Could not start process: " + StringUtils.join(command));
      return false;
    }
  }


  /**
   * Ensure that the server we're trying to connect to exists.
   * This is certainly called from {@link #annotate(Annotation)}, but can also
   * be called from the constructor of the annotator to cache startup times.
   *
   * @throws TimeoutException Thrown if we could not connect to the server for the timeout period.
   * @throws IOException Thrown if we could not start the server process.
   */
  protected void ensureServer() throws TimeoutException, IOException {
    long startTime = System.currentTimeMillis();

    // if the server was active last time we checked, see if the server is still active
    if (serverWasActive) {
      if (ready(false))
        return;
    }

    // 1. Start a server, if applicable
    boolean serverStarted = startCommand().map(this::startServer).orElse(true);
    if (!serverStarted) {
      throw new IOException("Could not start a local server!");
    }

    // 2. Wait for the target server to come online
    while (!everLive) {
      if (System.currentTimeMillis() > startTime + CONNECT_TIMEOUT) {
        throw new TimeoutException("Could not connect to annotator: " + this);
      }
      if (!live()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
      } else {
        everLive = true;
      }
    }
    log.info("Got liveness from server for " + this);

    // 3. Wait for the target server to become ready
    synchronized (this) {
      if (this.server.isPresent()) {
        while (!this.server.get().ready) {
          if (System.currentTimeMillis() > startTime + CONNECT_TIMEOUT) {
            throw new TimeoutException("Never got readiness from annotator: " + this);
          }
          if (!ready(true)) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
          } else {
            this.server.get().ready = true;
          }
        }
      } else if (!ready(false)) { // The server is not ready
        throw new IOException("Server is not ready and can not start it!");
      }
    }
    log.info("Got readiness from server for " + this);
    serverWasActive = true;

    // 4. Server is ensured! We can continue
  }

  /** {@inheritDoc} */
  public void unmount() {
    log.info("Unmounting server: " + this);
    synchronized (this) {
      if (this.server.isPresent()) {
        this.server.get().kill();
        this.server = Optional.empty();
      }
      // run optional stop script
      try {
        if (stopCommand().isPresent()) {
          ProcessBuilder proc = new ProcessBuilder(stopCommand().get());
          proc.start();
        }
      } catch (Exception e) {
        log.error("Error: problem with running stop command for WebServiceAnnotator");
      }
    }
  }


  /** {@inheritDoc} */
  public void annotate(Annotation annotation) {
    annotate(annotation, 0);
  }


  /**
   * The actual implementation of {@link Annotator#annotate(Annotation)}.
   * This calls {@link #annotateImpl(Annotation)}, which should actually make the server calls.
   * This method just handles starting/stopping the server, and waiting for readiness
   *
   * @param annotation The annotation to annotate.
   * @param tries The number of times we have tried to annotate this document.
   */
  private void annotate(Annotation annotation, int tries) {
    try {
      // 1. Ensure that we have a server to annotate against
      synchronized(this) {
        ensureServer();
      }

      try {

        // 2. Annotate the document
        annotateImpl(annotation);

      } catch (PermanentlyFailedException e) {

        // 3A. We've failed to annotate. Give up
        // 3A.1. Stop the server
        synchronized (this) {
          if (this.server.isPresent()) {
            this.server.get().kill();
            this.server = Optional.empty();
          }
        }
        // 3A.1. Throw an exception
        Throwable cause = e.getCause();
        if (cause != null && cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        } else if (cause != null) {
          throw new RuntimeException(cause);
        } else {
          throw new RuntimeException(e);
        }

      } catch (ShouldRetryException e) {

        // 3B. We've failed to annotate, but should maybe retry
        // 3B.1. Stop the server, if this is our third try
        synchronized (this) {
          if (tries >= 2 && this.server.isPresent()) {
            this.server.get().kill();
            this.server = Optional.empty();
          }
        }
        // 3B.2. Retry
        if (tries < 3) {
          annotate(annotation, tries + 1);
        } else {
          throw new RuntimeException("Could not annotate document after 3 tries:", e);
        }

      }
    } catch (TimeoutException | IOException e) {
      throw new RuntimeException("Could not ensure a server:", e);
    }
  }


  /**
   * A quick script to debug server lifecycle.
   */
  public static void main(String[] args) throws InterruptedException {
    WebServiceAnnotator annotator = new WebServiceAnnotator(){

      @Override
      public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
        return Collections.emptySet();
      }

      @Override
      public Set<Class<? extends CoreAnnotation>> requires() {
        return Collections.emptySet();
      }

      @Override
      protected Optional<String[]> startCommand() {
        return Optional.of(new String[]{"bash", "script.sh"});
      }

      @Override
      protected Optional<String[]> stopCommand() {
        return Optional.empty();
      }

      @Override
      protected boolean ready(boolean initialTest) {
        return this.ping("http://localhost:8000");
      }

      @Override
      protected void annotateImpl(Annotation ann) throws ShouldRetryException, PermanentlyFailedException {
        log.info("Fake annotated! ping=" + this.ping("http://localhost:8000"));
      }

      public String toString() { return "<test WebServiceAnnotator>"; }
    };

    Annotation ann = new Annotation("");
    annotator.annotate(ann);
  }

}
