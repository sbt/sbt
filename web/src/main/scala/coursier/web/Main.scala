package coursier.web

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel, JSImport}
import org.scalajs.dom.document

// Initializing bootstrap, like
// https://getbootstrap.com/docs/4.0/getting-started/webpack/#importing-javascript
// but from scala-js, see e.g.
// https://github.com/scalacenter/scalajs-bundler/issues/62#issuecomment-266695471
@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object

@js.native
@JSImport("bootstrap-treeview", JSImport.Namespace)
object BootstrapTreeView extends js.Object

@JSExportTopLevel("CoursierWeb")
object Main {
  @JSExport
  def main(args: Array[String]): Unit = {

    println("bootstrap")
    val bootstrap = Bootstrap

    println("bootstrap-treeview")
    val bootstrapTreeView = BootstrapTreeView

    println("Initializing")
    App.app()
      .renderIntoDOM(document.getElementById("demoContent"))
    println("Initialized")
  }
}
