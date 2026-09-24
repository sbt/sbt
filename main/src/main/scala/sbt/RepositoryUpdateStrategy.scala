/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.concurrent.duration.FiniteDuration

/**
 * Strategy for updating remote VCS project dependencies.
 *
 * Used with the `repositoryUpdateStrategy` setting key to control when
 * remote project dependencies are updated from their upstream repositories.
 */
enum RepositoryUpdateStrategy:

  /**
   * Never update automatically.
   * Users must manually delete staging directories or run `updateRemoteProjects`.
   */
  case Manual

  /** Update on every `update` task invocation. */
  case Always

  /** Update at most once per the given interval. */
  case Every(interval: FiniteDuration)
