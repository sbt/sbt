/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.Def._
import sbt.Keys._
import sbt.Project.richInitializeTask
import sbt._
import sbt.internal.io.Source
import sbt.internal.nio.Globs
import sbt.internal.util.AttributeMap
import sbt.internal.util.complete.Parser
import sbt.nio.FileStamper
import sbt.nio.Keys._
import sbt.nio.file.Glob

import scala.annotation.{ nowarn, tailrec }

private[sbt] object WatchTransitiveDependencies {
  private implicit class SourceOps(val source: Source) {
    def toGlob: Glob = {
      val filter = source.includeFilter -- source.excludeFilter
      Globs.apply(source.base.toPath, source.recursive, filter)
    }
  }
  private[sbt] def task: Def.Initialize[Task[Seq[DynamicInput]]] =
    Def.task(transitiveDynamicInputs(arguments.value))
  private[sbt] def task(
      key: ScopedKey[_]
  ): Def.Initialize[Task[Seq[DynamicInput]]] =
    withParams((e, cm) => Def.task(transitiveDynamicInputs(argumentsImpl(key, e, cm).value)))
  private def withParams[R](
      f: (Extracted, CompiledMap) => Def.Initialize[Task[R]]
  ): Def.Initialize[Task[R]] = Def.taskDyn {
    val extracted = Project.extract(state.value)
    f(extracted, compile(extracted.structure))
  }

  private[sbt] def compile(structure: BuildStructure): CompiledMap = structure.compiledMap
  private[sbt] final class Arguments(
      val scopedKey: ScopedKey[_],
      val extracted: Extracted,
      val compiledMap: CompiledMap,
      val log: sbt.util.Logger,
      val dependencyConfigurations: Seq[(ProjectRef, Set[String])],
      val state: State
  ) {
    def structure: BuildStructure = extracted.structure
    def data: Map[Scope, AttributeMap] = extracted.structure.data.data
  }

  @nowarn
  private def argumentsImpl(
      scopedKey: ScopedKey[_],
      extracted: Extracted,
      compiledMap: CompiledMap
  ): Def.Initialize[Task[Arguments]] = Def.task {
    val log = (streamsManager map { mgr =>
      val stream = mgr(scopedKey)
      stream.open()
      stream
    }).value.log
    val configs = (internalDependencyConfigurations in scopedKey.scope).value
    new Arguments(
      scopedKey,
      extracted,
      compiledMap,
      log,
      configs,
      state.value
    )
  }
  private val ShowTransitive = "(?:show)?(?:[ ]*)(.*)/(?:[ ]*)transitive(?:Inputs|Globs|Triggers)".r
  private def arguments: Def.Initialize[Task[Arguments]] = Def.taskDyn {
    Def.task {
      val extracted = Project.extract(state.value)
      val compiledMap = compile(extracted.structure)
      state.value.currentCommand.map(_.commandLine) match {
        case Some(ShowTransitive(key)) =>
          Parser.parse(key.trim, Act.scopedKeyParser(state.value)) match {
            case Right(scopedKey) => argumentsImpl(scopedKey, extracted, compiledMap)
            case _                => argumentsImpl(Keys.resolvedScoped.value, extracted, compiledMap)
          }
        case Some(_) => argumentsImpl(Keys.resolvedScoped.value, extracted, compiledMap)
      }
    }.value
  }
  private[sbt] def transitiveDynamicInputs(args: Arguments): Seq[DynamicInput] = {
    import args._
    val taskScope = Project.fillTaskAxis(scopedKey).scope
    def delegates(sk: ScopedKey[_]): Seq[ScopedKey[_]] =
      Project.delegates(structure, sk.scope, sk.key)
    // We add the triggers to the delegate scopes to make it possible for the user to do something
    // like: Compile / compile / watchTriggers += baseDirectory.value ** "*.proto". We do not do the
    // same for inputs because inputs are expected to be explicitly used as part of the task.
    val allKeys: Seq[ScopedKey[_]] =
      (delegates(scopedKey).toSet ++ delegates(ScopedKey(taskScope, watchTriggers.key))).toSeq
    val keys = collectKeys(args, allKeys, Set.empty, Set.empty)
    def getDynamicInputs(scopedKey: ScopedKey[Seq[Glob]], trigger: Boolean): Seq[DynamicInput] = {
      data
        .get(scopedKey.scope)
        .map { am =>
          am.get(scopedKey.key) match {
            case Some(globs: Seq[Glob]) =>
              if (!trigger) {
                val stamper = am.get(inputFileStamper.key).getOrElse(FileStamper.Hash)
                val forceTrigger = am.get(watchForceTriggerOnAnyChange.key).getOrElse(false)
                globs.map(g => DynamicInput(g, stamper, forceTrigger))
              } else {
                globs.map(g => DynamicInput(g, FileStamper.LastModified, forceTrigger = true))
              }
            case None => Nil: Seq[DynamicInput]
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
    (inputGlobs ++ triggerGlobs ++ legacy(keys :+ scopedKey, args)).distinct.sorted
  }

  @nowarn
  private def legacy(keys: Seq[ScopedKey[_]], args: Arguments): Seq[DynamicInput] = {
    import args._
    val projectScopes =
      keys.view
        .map(_.scope.copy(task = Zero, extra = Zero))
        .distinct
        .toIndexedSeq
    val projects = projectScopes.flatMap(_.project.toOption).distinct.toSet
    val scopes: Seq[Either[Scope, Seq[Glob]]] =
      data.flatMap {
        case (s, am) =>
          if (s == Scope.Global || s.project.toOption.exists(projects.contains))
            am.get(Keys.watchSources.key) match {
              case Some(k) =>
                k.work match {
                  // Avoid extracted.runTask if possible.
                  case Pure(w, _) => Some(Right(w().map(_.toGlob)))
                  case _          => Some(Left(s))
                }
              case _ => None
            }
          else {
            None
          }
      }.toSeq
    def toDynamicInput(glob: Glob): DynamicInput =
      DynamicInput(glob, FileStamper.LastModified, forceTrigger = true)
    scopes.flatMap {
      case Left(scope) =>
        extracted.runTask(Keys.watchSources in scope, state)._2.map(s => toDynamicInput(s.toGlob))
      case Right(globs) => globs.map(toDynamicInput)
    }
  }

  @nowarn
  @tailrec
  private def collectKeys(
      arguments: Arguments,
      dependencies: Seq[ScopedKey[_]],
      accumulator: Set[ScopedKey[Seq[Glob]]],
      visited: Set[ScopedKey[_]]
  ): Seq[ScopedKey[Seq[Glob]]] = dependencies match {
    // Iterates until the dependency list is empty. The visited parameter prevents the graph
    // traversal from getting stuck in a cycle.
    case Seq(dependency, rest @ _*) =>
      (if (!visited(dependency)) arguments.compiledMap.get(dependency) else None) match {
        case Some(compiled) =>
          val newVisited = visited + compiled.key
          val baseGlobs: Seq[ScopedKey[Seq[Glob]]] = compiled.key match {
            case key: ScopedKey[Seq[Glob]] @unchecked if isGlobKey(key) => key :: Nil
            case _                                                      => Nil
          }
          val base: (Seq[ScopedKey[_]], Seq[ScopedKey[Seq[Glob]]]) = (Nil, baseGlobs)
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
                        case (p, configs) =>
                          configs.map(c => ScopedKey(zeroedTaskScope in (p, ConfigKey(c)), task))
                      }

                      (d ++ transitiveKeys.filterNot(newVisited), s)
                    }
                    .getOrElse((d, s))
                case ((d, s), key) =>
                  (d ++ (if (!newVisited(key)) Some(key) else None), s)
              }
          // Append the Keys.triggers key in case there are no other references to Keys.triggers.
          val transitiveTrigger = compiled.key.scope.task.toOption match {
            case _: Some[_] => ScopedKey(compiled.key.scope, watchTriggers.key)
            case None       => ScopedKey(Project.fillTaskAxis(compiled.key).scope, watchTriggers.key)
          }
          val newRest = rest ++ newDependencies ++ (if (newVisited(transitiveTrigger)) Nil
                                                    else Some(transitiveTrigger))
          collectKeys(arguments, newRest, accumulator ++ newScopes, newVisited)
        case _ if rest.nonEmpty => collectKeys(arguments, rest, accumulator, visited)
        case _                  => accumulator.toIndexedSeq
      }
    case _ => accumulator.toIndexedSeq
  }
  private[this] def isGlobKey(key: ScopedKey[_]): Boolean = key.key match {
    case fileInputs.key | watchTriggers.key => true
    case _                                  => false
  }
}
