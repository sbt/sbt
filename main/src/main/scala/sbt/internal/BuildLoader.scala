/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URI
import BuildLoader._
import sbt.internal.io.Alternatives._
import sbt.internal.util.Types.{ const, idFun }
import sbt.util.Logger
import sbt.librarymanagement.ModuleID

final class MultiHandler[S, T](builtIn: S => Option[T],
                               root: Option[S => Option[T]],
                               nonRoots: List[(URI, S => Option[T])],
                               getURI: S => URI,
                               log: S => Logger) {
  def applyFun: S => Option[T] = apply
  def apply(info: S): Option[T] =
    (baseLoader(info), applyNonRoots(info)) match {
      case (None, Nil) => None
      case (None, xs @ (_, nr) :: ignored) =>
        if (ignored.nonEmpty)
          warn("Using first of multiple matching non-root build resolvers for " + getURI(info),
               log(info),
               xs)
        Some(nr)
      case (Some(b), xs) =>
        if (xs.nonEmpty)
          warn("Ignoring shadowed non-root build resolver(s) for " + getURI(info), log(info), xs)
        Some(b)
    }

  def baseLoader: S => Option[T] = root match {
    case Some(rl) => rl | builtIn; case None => builtIn
  }

  def addNonRoot(uri: URI, loader: S => Option[T]) =
    new MultiHandler(builtIn, root, (uri, loader) :: nonRoots, getURI, log)
  def setRoot(resolver: S => Option[T]) =
    new MultiHandler(builtIn, Some(resolver), nonRoots, getURI, log)
  def applyNonRoots(info: S): List[(URI, T)] =
    nonRoots flatMap {
      case (definingURI, loader) =>
        loader(info) map { unit =>
          (definingURI, unit)
        }
    }

  private[this] def warn(baseMessage: String, log: Logger, matching: Seq[(URI, T)]): Unit = {
    log.warn(baseMessage)
    log.debug("Non-root build resolvers defined in:")
    log.debug(matching.map(_._1).mkString("\n\t"))
  }
}

object BuildLoader {

  /**
   * in: Build URI and staging directory
   * out: None if unhandled or Some containing the retrieve function, which returns the directory retrieved to (can be the same as the staging directory)
   */
  type Resolver = ResolveInfo => Option[() => File]
  type Builder = BuildInfo => Option[() => BuildUnit]
  type Transformer = TransformInfo => BuildUnit
  type Loader = LoadInfo => Option[() => BuildUnit]
  type TransformAll = PartBuild => PartBuild

  final class Components(val resolver: Resolver,
                         val builder: Builder,
                         val transformer: Transformer,
                         val full: Loader,
                         val transformAll: TransformAll) {
    def |(cs: Components): Components =
      new Components(resolver | cs.resolver,
                     builder | cs.builder,
                     seq(transformer, cs.transformer),
                     full | cs.full,
                     transformAll andThen cs.transformAll)
  }
  def transform(t: Transformer): Components = components(transformer = t)
  def resolve(r: Resolver): Components = components(resolver = r)
  def build(b: Builder): Components = components(builder = b)
  def full(f: Loader): Components = components(full = f)
  def transformAll(t: TransformAll) = components(transformAll = t)
  def components(resolver: Resolver = const(None),
                 builder: Builder = const(None),
                 transformer: Transformer = _.unit,
                 full: Loader = const(None),
                 transformAll: TransformAll = idFun) =
    new Components(resolver, builder, transformer, full, transformAll)

  def seq(a: Transformer, b: Transformer): Transformer = info => b(info.setUnit(a(info)))

  sealed trait Info {
    def uri: URI
    def config: LoadBuildConfiguration
    def state: State
  }
  final class ResolveInfo(val uri: URI,
                          val staging: File,
                          val config: LoadBuildConfiguration,
                          val state: State)
      extends Info
  final class BuildInfo(val uri: URI,
                        val base: File,
                        val config: LoadBuildConfiguration,
                        val state: State)
      extends Info
  final class TransformInfo(val uri: URI,
                            val base: File,
                            val unit: BuildUnit,
                            val config: LoadBuildConfiguration,
                            val state: State)
      extends Info {
    def setUnit(newUnit: BuildUnit): TransformInfo =
      new TransformInfo(uri, base, newUnit, config, state)
  }

