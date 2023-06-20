/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.reflect._
import macros._

/**
 * A builder that uses `TupleN` as the representation for small numbers of inputs (up to `TupleNBuilder.MaxInputs`)
 * and `KList` for larger numbers of inputs. This builder cannot handle fewer than 2 inputs.
 */
object MixedBuilder extends TupleBuilder {
  def make(
      c: blackbox.Context
  )(mt: c.Type, inputs: Inputs[c.universe.type]): BuilderResult[c.type] = {
    val delegate =
      if (inputs.size > TupleNBuilder.MaxInputs) (KListBuilder: TupleBuilder)
      else (TupleNBuilder: TupleBuilder)
    delegate.make(c)(mt, inputs)
  }
}
