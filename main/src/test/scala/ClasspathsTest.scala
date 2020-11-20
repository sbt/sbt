/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.test

import sbt._
import sbt.Classpaths._
import sbt.Def.Initialize

class ClasspathsTest[T](
    settKey: SettingKey[Seq[T]],
    taskKey: TaskKey[Seq[T]],
    initVal: Initialize[Seq[T]],
    taskVal: Initialize[Task[Seq[T]]],
) {

  def testConcat() = {
    concat(settKey, settKey)
    concat(settKey, taskKey)
    concat(settKey, initVal)
    concat(settKey, taskVal)
    concat(taskKey, settKey)
    concat(taskKey, taskKey)
    concat(taskKey, initVal)
    concat(taskKey, taskVal)
    concat(initVal, settKey)
    concat(initVal, taskKey)
    concat(initVal, initVal)
    concat(initVal, taskVal)
    concat(taskVal, settKey)
    concat(taskVal, taskKey)
    concat(taskVal, initVal)
    concat(taskVal, taskVal)
  }

  def testConcatSettings() = {
    concatSettings(settKey, settKey)
    concatSettings(settKey, initVal)
    concatSettings(initVal, settKey)
    concatSettings(initVal, initVal)
  }
}
