/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import java.lang.Character.{ toLowerCase as lower }

/** @author Paul Phillips */
object EditDistance {

  /**
   * Translated from the java version at http://www.merriampark.com/ld.htm which is declared to be
   * public domain.
   */
  def levenshtein(
      s: String,
      t: String,
      insertCost: Int = 1,
      deleteCost: Int = 1,
      subCost: Int = 1,
      transposeCost: Int = 1,
      matchCost: Int = 0,
      caseCost: Int = 1,
      transpositions: Boolean = false
  ): Int = {
    val _ = transposeCost
    val n = s.length
    val m = t.length
    if (n == 0) return m
    if (m == 0) return n

    val d = Array.ofDim[Int](n + 1, m + 1)
    0 to n foreach (x => d(x)(0) = x)
    0 to m foreach (x => d(0)(x) = x)

    for (i <- 1 to n; s_i = s(i - 1); j <- 1 to m) {
      val t_j = t(j - 1)
      val cost = if (s_i == t_j) matchCost else if (lower(s_i) == lower(t_j)) caseCost else subCost

      val c1 = d(i - 1)(j) + deleteCost
      val c2 = d(i)(j - 1) + insertCost
      val c3 = d(i - 1)(j - 1) + cost

      d(i)(j) = c1 min c2 min c3

      if (transpositions) {
        if (i > 1 && j > 1 && s(i - 1) == t(j - 2) && s(i - 2) == t(j - 1))
          d(i)(j) = d(i)(j) min (d(i - 2)(j - 2) + cost)
      }
    }

    d(n)(m)
  }
}
