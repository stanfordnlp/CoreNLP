package edu.stanford.nlp.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * An abstract class with a command line program for the processing of protobuf requests.
 *<br>
 * For example, the Semgrex version of this can process semgrexes over SemanticGraphs.
 * This will compile a given semgrex expression, build SemanticGraph objects,
 * and return the results of those objects
 */
public abstract class ProcessProtobufRequest {
  /** Constructor for subclasses; there is no state to initialize. */
  public ProcessProtobufRequest() { }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   *
   * @param in The stream to read the request from
   * @param out The stream to write the response to
   * @throws IOException if reading or writing fails
   */
  public abstract void processInputStream(InputStream in, OutputStream out) throws IOException;

  /**
   * Processes multiple requests from the same stream.
   *<br>
   * As per the google suggestion for streaming multiple messages,
   * this reads the length of the buffer, then reads exactly that many
   * bytes and decodes it.  It repeats until either 0 is read for the
   * length or until EOF.
   *<br>
   * https://developers.google.com/protocol-buffers/docs/techniques#streamimg
   *<br>
   * Each response is written as its length followed by its bytes, and a
   * final 0 is written when the input ends.
   *
   * @param in The stream to read length-prefixed requests from
   * @param out The stream to write length-prefixed responses to
   * @throws IOException if reading or writing fails, or if the stream ends
   *     before the number of bytes given by a request's length is read
   */
  public void processMultipleInputs(InputStream in, OutputStream out) throws IOException {
    DataInputStream din = new DataInputStream(new BufferedInputStream(in));
    DataOutputStream dout = new DataOutputStream(out);
    int size = 0;
    do {
      try {
        size = din.readInt();
      } catch (EOFException e) {
        // If the stream ends without a closing 0, we consider that okay too
        size = 0;
      }

      // stream is done if there's a closing 0 or if the stream ends
      if (size == 0) {
        dout.writeInt(0);
        break;
      }

      byte[] inputArray = new byte[size];
      int lenRead = 0;
      while (lenRead < size) {
        int chunk = din.read(inputArray, lenRead, size - lenRead);
        if (chunk <= 0) {
          // Oops, guess something went wrong on the other side
          throw new IOException("Failed to read as much data as we were supposed to!");
        }
        lenRead += chunk;
      }
      ByteArrayInputStream bin = new ByteArrayInputStream(inputArray);
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      processInputStream(bin, result);
      byte[] outputArray = result.toByteArray();
      dout.writeInt(outputArray.length);
      dout.write(outputArray);
    } while (size > 0);
  }

  /**
   * Return args after filtering the args used by the processor
   *
   * Currently that is just -multiple (or --multiple), matched case-sensitively
   *
   * @param args The command line arguments
   * @return A new array of the arguments other than -multiple and --multiple
   */
  public static String[] leftoverArgs(String[] args) {
    args = Arrays.stream(args).filter(x -> !x.equals("-multiple") && !x.equals("--multiple")).toArray(String[]::new);
    return args;
  }

  /**
   * Processes requests from System.in, writing responses to System.out.
   * If the first argument is -multiple or --multiple (ignoring case),
   * multiple length-prefixed requests are processed using
   * processMultipleInputs; otherwise a single request is processed.
   *
   * @param processor The processor to use
   * @param args The command line arguments; only the first is examined
   * @throws IOException if reading or writing fails
   */
  public static void process(ProcessProtobufRequest processor, String ... args) throws IOException {
    if (args.length > 0 &&
        (args[0].equalsIgnoreCase("-multiple") || args[0].equalsIgnoreCase("--multiple"))) {
      processor.processMultipleInputs(System.in, System.out);
    } else {
      processor.processInputStream(System.in, System.out);
    }    
  }
}


