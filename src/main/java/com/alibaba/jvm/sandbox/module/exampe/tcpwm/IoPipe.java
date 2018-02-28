package com.alibaba.jvm.sandbox.module.exampe.tcpwm;

/**
 * IO管道
 */
public interface IoPipe {

    /**
     * 读
     *
     * @param buf 数据缓冲
     * @param off 偏移量
     * @param len 读出长度
     */
    void read(byte buf[], int off, int len);

    /**
     * 写
     *
     * @param buf 数据缓冲
     * @param off 偏移量
     * @param len 写入长度
     */
    void write(byte buf[], int off, int len);

}
