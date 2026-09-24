package com.him.implatform.converter;

import com.him.imcommon.util.BeanUtil;
import com.him.implatform.entity.GroupMessage;
import com.him.implatform.vo.GroupMessageVO;

/**
 * 群聊消息 实体 → VO。
 */
public final class GroupMessageConverter {

    private GroupMessageConverter() {
    }

    /**
     * @param message     消息实体
     * @param deleted     该消息对<b>当前查询者</b>是否已删除(删除仅对自己生效)
     * @param readedCount 已读人数(与查看者无关,由调用方按群已读位置统计)
     */
    public static GroupMessageVO toVo(GroupMessage message, boolean deleted, int readedCount) {
        GroupMessageVO vo = BeanUtil.copyProperties(message, GroupMessageVO.class);
        // 实体里 @用户列表是逗号分隔字符串,VO 里是 List
        vo.setAtUserIds(GroupMessage.parseUserIds(message.getAtUserIds()));
        vo.setDeleted(deleted);
        vo.setReadedCount(readedCount);
        return vo;
    }
}
