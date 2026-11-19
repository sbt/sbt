package lmcoursier.internal

import java.util.Locale

import lmcoursier.internal.jniutils.LmNativeApi

import scala.util.control.NonFatal

/** Makes the shaded coursier use LmNativeApi, whose unshaded native methods the jni-utils DLL binds. */
object JniUtilsSetup:
  private lazy val setup: Unit =
    if isWindows then
      try LmNativeApi.setup()
      catch case NonFatal(_) | _: LinkageError => ()

  private def isWindows: Boolean =
    sys.props.get("os.name").exists(_.toLowerCase(Locale.ROOT).contains("windows"))

  def ensureSetup(): Unit = setup
end JniUtilsSetup
