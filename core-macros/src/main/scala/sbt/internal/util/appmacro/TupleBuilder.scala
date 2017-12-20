/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.reflect._
import macros._

/**
 * A `TupleBuilder` abstracts the work of constructing a tuple data structure such as a `TupleN` or `KList`
 * and extracting values from it.  The `Instance` macro implementation will (roughly) traverse the tree of its argument
 * and ultimately obtain a list of expressions with type `M[T]` for different types `T`.
 * The macro constructs an `Input` value for each of these expressions that contains the `Type` for `T`,
 * the `Tree` for the expression, and a `ValDef` that will hold the value for the input.
 *
 * `TupleBuilder.apply` is provided with the list of `Input`s and is expected to provide three values in the returned BuilderResult.
 * First, it returns the constructed tuple data structure Tree in `input`.
 * Next, it provides the type constructor `representationC` that, when applied to M, gives the type of tuple data structure.
 * For example, a builder that constructs a `Tuple3` for inputs `M[Int]`, `M[Boolean]`, and `M[String]`
 * would provide a Type representing `[L[x]] (L[Int], L[Boolean], L[String])`.  The `input` method
 * would return a value whose type is that type constructor applied to M, or `(M[Int], M[Boolean], M[String])`.
 *
 * Finally, the `extract` method provides a list of vals that extract information from the applied input.
 * The type of the applied input is the type constructor applied to `Id` (`[X] X`).
 * The returned list of ValDefs should be the ValDefs from `inputs`, but with non-empty right-hand sides.
 */
trait TupleBuilder {

  /** A convenience alias for a list of inputs (associated with a Universe of type U). */
  type Inputs[U <: Universe with Singleton] = List[Instance.Input[U]]

  /** Constructs a one-time use Builder for Context `c` and type constructor `tcType`. */
  def make(c: blackbox.Context)(tcType: c.Type,
                                inputs: Inputs[c.universe.type]): BuilderResult[c.type]
}

trait BuilderResult[C <: blackbox.Context with Singleton] {
  val ctx: C
  import ctx.universe._

  /**
   * Represents the higher-order type constructor `[L[x]] ...` where `...` is the
   * type of the data structure containing the added expressions,
   * except that it is abstracted over the type constructor applied to each heterogeneous part of the type .
   */
  def representationC: PolyType

  /** The instance of AList for the input.  For a `representationC` of `[L[x]]`, this `Tree` should have a `Type` of `AList[L]`*/
  def alistInstance: Tree

  /** Returns the completed value containing all expressions added to the builder. */
  def input: Tree

  /* The list of definitions that extract values from a value of type `$representationC[Id]`.
	* The returned value should be identical to the `ValDef`s provided to the `TupleBuilder.make` method but with
	* non-empty right hand sides. Each `ValDef` may refer to `param` and previous `ValDef`s in the list.*/
  def extract(param: ValDef): List[ValDef]
}
