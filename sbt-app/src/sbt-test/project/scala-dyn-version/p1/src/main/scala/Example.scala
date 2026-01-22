package example

import cats.effect.IO

object Example:
  def hello: IO[String] = IO.pure("Hello from Scala 3 RC!")
