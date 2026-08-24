package example

import java.util.ServiceLoader

import org.scalatest.funsuite.AnyFunSuite

trait RequiredCredentialProvider {
  def accessToken: String
}

object RequiredCredentialProvider {
  private val provider: RequiredCredentialProvider =
    ServiceLoader.load(classOf[RequiredCredentialProvider]).iterator().next()

  def current: RequiredCredentialProvider = provider
}

/**
 * The JVM wraps the singleton initializer's NoSuchElementException in
 * ExceptionInInitializerError before ScalaTest can construct this suite.
 */
class ImplicitInitializationFailure extends AnyFunSuite {
  private val provider = RequiredCredentialProvider.current

  test("unreachable") {
    fail("suite construction must fail first")
  }
}

class ExplicitInitializationFailure extends AnyFunSuite {
  test("throws ExceptionInInitializerError from user test code") {
    throw new ExceptionInInitializerError(
      new IllegalStateException("explicitly thrown by user test code")
    )
  }
}
