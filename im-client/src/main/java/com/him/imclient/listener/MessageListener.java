package com.him.imclient.listener;


import com.him.imcommon.model.IMSendResult;

import java.util.List;

public interface MessageListener<T> {

     void process(List<IMSendResult<T>> result);

}
