package test

import scala.scalajs.js

final case class RouteLocation(loc: String, state: js.UndefOr[js.Any])


object RouteLocation:
  def apply(loc: String): RouteLocation = RouteLocation(loc, js.undefined)
  
/**
  * ```scala sc:compile
  * import scala.scalajs.js
  * final case class RouteLocation(loc: String, state: js.UndefOr[js.Any])
  *
  *
  * object RouteLocation:
  *   def apply(loc: String): RouteLocation = RouteLocation(loc, js.undefined)
  * ```
  */
case class Test(s: String)