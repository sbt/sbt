package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
	private boolean ansiCodesSupported;
	private boolean parallel;

	public ForkConfiguration(boolean ansiCodesSupported, boolean parallel) {
		this.ansiCodesSupported = ansiCodesSupported;
		this.parallel = parallel;
	}

	public boolean isAnsiCodesSupported() {
		return ansiCodesSupported;
	}

	public boolean isParallel() {
		return parallel;
	}
}
