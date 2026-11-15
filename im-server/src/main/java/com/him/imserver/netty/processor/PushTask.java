package com.him.imserver.netty.processor;

import com.him.imcommon.model.IMUserInfo;
import io.netty.channel.ChannelFuture;
import lombok.AllArgsConstructor;
import lombok.Getter;

//@Getter：生成 getReceiver() 和 getFuture()，方便外部读取。
@Getter
@AllArgsConstructor
public class PushTask {

    // 接收方信息
    private final IMUserInfo receiver;

    //Netty里面的类
    //在 Netty 中，调 ctx.writeAndFlush(msg) 发送消息，并不是立刻就把数据发出去了。
    // 网络发送是异步的，Netty 只是把数据塞进了发送缓冲区，然后立刻返回一个 ChannelFuture 对象给你。
    private final ChannelFuture future;
}
