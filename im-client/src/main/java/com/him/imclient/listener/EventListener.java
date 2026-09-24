package com.him.imclient.listener;

import com.him.imcommon.model.IMUserEvent;

import java.util.List;

public interface EventListener {

     void process(List<IMUserEvent> event);

}
