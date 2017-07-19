object Compatibility {

  implicit class UpdateReportOps(val rep: sbt.UpdateReport) extends AnyVal {
    def configuration(conf: sbt.Configuration) =
      rep.configuration(conf.name)
  }

}
