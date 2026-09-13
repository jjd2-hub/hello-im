package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.util.BeanUtil;
import com.him.imcommon.util.JwtUtil;
import com.him.implatform.config.props.JwtProperties;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.LoginDTO;
import com.him.implatform.dto.ModifyPwdDTO;
import com.him.implatform.dto.RegisterDTO;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.UserMapper;
import com.him.implatform.service.FriendService;
import com.him.implatform.service.UserService;
import com.him.implatform.session.UserSession;
import com.him.implatform.vo.LoginVO;
import com.him.implatform.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;


    @Override
    public void register(RegisterDTO dto) {
        // 昵称默认用户名
        if (StrUtil.isEmpty(dto.getNickname())) {
            dto.setNickname(dto.getUsername());
        }
        User user = this.findUserByUsername(dto.getUsername());
        // TODO 用户名/昵称敏感字符检验
        if (!Objects.isNull(user)) {
            throw new GlobalException(ResultCode.USERNAME_ALREADY_REGISTER);
        }
        user = BeanUtil.copyProperties(dto, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        this.save(user);
        log.info("注册用户,用户id:{},用户名:{},昵称:{}", user.getId(), dto.getUsername(), dto.getNickname());
    }

    @Override
    public User findUserByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(User::getUsername, username);
        return this.getOne(queryWrapper);
    }

    @Override
    public List<UserVO> findUserByName(String name) {
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.like(User::getUsername, name).or().like(User::getNickname, name);
        List<User> users = this.list(queryWrapper);
        List<Long> userIds = users.stream().map(User::getId).toList();
        // TODO 此处用户在线状态需im-client，暂时不设置
        return users.stream().map(user ->
                BeanUtil.copyProperties(user, UserVO.class)).toList();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = this.findUserByUsername(dto.getUsername());
        if (Objects.isNull(user)) {
            throw new GlobalException(ResultCode.USER_NOT_EXISTS);
        }
        if(!passwordEncoder.matches(dto.getPassword(),user.getPassword())) {
            throw new GlobalException(ResultCode.PASSWORD_ERROR);
        }
        if(user.getIsBanned()){
            String tip=String.format("你的账号因为%s已被封禁，请联系客服。",user.getReason());
            throw new GlobalException(ResultCode.USER_BANNED.getCode(),tip);
        }
        // 生成token
        UserSession session= BeanUtil.copyProperties(user,UserSession.class);
        session.setUserId(user.getId());
        String strJson = JSON.toJSONString(session);
        String accessToken= JwtUtil.sign(user.getId(),strJson,
                jwtProperties.getAccessTokenExpireIn(),jwtProperties.getAccessTokenSecret());
        String refreshToken=JwtUtil.sign(user.getId(),strJson,
                jwtProperties.getRefreshTokenExpireIn(),jwtProperties.getRefreshTokenSecret());
        return new LoginVO(accessToken,jwtProperties.getAccessTokenExpireIn(),
                refreshToken,jwtProperties.getRefreshTokenExpireIn());
    }

    @Override
    public LoginVO refreshToken(String token) {
        // TODO 要么为refreshToken维护一个redis黑名单，要么使用版本号方案
        // 验证refreshToken
        if(!JwtUtil.checkToken(token,jwtProperties.getRefreshTokenSecret())){
            throw new GlobalException(ResultCode.LOGIN_EXPIRED);
        }
        String strJson=JwtUtil.getInfoByToken(token);
        Long userId=JwtUtil.getUserIdByToken(strJson);
        User user=this.getById(userId);
        if(Objects.isNull(user)){
            throw new GlobalException(ResultCode.USER_NOT_EXISTS);
        }
        if(user.getIsBanned()){
            String tip=String.format("你的账号因为%s已被封禁，请联系客服。",user.getReason());
            throw new GlobalException(ResultCode.USER_BANNED.getCode(),tip);
        }
        String accessToken=JwtUtil.sign(userId,strJson,
                jwtProperties.getAccessTokenExpireIn(),jwtProperties.getAccessTokenSecret());
        String refreshToken=JwtUtil.sign(userId,strJson,
                jwtProperties.getRefreshTokenExpireIn(),jwtProperties.getRefreshTokenSecret());
        return new LoginVO(accessToken,jwtProperties.getAccessTokenExpireIn(),
                refreshToken,jwtProperties.getRefreshTokenExpireIn());
    }

    @Override
    public void modifyPwd(ModifyPwdDTO dto) {
        User user=this.getById(UserContext.getUserId());
        if(!passwordEncoder.matches(dto.getOldPwd(),user.getPassword())){
            throw new GlobalException(ResultCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPwd()));
        this.updateById(user);
        log.info("用户修改密码,用户:id{},用户名:{},昵称:{}",
                user.getId(), user.getUsername(),user.getNickname());
    }

    @Override
    public UserVO findUserById(Long id) {
        User user=this.getById(id);
        if(Objects.isNull(user)){
            throw new GlobalException(ResultCode.USER_NOT_EXISTS);
        }
        // TODO 此处用户在线状态未知，需im-client
        return BeanUtil.copyProperties(user,UserVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UserVO vo) {
        Long userId = UserContext.getUserId();
        // TODO 用户昵称敏感字符检查
        if (userId != null && userId.equals(vo.getId())) {
            throw new GlobalException(ResultCode.CAN_OPERATE_OTHER_USER);
        }
        User user=this.getById(vo.getId());
        if(Objects.isNull(user)){
            throw new GlobalException(ResultCode.USER_NOT_EXISTS);
        }
        // TODO 更新好友或者群聊中的昵称和头像
        user.setNickname(vo.getNickname());
        user.setSex(vo.getSex());
        user.setSignature(vo.getSignature());
        user.setHeadImage(vo.getHeadImage());
        user.setHeadImageThumb(vo.getHeadImageThumb());
        this.updateById(user);
        log.info("用户信息更新,用户:{}",user);
    }
}
