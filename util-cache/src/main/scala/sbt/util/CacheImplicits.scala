/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.BasicJsonProtocol

object CacheImplicits extends CacheImplicits
trait CacheImplicits extends BasicCacheImplicits with BasicJsonProtocol
