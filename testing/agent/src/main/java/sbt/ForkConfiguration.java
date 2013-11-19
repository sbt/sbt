package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
	private boolean ansiCodesSupported;
	private boolean parallel;
	private boolean hideStandardOutput;
	private boolean captureStandardOutput;

	public ForkConfiguration(boolean ansiCodesSupported, boolean parallel, boolean hideStandardOutput, boolean captureStandardOutput) {
		this.ansiCodesSupported = ansiCodesSupported;
		this.parallel = parallel;
		this.hideStandardOutput = hideStandardOutput;
		this.captureStandardOutput = captureStandardOutput;
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

	/**
	 * Whether standard output should be captured and sent to the parent process.
	 * When false empty standard output will be sent.
	 */
	public boolean isCaptureStandardOutput() {
		return captureStandardOutput;
	}
}
