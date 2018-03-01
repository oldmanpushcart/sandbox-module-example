package com.alibaba.jvm.sandbox.module.exampe.tcpwm;

import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.api.Module;
import com.alibaba.jvm.sandbox.api.ModuleException;
import com.alibaba.jvm.sandbox.api.ModuleLifecycle;
import com.alibaba.jvm.sandbox.api.http.Http;
import com.alibaba.jvm.sandbox.api.http.printer.ConcurrentLinkedQueuePrinter;
import com.alibaba.jvm.sandbox.api.http.printer.Printer;
import com.alibaba.jvm.sandbox.api.resource.ModuleController;
import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import org.kohsuke.MetaInfServices;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * JVM-SANDBOX练手模块：
 * Socket守望者
 */
@MetaInfServices(Module.class)
@Information(id = "ExpSocketWm", isActiveOnLoad = false, author = "oldmanpushcart@gmail.com", version = "0.0.6")
public class SocketWatchmanModule implements Module, ModuleLifecycle, IoPipe, Runnable {

    // 注入模块控制器(控制当前模块状态)
    @Resource
    private ModuleController moduleController;

    // 注入模块观察者
    @Resource
    private ModuleEventWatcher moduleEventWatcher;

    private final List<Printer> printers = new ArrayList<Printer>();

    @Override
    public void loadCompleted() {

        // 启动输出线程
        final Thread thread = new Thread(this,"socket-watchman-reporter");
        thread.setDaemon(true);
        thread.start();


        // 代码插桩
        SocketWatcher.Factory
                .make(moduleEventWatcher)
                .watching(this);
    }

    @Http("/show")
    public void show(final HttpServletResponse resp) throws ModuleException, IOException {

        // 注册Printer
        final Printer printer;
        synchronized (printers) {
            printers.add(printer = new ConcurrentLinkedQueuePrinter(resp.getWriter()));
        }

        moduleController.active();
        printer.println("SocketWatchman is working.\nPress CTRL_C abort it!");
        try {
            printer.waitingForBroken();
        } finally {
            moduleController.frozen();
            synchronized(printers) {
                printers.remove(printer);
            }
        }
    }

    /*
     * 5秒刷新一次
     */
    private static final long intervalMs = 1000 * 5;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Condition intervalCondition = rwLock.writeLock().newCondition();
    private final AtomicLong rByteCounter = new AtomicLong();
    private final AtomicLong wByteCounter = new AtomicLong();

    @Override
    public void read(byte[] buf, int off, int len) {
        rwLock.readLock().lock();
        try {
            rByteCounter.addAndGet(len);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        rwLock.readLock().lock();
        try {
            wByteCounter.addAndGet(len);
        } finally {
            rwLock.readLock().unlock();
        }
    }


    private volatile boolean isRunning = true;

    @Override
    public void run() {
        while (isRunning
                && !currentThread().isInterrupted()) {
            try {
                final long rByteCount;
                final long wByteCount;
                rwLock.writeLock().lock();
                try {
                    intervalCondition.await(intervalMs, MILLISECONDS);
                    rByteCount = rByteCounter.getAndSet(0);
                    wByteCount = wByteCounter.getAndSet(0);
                } finally {
                    rwLock.writeLock().unlock();
                }

                final String msg = String.format(""
                                + " READ : RATE=%.2f(kb)/sec ; TOTAL=%.2f(kb)\n"
                                + "WRITE : RATE=%.2f(kb)/sec ; TOTAL=%.2f(kb)\n"
                                + "statistics in %s(sec).\n",
                        1f * rByteCount / intervalMs, 1f * rByteCount / 1024,
                        1f * wByteCount / intervalMs, 1f * wByteCount / 1024,
                        intervalMs / 1000
                );

                synchronized(printers) {
                    for (final Printer printer : printers) {
                        try {
                            printer.println(msg);
                        } catch (Throwable cause) {
                            // ignore...
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onLoad() throws Throwable {
        isRunning = true;
    }

    @Override
    public void onUnload() throws Throwable {
        isRunning = false;
    }


    // -- 暂时不用

    @Override
    public void onActive() throws Throwable {

    }

    @Override
    public void onFrozen() throws Throwable {

    }
}
