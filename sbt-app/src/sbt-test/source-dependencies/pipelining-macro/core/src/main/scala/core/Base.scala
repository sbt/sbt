package core

import scala.quoted.*

trait Base:
  def name: String

object Base:
  inline def enclosingName: String = ${ enclosingNameImpl }

  private def enclosingNameImpl(using Quotes): Expr[String] =
    import quotes.reflect.*
    Expr(Symbol.spliceOwner.owner.owner.fullName)
