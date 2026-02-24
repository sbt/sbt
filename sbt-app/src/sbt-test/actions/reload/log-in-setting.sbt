
version := {
  sLog.value.info("Version is initializing")
  version.value
}

TaskKey[Unit]("evil-clear-logger") := Def.uncached {
  val logger = sLog.value
  val cls = logger.getClass
  val field = cls.getDeclaredField("ref")
  field.setAccessible(true)
  val ref = field.get(logger).asInstanceOf[java.lang.ref.WeakReference[?]]
  // MUHAHAHHAHAHAHAHHAHA, I am de evil GC, I clear things.
  ref.clear()
}