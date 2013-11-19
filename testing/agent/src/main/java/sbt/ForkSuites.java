package sbt;

import sbt.testing.TaskDef;

import java.io.Serializable;

public class ForkSuites implements Serializable {

	private String frameworkName;
	private TaskDef[] taskDefs;

	public ForkSuites(String frameworkName, TaskDef taskDef) {
		this.frameworkName = frameworkName;
		this.taskDefs = new TaskDef[] { taskDef };
	}

	public ForkSuites(String frameworkName, TaskDef[] taskDefs) {
		this.frameworkName = frameworkName;
		this.taskDefs = taskDefs;
	}

	public String getFrameworkName() {
		return frameworkName;
	}

	public TaskDef[] getTaskDefs() {
		return taskDefs;
	}
}
