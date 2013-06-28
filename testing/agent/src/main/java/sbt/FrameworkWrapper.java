package sbt;

import sbt.testing.*;

public class FrameworkWrapper implements Framework {

	private org.scalatools.testing.Framework oldFramework;

	public FrameworkWrapper(org.scalatools.testing.Framework oldFramework) {
		this.oldFramework = oldFramework;
	}

	public String name() {
		return oldFramework.name();
	}

	public Fingerprint[] fingerprints() {
		org.scalatools.testing.Fingerprint[] oldFingerprints = oldFramework.tests();
		int length = oldFingerprints.length;
		Fingerprint[] fingerprints = new Fingerprint[length];
		for (int i=0; i < length; i++) {
			org.scalatools.testing.Fingerprint oldFingerprint = oldFingerprints[i];
			if (oldFingerprint instanceof org.scalatools.testing.TestFingerprint)
				fingerprints[i] = new TestFingerprintWrapper((org.scalatools.testing.TestFingerprint) oldFingerprint);
			else if (oldFingerprint instanceof org.scalatools.testing.SubclassFingerprint)
				fingerprints[i] = new SubclassFingerprintWrapper((org.scalatools.testing.SubclassFingerprint) oldFingerprint);
			else
				fingerprints[i] = new AnnotatedFingerprintWrapper((org.scalatools.testing.AnnotatedFingerprint) oldFingerprint);
		}
		return fingerprints;
	}

	public Runner runner(String[] args, String[] remoteArgs, ClassLoader testClassLoader) {
		return new RunnerWrapper(oldFramework, testClassLoader, args);
	}
}

class SubclassFingerprintWrapper implements SubclassFingerprint {
	private String superclassName;
	private boolean isModule;
	private boolean requireNoArgConstructor;

	public SubclassFingerprintWrapper(org.scalatools.testing.SubclassFingerprint oldFingerprint) {
		this.superclassName = oldFingerprint.superClassName();
		this.isModule = oldFingerprint.isModule();
		this.requireNoArgConstructor = false;  // Old framework SubclassFingerprint does not require no arg constructor
	}

	public boolean isModule() {
		return isModule;
	}

	public String superclassName() {
		return superclassName;
	}
	
	public boolean requireNoArgConstructor() {
		return requireNoArgConstructor;
	}
}

class AnnotatedFingerprintWrapper implements AnnotatedFingerprint {
	private String annotationName;
	private boolean isModule;

	public AnnotatedFingerprintWrapper(org.scalatools.testing.AnnotatedFingerprint oldFingerprint) {
		this.annotationName = oldFingerprint.annotationName();
		this.isModule = oldFingerprint.isModule();
	}

	public boolean isModule() {
		return isModule;
	}

	public String annotationName() {
		return annotationName;
	}
}

class TestFingerprintWrapper extends SubclassFingerprintWrapper {

	public TestFingerprintWrapper(org.scalatools.testing.TestFingerprint oldFingerprint) {
		super(oldFingerprint);
	}
}

class EventHandlerWrapper implements org.scalatools.testing.EventHandler {

	private EventHandler newEventHandler;
	private String fullyQualifiedName;
	private Fingerprint fingerprint;

	public EventHandlerWrapper(EventHandler newEventHandler, String fullyQualifiedName, Fingerprint fingerprint) {
		this.newEventHandler = newEventHandler;
		this.fullyQualifiedName = fullyQualifiedName;
		this.fingerprint = fingerprint;
	}

	public void handle(org.scalatools.testing.Event oldEvent) {
		newEventHandler.handle(new EventWrapper(oldEvent, fullyQualifiedName, fingerprint));
	}
}

class EventWrapper implements Event {

	private org.scalatools.testing.Event oldEvent;
	private String className;
	private Fingerprint fingerprint;
	private OptionalThrowable throwable;

	public EventWrapper(org.scalatools.testing.Event oldEvent, String className, Fingerprint fingerprint) {
		this.oldEvent = oldEvent;
		this.className = className;
		this.fingerprint = fingerprint;
		Throwable oldThrowable = oldEvent.error();
		if (oldThrowable == null)
			throwable = new OptionalThrowable();
		else
			throwable = new OptionalThrowable(oldThrowable);
	}

	public String fullyQualifiedName() {
		return className;
	}

	public Fingerprint fingerprint() {
		return fingerprint; 
	}

	public Selector selector() {
		return new TestSelector(oldEvent.testName());
	}

	public Status status() {
		switch (oldEvent.result()) {
			case Success: 
				return Status.Success;
			case Error: 
				return Status.Error;
			case Failure:
				return Status.Failure;
			case Skipped:
				return Status.Skipped;
			default:
				throw new IllegalStateException("Invalid status.");
		}
	}

