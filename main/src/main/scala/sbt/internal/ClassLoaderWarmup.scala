/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.{ ExecutorService, Executors }
import sbt.plugins.{ CorePlugin, IvyPlugin, JvmPlugin }

private[internal] object ClassLoaderWarmup {
  def warmup(): Unit = {
    if (Runtime.getRuntime.availableProcessors > 1) {
      val executorService: ExecutorService =
        Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors - 1)
      def submit[R](f: => R): Unit = {
        executorService.submit(new Runnable {
          override def run(): Unit = { f; () }
        })
        ()
      }

      submit(Class.forName("sbt.internal.parser.SbtParserInit").getConstructor().newInstance())
      submit(CorePlugin.projectSettings)
      submit(IvyPlugin.projectSettings)
      submit(JvmPlugin.projectSettings)
      submit(() => {
        try {
          val clazz = Class.forName("scala.reflect.runtime.package$")
          clazz.getMethod("universe").invoke(clazz.getField("MODULE$").get(null))
        } catch {
          case _: Exception =>
        }
        executorService.shutdown()
      })
    }
  }
}
