package sbt.extra.dsl

import sbt._
import Scoped._
import Project._
import Project.Setting

/** Represents the new 'id' of a setting to define on a project. */
final class SettingId(name: String) {
  /** Creates a Task that has no dependencies. */
  final def is[R: Manifest](f: => R) =
    SettingKey[R](name) := f
  final def on[A1](a: Initialize[A1]): SettingDepend1[A1] = 
    new SettingDepend1[A1](name, a)
  final def on[A1, A2](a1: Initialize[A1], a2: Initialize[A2]): SettingDepend2[A1, A2] =
    new SettingDepend2[A1, A2](name, a1, a2)
  final def on[A1, A2, A3](a1: Initialize[A1],
                           a2: Initialize[A2],
                           a3: Initialize[A3]) =
    new SettingDepend3[A1, A2, A3](name, a1, a2, a3)
  final def on[A1, A2, A3, A4](a1: Initialize[A1],
                              a2: Initialize[A2],
                              a3: Initialize[A3],
                              a4: Initialize[A4]) =
    new SettingDepend4[A1, A2, A3, A4](name, a1, a2, a3, a4)
  final def on[A1, A2, A3, A4, A5](a1: Initialize[A1],
                                   a2: Initialize[A2],
                                   a3: Initialize[A3],
                                   a4: Initialize[A4],
                                   a5: Initialize[A5]) =
    new SettingDepend5[A1, A2, A3, A4, A5](name, a1, a2, a3, a4, a5)
  final def on[A1, A2, A3, A4, A5, A6](a1: Initialize[A1],
                                       a2: Initialize[A2],
                                       a3: Initialize[A3],
                                       a4: Initialize[A4],
                                       a5: Initialize[A5],
                                       a6: Initialize[A6]) =
    new SettingDepend6[A1, A2, A3, A4, A5, A6](name, a1, a2, a3, a4, a5, a6)
  final def on[A1, A2, A3, A4, A5, A6, A7](a1: Initialize[A1],
                                           a2: Initialize[A2],
                                           a3: Initialize[A3],
                                           a4: Initialize[A4],
                                           a5: Initialize[A5],
                                           a6: Initialize[A6],
                                           a7: Initialize[A7]) =
    new SettingDepend7[A1, A2, A3, A4, A5, A6, A7](name, a1, a2, a3, a4, a5, a6, a7)
  final def on[A1, A2, A3, A4, A5, A6, A7, A8](a1: Initialize[A1],
                                               a2: Initialize[A2],
                                               a3: Initialize[A3],
                                               a4: Initialize[A4],
                                               a5: Initialize[A5],
                                               a6: Initialize[A6],
                                               a7: Initialize[A7],
                                               a8: Initialize[A8]) =
    new SettingDepend8[A1, A2, A3, A4, A5, A6, A7, A8](name, a1, a2, a3, a4, a5, a6, a7, a8)
  final def on[A1, A2, A3, A4, A5, A6, A7, A8, A9](a1: Initialize[A1],
                                                   a2: Initialize[A2],
                                                   a3: Initialize[A3],
                                                   a4: Initialize[A4],
                                                   a5: Initialize[A5],
                                                   a6: Initialize[A6],
                                                   a7: Initialize[A7],
                                                   a8: Initialize[A8],
                                                   a9: Initialize[A9]) =
    new SettingDepend9[A1, A2, A3, A4, A5, A6, A7, A8, A9](name, a1, a2, a3, a4, a5, a6, a7, a8, a9)
}

/** Represents a not-yet-defined Setting that has one dependency */
final class SettingDepend1[A1](name: String, a1: Initialize[A1]) {
  final def is[R: Manifest](f: A1 => R): Setting[R] = {
    SettingKey[R](name) <<= a1 apply f
  }
}

/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend2[A1, A2](name: String,
                                a1: Initialize[A1],
                                a2: Initialize[A2]) {
  final def is[R: Manifest](f: (A1, A2) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend3[A1, A2, A3](name: String,
                                    a1: Initialize[A1],
                                    a2: Initialize[A2],
                                    a3: Initialize[A3]) {
  final def is[R: Manifest](f: (A1, A2, A3) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend4[A1, A2, A3, A4](name: String,
                                        a1: Initialize[A1],
                                        a2: Initialize[A2],
                                        a3: Initialize[A3],
                                        a4: Initialize[A4]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend5[A1, A2, A3, A4, A5](name: String,
                                            a1: Initialize[A1],
                                            a2: Initialize[A2],
                                            a3: Initialize[A3],
                                            a4: Initialize[A4],
                                            a5: Initialize[A5]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4, a5) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend6[A1, A2, A3, A4, A5, A6](name: String,
                                                a1: Initialize[A1],
                                                a2: Initialize[A2],
                                                a3: Initialize[A3],
                                                a4: Initialize[A4],
                                                a5: Initialize[A5],
                                                a6: Initialize[A6]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4, a5, a6) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend7[A1, A2, A3, A4, A5, A6, A7](name: String,
                                                    a1: Initialize[A1],
                                                    a2: Initialize[A2],
                                                    a3: Initialize[A3],
                                                    a4: Initialize[A4],
                                                    a5: Initialize[A5],
                                                    a6: Initialize[A6],
                                                    a7: Initialize[A7]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend8[A1, A2, A3, A4, A5, A6, A7, A8](name: String,
                                                        a1: Initialize[A1],
                                                        a2: Initialize[A2],
                                                        a3: Initialize[A3],
                                                        a4: Initialize[A4],
                                                        a5: Initialize[A5],
                                                        a6: Initialize[A6],
                                                        a7: Initialize[A7],
                                                        a8: Initialize[A8]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7, A8) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7, a8) apply f
  }
}
/** Represents a not-yet-defined Setting that has two dependencies */
final class SettingDepend9[A1, A2, A3, A4, A5, A6, A7, A8, A9](name: String,
                                                            a1: Initialize[A1],
                                                            a2: Initialize[A2],
                                                            a3: Initialize[A3],
                                                            a4: Initialize[A4],
                                                            a5: Initialize[A5],
                                                            a6: Initialize[A6],
                                                            a7: Initialize[A7],
                                                            a8: Initialize[A8],
                                                            a9: Initialize[A9]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9) => R): Setting[R] = {
    SettingKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7, a8, a9) apply f
  }
}



