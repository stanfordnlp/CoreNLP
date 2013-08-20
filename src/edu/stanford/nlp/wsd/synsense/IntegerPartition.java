package edu.stanford.nlp.wsd.synsense;

/**
 * Represents a partition of the integers. The partition is defined at construction by
 * an array of ints, called dividers. The partitions are then defined as follows. Partition 0 includes all integers
 * from negative infinity to dividers[0] exclusive, partition 1 includes all integers from dividers[0] inclusive to
 * dividers[1] exclusive, and so on. The last partition (numbered dividers.length) includes all integers from
 * dividers[dividers.length-1] inclusive to positive infinity.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class IntegerPartition {
  int[] dividers;

  public int[] getDividers() {
    return dividers;
  }

  public void setDividers(int[] dividers) {
    this.dividers = dividers;
  }

  public int getBucket(int n) {
    for (int i = 0; i < dividers.length; i++) {
      if (n < dividers[i]) {
        return i; // the bucket bounded above by dividers[i] exclusive
      }
    }
    return dividers.length; // the last bucket
  }

  public IntegerPartition(int[] dividers) {
    this.dividers = dividers;
  }
}
