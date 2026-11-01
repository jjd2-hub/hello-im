package com.him.imserver.netty;

import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserChannelCtxMap {

    /**
     *  维护userId和ctx  Context上下文的关联关系，格式:Map<userId,map<terminal，ctx>>
     *
     * 这是 im-server 的本地内存路由表。它维护着“哪个用户的哪个终端，对应着哪条网络连接（Channel）
     *
     * 外层 Key (Long)：userId（用户 ID）。
     * 内层 Key (Integer)：terminal（终端类型，如 0=Web, 1=App）。
     * 内层 Value (ChannelHandlerContext)：Netty 的长连接上下文。通过它可以直接给客户端发消息。
     * 100 (用户A)
     *      ├── 0 (Web端) ──> ChannelHandlerContext@abc (连接1)
     *      └── 2 (PC端)  ──> ChannelHandlerContext@xyz (连接2)
     */
    private static Map<Long, Map<Integer, ChannelHandlerContext>> channelMap = new ConcurrentHashMap();

    //用户登录成功时（在 LoginProcessor 中调用）。把当前连接存入内存。
    public static void addChannelCtx(Long userId, Integer channel, ChannelHandlerContext ctx) {
        channelMap.computeIfAbsent(userId, key -> new ConcurrentHashMap()).put(channel, ctx);
    }

    public static void removeChannelCtx(Long userId, Integer terminal) {
        if (userId != null && terminal != null && channelMap.containsKey(userId)) {
            Map<Integer, ChannelHandlerContext> userChannelMap = channelMap.get(userId);
            userChannelMap.remove(terminal);
            if (userChannelMap.isEmpty()) {
                channelMap.remove(userId);
            }
        }
    }

    public static ChannelHandlerContext getChannelCtx(Long userId, Integer terminal) {
        if (userId != null && terminal != null && channelMap.containsKey(userId)) {
            Map<Integer, ChannelHandlerContext> userChannelMap = channelMap.get(userId);
            if (userChannelMap.containsKey(terminal)) {
                return userChannelMap.get(terminal);
            }
        }
        return null;
    }

    public static Map<Integer, ChannelHandlerContext> getChannelCtx(Long userId) {
        if (userId == null) {
            return null;
        }
        return channelMap.get(userId);
    }

}
