package com.him.implatform.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    /**
     * 成功
     */
    SUCCESS(200, "成功"),
    /**
     * 未登录
     */
    NO_LOGIN(400, "未登录"),
    /**
     * token无效或已过期
     */
    INVALID_TOKEN(401, "token无效或已过期"),
    /**
     * 系统繁忙，请稍后再试
     */
    PROGRAM_ERROR(500, "系统繁忙，请稍后再试"),
    /**
     * 密码不正确
     */
    PASSWORD_ERROR(10001, "密码不正确"),
    /**
     * 该用户名已注册
     */
    USERNAME_ALREADY_REGISTER(10003, "该用户名已注册"),
    /**
     * 请不要输入非法内容
     */
    XSS_PARAM_ERROR(10004, "请不要输入非法内容"),
    /**
     * 用户不存在
     */
    USER_NOT_EXISTS(10005,"该用户不存在"),
    /**
     * 账号被封禁
     */
    USER_BANNED(10006,"该用户已被封禁"),
    /**
     * 登录过期
     */
    LOGIN_EXPIRED(10007,"该用户登录过期"),
    /**
     * 不可操作别人账号
     */
    CAN_OPERATE_OTHER_USER(10008,"不能操作别人账号"),
    /**
     * 上传文件不符合约定
     */
    FILE_NOT_RIGHT(10009,"上传文件不符合规定");

    private final int code;
    private final String msg;
}

