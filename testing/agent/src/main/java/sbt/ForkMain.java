/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt;

import sbt.testing.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class ForkMain {
	static class SubclassFingerscan implements SubclassFingerprint, Serializable {
		private boolean isModule;
		private String superclassName;
		private boolean requireNoArgConstructor;
		SubclassFingerscan(SubclassFingerprint print) {
			isModule = print.isModule();
			superclassName = print.superclassName();
			requireNoArgConstructor = print.requireNoArgConstructor();
		}
		public boolean isModule() { return isModule; }
		public String superclassName() { return superclassName; }
		public boolean requireNoArgConstructor() { return requireNoArgConstructor; }
	}
	static class AnnotatedFingerscan implements AnnotatedFingerprint, Serializable {
		private boolean isModule;
		private String annotationName;
		AnnotatedFingerscan(AnnotatedFingerprint print) {
			isModule = print.isModule();
			annotationName = print.annotationName();
		}
		public boolean isModule() { return isModule; }
		public String annotationName() { return annotationName; }
	}
	public static class ForkTestDefinition implements Serializable {
		public String name;
		public Fingerprint fingerprint;

		public ForkTestDefinition(String name, Fingerprint fingerprint) {
			this.name = name;
			if (fingerprint instanceof SubclassFingerprint) {
				this.fingerprint = new SubclassFingerscan((SubclassFingerprint) fingerprint);
			} else {
				this.fingerprint = new AnnotatedFingerscan((AnnotatedFingerprint) fingerprint);
			}
		}
	}
	static class ForkError extends Exception {
		private String originalMessage;
		private ForkError cause;
		ForkError(Throwable t) {
			originalMessage = t.getMessage();
			setStackTrace(t.getStackTrace());
			if (t.getCause() != null) cause = new ForkError(t.getCause());
		}
		public String getMessage() { return originalMessage; }
		public Exception getCause() { return cause; }
	}
	
	static class ForkEvent implements Event, Serializable {
		private String fullyQualifiedName;
		private Fingerprint fingerprint;
		private Selector selector;
		private Status status;
		private OptionalThrowable throwable;
		private long duration;
		ForkEvent(Event e) {
			fullyQualifiedName = e.fullyQualifiedName();
			Fingerprint rawFingerprint = e.fingerprint();
			if (rawFingerprint instanceof SubclassFingerprint) 
				this.fingerprint = new SubclassFingerscan((SubclassFingerprint) rawFingerprint);
			else 
				this.fingerprint = new AnnotatedFingerscan((AnnotatedFingerprint) rawFingerprint);
			selector = forkSelector(e.selector());
			status = e.status();
			OptionalThrowable originalThrowable = e.throwable();
			if (originalThrowable.isDefined())
				this.throwable = new OptionalThrowable(new ForkError(originalThrowable.get()));
			else
				this.throwable = originalThrowable;
			this.duration = e.duration();
		}
		public String fullyQualifiedName() { return fullyQualifiedName; }
		public Fingerprint fingerprint() { return fingerprint; }
		public Selector selector() { return selector; }
		public Status status() { return status; }
		public OptionalThrowable throwable() { return throwable; }
		public long duration() { return duration; }
		protected Selector forkSelector(Selector selector) {
			if (selector instanceof Serializable)
				return selector;
			else
				throw new UnsupportedOperationException("Selector implementation must be Serializable.");
		}
	}
	public static void main(String[] args) throws Exception {
		Socket socket = new Socket(InetAddress.getByName(null), Integer.valueOf(args[0]));
		final ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
		final ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
		try {
			try {
				new Run().run(is, os);
			} finally {
				is.close();
				os.close();
			}
		} finally {
			System.exit(0);
		}
	}
	private static class Run {
		boolean matches(Fingerprint f1, Fingerprint f2) {
			if (f1 instanceof SubclassFingerprint && f2 instanceof SubclassFingerprint) {
				final SubclassFingerprint sf1 = (SubclassFingerprint) f1;
				final SubclassFingerprint sf2 = (SubclassFingerprint) f2;
				return sf1.isModule() == sf2.isModule() && sf1.superclassName().equals(sf2.superclassName());
			} else if (f1 instanceof AnnotatedFingerprint && f2 instanceof AnnotatedFingerprint) {
				AnnotatedFingerprint af1 = (AnnotatedFingerprint) f1;
				AnnotatedFingerprint af2 = (AnnotatedFingerprint) f2;
				return af1.isModule() == af2.isModule() && af1.annotationName().equals(af2.annotationName());
			}
			return false;
		}
		class RunAborted extends RuntimeException {
			RunAborted(Exception e) { super(e); }
		}
		synchronized void write(ObjectOutputStream os, Object obj) {
			try {
				os.writeObject(obj);
				os.flush();
			} catch (IOException e) {
				throw new RunAborted(e);
			}
		}
		void logError(ObjectOutputStream os, String message) {
			write(os, new Object[]{ForkTags.Error, message});
		}
		void logDebug(ObjectOutputStream os, String message) {
			write(os, new Object[]{ForkTags.Debug, message});
		}
		void writeEvents(ObjectOutputStream os, TaskDef taskDef, ForkEvent[] events) {
			write(os, new Object[]{taskDef.fullyQualifiedName(), events});
		}
		void runTests(ObjectInputStream is, final ObjectOutputStream os) throws Exception {
			final boolean ansiCodesSupported = is.readBoolean();
			final ForkTestDefinition[] tests = (ForkTestDefinition[]) is.readObject();
			int nFrameworks = is.readInt();
			Logger[] loggers = {
				new Logger() {
					public boolean ansiCodesSupported() { return ansiCodesSupported; }
					public void error(String s) { logError(os, s); }
					public void warn(String s) { write(os, new Object[]{ForkTags.Warn, s}); }
					public void info(String s) { write(os, new Object[]{ForkTags.Info, s}); }
					public void debug(String s) { write(os, new Object[]{ForkTags.Debug, s}); }
					public void trace(Throwable t) { write(os, t); }
				}
			};

			for (int i = 0; i < nFrameworks; i++) {
				final String[] implClassNames = (String[]) is.readObject();
				final String[] frameworkArgs = (String[]) is.readObject();
				final String[] remoteFrameworkArgs = (String[]) is.readObject();

				Framework framework = null;
				for (String implClassName : implClassNames) {
					try {
						Object rawFramework = Class.forName(implClassName).newInstance();
						if (rawFramework instanceof Framework)
							framework = (Framework) rawFramework;
						else
							framework = new FrameworkWrapper((org.scalatools.testing.Framework) rawFramework);
						break;
					} catch (ClassNotFoundException e) {
						logDebug(os, "Framework implementation '" + implClassName + "' not present.");
					}
				}

				if (framework == null)
					continue;

				ArrayList<TaskDef> filteredTests = new ArrayList<TaskDef>();
				for (Fingerprint testFingerprint : framework.fingerprints()) {
					for (ForkTestDefinition test : tests) {
						// TODO: To pass in correct explicitlySpecified and selectors
						if (matches(testFingerprint, test.fingerprint)) 
							filteredTests.add(new TaskDef(test.name, test.fingerprint, false, new Selector[] { new SuiteSelector() }));
					}
				}
				final Runner runner = framework.runner(frameworkArgs, remoteFrameworkArgs, getClass().getClassLoader());
				Task[] tasks = runner.tasks(filteredTests.toArray(new TaskDef[filteredTests.size()]));
				for (Task task : tasks)
					runTestSafe(task, runner, loggers, os);
				runner.done();
			}
			write(os, ForkTags.Done);
			is.readObject();
		}
		class NestedTask {
			private String parentName;
			private Task task;
			NestedTask(String parentName, Task task) {
				this.parentName = parentName;
				this.task = task;
			}
			public String getParentName() {
				return parentName;
			}
			public Task getTask() {
				return task;
			}
		}
		void runTestSafe(Task task, Runner runner, Logger[] loggers, ObjectOutputStream os) {
			TaskDef taskDef = task.taskDef();
			try {
				List<NestedTask> nestedTasks = new ArrayList<NestedTask>();
				for (Task nt : runTest(taskDef, task, loggers, os))
					nestedTasks.add(new NestedTask(taskDef.fullyQualifiedName(), nt));
				while (true) {
					List<NestedTask> newNestedTasks = new ArrayList<NestedTask>();
					int nestedTasksLength = nestedTasks.size();
					for (int i = 0; i < nestedTasksLength; i++) {
						NestedTask nestedTask = nestedTasks.get(i);
						String nestedParentName = nestedTask.getParentName() + "-" + i;
						for (Task nt : runTest(nestedTask.getTask().taskDef(), nestedTask.getTask(), loggers, os)) {
							newNestedTasks.add(new NestedTask(nestedParentName, nt));
						}
					}
					if (newNestedTasks.size() == 0)
						break;
					else {
						nestedTasks = newNestedTasks;
					}
				}
			} catch (Throwable t) {
				writeEvents(os, taskDef, new ForkEvent[] { testError(os, taskDef, "Uncaught exception when running " + taskDef.fullyQualifiedName() + ": " + t.toString(), t) });
			}
		}
		Task[] runTest(TaskDef taskDef, Task task, Logger[] loggers, ObjectOutputStream os) {
			ForkEvent[] events;
			Task[] nestedTasks;
			try {
				final List<ForkEvent> eventList = new ArrayList<ForkEvent>();
				EventHandler handler = new EventHandler() { public void handle(Event e){ eventList.add(new ForkEvent(e)); } };
				nestedTasks = task.execute(handler, loggers);
				events = eventList.toArray(new ForkEvent[eventList.size()]);
			}
			catch (Throwable t) {
				nestedTasks = new Task[0];
				events = new ForkEvent[] { testError(os, taskDef, "Uncaught exception when running " + taskDef.fullyQualifiedName() + ": " + t.toString(), t) };
			}
			writeEvents(os, taskDef, events);
			return nestedTasks;
		}
		void run(ObjectInputStream is, ObjectOutputStream os) throws Exception {
			try {
				runTests(is, os);
			} catch (RunAborted e) {
				internalError(e);
			} catch (Throwable t) {
				try {
					logError(os, "Uncaught exception when running tests: " + t.toString());
					write(os, t);
				} catch (Throwable t2) {
					internalError(t2);
				}
			}
		}
		void internalError(Throwable t) {
			System.err.println("Internal error when running tests: " + t.toString());
		}
		ForkEvent testEvent(final String fullyQualifiedName, final Fingerprint fingerprint, final Selector selector, final Status r, final Throwable err, final long duration) {
			final OptionalThrowable throwable;
			if (err == null)
				throwable = new OptionalThrowable();
			else
				throwable = new OptionalThrowable(err);
			return new ForkEvent(new Event() {
				public String fullyQualifiedName() { return fullyQualifiedName; }
				public Fingerprint fingerprint() { return fingerprint; }
				public Selector selector() { return selector; }
				public Status status() { return r; }
				public OptionalThrowable throwable() { 
					return throwable; 
				}
				public long duration() {
					return duration;
				}
			});
		}
		ForkEvent testError(ObjectOutputStream os, TaskDef taskDef, String message) {
			logError(os, message);
			return testEvent(taskDef.fullyQualifiedName(), taskDef.fingerprint(), new SuiteSelector(), Status.Error, null, 0);
		}
		ForkEvent testError(ObjectOutputStream os, TaskDef taskDef, String message, Throwable t) {
			logError(os, message);
			write(os, t);
			return testEvent(taskDef.fullyQualifiedName(), taskDef.fingerprint(), new SuiteSelector(), Status.Error, t, 0);
		}
	}
}
