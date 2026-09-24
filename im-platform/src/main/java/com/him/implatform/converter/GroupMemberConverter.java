package com.him.implatform.converter;

import cn.hutool.core.util.StrUtil;
import com.him.imcommon.util.BeanUtil;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.entity.Group;
import com.him.implatform.vo.GroupMemberVO;

/**
 * 群成员 实体 → VO。
 */
public final class GroupMemberConverter {

    private GroupMemberConverter() {
    }

    /**
     * @param member 群成员(不能为null)
     * @param group  所在群,用于在没设群备注时回退成群名(可为null)
     */
    public static GroupMemberVO toVo(GroupMember member, Group group) {
        GroupMemberVO vo = BeanUtil.copyProperties(member, GroupMemberVO.class);
        vo.setShowNickName(member.getShowNickName());
        vo.setShowGroupName(StrUtil.blankToDefault(member.getRemarkGroupName(),
                group == null ? null : group.getName()));
        return vo;
    }
}
