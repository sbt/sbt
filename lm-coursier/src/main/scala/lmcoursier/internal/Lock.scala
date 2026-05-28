package lmcoursier.internal

private[lmcoursier] object Lock {
  private val lock = new Object

  /* The lock guards coursier's interactive progress bar (ProgressBarRefreshDisplay), the only thing
   * here that cannot be driven from more than one module at a time. coursier renders it only when no
   * custom cache logger is supplied and it is not in fallback mode; otherwise (a custom logger, or
   * the line-based fallback display) modules can be resolved in parallel. A CacheLogger is already
   * invoked concurrently within a single resolution, so it must be thread-safe regardless. */
  def progressBarActive(hasCustomLogger: Boolean, fallbackMode: Boolean): Boolean =
    !hasCustomLogger && !fallbackMode

  /* Progress bars require us to only work on one module at the time. Without those we can go faster */
  def maybeSynchronized[T](needsLock: Boolean)(f: => T): T =
    if (needsLock) lock.synchronized(f)
    else f
}
