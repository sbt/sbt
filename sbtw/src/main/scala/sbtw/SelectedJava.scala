/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbtw

import java.io.File

/** The JDK chosen to launch sbt: `javaCmd` to run, plus `envOverlay` so indirect launches use it too. */
final case class SelectedJava(
    javaCmd: String,
    envOverlay: Seq[(String, String)],
    explicit: Boolean = false
):
  /** Overlay for the native-client handoff, plus the one-hop explicit marker. */
  def handoffEnv: Seq[(String, String)] =
    if explicit then envOverlay :+ (SelectedJava.explicitMarker -> "1") else envOverlay
end SelectedJava

object SelectedJava:

  /** One-hop marker telling `sbt.bat`'s `.java-version` block to defer to an explicit `--java-home`. */
  val explicitMarker = "SBT_EXPLICIT_JAVA_HOME"

  /** Resolves `--java-home` > `JAVACMD` > `JAVA_HOME` > `PATH`; an invalid `--java-home` is an error. */
  def resolve(
      javaHome: Option[String],
      env: Map[String, String] = sys.env
  ): Either[String, SelectedJava] =
    javaHome match
      case Some(home) =>
        val exe = new File(home, "bin/java.exe")
        if exe.isFile then Right(fromHome(home, env, explicit = true))
        else Left(s"""[error] Directory "$home" for JAVA_HOME is not valid""")
      case None =>
        getIgnoreCase(env, "JAVACMD").map(stripQuotes) match
          case Some(cmd) => Right(SelectedJava(cmd, Nil))
          case None      =>
            getIgnoreCase(env, "JAVA_HOME") match
              case Some(home) => Right(fromHome(home, env, explicit = false))
              case None       => Right(SelectedJava("java", Nil))

  private def fromHome(home: String, env: Map[String, String], explicit: Boolean): SelectedJava =
    val absoluteHome = new File(home).getAbsolutePath
    val cmd = new File(absoluteHome, "bin/java.exe").getAbsolutePath
    val bin = absoluteHome + File.separator + "bin"
    val path = getIgnoreCase(env, "PATH") match
      case Some(old) => bin + File.pathSeparator + old
      case None      => bin
    val overlay = Seq(
      keyFor(env, "JAVACMD") -> cmd,
      keyFor(env, "JAVA_HOME") -> absoluteHome,
      keyFor(env, "JDK_HOME") -> absoluteHome,
      keyFor(env, "PATH") -> path
    )
    SelectedJava(cmd, overlay, explicit)

  private def getIgnoreCase(env: Map[String, String], key: String): Option[String] =
    env.collectFirst { case (k, v) if k.equalsIgnoreCase(key) => v }

  /** Existing env key matching `name` case-insensitively, so overlays replace rather than duplicate. */
  private def keyFor(env: Map[String, String], name: String): String =
    env.keys.find(_.equalsIgnoreCase(name)).getOrElse(name)

  private def stripQuotes(s: String): String = s.replace("\"", "")
end SelectedJava
