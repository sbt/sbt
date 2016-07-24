package sbt.internal.util

import sjsonnew.BasicJsonProtocol

object CacheImplicits extends BasicCacheImplicits
  with BasicJsonProtocol
  with FileFormat
  with HListFormat
  with URIFormat
  with URLFormat
  with StreamFormat
