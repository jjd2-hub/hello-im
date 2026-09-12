package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.dto.LoginDTO;
import com.him.implatform.dto.ModifyPwdDTO;
import com.him.implatform.dto.RegisterDTO;
import com.him.implatform.entity.User;
import com.him.implatform.vo.LoginVO;
import com.him.implatform.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface UserService extends IService<User> {
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
     * 根据vo更新用户
     * @param vo 用户部分数据
     */
    void update(@Valid UserVO vo);
}
