package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
	private boolean ansiCodesSupported;
	private boolean parallel;
	private boolean hideStandardOutput;

	public ForkConfiguration(boolean ansiCodesSupported, boolean parallel, boolean hideStandardOutput) {
		this.ansiCodesSupported = ansiCodesSupported;
		this.parallel = parallel;
		this.hideStandardOutput = hideStandardOutput;
	}

	public boolean isAnsiCodesSupported() {
		return ansiCodesSupported;
	}

	public boolean isParallel() {
		return parallel;
	}

	public boolean isHideStandardOutput() {
		return hideStandardOutput;
	}
}
