package com.him.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户表
 */
@Data
@TableName("im_user")
public class User {
    /**
     * 主键自增，非空唯一
     */
    @TableId
    private Long id;
    /**
     * 用户名（账号），唯一，非空
     */
    private String username;
    /**
     * 昵称，索引，非空
     */
    private String nickname;
    /**
     * 头像
     */
    private String headImage;
    /**
     * 头像缩略图
     */
    private String headImageThumb;
    /**
     * 密码，密文存储，非空
     */
    private String password;
    /**
     * 性别，0男1女
     */
    private Integer sex;
    /**
     * 是否封禁
     */
    private Boolean isBanned;
    /**
     * 封禁原因
     */
    private String reason;
    /**
     * 类型，1普通用户2审核账户
     */
    private Integer type;
    /**
     * 个性签名
     */
    private String signature;
    /**
     * 最后登录时间
     */
    private Date lastLoginTime;
    /**
     * 创建时间
     */
    private Date createTime;
}
