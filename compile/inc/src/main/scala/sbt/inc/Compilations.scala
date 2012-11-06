package sbt.inc

import xsbti.api.Compilation

/** Information about compiler runs accumulated since `clean` command has been run. */
trait Compilations {
  def allCompilations: Seq[Compilation]
  def ++(o: Compilations): Compilations
  def add(c: Compilation): Compilations
}

object Compilations {
  val empty: Compilations = new MCompilations(Seq.empty)
  def make(s: Seq[Compilation]): Compilations = new MCompilations(s)
}

private final class MCompilations(val allCompilations: Seq[Compilation]) extends Compilations {
  def ++(o: Compilations): Compilations = new MCompilations(allCompilations ++ o.allCompilations)
  def add(c: Compilation): Compilations = new MCompilations(allCompilations :+ c)
}
