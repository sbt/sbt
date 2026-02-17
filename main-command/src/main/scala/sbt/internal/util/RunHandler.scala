/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package util

import java.io.File
import java.nio.file.Paths
import sbt.internal.worker.JvmRunInfo
import sbt.util.Logger
import scala.util.Try

// Process runinfos
object RunHandler:
  private[internal] def mergedEnvVars(
      environmentVariables: Map[String, String]
  ): Map[String, String] =
    sys.env.toMap ++ environmentVariables

  def jvmRun(info: JvmRunInfo, log: Logger): Try[Unit] =
    val option = ForkOptions(
      javaHome = info.javaHome.map(File(_)),
      outputStrategy = None,
      bootJars = Vector.empty,
      workingDirectory = info.workingDirectory.map(File(_)),
      runJVMOptions = info.jvmOptions,
      connectInput = info.connectInput,
      envVars = mergedEnvVars(info.environmentVariables),
    )
    // ForkRun handles exit code handling and cancellation
    val runner = new ForkRun(option)
    runner
      .run(
        mainClass = info.mainClass,
        classpath = info.classpath.map(_.path).map(Paths.get),
        options = info.args,
        log = log
      )
end RunHandler
