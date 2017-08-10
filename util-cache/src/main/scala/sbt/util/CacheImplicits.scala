package sbt.util

import sjsonnew.BasicJsonProtocol

object CacheImplicits extends CacheImplicits
trait CacheImplicits extends BasicCacheImplicits with BasicJsonProtocol
