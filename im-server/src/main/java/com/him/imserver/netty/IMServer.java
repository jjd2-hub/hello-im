package com.him.imserver.netty;

public interface IMServer {

    boolean isReady();

    void start();

    void stop();
}
