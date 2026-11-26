/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

scalaVersion := "3.3.9"
libraryDependencies += "org.specs2" %% "specs2-core" % "4.23.0" % Test

Global / localCacheDirectory := baseDirectory.value / "diskcache"
