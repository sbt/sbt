/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.Files
import java.nio.file.attribute.FileTime

import sbt.io.IO
import verify.BasicTestSuite

object PluginDiscoveryTest extends BasicTestSuite:
  import PluginDiscovery.Paths.AutoPlugins

  test("writeDescriptor preserves mtime when the descriptor content is unchanged"):
    val directory = Files.createTempDirectory("plugin-discovery-test").toFile
    try
      val descriptor =
        PluginDiscovery.writeDescriptor(Seq("example.B", "example.A"), directory, AutoPlugins).get
      val expectedTime = FileTime.fromMillis(1234L)
      Files.setLastModifiedTime(descriptor.toPath, expectedTime)

      PluginDiscovery.writeDescriptor(
        Seq("example.A", "example.B", "example.A"),
        directory,
        AutoPlugins
      )

      assert(Files.getLastModifiedTime(descriptor.toPath) == expectedTime)
    finally IO.delete(directory)

  test("writeDescriptor updates the descriptor when its content changes"):
    val directory = Files.createTempDirectory("plugin-discovery-test").toFile
    try
      val descriptor = PluginDiscovery.writeDescriptor(Seq("example.A"), directory, AutoPlugins).get

      PluginDiscovery.writeDescriptor(Seq("example.B"), directory, AutoPlugins)

      assert(IO.readLines(descriptor) == Seq("example.B"))
    finally IO.delete(directory)

  test("writeDescriptor deletes the descriptor when no modules are discovered"):
    val directory = Files.createTempDirectory("plugin-discovery-test").toFile
    try
      val descriptor = PluginDiscovery.writeDescriptor(Seq("example.A"), directory, AutoPlugins).get

      assert(PluginDiscovery.writeDescriptor(Nil, directory, AutoPlugins).isEmpty)
      assert(!descriptor.exists)
    finally IO.delete(directory)
end PluginDiscoveryTest
