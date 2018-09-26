package coursier.web

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("graphdracula", JSImport.Namespace)
object Dracula extends js.Object {
  def Graph: js.Dynamic = js.native
  def Layout: Layout = js.native
  def Renderer: Renderer = js.native
}

@js.native
trait Layout extends js.Any {
  def Spring: js.Dynamic
}

@js.native
trait Renderer extends js.Any {
  def Raphael: js.Dynamic
}
