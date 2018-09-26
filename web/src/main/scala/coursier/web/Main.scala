package coursier.web

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import org.scalajs.dom.document

@JSExportTopLevel("CoursierWeb")
object Main {
  @JSExport
  def main(): Unit =
    App.app()
      .renderIntoDOM(document.getElementById("demoContent"))
}
