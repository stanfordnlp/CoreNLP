package edu.stanford.nlp.trees;

/**
 * If you have a TreeTransformer which is not threadsafe, and you need
 * to call it from multiple threads, this will wrap it in a
 * synchronized manner.
 *
 * @author John Bauer
 */
public class SynchronizedTreeTransformer implements TreeTransformer {
  final TreeTransformer threadUnsafe;

  public SynchronizedTreeTransformer(TreeTransformer threadUnsafe) {
    this.threadUnsafe = threadUnsafe;
  }

  public Tree transformTree(Tree t) {
    synchronized(threadUnsafe) {
      return threadUnsafe.transformTree(t);
    }
  }
}
