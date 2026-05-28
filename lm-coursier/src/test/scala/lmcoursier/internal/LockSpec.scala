package lmcoursier.internal

import verify.*

// progressBarActive decides whether resolution must be serialized. It is true only when coursier
// renders its interactive progress bar (no custom logger and not in fallback mode) -- the one thing
// that cannot be driven from more than one module at a time.
object LockSpec extends BasicTestSuite:

  test("a custom cache logger does not require the lock") {
    assert(!Lock.progressBarActive(hasCustomLogger = true, fallbackMode = false))
    assert(!Lock.progressBarActive(hasCustomLogger = true, fallbackMode = true))
  }

  test("the fallback (line-based) display does not require the lock") {
    assert(!Lock.progressBarActive(hasCustomLogger = false, fallbackMode = true))
  }

  test("only the interactive progress bar requires the lock") {
    assert(Lock.progressBarActive(hasCustomLogger = false, fallbackMode = false))
  }

end LockSpec
