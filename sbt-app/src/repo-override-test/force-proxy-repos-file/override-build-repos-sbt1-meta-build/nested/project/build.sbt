val checkMetaBuildResolvers = taskKey[Unit]("Verifies the meta-build repository override")

checkMetaBuildResolvers := {
  val names = csrResolvers.value.map(_.name).sorted
  assert(names == Seq("internal-proxy", "local"), s"unexpected meta-build resolvers: $names")
}
