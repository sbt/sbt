/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

object XXHashConstants:
  final val PRIME1 = -1640531535
  final val PRIME2 = -2048144777
  final val PRIME3 = -1028477379
  final val PRIME4 = 668265263
  final val PRIME5 = 374761393

  final val PRIME64_1 = -7046029288634856825L // 11400714785074694791
  final val PRIME64_2 = -4417276706812531889L // 14029467366897019727
  final val PRIME64_3 = 1609587929392839161L
  final val PRIME64_4 = -8796714831421723037L // 9650029242287828579
  final val PRIME64_5 = 2870177450012600261L
end XXHashConstants
