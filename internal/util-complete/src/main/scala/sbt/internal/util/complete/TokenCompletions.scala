/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import Completion.{ token => ctoken, tokenDisplay }

sealed trait TokenCompletions {
  def hideWhen(f: Int => Boolean): TokenCompletions
}

object TokenCompletions {
  private[sbt] abstract class Delegating extends TokenCompletions { outer =>
    def completions(seen: String, level: Int, delegate: Completions): Completions
    final def hideWhen(hide: Int => Boolean): TokenCompletions = new Delegating {
      def completions(seen: String, level: Int, delegate: Completions): Completions =
        if (hide(level)) Completions.nil else outer.completions(seen, level, delegate)
    }
  }

  private[sbt] abstract class Fixed extends TokenCompletions { outer =>
    def completions(seen: String, level: Int): Completions
    final def hideWhen(hide: Int => Boolean): TokenCompletions = new Fixed {
      def completions(seen: String, level: Int) =
        if (hide(level)) Completions.nil else outer.completions(seen, level)
    }
  }

  val default: TokenCompletions = mapDelegateCompletions((seen, level, c) => ctoken(seen, c.append))

  def displayOnly(msg: String): TokenCompletions = new Fixed {
    def completions(seen: String, level: Int) = Completions.single(Completion.displayOnly(msg))
  }

  def overrideDisplay(msg: String): TokenCompletions =
    mapDelegateCompletions((seen, level, c) => tokenDisplay(display = msg, append = c.append))

  def fixed(f: (String, Int) => Completions): TokenCompletions = new Fixed {
    def completions(seen: String, level: Int) = f(seen, level)
  }

  def mapDelegateCompletions(f: (String, Int, Completion) => Completion): TokenCompletions =
    new Delegating {
      def completions(seen: String, level: Int, delegate: Completions) =
        Completions(delegate.get.map(c => f(seen, level, c)))
    }
}
