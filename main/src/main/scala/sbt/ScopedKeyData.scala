/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import Def.ScopedKey
import sbt.internal.util.KeyTag

final case class ScopedKeyData[A](key: ScopedKey[A], definingKey: ScopedKey[A], value: Any) {
  def typeName: String = key.key.tag.toString
  def settingValue: Option[Any] =
    key.key.tag match
      case KeyTag.Setting(_) => Some(value)
      case _                 => None
  def description: String =
    key.key.tag match
      case KeyTag.Task(typeArg)      => s"Task: $typeArg"
      case KeyTag.SeqTask(typeArg)   => s"Task: Seq[$typeArg]"
      case KeyTag.InputTask(typeArg) => s"Input task: $typeArg"
      case KeyTag.Setting(typeArg)   => s"Setting: $typeArg = $value"
}
