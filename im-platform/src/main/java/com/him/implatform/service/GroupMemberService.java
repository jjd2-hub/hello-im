package com.him.implatform.service;

import com.him.implatform.entity.GroupMember;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface GroupMemberService {
    /**
     * 根据群聊id和用户id查询群聊成员
     *
     * @param groupId 群聊id
     * @param userId  用户id
     * @return 群聊成员信息
     */
    GroupMember findByGroupAndUserId(Long groupId, Long userId);

    /**
     * 下一个版本号
     *
     * @return 版本号
     */
    Long getNextVersion();

    /**
     * 根据id查找本群成员id
     *
     * @param groupId 群id
     * @return 成员id
     */
    List<Long> findUserIdsByGroupId(Long groupId);

    /**
     * 根据id删除这个群聊
     *
     * @param groupId id
     */
    void removeByGroupId(Long groupId);

    /**
     * 根据id查询群聊本人
     *
     * @param userId id
     * @return 群聊成员
     */
    List<GroupMember> findByUserId(Long userId);

    /**
     * 获取退群后的群成员（自己）
     *
     * @param userId      id
     * @param minQuitTime 最少退群时间
     * @return 群成员（自己）
     */
    List<GroupMember> findQuitMembers(Long userId, Date minQuitTime);

    /**
     * 根据群id找成员
     *
     * @param groupId 群id
     * @param version 版本号
     * @return 成员
     */
    List<GroupMember> findByGroupId(@NotNull(message = "群id不可为空") Long groupId, long version);

    /**
     * 存储或更新成员
     *
     * @param id           群id
     * @param groupMembers 群成员
     */
    boolean saveOrUpdateBatch(Long id, List<GroupMember> groupMembers);

    /**
     * 删除群聊成员
     *
     * @param groupId 群聊id
     * @param userIds 成员id
     */
    boolean removeByGroupAndUserIds(@NotNull(message = "群id不可为空") Long groupId, @Size(max = 50, message = "一次最多只能选择50位用户") @NotEmpty(message = "成员用户id不可为空") List<Long> userIds);

    /**
     * 退出群聊
     *
     * @param groupId 群聊id
     */
    void removeByGroupAndUserId(Long groupId);

    /**
     * 设置免打扰
     *
     * @param groupId 群id
     * @param userId  用户id
     */
    void setDnd(@NotNull(message = "群id不可为空") Long groupId, Long userId, boolean isDnd);

    /**
     * 新增群成员
     *
     * @param member 成员记录
     */
    void addMember(GroupMember member);

    /**
     * 更新群成员(昵称备注、头像快照等)
     *
     * @param member 成员记录,必须有id
     */
    void updateMember(GroupMember member);

    /**
     * 按用户id + 版本号增量查询群成员记录。
     * 原来这个查询由 GroupService 自己拼 wrapper 调通用 list 实现,
     * 导致"有效成员"的过滤语义泄漏到了别的服务里,所以收回来
     *
     * @param userId  用户id
     * @param version 客户端已有的版本号,只返回比它大的
     * @return 成员记录
     */
    List<GroupMember> findByUserIdAndVersion(Long userId, Long version);
}
