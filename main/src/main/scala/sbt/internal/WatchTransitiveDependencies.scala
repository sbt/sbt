/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Def.*
import sbt.Keys.*
// import sbt.Project.richInitializeTask
import sbt.ProjectExtra.*
import sbt.ScopeAxis.Zero
import sbt.internal.io.Source
import sbt.internal.nio.Globs
import sbt.internal.util.complete.Parser
import sbt.nio.FileStamper
import sbt.nio.Keys.*
import sbt.nio.file.Glob

import scala.annotation.tailrec

private[sbt] object WatchTransitiveDependencies {
  extension (source: Source) {
    private def toGlob: Glob = {
      val filter = source.includeFilter -- source.excludeFilter
      Globs.apply(source.base.toPath, source.recursive, filter)
    }
  }
  private[sbt] def task: Def.Initialize[Task[Seq[DynamicInput]]] =
    Def.task(transitiveDynamicInputs(arguments.value))
  private[sbt] def task(
      key: ScopedKey[?]
  ): Def.Initialize[Task[Seq[DynamicInput]]] =
    withParams((e, cm) => Def.task(transitiveDynamicInputs(argumentsImpl(key, e, cm).value)))
  private def withParams[R](
      f: (Extracted, CompiledMap) => Def.Initialize[Task[R]]
  ): Def.Initialize[Task[R]] =
    Def.task { Project.extract(state.value) }.flatMapTask { extracted =>
      f(extracted, compile(extracted.structure))
    }

  private[sbt] def compile(structure: BuildStructure): CompiledMap = structure.compiledMap
  private[sbt] final class Arguments(
      val scopedKey: ScopedKey[?],
      val extracted: Extracted,
      val compiledMap: CompiledMap,
      val log: sbt.util.Logger,
      val dependencyConfigurations: Seq[(ProjectRef, Set[String])],
      val state: State
  ) {
    def structure: BuildStructure = extracted.structure
    def data: Settings = extracted.structure.data
  }

  private def argumentsImpl(
      scopedKey: ScopedKey[?],
      extracted: Extracted,
      compiledMap: CompiledMap
  ): Def.Initialize[Task[Arguments]] =
    import sbt.TupleSyntax.*
    (
      (streamsManager.map { mgr =>
        val stream = mgr(scopedKey)
        stream.open()
        stream
      }).toTaskable,
      (scopedKey.scope / internalDependencyConfigurations).toTaskable,
      state,
    ).mapN { (log, configs, st) =>
      new Arguments(
        scopedKey,
        extracted,
        compiledMap,
        log.log,
        configs,
        st
      )
    }
  private val ShowTransitive = "(?:show)?(?:[ ]*)(.*)/(?:[ ]*)transitive(?:Inputs|Globs|Triggers)".r
  private def arguments: Def.Initialize[Task[Arguments]] =
    Def
      .task {
        val extracted = Project.extract(state.value)
        val compiledMap = compile(extracted.structure)
        val st = state.value
        val rs = Keys.resolvedScoped.value
        (extracted, compiledMap, st, rs)
      }
      .flatMapTask { (extracted, compiledMap, st, rs) =>
        st.currentCommand.get.commandLine match
          case ShowTransitive(key) =>
            Parser.parse(key.trim, Act.scopedKeyParser(st)) match
              case Right(scopedKey) => argumentsImpl(scopedKey, extracted, compiledMap)
              case _                => argumentsImpl(rs, extracted, compiledMap)
          case _ => argumentsImpl(rs, extracted, compiledMap)
      }

  private[sbt] def transitiveDynamicInputs(args: Arguments): Seq[DynamicInput] = {
    import args.*
    val taskScope = Project.fillTaskAxis(scopedKey).scope
    def delegates(sk: ScopedKey[?]): Seq[ScopedKey[?]] =
      Project.delegates(structure, sk.scope, sk.key)
    // We add the triggers to the delegate scopes to make it possible for the user to do something
    // like: Compile / compile / watchTriggers += baseDirectory.value ** "*.proto". We do not do the
    // same for inputs because inputs are expected to be explicitly used as part of the task.
    val allKeys: Seq[ScopedKey[?]] =
      (delegates(scopedKey).toSet ++ delegates(ScopedKey(taskScope, watchTriggers.key))).toSeq
    val keys = collectKeys(args, allKeys, Set.empty, Set.empty)
    def getDynamicInputs(scopedKey: ScopedKey[Seq[Glob]], trigger: Boolean): Seq[DynamicInput] = {
      data
        .getDirect(scopedKey)
        .map { globs =>
          if (!trigger) {
            val stamper =
              data.getDirect(scopedKey.copy(key = inputFileStamper.key)).getOrElse(FileStamper.Hash)
            val forceTrigger =
              data
                .getDirect(scopedKey.copy(key = watchForceTriggerOnAnyChange.key))
                .getOrElse(false)
            globs.map(g => DynamicInput(g, stamper, forceTrigger))
          } else {
            globs.map(g => DynamicInput(g, FileStamper.LastModified, forceTrigger = true))
          }
        }
        .getOrElse(Nil)
    }
    val (inputGlobs, triggerGlobs) = keys.partition(_.key == fileInputs.key) match {
      case (inputs, triggers) =>
        (
          inputs.flatMap(getDynamicInputs(_, trigger = false)),
          triggers.flatMap(getDynamicInputs(_, trigger = true))
        )
    }
    // If watchTriggers is explicitly set (non-empty), use only watchTriggers instead of combining with fileInputs
    // This allows users to control what triggers the watch by setting watchTriggers
    val result = if (triggerGlobs.nonEmpty) {
      triggerGlobs ++ legacy(keys :+ scopedKey, args)
    } else {
      inputGlobs ++ triggerGlobs ++ legacy(keys :+ scopedKey, args)
    }
    result.distinct.sorted
  }

  private def legacy(keys: Seq[ScopedKey[?]], args: Arguments): Seq[DynamicInput] = {
    import args.*
    val projectScopes =
      keys.view
        .map(_.scope.copy(task = Zero, extra = Zero))
        .distinct
        .toIndexedSeq
    val projects = projectScopes.flatMap(_.project.toOption).distinct.toSet
    val scopes: Seq[Either[Scope, Seq[Glob]]] =
      data.scopes.toSeq
        .withFilter(s => s == Scope.Global || s.project.toOption.exists(projects.contains))
        .flatMap { s =>
          data.getDirect(ScopedKey(s, Keys.watchSources.key)).map { task =>
            task.work match
              case a: Action.Pure[Seq[Watched.WatchSource]] => Right(a.f().map(_.toGlob))
              case _                                        => Left(s)
          }
        }
    def toDynamicInput(glob: Glob): DynamicInput =
      DynamicInput(glob, FileStamper.LastModified, forceTrigger = true)
    scopes.flatMap {
      case Left(scope) =>
        extracted.runTask(scope / Keys.watchSources, state)._2.map(s => toDynamicInput(s.toGlob))
      case Right(globs) => globs.map(toDynamicInput)
    }
  }

  @tailrec
  private def collectKeys(
      arguments: Arguments,
      dependencies: Seq[ScopedKey[?]],
      accumulator: Set[ScopedKey[Seq[Glob]]],
      visited: Set[ScopedKey[?]]
  ): Seq[ScopedKey[Seq[Glob]]] = dependencies match {
    // Iterates until the dependency list is empty. The visited parameter prevents the graph
    // traversal from getting stuck in a cycle.
    case Seq(dependency, rest*) =>
      (if (!visited(dependency)) arguments.compiledMap.get(dependency) else None) match {
        case Some(compiled) =>
          val newVisited = visited + compiled.key
          val baseGlobs: Seq[ScopedKey[Seq[Glob]]] = compiled.key match {
            case key: ScopedKey[Seq[Glob]] @unchecked if isGlobKey(key) => key :: Nil
            case _                                                      => Nil
          }
          val base: (Seq[ScopedKey[?]], Seq[ScopedKey[Seq[Glob]]]) = (Nil, baseGlobs)
          val (newDependencies, newScopes) =
            (compiled.dependencies.filterNot(newVisited) ++ compiled.settings.map(_.key))
              .foldLeft(base) {
                case ((d, s), key: ScopedKey[Seq[Glob]] @unchecked)
                    if isGlobKey(key) && !newVisited(key) =>
                  (d, s :+ key)
                case ((d, s), key) if key.key == dynamicDependency.key =>
                  key.scope.task.toOption
                    .map { k =>
                      val newKey = ScopedKey(key.scope.copy(task = Zero), k)
                      if (newVisited(newKey)) (d, s) else (d :+ newKey, s)
                    }
                    .getOrElse((d, s))
                case ((d, s), key) if key.key == transitiveClasspathDependency.key =>
                  key.scope.task.toOption
                    .map { task =>
                      val zeroedTaskScope = key.scope.copy(task = Zero)
                      val transitiveKeys = arguments.dependencyConfigurations.flatMap {
                        (p, configs) =>
                          configs.map(c =>
                            ScopedKey(zeroedTaskScope.rescope(p).rescope(ConfigKey(c)), task)
                          )
                      }

                      (d ++ transitiveKeys.filterNot(newVisited), s)
                    }
                    .getOrElse((d, s))
                case ((d, s), key) =>
                  (d ++ (if (!newVisited(key)) Some(key) else None), s)
              }
          // Append the Keys.triggers key in case there are no other references to Keys.triggers.
          val transitiveTrigger = compiled.key.scope.task.toOption match {
            case _: Some[?] => ScopedKey(compiled.key.scope, watchTriggers.key)
            case None => ScopedKey(Project.fillTaskAxis(compiled.key).scope, watchTriggers.key)
          }
          val newRest = rest ++ newDependencies ++ (if (newVisited(transitiveTrigger)) Nil
                                                    else Some(transitiveTrigger))
          collectKeys(arguments, newRest, accumulator ++ newScopes, newVisited)
        case _ if rest.nonEmpty => collectKeys(arguments, rest, accumulator, visited)
        case _                  => accumulator.toIndexedSeq
      }
    case _ => accumulator.toIndexedSeq
  }
  private def isGlobKey(key: ScopedKey[?]): Boolean = key.key match {
    case fileInputs.key | watchTriggers.key => true
    case _                                  => false
  }
}
