package com.him.implatform.converter;

import com.him.imcommon.util.BeanUtil;
import com.him.implatform.entity.User;
import com.him.implatform.vo.UserVO;

import java.util.List;

/**
 * 用户 实体 → VO。
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVO toVo(User user) {
        if (user == null) {
            return null;
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    public static List<UserVO> toVoList(List<User> users) {
        return users.stream().map(UserConverter::toVo).toList();
    }
}
