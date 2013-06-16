val confFilter = settingKey[ScopeFilter.ConfigurationFilter]("ConfigurationFilter")

confFilter := inConfigurations(Compile, Test)

val updateReports = Def.taskDyn { updateClassifiers.all(ScopeFilter(configurations = confFilter.value)) }

val newTask = taskKey[Unit]("")

newTask := updateReports.value
