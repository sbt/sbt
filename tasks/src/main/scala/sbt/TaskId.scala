package sbt

trait TaskId[A]:
  def tags: ConcurrentRestrictions.TagMap
