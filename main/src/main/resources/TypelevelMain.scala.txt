package example

import cats.effect.*

object Hello extends IOApp.Simple:
  def run = IO.println("Hello toolkit!")
