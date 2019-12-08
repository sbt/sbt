/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package com.github.ghik.silencer

import scala.annotation.Annotation

/**
 * When silencer compiler plugin is enabled, this annotation suppresses all warnings emitted by scalac for some portion
 * of source code. It can be applied on any definition (`class`, def`, `val`, `var`, etc.) or on arbitrary expression,
 * e.g. {123; 456}: @silent`
 */
class silent extends Annotation
