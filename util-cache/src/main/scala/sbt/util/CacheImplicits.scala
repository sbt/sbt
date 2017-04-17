package sbt.util

import sjsonnew.BasicJsonProtocol
import sbt.internal.util.HListFormats

object CacheImplicits extends CacheImplicits
trait CacheImplicits extends BasicCacheImplicits
  with BasicJsonProtocol
  with HListFormats
