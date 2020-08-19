/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.{ baseDirectory, connectInput, envVars, javaHome, javaOptions, outputStrategy }

private[sbt] object ForkOptionsTask {
  val get: Def.Initialize[Task[ForkOptions]] =
    Def.task {
      ForkOptions(
        javaHome = javaHome.value,
        outputStrategy = outputStrategy.value,
        // bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
        bootJars = Vector(),
        workingDirectory = Some(baseDirectory.value),
        runJVMOptions = javaOptions.value.toVector,
        connectInput = connectInput.value,
        envVars = envVars.value
      )
    }
}
