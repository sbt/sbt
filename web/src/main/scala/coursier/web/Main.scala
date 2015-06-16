package coursier.web

import japgolly.scalajs.react.React
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom.document

@JSExport
object Main {
  @JSExport
  def main(): Unit = {
    React.render(App.app("Coursier"), document.getElementById("demoContent"))
  }
}
