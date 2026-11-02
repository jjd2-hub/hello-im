package com.him.imserver.netty.processor;

import io.netty.channel.ChannelHandlerContext;

public abstract class AbstractMessageProcessor<T> {

    //处理“客户端主动发来”的消息
    public void process(ChannelHandlerContext ctx, T data) {
    }

    //处理“后台服务通过 Redis 队列发来”的消息。
    public void process(T data) {
    }

    //把 JSON 变成 Java 对象
    //经过 Netty 的 MessageProtocolDecoder 解码后，IMSendInfo 里的 data 字段默认是一个 HashMap（或者 JSONObject）。
    //但是，LoginProcessor 需要的是 IMLoginInfo 对象，HeartbeatProcessor 需要的是 IMHeartbeatInfo 对象。
    public T transForm(Object o) {
        return (T) o;
    }


}
