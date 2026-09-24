package com.him.implatform.service;

import com.him.implatform.dto.GroupCreateDTO;
import com.him.implatform.dto.GroupDndDTO;
import com.him.implatform.dto.GroupInviteDTO;
import com.him.implatform.dto.GroupMemberRemoveDTO;
import com.him.implatform.dto.GroupModifyDTO;
import com.him.implatform.entity.Group;
import com.him.implatform.vo.GroupMemberVO;
import com.him.implatform.vo.GroupVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface GroupService {
    /**
     * 创建群聊
     * @param dto 群资料
     * @return 创建后的群聊
     */
    GroupVO createGroup(@Valid GroupCreateDTO dto);

    /**
     * 查询群聊
     * @param id 群聊id
     * @return 群聊
     */
    GroupVO findById(Long id);

    /**
     * 修改群聊。任何成员可改自己的群内备注,只有群主能改群资料
     * @param dto 待修改内容
     * @return 修改后的群聊
     */
    GroupVO modifyGroup(@Valid GroupModifyDTO dto);

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
