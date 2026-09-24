package com.him.imclient.listener;

import cn.hutool.core.collection.CollUtil;
import com.him.imclient.annotation.IMListener;
import com.him.imcommon.enums.IMListenerType;
import com.him.imcommon.model.IMUserEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
 *用户事件的分发器——把 im-server 推来的"用户上下线事件"分发给业务方实现的所有 EventListener。
 */
@Component
public class EventListenerMulticaster {

    @Autowired(required = false)//找不到就不注入，没有实现类时也能启动
    private List<EventListener> eventListeners = Collections.emptyList();

    public void multicast(List<IMUserEvent> events) {
        if (CollUtil.isEmpty(events)) {
            return;
        }
        //遍历所有监听器
        for (EventListener listener : eventListeners) {
            IMListener annotation = listener.getClass().getAnnotation(IMListener.class);
            //如果业务类没标 @IMListener，返回 null
            // 如果监听器上有 @IMListener 注解，并且类型是 USER_EVENT
            if (!Objects.isNull(annotation) && annotation.type().equals(IMListenerType.USER_EVENT)) {
                // 回调到调用方处理，让业务处理具体实现
                listener.process(events);
                //业务方接收到 IMUserEvent 后，在 process 方法里自己区分，根据 eventType 判断是上线还是下线，然后做对应处理。
                //这个逻辑写在业务方自己实现的 EventListener 里，原项目里是 UserEventListener.java。
            }
        }
    }
}
