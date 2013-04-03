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
	static class ForkSelector extends Selector implements Serializable {}
	static class ForkSuiteSelector extends ForkSelector {}
	static class ForkTestSelector extends ForkSelector {
		private String testName;
		ForkTestSelector(TestSelector testSelector) {
			this.testName = testSelector.getTestName();
		}
		public String getTestName() {
			return testName;
		}
	}
	static class ForkNestedSuiteSelector extends ForkSelector {
		private String suiteId;
		ForkNestedSuiteSelector(NestedSuiteSelector nestedSuiteSelector) {
			this.suiteId = nestedSuiteSelector.getSuiteId();
		}
		public String getSuiteId() {
			return suiteId;
		}
	}
	static class ForkNestedTestSelector extends ForkSelector {
		private String suiteId;
		private String testName;
		ForkNestedTestSelector(NestedTestSelector nestedTestSelector) {
			this.suiteId = nestedTestSelector.getSuiteId();
			this.testName = nestedTestSelector.getTestName();
		}
		public String getSuiteId() {
			return suiteId;
		}
		public String getTestName() {
			return testName;
		}
	}
	
	static class ForkEvent implements Event, Serializable {
		private String fullyQualifiedName;
		private boolean isModule;
		private ForkSelector selector;
		private Status status;
		private Throwable throwable;
		ForkEvent(Event e) {
			fullyQualifiedName = e.fullyQualifiedName();
			isModule = e.isModule();
			selector = forkSelector(e.selector());
			status = e.status();
			if (e.throwable() != null) throwable = new ForkError(e.throwable());
		}
		public String fullyQualifiedName() { return fullyQualifiedName; }
		public boolean isModule() { return isModule; }
		public Selector selector() { return selector; }
		public Status status() { return status; }
		public Throwable throwable() { return throwable; }
		protected ForkSelector forkSelector(Selector selector) {
			if (selector instanceof SuiteSelector)
				return new ForkSuiteSelector();
			else if (selector instanceof TestSelector)
				return new ForkTestSelector((TestSelector) selector);
			else if (selector instanceof NestedSuiteSelector)
				return new ForkNestedSuiteSelector((NestedSuiteSelector) selector);
			else
				return new ForkNestedTestSelector((NestedTestSelector) selector);
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
		void writeEvents(ObjectOutputStream os, ForkTestDefinition test, ForkEvent[] events) {
			write(os, new Object[]{test.name, events});
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

				ArrayList<ForkTestDefinition> filteredTests = new ArrayList<ForkTestDefinition>();
				for (Fingerprint testFingerprint : framework.fingerprints()) {
					for (ForkTestDefinition test : tests) {
						if (matches(testFingerprint, test.fingerprint)) filteredTests.add(test);
					}
				}
				final Runner runner = framework.runner(frameworkArgs, remoteFrameworkArgs, getClass().getClassLoader());
				for (ForkTestDefinition test : filteredTests)
					runTestSafe(test, runner, loggers, os);
				runner.done();
			}
			write(os, ForkTags.Done);
			is.readObject();
		}
		void runTestSafe(ForkTestDefinition test, Runner runner, Logger[] loggers, ObjectOutputStream os) {
			ForkEvent[] events;
			try {
				events = runTest(test, runner, loggers, os);
			} catch (Throwable t) {
				events = new ForkEvent[] { testError(os, test, "Uncaught exception when running " + test.name + ": " + t.toString(), t) };
			}
			writeEvents(os, test, events);
		}
		ForkEvent[] runTest(ForkTestDefinition test, Runner runner, Logger[] loggers, ObjectOutputStream os) {
			final List<ForkEvent> events = new ArrayList<ForkEvent>();
			EventHandler handler = new EventHandler() { public void handle(Event e){ events.add(new ForkEvent(e)); } };
			// TODO: To pass in correct explicitlySpecified and selectors
			runner.task(test.name, test.fingerprint, false, new Selector[] { new SuiteSelector() }).execute(handler, loggers);
			return events.toArray(new ForkEvent[events.size()]);
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
		ForkEvent testEvent(final String fullyQualifiedName, final Fingerprint fingerprint, final Selector selector, final Status r, final Throwable err) {
			return new ForkEvent(new Event() {
				public String fullyQualifiedName() { return fullyQualifiedName; }
				public boolean isModule() { return fingerprint instanceof SubclassFingerprint ? ((SubclassFingerprint) fingerprint).isModule() : ((AnnotatedFingerprint) fingerprint).isModule(); }
				public Selector selector() { return selector; }
				public Status status() { return r; }
				public Throwable throwable() { return err; }
			});
		}
		ForkEvent testError(ObjectOutputStream os, ForkTestDefinition test, String message) {
			logError(os, message);
			return testEvent(test.name, test.fingerprint, new SuiteSelector(), Status.Error, null);
		}
		ForkEvent testError(ObjectOutputStream os, ForkTestDefinition test, String message, Throwable t) {
			logError(os, message);
			write(os, t);
			return testEvent(test.name, test.fingerprint, new SuiteSelector(), Status.Error, t);
		}
	}
}
