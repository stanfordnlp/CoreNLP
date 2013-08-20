package edu.stanford.nlp.stats;

import java.util.*;

/**
 * This class takes multiple nlp processors
 * (such as POS tagger, parser, NER tagger, etc)
 * that implement (@link Sampleable} and are
 * put together in a pipeline, and draws samples from
 * each stage passing the samples from one stage to
 * the next.  It is used to draw samples from
 * the space of complete labelings (over all stages)
 * and itself implements {@link Sampleable}.
 *
 * @author Jenny Finkel
 */
@SuppressWarnings("unchecked")
public class SamplingPipeline<T1,T2> implements Sampleable<T1,T2> {

  private List<Sampleable> stages;

  public SamplingPipeline (List<Sampleable> stages) {
    this.stages = new ArrayList(stages); // defensive copy
  }

  /**
   * This method takes the input and puts it into the
   * first stage of the pipeline, takes the output from that
   * and feeds it through the second stage, ..., and
   * eventually returns the output from the last stage.
   */
  public Sampler<T2> getSampler (final T1 input) {

    return new Sampler<T2>() {
      public T2 drawSample() {
        Object result = input;
        for (Sampleable stage : stages) {
          Sampler sampler = stage.getSampler(result);
          result = sampler.drawSample();
        }
        return (T2)result;
      }
    };
  }
}
