/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import sbt.util.Show

/** Define our settings system */
// A basic scope indexed by an integer.
final case class Scope(nestIndex: Int, idAtIndex: Int = 0)

// Extend the Init trait.
//  (It is done this way because the Scope type parameter is used everywhere in Init.
//  Lots of type constructors would become binary, which as you may know requires lots of type lambdas
//  when you want a type function with only one parameter.
//  That would be a general pain.)
case class SettingsExample() extends Init[Scope] {
  // Provides a way of showing a Scope+AttributeKey[_]
  val showFullKey: Show[ScopedKey[_]] = Show[ScopedKey[_]]((key: ScopedKey[_]) => {
    s"${key.scope.nestIndex}(${key.scope.idAtIndex})/${key.key.label}"
  })

  // A sample delegation function that delegates to a Scope with a lower index.
  val delegates: Scope => Seq[Scope] = {
    case s @ Scope(index, proj) =>
      s +: (if (index <= 0) Nil
            else { (if (proj > 0) List(Scope(index)) else Nil) ++: delegates(Scope(index - 1)) })
  }

  // Not using this feature in this example.
  val scopeLocal: ScopeLocal = _ => Nil

  // These three functions + a scope (here, Scope) are sufficient for defining our settings system.
}

/** Usage Example **/
case class SettingsUsage(val settingsExample: SettingsExample) {
  import settingsExample._

  // Define some keys
  val a = AttributeKey[Int]("a")
  val b = AttributeKey[Int]("b")

  // Scope these keys
  val a3 = ScopedKey(Scope(3), a)
  val a4 = ScopedKey(Scope(4), a)
  val a5 = ScopedKey(Scope(5), a)

  val b4 = ScopedKey(Scope(4), b)

  // Define some settings
  val mySettings: Seq[Setting[_]] = Seq(
    setting(a3, value(3)),
    setting(b4, map(a4)(_ * 3)),
    update(a5)(_ + 1)
  )

  // "compiles" and applies the settings.
  //  This can be split into multiple steps to access intermediate results if desired.
  //  The 'inspect' command operates on the output of 'compile', for example.
  val applied: Settings[Scope] = make(mySettings)(delegates, scopeLocal, showFullKey)

  // Show results.
  /*	for(i <- 0 to 5; k <- Seq(a, b)) {
		println( k.label + i + " = " + applied.get( Scope(i), k) )
	}*/

  /**
 * Output:
 * For the None results, we never defined the value and there was no value to delegate to.
 * For a3, we explicitly defined it to be 3.
 * a4 wasn't defined, so it delegates to a3 according to our delegates function.
 * b4 gets the value for a4 (which delegates to a3, so it is 3) and multiplies by 3
 * a5 is defined as the previous value of a5 + 1 and
 *   since no previous value of a5 was defined, it delegates to a4, resulting in 3+1=4.
 * b5 isn't defined explicitly, so it delegates to b4 and is therefore equal to 9 as well
 * a0 = None
 * b0 = None
 * a1 = None
 * b1 = None
 * a2 = None
 * b2 = None
 * a3 = Some(3)
 * b3 = None
 * a4 = Some(3)
 * b4 = Some(9)
 * a5 = Some(4)
 * b5 = Some(9)
 */
}
