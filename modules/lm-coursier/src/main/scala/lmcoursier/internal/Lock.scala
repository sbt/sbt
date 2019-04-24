package lmcoursier.internal

private[lmcoursier] object Lock {

  // Wrap blocks downloading stuff (resolution / artifact downloads) in lock.synchronized.
  // Downloads are already parallel, no need to parallelize further, and this results in
  // a clearer output.
  val lock = new Object

}
