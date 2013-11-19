// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package sbt;

public class OutputCapturerInstaller {

    private final OutErr outErr;

    public OutputCapturerInstaller(OutErr outErr) {
        this.outErr = outErr;
    }

    public void install(OutputCapturer capturer) {
        outErr.setOut(capturer.out());
        outErr.setErr(capturer.err());
    }
}
