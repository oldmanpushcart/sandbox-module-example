package com.alibaba.jvm.sandbox.module.exampe.tcpwm.impl;

import com.alibaba.jvm.sandbox.api.listener.ext.Advice;
import com.alibaba.jvm.sandbox.api.listener.ext.AdviceListener;
import com.alibaba.jvm.sandbox.api.listener.ext.EventWatchBuilder;
import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import com.alibaba.jvm.sandbox.module.exampe.tcpwm.IoPipe;
import com.alibaba.jvm.sandbox.module.exampe.tcpwm.SocketWatcher;

/**
 * Hotspot.JDK8版本下的Socket守望者实现
 */
public class SocketWatcherImplHotspotJDK8 implements SocketWatcher {

    private final ModuleEventWatcher moduleEventWatcher;

    public SocketWatcherImplHotspotJDK8(final ModuleEventWatcher moduleEventWatcher) {
        this.moduleEventWatcher = moduleEventWatcher;
    }

    @Override
    public void watching(final IoPipe ioPipe) {

        new EventWatchBuilder(moduleEventWatcher)
                .onClass("java.net.SocketInputStream").includeBootstrap()
                /**/.onBehavior("read").withParameterTypes(byte[].class, int.class, int.class, int.class)
                .onClass("java.net.SocketOutputStream").includeBootstrap()
                /**/.onBehavior("socketWrite").withParameterTypes(byte[].class, int.class, int.class)
                .onWatch(new AdviceListener() {
                    @Override
                    protected void afterReturning(Advice advice) {
                        final String behaviorName = advice.getBehavior().getName();
                        if ("read".equals(behaviorName)) {
                            ioPipe.read(
                                    (byte[]) advice.getParameterArray()[0],
                                    (Integer) advice.getParameterArray()[1],
                                    (Integer) advice.getReturnObj()
                            );
                        } else if ("socketWrite".equals(behaviorName)) {
                            ioPipe.write(
                                    (byte[]) advice.getParameterArray()[0],
                                    (Integer) advice.getParameterArray()[1],
                                    (Integer) advice.getParameterArray()[2]
                            );
                        }
                    }

                });


    }

}
