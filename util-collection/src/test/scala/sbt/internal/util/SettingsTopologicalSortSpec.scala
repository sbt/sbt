/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import verify.BasicTestSuite

object SettingsTopologicalSortSpec extends BasicTestSuite:
  import Init.*

  test("Topological sort provides informative error for missing dependencies") {
    // Create a malformed CompiledMap where a dependency is missing
    // This simulates a bug in settings compilation
    val key1 = ScopedKey(Scope(0), AttributeKey[Int]("key1"))
    val key2 = ScopedKey(Scope(0), AttributeKey[Int]("key2"))
    val key3 = ScopedKey(Scope(0), AttributeKey[Int]("key3"))
    
    // Create Compiled entries where key2 depends on key3, but key3 is not in the map
    val compiled1 = Compiled(key1, Nil, Nil)
    val compiled2 = Compiled(key2, Seq(key3), Nil) // key2 depends on key3, but key3 is missing
    
    val malformedMap: Map[ScopedKey[?], Compiled[?]] = Map(
      key1 -> compiled1,
      key2 -> compiled2
      // key3 is intentionally missing
    )
    
    // This should throw an IllegalStateException with a helpful error message
    try {
      Init.sort(malformedMap)
      assert(false, "Expected IllegalStateException for missing dependency")
    } catch {
      case e: IllegalStateException =>
        val msg = e.getMessage
        assert(
          msg.contains("dependency") || msg.contains("not found"),
          s"Error message should mention missing dependency, got: $msg"
        )
        assert(
          msg.contains("key2") || msg.contains("key3"),
          s"Error message should mention the keys involved, got: $msg"
        )
      case e: Throwable =>
        assert(false, s"Expected IllegalStateException, got ${e.getClass.getName}: ${e.getMessage}")
    }
  }
end SettingsTopologicalSortSpec

