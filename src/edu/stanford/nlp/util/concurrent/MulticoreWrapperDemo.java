package edu.stanford.nlp.util.concurrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * Illustrates simple multithreading of threadsafe objects. See
 * the MulticoreWrapperTest (unit test) for another example. 
 * 
 * @author Spence Green
 *
 */
public class MulticoreWrapperDemo {

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s model_file < input_file%n", MulticoreWrapperDemo.class.getName());
      System.exit(-1);
    }
    try {
      // Load MaxentTagger, which is threadsafe
      String modelFile = args[0];      
      final MaxentTagger tagger = new MaxentTagger(modelFile);
      
      // Configure to run with 4 worker threads
      int nThreads = 4;
      MulticoreWrapper<String,String> wrapper = 
          new MulticoreWrapper<String,String>(nThreads, 
              new ThreadsafeProcessor<String,String>(){
                @Override
                public String process(String input) {
                  return tagger.tagString(input);
                } 
                @Override
                public ThreadsafeProcessor<String, String> newInstance() {
                  // MaxentTagger is threadsafe
                  return this;
                }
              });

      // Submit jobs, which come from stdin
      BufferedReader br = 
          new BufferedReader(new InputStreamReader(System.in));
      for (String line; (line = br.readLine()) != null;) {
        wrapper.put(line);
        while(wrapper.peek()) {
          System.out.println(wrapper.poll());
        }
      }

      // Finished reading the input. Wait for jobs to finish
      wrapper.join();
      while(wrapper.peek()) {
        System.out.println(wrapper.poll());
      }

    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }
}
