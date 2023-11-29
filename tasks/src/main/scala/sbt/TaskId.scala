package sbt

import sbt.internal.util.AttributeMap

trait TaskId[A]:
  def tags: ConcurrentRestrictions.TagMap
