/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.complete.Parser
import sbt.Keys.{ bgJobService, bgList, streams }

object BgJobsTasks {

  private def foreachJobTask(
      f: (BackgroundJobService, JobHandle) => Unit
  ): Def.Initialize[InputTask[Unit]] = {
    val parser: Def.Initialize[State => Parser[Seq[JobHandle]]] = Def.setting { (s: State) =>
      val extracted = Project.extract(s)
      val service = extracted.get(bgJobService)
      // you might be tempted to use the jobList task here, but the problem
      // is that its result gets cached during execution and therefore stale
      BackgroundJobService.jobIdParser(s, service.jobs)
    }
    Def.inputTask {
      val handles = parser.parsed
      for (handle <- handles) {
        f(bgJobService.value, handle)
      }
    }
  }

  def psTask: Def.Initialize[Task[Seq[JobHandle]]] =
    Def.task {
      val xs = bgList.value
      val s = streams.value
      xs foreach { x =>
        s.log.info(x.toString)
      }
      xs
    }

  def bgStopTask: Def.Initialize[InputTask[Unit]] = foreachJobTask { (manager, handle) =>
    manager.stop(handle)
  }

  def bgWaitForTask: Def.Initialize[InputTask[Unit]] = foreachJobTask { (manager, handle) =>
    manager.waitForTry(handle)
    ()
  }
}
