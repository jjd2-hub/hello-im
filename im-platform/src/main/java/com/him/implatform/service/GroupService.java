package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.dto.GroupDndDTO;
import com.him.implatform.dto.GroupInviteDTO;
import com.him.implatform.dto.GroupMemberRemoveDTO;
import com.him.implatform.entity.Group;
import com.him.implatform.vo.GroupMemberVO;
import com.him.implatform.vo.GroupVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface GroupService extends IService<Group> {
    /**
     * 创建群聊
     * @param vo 所需dto
     * @return vo
     */
    GroupVO createGroup(@Valid GroupVO vo);

    /**
     * 查询群聊
     * @param id 群聊id
     * @return 群聊
     */
    GroupVO findById(Long id);

    /**
     * 修改群聊
     * @param vo 传参
     * @return 修改后
     */
    GroupVO modifyGroup(@Valid GroupVO vo);

    /**
     * 根据id查找群聊
     * @param id 群聊id
     * @return 群聊
     */
    Group getAndCheckById(Long id);

    /**
     * 根据id解散群聊
     * @param groupId id
     */
    void deleteGroup(@NotNull(message = "群聊id不能为空") Long groupId);

    /**
     * 查询群聊列表
     * @param version 版本号
     * @return 群聊列表
     */
    List<GroupVO> findGroups(Long version);

    /**
     * 邀请用户进群
     * @param dto 进群dto
     */
    void invite(@Valid GroupInviteDTO dto);

    /**
     * 查询群聊成员
     * @param groupId 群聊id
     * @param version 版本号
     * @return 群聊成员
     */
    List<GroupMemberVO> findGroupMembers(@NotNull(message = "群聊id不能为空") Long groupId, Long version);

    /**
     * 移除成员
     * @param dto dto
     */
    void removeGroupMembers(@Valid GroupMemberRemoveDTO dto);

    /**
     * 退出群聊
     * @param groupId 群
     */
    void quitGroup(@NotNull(message = "群聊id不能为空") Long groupId);

    /**
     * 设置免打扰
     * @param dto dto
     */
    void setDnd(@Valid GroupDndDTO dto);
}
