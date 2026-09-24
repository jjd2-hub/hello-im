package com.him.implatform.service;

import com.him.implatform.dto.LoginDTO;
import com.him.implatform.dto.ModifyPwdDTO;
import com.him.implatform.dto.RegisterDTO;
import com.him.implatform.dto.UserUpdateDTO;
import com.him.implatform.entity.User;
import com.him.implatform.vo.LoginVO;
import com.him.implatform.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface UserService {
    /**
     * 用户注册
     * @param dto 用户注册DTO
     */
    void register(@Valid RegisterDTO dto);

    User findUserByUsername(@NotEmpty(message = "用户名不能为空") String username);

    /**
     * 根据用户名或昵称模糊查找用户
     * @param name 用户名或昵称
     * @return 用户集合
     */
    List<UserVO> findUserByName(String name);

    /**
     * 用户登录
     * @param dto 用户登录DTO
     * @return 双token+过期时间
     */
    LoginVO login(@Valid LoginDTO dto);

    /**
     * 刷新token
     * @param token refreshToken
     * @return 新accessToken
     */
    LoginVO refreshToken(String token);

    /**
     * 退出登录:使该用户已签发的所有token立即失效
     */
    void logout();

    /**
     * 修改密码
     * @param dto 新旧密码
     */
    void modifyPwd(@Valid ModifyPwdDTO dto);

    /**
     * 根据id查找用户
     * @param id 用户id
     * @return 用户
     */
    UserVO findUserById(@NotNull Long id);

    /**
     * 根据id取用户实体。给需要用户昵称/头像的其它服务用,
     * 避免它们绕到通用 CRUD 上去查表
     * @param id 用户id
     * @return 用户实体,不存在返回null
     */
    User getUserById(Long id);

    /**
     * 修改个人资料。只允许改自己的,且只能改 {@link UserUpdateDTO} 里声明的字段
     * @param dto 待修改内容
     */
    void update(@Valid UserUpdateDTO dto);
}
