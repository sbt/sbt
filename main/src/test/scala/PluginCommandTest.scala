/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io._

import sbt.internal._
import sbt.internal.util.{
  AttributeEntry,
  AttributeMap,
  ConsoleOut,
  GlobalLogging,
  MainAppender,
  Settings,
  Terminal => ITerminal,
}

object PluginCommandTestPlugin0 extends AutoPlugin { override def requires = empty }

package subpackage {

  object PluginCommandTestPlugin1 extends AutoPlugin { override def requires = empty }

}

object PluginCommandTest extends verify.BasicTestSuite {
  import subpackage._
  import FakeState._

  test("`plugin` command should work for plugins within nested in one package") {
    val output = processCommand(
      "plugin sbt.PluginCommandTestPlugin0",
      PluginCommandTestPlugin0,
      PluginCommandTestPlugin1
    )
    assert(output.contains("sbt.PluginCommandTestPlugin0 is activated."))
  }

  test("it should work for plugins nested more than one package") {
    val output = processCommand(
      "plugin sbt.subpackage.PluginCommandTestPlugin1",
      PluginCommandTestPlugin0,
      PluginCommandTestPlugin1
    )
    assert(output.contains("sbt.subpackage.PluginCommandTestPlugin1 is activated."))
  }

  test("it should suggest a plugin when given an incorrect plugin with a similar name") {

    val output = processCommand(
      "plugin PluginCommandTestPlugin0",
      PluginCommandTestPlugin0,
      PluginCommandTestPlugin1
    )
    assert(
      output.contains(
        "Not a valid plugin: PluginCommandTestPlugin0 (similar: sbt.PluginCommandTestPlugin0, sbt.subpackage.PluginCommandTestPlugin1)"
      )
    )
  }
}

object FakeState {

  def processCommand(input: String, enabledPlugins: AutoPlugin*): String = {
    val outBuffer = new ByteArrayOutputStream
    val logFile = File.createTempFile("sbt", ".log")
    try {
      val state = FakeState(logFile, enabledPlugins: _*)
      ITerminal.withOut(new PrintStream(outBuffer, true)) {
        MainLoop.processCommand(Exec(input, None), state)
      }
      new String(outBuffer.toByteArray)
    } finally {
      logFile.delete()
      ()
    }
  }

  def apply(logFile: File, plugins: AutoPlugin*) = {

    val base = new File("").getAbsoluteFile
    val testProject = Project("test-project", base).setAutoPlugins(plugins)

    val settings: Seq[Def.Setting[_]] = Nil

    val currentProject = Map(testProject.base.toURI -> testProject.id)
    val currentEval: () => sbt.compiler.Eval = () => Load.mkEval(Nil, base, Nil)
    val sessionSettings =
      SessionSettings(base.toURI, currentProject, Nil, Map.empty, Nil, currentEval)

    val delegates: (Scope) => Seq[Scope] = _ => Nil
    val scopeLocal: Def.ScopeLocal = _ => Nil

    val (cMap, data: Settings[Scope]) =
      Def.makeWithCompiledMap(settings)(delegates, scopeLocal, Def.showFullKey)
    val extra: KeyIndex => BuildUtil[_] = (keyIndex) =>
      BuildUtil(base.toURI, Map.empty, keyIndex, data)
    val structureIndex: StructureIndex =
      Load.structureIndex(data, settings, extra, Map.empty)
    val streams: (State) => BuildStreams.Streams = null

    val loadedDefinitions: LoadedDefinitions = new LoadedDefinitions(
      base,
      Nil,
      ClassLoader.getSystemClassLoader,
      Nil,
      Seq(testProject),
      Nil
    )

    val pluginData = PluginData(Nil, Nil, None, None, Nil, Nil, Nil, Nil, Nil, None)
    val builds: DetectedModules[BuildDef] = new DetectedModules[BuildDef](Nil)

    val detectedAutoPlugins: Seq[DetectedAutoPlugin] =
      plugins.map(p => DetectedAutoPlugin(p.label, p, hasAutoImport = false))
    val detectedPlugins = new DetectedPlugins(detectedAutoPlugins, builds)
    val loadedPlugins =
      new LoadedPlugins(base, pluginData, ClassLoader.getSystemClassLoader, detectedPlugins)
    val buildUnit = new BuildUnit(base.toURI, base, loadedDefinitions, loadedPlugins)

    val (partBuildUnit: PartBuildUnit, _) = Load.loaded(buildUnit)
    val loadedBuildUnit = Load.resolveProjects(base.toURI, partBuildUnit, _ => testProject.id)

    val units = Map(base.toURI -> loadedBuildUnit)
    val buildStructure = new BuildStructure(
      units,
      base.toURI,
      settings,
      data,
      structureIndex,
      streams,
      delegates,
      scopeLocal,
      cMap,
    )

    val attributes = AttributeMap.empty ++ AttributeMap(
      AttributeEntry(Keys.sessionSettings, sessionSettings),
      AttributeEntry(Keys.stateBuildStructure, buildStructure)
    )

    State(
      null,
      Seq(BuiltinCommands.plugin),
      Set.empty,
      None,
      List(),
      State.newHistory,
      attributes,
      GlobalLogging.initial(
        MainAppender.globalDefault(ConsoleOut.globalProxy),
        logFile,
        ConsoleOut.globalProxy
      ),
      None,
      State.Continue
    )

  }

}
