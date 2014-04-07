package sbt;

import sbt.testing.Framework;

public abstract class FrameworkLoader {

	protected abstract void logDebug(String message);

	/**
	 * @return null if no {@link Framework} could be loaded out of `names`
	 */
	public Framework loadFramework(String[] names) {
		for (String implClassName : names) {
			try {
				Object rawFramework = Class.forName(implClassName).newInstance();
				if (rawFramework instanceof Framework)
					return (Framework) rawFramework;
				else {
					try {
						return new FrameworkWrapper((org.scalatools.testing.Framework) rawFramework);
					} catch (ClassCastException e) {
						logDebug("Framework " + rawFramework + " is neither an sbt.testing.Framework nor an org.scalatools.testing.Framework");
					}
				}
			} catch (ClassNotFoundException e) {
				logDebug("Framework implementation '" + implClassName + "' not present.");
			} catch (InstantiationException e) {
				logDebug("Framework implementation '" + implClassName + "' cannot be instiated: " + e.getMessage());
			} catch (IllegalAccessException e) {
				logDebug("Framework implementation '" + implClassName + "' cannot be accessed: " + e.getMessage());
			}
		}
		return null;
	}
}
