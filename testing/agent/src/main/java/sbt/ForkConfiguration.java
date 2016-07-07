package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
	private final boolean ansiCodesSupported;
	private final boolean parallel;

	public ForkConfiguration(final boolean ansiCodesSupported, final boolean parallel) {
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
