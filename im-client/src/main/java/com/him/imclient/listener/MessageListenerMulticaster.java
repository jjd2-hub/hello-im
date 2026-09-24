package com.him.imclient.listener;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSONObject;
import com.him.imclient.annotation.IMListener;
import com.him.imcommon.enums.IMListenerType;
import com.him.imcommon.model.IMBatchSendResult;
import com.him.imcommon.model.IMSendResult;
import com.him.imcommon.model.IMUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 *消息发送结果的分发器——把 im-server 回推的"消息发送结果"分发给业务方实现的所有 MessageListener
 */
@Component
public class MessageListenerMulticaster {

    @Autowired(required = false)
    private List<MessageListener> messageListeners = Collections.emptyList();

    /*
     *@param listenerType 本次结果属于哪种消息类型（PRIVATE_MESSAGE / GROUP_MESSAGE / SYSTEM_MESSAGE
     *@param batchResults 批量消息发送结果
     */
    public void multicast(IMListenerType listenerType, List<IMBatchSendResult> batchResults) {
        if (CollUtil.isEmpty(batchResults)) {
            return;
        }
        List<IMSendResult> results = new ArrayList<>();
        //IMBatchSendResult 是"批量结果"——一条消息发给 N 个人的结果。
        for (IMBatchSendResult batchResult : batchResults) {
            List<IMUserInfo> receivers = batchResult.getReceivers();
            //对于每个 receiver 生成一个 IMSendResult
            for (IMUserInfo receiver : receivers) {
                IMSendResult result = new IMSendResult();
                result.setSender(batchResult.getSender());
                result.setCode(batchResult.getCode());
                result.setReceiver(receiver);
                result.setData(batchResult.getData());
                results.add(result);
            }
        }

        for (MessageListener listener : messageListeners) {
            IMListener annotation = listener.getClass().getAnnotation(IMListener.class);
            //读取注解@IMListener，判断 ALL 或精确匹配（因为消息有 4 种类型）
            if (annotation != null && (annotation.type().equals(IMListenerType.ALL) || annotation.type()
                    .equals(listenerType))) {
                //泛型反序列化
                // IMBatchSendResult 从 Redis 读出来时，FastJSON 把 data（类型是 Object）反序列化成 JSONObject。
                //业务期望的是具体的 VO（如 PrivateMessageVO）。
                //需要把 JSONObject 转成具体类型。
                results.forEach(result -> {
                    // 将data转回对象类型
                    if (result.getData() instanceof JSONObject) {
                        //1、拿监听器的接口
                        Type superClass = listener.getClass().getGenericInterfaces()[0];
                        //第 2 步：拿泛型参数
                        Type type = ((ParameterizedType)superClass).getActualTypeArguments()[0];
                        //第 3 步：JSONObject 转具体类型
                        JSONObject data = (JSONObject)result.getData();
                        result.setData(data.toJavaObject(type));
                    }
                });
                // 回调到调用方处理
                listener.process(results);

            }
        }
    }

}
