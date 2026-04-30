package example;

import java.lang.instrument.Instrumentation;

public class JavaAgentMain {
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        new example.A();
    }
}
