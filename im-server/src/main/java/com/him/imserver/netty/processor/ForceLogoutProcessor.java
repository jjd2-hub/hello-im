package com.him.imserver.netty.processor;

import cn.hutool.core.util.StrUtil;
import com.him.imcommon.enums.IMCmdType;
import com.him.imcommon.model.IMForceLogoutData;
import com.him.imcommon.model.IMForceLogoutInfo;
import com.him.imcommon.model.IMSendInfo;
import com.him.imserver.constant.ChannelAttrKey;
import com.him.imserver.netty.UserChannelCtxMap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;


@Slf4j
@Component
@RequiredArgsConstructor

/**
 * 强制下线处理器
 */
public class ForceLogoutProcessor extends AbstractMessageProcessor<IMForceLogoutInfo> {
    @Override
    public void process(IMForceLogoutInfo info) {
        //通过UserId和Terminal从内存表（UserChannelCtxMap）里查
        ChannelHandlerContext context = UserChannelCtxMap.getChannelCtx(info.getUserId(), info.getTerminal());
        if (Objects.isNull(context)) {
            return;
        }
        //处理异地登陆，在电脑A登录，又在电脑B登录
        AttributeKey<String> devIdAttr = AttributeKey.valueOf(ChannelAttrKey.DEVICE_ID);
        String devId = context.channel().attr(devIdAttr).get();
        if (StrUtil.isEmpty(info.getDevId()) || !info.getDevId().equals(devId)) {
            IMForceLogoutData data = new IMForceLogoutData();
            data.setType(info.getType());
            data.setReason(info.getReason());
            IMSendInfo<IMForceLogoutData> sendInfo = new IMSendInfo<>();
            sendInfo.setCmd(IMCmdType.FORCE_LOGOUT.code());
            sendInfo.setData(data);
            context.channel().writeAndFlush(sendInfo).addListener(ChannelFutureListener.CLOSE);
            log.info("强制下线,userId:{},终端:{},type:{}", info.getUserId(), info.getTerminal(), info.getType());
        }
    }
}
