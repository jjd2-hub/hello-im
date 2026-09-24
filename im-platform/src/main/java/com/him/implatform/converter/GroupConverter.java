package com.him.implatform.converter;

import cn.hutool.core.util.StrUtil;
import com.him.imcommon.util.BeanUtil;
import com.him.implatform.entity.Group;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.vo.GroupVO;

/**
 * 群聊 实体 → VO。
 *
 * <p>群的信息是"群本身 + 我在群里的成员记录"两部分拼出来的(群备注、免打扰都挂在成员记录上),
 * 所以转换需要两个入参。这个拼装规则集中在这里,避免各处遗漏某个字段。
 */
public final class GroupConverter {

    private GroupConverter() {
    }

    /**
     * @param group  群(不能为null)
     * @param member 当前用户在该群的成员记录(不能为null)
     */
    public static GroupVO toVo(Group group, GroupMember member) {
        GroupVO vo = BeanUtil.copyProperties(group, GroupVO.class);
        vo.setRemarkGroupName(member.getRemarkGroupName());
        vo.setRemarkNickName(member.getRemarkNickName());
        vo.setShowNickName(member.getShowNickName());
        vo.setShowGroupName(StrUtil.blankToDefault(member.getRemarkGroupName(), group.getName()));
        vo.setQuit(member.getQuit());
        vo.setIsDnd(member.getIsDnd());
        return vo;
    }
}
