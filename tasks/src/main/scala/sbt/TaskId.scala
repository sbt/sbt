package sbt

trait TaskId[A]:
  def tags: ConcurrentRestrictions.TagMap

  /**
   * Which task a [[CompletionService]] should start next when one it is holding back becomes
   * runnable. 0 is the same standing as every other task, negative is increasingly ahead of them,
   * positive increasingly behind. Equal priorities keep submission order.
   */
  def priority: Int = 0
