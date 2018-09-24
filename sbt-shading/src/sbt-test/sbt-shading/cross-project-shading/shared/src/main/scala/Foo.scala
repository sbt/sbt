
import argonaut._

object Foo {

  def expectedClassName(shaded: Boolean) =
    if (shaded)
      "test.shaded.argonaut.Json"
    else
      // Don't use the literal "argonaut.Json", that seems to get
      // changed to "test.shaded.argonaut.Json" by shading
      "argonaut" + '.' + "Json"

  val className = classOf[Json].getName

}
