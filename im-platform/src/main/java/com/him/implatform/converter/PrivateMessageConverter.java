package com.him.implatform.converter;

import com.him.imcommon.util.BeanUtil;
import com.him.implatform.entity.PrivateMessage;
import com.him.implatform.vo.PrivateMessageVO;

/**
 * 私聊消息 实体 → VO。
 */
public final class PrivateMessageConverter {

    private PrivateMessageConverter() {
    }

    /**
     * @param message 消息实体
     * @param deleted 该消息对<b>当前查询者</b>是否已删除。删除是"仅对自己生效"的,
     *                所以这个标记必须由调用方按查看者算好传进来,不能从实体上取
     */
    public static PrivateMessageVO toVo(PrivateMessage message, boolean deleted) {
        PrivateMessageVO vo = BeanUtil.copyProperties(message, PrivateMessageVO.class);
        vo.setDeleted(deleted);
        return vo;
    }
}
