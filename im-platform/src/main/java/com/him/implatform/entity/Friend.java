package com.him.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 好友表,相互,联合唯一索引(userId,friendId)
 */
@Data
@TableName("im_friend")
public class Friend {
    /**
     * 好友表id
     */
    @TableId
    private Long id;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 好友id
     */
    private Long friendId;
    /**
     * 好友昵称
     */
    private String friendNickname;
    /**
     * 好友头像
     */
    private String friendHeadImage;
    /**
     * 免打扰标记(do not disturb),0关闭1开启
     */
    private Boolean isDnd;
    /**
     * 删除标记,0正常1删除
     */
    private Boolean deleted;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 版本号
     */
    private Long version;
}
