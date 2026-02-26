/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.util.Level

object InitialLogLevelSpec extends verify.BasicTestSuite:

  test("logLevelFromArguments returns Debug when -debug is in arguments"):
    assert(StandardMain.logLevelFromArguments(Seq("-debug")) == Level.Debug)

  test("logLevelFromArguments returns Debug when --debug is in arguments"):
    assert(StandardMain.logLevelFromArguments(Seq("compile", "--debug")) == Level.Debug)

  test("logLevelFromArguments returns Debug when early(debug) is in arguments"):
    assert(StandardMain.logLevelFromArguments(Seq("early(debug)")) == Level.Debug)
    assert(StandardMain.logLevelFromArguments(Seq("compile", "early(debug)")) == Level.Debug)

  test("logLevelFromArguments returns Info when no level option in arguments"):
    assert(StandardMain.logLevelFromArguments(Seq()) == Level.Info)
    assert(StandardMain.logLevelFromArguments(Seq("compile", "run")) == Level.Info)

  test("logLevelFromArguments uses first level option when multiple present"):
    assert(StandardMain.logLevelFromArguments(Seq("-warn", "-debug")) == Level.Warn)
    assert(StandardMain.logLevelFromArguments(Seq("-error", "-info")) == Level.Error)

  test("logLevelFromArguments supports all level options"):
    assert(StandardMain.logLevelFromArguments(Seq("-info")) == Level.Info)
    assert(StandardMain.logLevelFromArguments(Seq("--warn")) == Level.Warn)
    assert(StandardMain.logLevelFromArguments(Seq("--error")) == Level.Error)
end InitialLogLevelSpec
