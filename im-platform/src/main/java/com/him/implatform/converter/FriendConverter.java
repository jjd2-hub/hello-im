package com.him.implatform.converter;

import com.him.implatform.entity.Friend;
import com.him.implatform.vo.FriendVO;

import java.util.List;

/**
 * 好友 实体 → VO。
 *
 * <p>注意:{@code FriendVO.id} 是<b>对方的用户id</b>,不是 im_friend 表的主键。
 * 前端拿它当"对方id"用(删好友、免打扰、进聊天),这一点很容易写错,
 * 所以集中在这里并加注释,改的时候只有一处需要小心。
 */
public final class FriendConverter {

    private FriendConverter() {
    }

    public static FriendVO toVo(Friend friend) {
        if (friend == null) {
            return null;
        }
        FriendVO vo = new FriendVO();
        vo.setId(friend.getFriendId());
        vo.setNickname(friend.getFriendNickname());
        vo.setHeadImage(friend.getFriendHeadImage());
        vo.setIsDnd(friend.getIsDnd());
        vo.setDeleted(friend.getDeleted());
        vo.setVersion(friend.getVersion());
        return vo;
    }

    public static List<FriendVO> toVoList(List<Friend> friends) {
        return friends.stream().map(FriendConverter::toVo).toList();
    }
}
