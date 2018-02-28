package com.alibaba.jvm.sandbox.module.exampe.tcpwm;

import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import com.alibaba.jvm.sandbox.module.exampe.tcpwm.impl.SocketWatcherImplHotspotJDK8;

/**
 * TCP观察者
 */
public interface SocketWatcher {

    /**
     * 观察IO
     *
     * @param ioPipe IO管道
     */
    void watching(IoPipe ioPipe);

    class Factory {

        static SocketWatcher make(final ModuleEventWatcher moduleEventWatcher) {
            // 各种平台适配...
            return new SocketWatcherImplHotspotJDK8(moduleEventWatcher);
        }

    }

}
