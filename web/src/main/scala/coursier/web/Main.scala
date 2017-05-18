package coursier.web

import japgolly.scalajs.react.React

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import org.scalajs.dom.document

@JSExportTopLevel("CoursierWeb")
object Main {
  @JSExport
  def main(): Unit = {
    React.render(App.app("Coursier"), document.getElementById("demoContent"))
  }
}
