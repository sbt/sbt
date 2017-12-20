/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

object KeyRanks {
  // task and setting ranks, used to prioritize displaying information
  // main tasks
  final val APlusTask = 4
  final val ATask = 5
  final val AMinusTask = 6

  // main settings
  final val APlusSetting = 9
  final val ASetting = 10
  final val AMinusSetting = 11

  // less major tasks or tasks that print useful information
  final val BPlusTask = 29
  final val BTask = 30
  final val BMinusTask = 31

  // secondary settings
  final val BPlusSetting = 39
  final val BSetting = 40
  final val BMinusSetting = 41

  // advanced settings
  final val CSetting = 100
  // advanced tasks
  final val CTask = 200
  // explicit settings
  final val DSetting = 10000
  // explicit tasks
  final val DTask = 20000

  final val MainTaskCutoff = AMinusTask
  final val MainSettingCutoff = AMinusSetting
  final val MainCutoff = math.max(AMinusTask, AMinusSetting)

  final val DefaultTaskRank = (ATask + BTask) / 2
  final val DefaultInputRank = ATask // input tasks are likely a main task
  final val DefaultSettingRank = (ASetting + BSetting) / 2

  // implementation details
  val Invisible = Int.MaxValue
}
