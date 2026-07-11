/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbtw

import java.io.File
import java.nio.file.Files

object SelectedJavaSpec extends verify.BasicTestSuite:

  private def jdkHome(): File =
    val home = Files.createTempDirectory("sbtw-jdk").toFile
    val exe = new File(home, "bin/java.exe")
    exe.getParentFile.mkdirs()
    exe.createNewFile()
    home

  test("resolve should use a valid --java-home") {
    val home = jdkHome()
    val selected = SelectedJava.resolve(Some(home.getPath), Map.empty).toOption.get
    assert(selected.javaCmd == new File(home, "bin/java.exe").getAbsolutePath)
  }

  test("resolve should reject an invalid --java-home instead of falling back") {
    val result =
      SelectedJava.resolve(Some("no-such-dir"), Map("JAVACMD" -> "C:\\jdk\\bin\\java.exe"))
    assert(result.isLeft)
    assert(result.swap.exists(_.contains("for JAVA_HOME is not valid")))
  }

  test("--java-home overlay makes JAVACMD authoritative over an inherited one") {
    val home = jdkHome()
    val exe = new File(home, "bin/java.exe").getAbsolutePath
    val overlay =
      SelectedJava
        .resolve(Some(home.getPath), Map("JAVACMD" -> "C:\\old\\bin\\java.exe"))
        .toOption
        .get
        .envOverlay
        .toMap
    assert(overlay.get("JAVACMD") == Some(exe))
    assert(overlay.get("JAVA_HOME") == Some(home.getPath))
    assert(overlay.get("JDK_HOME") == Some(home.getPath))
    val expectedPath = home.getPath + File.separator + "bin" + File.pathSeparator + "C:\\old"
    assert(overlay.get("PATH") == Some(home.getPath + File.separator + "bin"))
    assert(
      SelectedJava
        .resolve(Some(home.getPath), Map("PATH" -> "C:\\old"))
        .toOption
        .get
        .envOverlay
        .toMap
        .get("PATH") == Some(expectedPath)
    )
  }

  test("resolve should prefer JAVACMD over JAVA_HOME with no overlay") {
    val env = Map("JAVACMD" -> "C:\\some\\java.exe", "JAVA_HOME" -> "C:\\jdk")
    val selected = SelectedJava.resolve(None, env).toOption.get
    assert(selected.javaCmd == "C:\\some\\java.exe")
    assert(selected.envOverlay.isEmpty)
  }

  test("resolve should strip quotes from JAVACMD") {
    val selected =
      SelectedJava.resolve(None, Map("JAVACMD" -> "\"C:\\some\\java.exe\"")).toOption.get
    assert(selected.javaCmd == "C:\\some\\java.exe")
  }

  test("a JDK derived from JAVA_HOME still gets a full overlay (PATH prepend)") {
    val home = jdkHome().getPath
    val selected =
      SelectedJava.resolve(None, Map("JAVA_HOME" -> home, "PATH" -> "/old")).toOption.get
    assert(selected.javaCmd == new File(home, "bin/java.exe").getAbsolutePath)
    val overlay = selected.envOverlay.toMap
    assert(overlay.get("JAVA_HOME") == Some(home))
    assert(overlay.get("PATH") == Some(home + File.separator + "bin" + File.pathSeparator + "/old"))
  }

  test("a relative home is exported as an absolute path") {
    val overlay =
      SelectedJava.resolve(None, Map("JAVA_HOME" -> "rel-jdk")).toOption.get.envOverlay.toMap
    assert(new File(overlay("JAVA_HOME")).isAbsolute)
    assert(new File(overlay("JDK_HOME")).isAbsolute)
  }

  test("resolve should read JAVACMD case-insensitively") {
    val selected = SelectedJava.resolve(None, Map("JavaCmd" -> "C:\\x\\java.exe")).toOption.get
    assert(selected.javaCmd == "C:\\x\\java.exe")
    assert(selected.envOverlay.isEmpty)
  }

  test("resolve should read JAVA_HOME case-insensitively") {
    val home = jdkHome().getPath
    val selected = SelectedJava.resolve(None, Map("Java_Home" -> home)).toOption.get
    assert(selected.javaCmd == new File(home, "bin/java.exe").getAbsolutePath)
  }

  test("resolve should fall back to bare java with no overlay") {
    val selected = SelectedJava.resolve(None, Map.empty).toOption.get
    assert(selected.javaCmd == "java")
    assert(selected.envOverlay.isEmpty)
  }

  test("overlay should reuse the existing PATH key case") {
    val overlay = SelectedJava
      .resolve(Some(jdkHome().getPath), Map("Path" -> "C:\\old"))
      .toOption
      .get
      .envOverlay
      .toMap
    assert(overlay.contains("Path"))
    assert(!overlay.contains("PATH"))
  }

  test("an explicit --java-home carries the provenance marker only on the handoff") {
    val selected = SelectedJava.resolve(Some(jdkHome().getPath), Map.empty).toOption.get
    assert(!selected.envOverlay.toMap.contains(SelectedJava.explicitMarker))
    assert(selected.handoffEnv.toMap.get(SelectedJava.explicitMarker) == Some("1"))
  }

  test("a JAVA_HOME-derived selection never carries the provenance marker") {
    val selected = SelectedJava.resolve(None, Map("JAVA_HOME" -> jdkHome().getPath)).toOption.get
    assert(!selected.envOverlay.toMap.contains(SelectedJava.explicitMarker))
    assert(!selected.handoffEnv.toMap.contains(SelectedJava.explicitMarker))
  }

  test("overlay should replace any-cased alias rather than duplicate it") {
    val home = jdkHome().getPath
    val overlay =
      SelectedJava
        .resolve(Some(home), Map("javacmd" -> "old", "JavaCmd2" -> "x"))
        .toOption
        .get
        .envOverlay
        .toMap
    assert(overlay.contains("javacmd"))
    assert(!overlay.contains("JAVACMD"))
    assert(overlay.get("javacmd") == Some(new File(home, "bin/java.exe").getAbsolutePath))
    assert(overlay.contains("JAVA_HOME"))
  }
end SelectedJavaSpec
