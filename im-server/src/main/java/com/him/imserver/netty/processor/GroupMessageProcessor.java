package com.him.imserver.netty.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.him.imcommon.constant.IMRedisKey;
import com.him.imcommon.enums.IMCmdType;
import com.him.imcommon.enums.IMSendCode;
import com.him.imcommon.model.IMBatchSendResult;
import com.him.imcommon.model.IMRecvInfo;
import com.him.imcommon.model.IMSendInfo;
import com.him.imcommon.model.IMUserInfo;
import com.him.imcommon.mq.RedisMQTemplate;
import com.him.imcommon.util.ThreadPoolExecutorFactory;
import com.him.imserver.netty.UserChannelCtxMap;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
//从 Redis 队列里拿到一条群聊消息，然后把它推给当前服务器上所有在线的群成员，
// 并异步统计“谁推送成功、谁推送失败”，最后把结果回写给业务系统。
public class GroupMessageProcessor extends AbstractMessageProcessor<IMRecvInfo> {

    private final RedisMQTemplate redisMQTemplate;
    private final ScheduledThreadPoolExecutor EXECUTOR = ThreadPoolExecutorFactory.getThreadPoolExecutor();

    @Override
    public void process(IMRecvInfo recvInfo) {
        IMUserInfo sender = recvInfo.getSender();
        List<IMUserInfo> receivers = recvInfo.getReceivers();
        log.info("接收到群消息，发送者:{},接收用户数量:{}，内容:{}", sender.getId(), receivers.size(), recvInfo.getData());
        //三种接收者
        // 没找到连接（noChannelReceivers）：对方不在线，或者不在当前这台服务器上。
        //抛出异常（errorReceivers）：比如 Netty 的通道已经损坏。
        //成功交给 Netty 发送（pushTasks）
        List<IMUserInfo> noChannelReceivers = new ArrayList<>();
        List<IMUserInfo> errorReceivers = new ArrayList<>();
        List<PushTask> pushTasks = new ArrayList<>(receivers.size());
        for (IMUserInfo receiver : receivers) {
            try {
                ChannelHandlerContext channelCtx =
                    UserChannelCtxMap.getChannelCtx(receiver.getId(), receiver.getTerminal());
                if (!Objects.isNull(channelCtx)) {
                    IMSendInfo<Object> sendInfo = new IMSendInfo<>();
                    sendInfo.setCmd(IMCmdType.GROUP_MESSAGE.code());
                    sendInfo.setData(recvInfo.getData());
                    pushTasks.add(new PushTask(receiver, channelCtx.channel().writeAndFlush(sendInfo)));
                } else {
                    noChannelReceivers.add(receiver);
                    log.error("未找到channel,发送者:{},接收id:{}，内容:{}", sender.getId(), receiver.getId(),
                        recvInfo.getData());
                }
            } catch (Exception e) {
                errorReceivers.add(receiver);
                log.error("发送消息异常,发送者:{},接收id:{}，内容:{}", sender.getId(), receiver.getId(),
                    recvInfo.getData(), e);
            }
        }
        sendResult(recvInfo, noChannelReceivers, IMSendCode.NOT_FIND_CHANNEL);
        sendResult(recvInfo, errorReceivers, IMSendCode.UNKONW_ERROR);
        listenPushResults(recvInfo, sender, pushTasks);
    }

    //异步回执
    //Netty 的 writeAndFlush 是异步的（它返回一个 ChannelFuture）。
    // 如果群里有 500 个人，你不知道这 500 个 Future 什么时候全部执行完？如果某一个卡住了，难道要死等吗？
    private void listenPushResults(IMRecvInfo recvInfo, IMUserInfo sender, List<PushTask> pushTasks) {
        if (!Boolean.TRUE.equals(recvInfo.getSendResult()) || CollectionUtil.isEmpty(pushTasks)) {
            return;
        }
        //// 线程安全的 List，用于收集异步回调的结果
        List<IMUserInfo> successReceivers = Collections.synchronizedList(new ArrayList<>(pushTasks.size()));
        List<IMUserInfo> asyncErrorReceivers = Collections.synchronizedList(new ArrayList<>());
        // 原子类：记录还有多少个任务没完成
        AtomicInteger pendingPush = new AtomicInteger(pushTasks.size());
        // 原子类：防止 for 循环还没跑完，回调就提前触发了
        //loopFinished 保证了：必须等 for 循环把所有人都注册完，才有资格去判断“是否全部完成”。
        AtomicBoolean loopFinished = new AtomicBoolean(false);
        // 当所有任务都完成时，执行的逻辑
        Runnable tryComplete = () -> {
            if (loopFinished.get() && pendingPush.get() == 0) {
                sendResult(recvInfo, successReceivers, IMSendCode.SUCCESS);
                sendResult(recvInfo, asyncErrorReceivers, IMSendCode.UNKONW_ERROR);
            }
        };
        for (PushTask task : pushTasks) {
            IMUserInfo receiver = task.getReceiver();
            task.getFuture().addListener(f -> {
                if (f.isSuccess()) {
                    successReceivers.add(receiver);
                } else {
                    asyncErrorReceivers.add(receiver);
                    log.error("消息推送失败,发送者:{},接收id:{}，内容:{}", sender.getId(), receiver.getId(),
                        recvInfo.getData(), f.cause());
                }
                pendingPush.decrementAndGet();
                tryComplete.run();
            });
        }
        //因为 Netty 太快了。假设群里有 3 个人，你可能在第 1 个人的 addListener 刚注册完，它的回调就瞬间被执行了。
        // 如果此时 pendingPush 变成了 0，触发 tryComplete，但实际上你后面还有第 2、第 3 个人还没加监听呢！
        loopFinished.set(true);
        tryComplete.run();
    }

    //把结果返回给 Redis
    private void sendResult(IMRecvInfo recvInfo, List<IMUserInfo> receivers, IMSendCode sendCode) {
        if (Boolean.TRUE.equals(recvInfo.getSendResult()) && CollectionUtil.isNotEmpty(receivers)) {
            EXECUTOR.execute(() -> {
                IMBatchSendResult<Object> result = new IMBatchSendResult<>();
                result.setSender(recvInfo.getSender());
                result.setReceivers(receivers);
                result.setCode(sendCode.code());
                result.setData(recvInfo.getData());
                String key = StrUtil.join(":", IMRedisKey.IM_RESULT_GROUP_QUEUE, recvInfo.getServiceName());
                redisMQTemplate.opsForList().rightPush(key, result);
            });
        }
    }
}
