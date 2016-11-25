package sbt.internal.util

import sjsonnew.BasicJsonProtocol

object CacheImplicits extends BasicCacheImplicits
  with BasicJsonProtocol
  with HListFormat
  with StreamFormat