	public OptionalThrowable throwable() {
		return throwable;
	}

	public long duration() {
		return 0;  // Just return 0 as old event does not have duration.
	}
}

class RunnerWrapper implements Runner {

	private org.scalatools.testing.Framework oldFramework;
	private ClassLoader testClassLoader;
	private String[] args;

	public RunnerWrapper(org.scalatools.testing.Framework oldFramework, ClassLoader testClassLoader, String[] args) {
		this.oldFramework = oldFramework;
		this.testClassLoader = testClassLoader;
		this.args = args;
	}
	
	public Task[] tasks(TaskDef[] taskDefs) {
		int length = taskDefs.length;
		Task[] tasks = new Task[length];
		for (int i = 0; i < length; i++) {
			TaskDef taskDef = taskDefs[i];
			tasks[i] = createTask(taskDef.fullyQualifiedName(), taskDef.fingerprint(), taskDef.explicitlySpecified(), taskDef.selectors());
		}
		return tasks;
	}

	public Task createTask(final String fullyQualifiedName, final Fingerprint fingerprint, boolean explicitlySpecified, Selector[] selectors) {
		final TaskDef taskDef = new TaskDef(fullyQualifiedName, fingerprint, explicitlySpecified, selectors);
		return new Task() {
			public String[] tags() {
				return new String[0];  // Old framework does not support tags
			}
			
			private org.scalatools.testing.Logger createOldLogger(final Logger logger) {
				return new org.scalatools.testing.Logger() {
					public boolean ansiCodesSupported() { return logger.ansiCodesSupported(); } 
					public void error(String msg) { logger.error(msg); }
					public void warn(String msg) { logger.warn(msg); }
					public void info(String msg) { logger.info(msg); }
					public void debug(String msg) { logger.debug(msg); }
					public void trace(Throwable t) { logger.trace(t); }
				};
			}
			
			private void runRunner(org.scalatools.testing.Runner runner, Fingerprint fingerprint, EventHandler eventHandler) {
				// Old runner only support subclass fingerprint.
				final SubclassFingerprint subclassFingerprint = (SubclassFingerprint) fingerprint;
				org.scalatools.testing.TestFingerprint oldFingerprint = 
					new org.scalatools.testing.TestFingerprint() {
						public boolean isModule() { return subclassFingerprint.isModule(); }
						public String superClassName() { return subclassFingerprint.superclassName(); }
					};
				runner.run(fullyQualifiedName, oldFingerprint, new EventHandlerWrapper(eventHandler, fullyQualifiedName, subclassFingerprint), args);
			}
			
			private void runRunner2(org.scalatools.testing.Runner2 runner, Fingerprint fingerprint, EventHandler eventHandler) {
				org.scalatools.testing.Fingerprint oldFingerprint = null;
				if (fingerprint instanceof SubclassFingerprint) {
					final SubclassFingerprint subclassFingerprint = (SubclassFingerprint) fingerprint;
					oldFingerprint = new org.scalatools.testing.SubclassFingerprint() {
						public boolean isModule() { return subclassFingerprint.isModule(); }
						public String superClassName() { return subclassFingerprint.superclassName(); }
					};
				}
				else {
					final AnnotatedFingerprint annotatedFingerprint = (AnnotatedFingerprint) fingerprint;
					oldFingerprint = new org.scalatools.testing.AnnotatedFingerprint() {
						public boolean isModule() { return annotatedFingerprint.isModule(); }
						public String annotationName() { return annotatedFingerprint.annotationName(); }
					};
				}
				runner.run(fullyQualifiedName, oldFingerprint, new EventHandlerWrapper(eventHandler, fullyQualifiedName, fingerprint), args);
			}
        
			public Task[] execute(EventHandler eventHandler, Logger[] loggers) {
				int length = loggers.length;
				org.scalatools.testing.Logger[] oldLoggers = new org.scalatools.testing.Logger[length];
				for (int i=0; i<length; i++) {
					oldLoggers[i] = createOldLogger(loggers[i]);
				}

				org.scalatools.testing.Runner runner = oldFramework.testRunner(testClassLoader, oldLoggers); 
				if (runner instanceof org.scalatools.testing.Runner2) {
					runRunner2((org.scalatools.testing.Runner2) runner, fingerprint, eventHandler);
				}
				else {
					runRunner(runner, fingerprint, eventHandler);
				}
				return new Task[0];
			}
			
			public TaskDef taskDef() {
				return taskDef;
			}
		};
	}

	public String done() {
		return "";
	}

	public String[] args() {
		return args;
	}

	public String[] remoteArgs() {
		return new String[0];  // Old framework does not support remoteArgs
	}
}