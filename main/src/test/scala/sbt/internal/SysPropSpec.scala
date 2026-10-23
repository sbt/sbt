/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import hedgehog.*
import hedgehog.runner.*

object SysPropSpec extends Properties:
  private val genDir: Gen[String] =
    Gen.string(Gen.alpha, Range.linear(1, 10)).map("/" + _)

  private def cache(
      props: Map[String, String] = Map.empty,
      env: Map[String, String] = Map.empty,
      windows: Boolean = false,
      mac: Boolean = false,
      home: String
  ): File =
    SysProp.globalLocalCacheFrom(props.get, env.get, windows, mac, new File(home))

  private def expected(base: File): File = new File(base.getAbsoluteFile, "v2")

  override def tests: List[Test] =
    List(
      property(
        "prefers XDG_CACHE_HOME over LOCALAPPDATA on Windows",
        for
          xdg <- genDir.forAll
          app <- genDir.forAll
          home <- genDir.forAll
        yield
          val env = Map("XDG_CACHE_HOME" -> xdg, "LOCALAPPDATA" -> app)
          cache(env = env, windows = true, home = home) ==== expected(new File(xdg, "sbt"))
      ),
      property(
        "prefers XDG_CACHE_HOME over Library/Caches on macOS",
        for
          xdg <- genDir.forAll
          home <- genDir.forAll
        yield
          val env = Map("XDG_CACHE_HOME" -> xdg)
          cache(env = env, mac = true, home = home) ==== expected(new File(xdg, "sbt"))
      ),
      property(
        "uses LOCALAPPDATA on Windows without XDG_CACHE_HOME",
        for
          app <- genDir.forAll
          home <- genDir.forAll
        yield
          val env = Map("LOCALAPPDATA" -> app)
          cache(env = env, windows = true, home = home) ==== expected(new File(app, "sbt")) and
            cache(env = env, home = home) ==== expected(new File(home, ".cache/sbt"))
      ),
      property(
        "uses Library/Caches on macOS without XDG_CACHE_HOME",
        genDir.forAll.map: home =>
          cache(mac = true, home = home) ==== expected(new File(home, "Library/Caches/sbt"))
      ),
      property(
        "SBT_LOCAL_CACHE wins over XDG_CACHE_HOME",
        for
          local <- genDir.forAll
          xdg <- genDir.forAll
          home <- genDir.forAll
        yield
          val env = Map("SBT_LOCAL_CACHE" -> local, "XDG_CACHE_HOME" -> xdg)
          cache(env = env, home = home) ==== expected(new File(local))
      ),
      property(
        "sbt.global.localcache wins over everything",
        for
          local <- genDir.forAll
          other <- genDir.forAll
        yield
          val props = Map("sbt.global.localcache" -> local, "sbt.global.base" -> other)
          val env = Map("SBT_LOCAL_CACHE" -> other, "XDG_CACHE_HOME" -> other)
          cache(props, env, windows = true, mac = true, home = other) ==== expected(new File(local))
      ),
    )
end SysPropSpec
