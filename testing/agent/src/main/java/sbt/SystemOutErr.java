// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import java.io.PrintStream;

public class SystemOutErr implements OutErr {

    @Override
    public PrintStream out() {
        return System.out;
    }

    @Override
    public PrintStream err() {
        return System.err;
    }

    @Override
    public void setOut(PrintStream out) {
        System.setOut(out);
    }

    @Override
    public void setErr(PrintStream err) {
        System.setErr(err);
    }
}
