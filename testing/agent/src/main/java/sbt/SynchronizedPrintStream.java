// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import net.sf.cglib.core.*;
import net.sf.cglib.proxy.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

class SynchronizedPrintStream {

    @SuppressWarnings({"UnusedDeclaration", "MismatchedReadAndWriteOfArray"})
    private static final Class<?>[] DO_NOT_REMOVE_WHEN_MINIMIZING_THE_JAR = {
            // these are the same as in net.sf.cglib.proxy.CallbackInfo.CALLBACKS
            NoOp.class,
            MethodInterceptor.class,
            InvocationHandler.class,
            LazyLoader.class,
            Dispatcher.class,
            FixedValue.class,
            ProxyRefDispatcher.class,
    };

    public static PrintStream create(OutputStream out, Charset charset, Object sharedLock) {
        Enhancer e = new Enhancer();
        e.setSuperclass(PrintStream.class);
        e.setCallback(new SynchronizedMethodInterceptor(sharedLock));
        e.setNamingPolicy(new PrefixOverridingNamingPolicy(SynchronizedPrintStream.class.getName()));
        e.setUseFactory(false);
        return (PrintStream) e.create(
                new Class[]{OutputStream.class, boolean.class, String.class},
                new Object[]{out, false, charset.name()}
        );
    }

    private static class SynchronizedMethodInterceptor implements MethodInterceptor {
        private final Object sharedLock;

        public SynchronizedMethodInterceptor(Object sharedLock) {
            this.sharedLock = sharedLock;
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            synchronized (obj) {
                synchronized (sharedLock) {
                    return proxy.invokeSuper(obj, args);
                }
            }
        }
    }

    private static class PrefixOverridingNamingPolicy extends DefaultNamingPolicy {
        private final String prefix;

        private PrefixOverridingNamingPolicy(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String getClassName(String prefix, String source, Object key, Predicate names) {
            return super.getClassName(this.prefix, source, key, names);
        }
    }
}
