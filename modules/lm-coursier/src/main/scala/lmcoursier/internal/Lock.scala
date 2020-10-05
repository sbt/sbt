package lmcoursier.internal

private[lmcoursier] object Lock {
  private val lock = new Object

  /* Progress bars require us to only work on one module at the time. Without those we can go faster */
  def maybeSynchronized[T](needsLock: Boolean)(f: => T): T =
    if (needsLock) lock.synchronized(f)
    else f
}
