package app

import scala.quoted.*

class Impl(val name: String) extends core.Base

object Impl:
  def create(name: String): Impl = new Impl(name)

  inline def make: Impl = ${ makeImpl }

  private def makeImpl(using Quotes): Expr[Impl] =
    import quotes.reflect.*
    '{ create(${ Expr(Symbol.spliceOwner.owner.owner.fullName) }) }