  final class LoadInfo(val uri: URI,
                       val staging: File,
                       val config: LoadBuildConfiguration,
                       val state: State,
                       val components: Components)
      extends Info

  def apply(base: Components,
            fail: URI => Nothing,
            s: State,
            config: LoadBuildConfiguration): BuildLoader = {
    def makeMulti[S <: Info, T](base: S => Option[T]) =
      new MultiHandler[S, T](base, None, Nil, _.uri, _.config.log)
    new BuildLoader(fail,
                    s,
                    config,
                    makeMulti(base.resolver),
                    makeMulti(base.builder),
                    base.transformer,
                    makeMulti(base.full),
                    base.transformAll)
  }

  def componentLoader: Loader = (info: LoadInfo) => {
    import info.{ config, staging, state, uri }
    val cs = info.components
    for {
      resolve <- cs.resolver(new ResolveInfo(uri, staging, config, state))
      base = resolve()
      build <- cs.builder(new BuildInfo(uri, base, config, state))
    } yield
      () => {
        val unit = build()
        cs.transformer(new TransformInfo(uri, base, unit, config, state))
      }
  }
}

/** Defines the responsible for loading builds.
 *
 * @param fail A reporter for failures.
 * @param state The state.
 * @param config The current configuration for any build.
 * @param resolvers The structure responsible of mapping base directories.
 * @param builders The structure responsible of mapping to build units.
 * @param transformer An instance to modify the created build unit.
 * @param full The structure responsible of mapping to loaded build units.
 * @param transformAll A function specifying which builds units should be transformed.
 */
final class BuildLoader(
    val fail: URI => Nothing,
    val state: State,
    val config: LoadBuildConfiguration,
    val resolvers: MultiHandler[ResolveInfo, () => File],
    val builders: MultiHandler[BuildInfo, () => BuildUnit],
    val transformer: Transformer,
    val full: MultiHandler[LoadInfo, () => BuildUnit],
    val transformAll: TransformAll
) {
  def addNonRoot(uri: URI, loaders: Components): BuildLoader =
    new BuildLoader(
      fail,
      state,
      config,
      resolvers.addNonRoot(uri, loaders.resolver),
      builders.addNonRoot(uri, loaders.builder),
      seq(transformer, loaders.transformer),
      full.addNonRoot(uri, loaders.full),
      transformAll andThen loaders.transformAll
    )
  def setRoot(loaders: Components): BuildLoader =
    new BuildLoader(
      fail,
      state,
      config,
      resolvers.setRoot(loaders.resolver),
      builders.setRoot(loaders.builder),
      seq(loaders.transformer, transformer),
      full.setRoot(loaders.full),
      loaders.transformAll andThen transformAll
    )
  def resetPluginDepth: BuildLoader = copyWithNewPM(config.pluginManagement.resetDepth)

  def updatePluginManagement(overrides: Set[ModuleID]): BuildLoader = {
    val mgmt = config.pluginManagement
    copyWithNewPM(mgmt.copy(overrides = mgmt.overrides ++ overrides))
  }
  private[this] def copyWithNewPM(newpm: PluginManagement): BuildLoader = {
    val newConfig = config.copy(pluginManagement = newpm)
    new BuildLoader(fail, state, newConfig, resolvers, builders, transformer, full, transformAll)
  }

  def components =
    new Components(resolvers.applyFun, builders.applyFun, transformer, full.applyFun, transformAll)
  def apply(uri: URI): BuildUnit = {
    val info = new LoadInfo(uri, config.stagingDirectory, config, state, components)
    val load = full(info) getOrElse fail(uri)
    load()
  }
}
