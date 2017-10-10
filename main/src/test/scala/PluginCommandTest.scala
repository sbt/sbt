/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io._

import org.specs2.mutable.Specification

import sbt.internal._
import sbt.internal.util.{
  AttributeEntry,
  AttributeMap,
  ConsoleOut,
  GlobalLogging,
  MainAppender,
  Settings
}

object PluginCommandTestPlugin0 extends AutoPlugin { override def requires = empty }

package subpackage {

  object PluginCommandTestPlugin1 extends AutoPlugin { override def requires = empty }

}

object PluginCommandTest extends Specification {
  sequential

  import subpackage._
  import FakeState._

  "The `plugin` command" should {

    "should work for plugins within nested in one package" in {
      val output = processCommand("plugin sbt.PluginCommandTestPlugin0",
                                  PluginCommandTestPlugin0,
                                  PluginCommandTestPlugin1)
      output must contain("sbt.PluginCommandTestPlugin0 is activated.")
    }

    "should work for plugins nested more than one package" in {
      val output = processCommand("plugin sbt.subpackage.PluginCommandTestPlugin1",
                                  PluginCommandTestPlugin0,
                                  PluginCommandTestPlugin1)
      output must contain("sbt.subpackage.PluginCommandTestPlugin1 is activated.")
    }

    "suggest a plugin when given an incorrect plugin with a similar name" in {
      val output = processCommand("plugin PluginCommandTestPlugin0",
                                  PluginCommandTestPlugin0,
                                  PluginCommandTestPlugin1)
      output must contain(
        "Not a valid plugin: PluginCommandTestPlugin0 (similar: sbt.PluginCommandTestPlugin0, sbt.subpackage.PluginCommandTestPlugin1)"
      )
    }

  }

}

object FakeState {

  def processCommand(input: String, enabledPlugins: AutoPlugin*): String = {
    val previousOut = System.out
    val outBuffer = new ByteArrayOutputStream
    try {
      System.setOut(new PrintStream(outBuffer, true))
      val state = FakeState(enabledPlugins: _*)
      MainLoop.processCommand(Exec(input, None), state)
      new String(outBuffer.toByteArray)
    } finally {
      System.setOut(previousOut)
    }
  }

  def apply(plugins: AutoPlugin*) = {

    val base = new File("").getAbsoluteFile
    val testProject = Project("test-project", base).setAutoPlugins(plugins)

    val settings: Seq[Def.Setting[_]] = Nil

    val currentProject = Map(testProject.base.toURI -> testProject.id)
    val currentEval: () => sbt.compiler.Eval = () => Load.mkEval(Nil, base, Nil)
    val sessionSettings =
      SessionSettings(base.toURI, currentProject, Nil, Map.empty, Nil, currentEval)

    val delegates: (Scope) => Seq[Scope] = _ => Nil
    val scopeLocal: Def.ScopeLocal = _ => Nil

    val data: Settings[Scope] = Def.make(settings)(delegates, scopeLocal, Def.showFullKey)
    val extra: KeyIndex => BuildUtil[_] = (keyIndex) =>
      BuildUtil(base.toURI, Map.empty, keyIndex, data)
    val structureIndex: StructureIndex = Load.structureIndex(data, settings, extra, Map.empty)
    val streams: (State) => BuildStreams.Streams = null

    val loadedDefinitions: LoadedDefinitions = new LoadedDefinitions(
      base,
      Nil,
      ClassLoader.getSystemClassLoader,
      Nil,
      Seq(testProject),
      Nil
    )

    val pluginData = PluginData(Nil, Nil, None, None, Nil)
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
    val buildStructure = new BuildStructure(units,
                                            base.toURI,
                                            settings,
                                            data,
                                            structureIndex,
                                            streams,
                                            delegates,
                                            scopeLocal)

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
      GlobalLogging.initial(MainAppender.globalDefault(ConsoleOut.systemOut),
                            File.createTempFile("sbt", ".log"),
                            ConsoleOut.systemOut),
      None,
      State.Continue
    )

  }

}
