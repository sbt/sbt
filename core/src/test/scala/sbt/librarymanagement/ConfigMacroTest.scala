package sbt.librarymanagement

import sbt.librarymanagement.Configurations.config
import org.scalatest._

class ConfigMacroTest extends FunSpec with Matchers {
  describe("Configurations.config") {
    it("should validate the ID in compile time") {
      """val A = config("a")""" should compile
      """val b = config("b")""" shouldNot compile
    }
  }
}
