package com.sld.faq.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sld.faq.common.BusinessException;
import com.sld.faq.module.user.entity.SysDepartment;
import com.sld.faq.module.user.entity.SysRole;
import com.sld.faq.module.user.entity.SysUser;
import com.sld.faq.module.user.entity.SysUserRole;
import com.sld.faq.module.user.mapper.SysDepartmentMapper;
import com.sld.faq.module.user.mapper.SysRoleMapper;
import com.sld.faq.module.user.mapper.SysUserMapper;
import com.sld.faq.module.user.mapper.SysUserRoleMapper;
import com.sld.faq.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDepartmentMapper sysDepartmentMapper;

    /**
     * 根据 wecomUserId 查找用户，不存在则自动创建（默认角色 SUBMITTER）
     *
     * @param wecomUserId 企业微信用户 ID
     * @param name        用户姓名
     * @param avatar      头像 URL
     * @param mobile      手机号
     * @return 用户实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser findOrCreate(String wecomUserId, String name, String avatar, String mobile) {
        SysUser existing = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getWecomUserId, wecomUserId)
        );
        if (existing != null) {
            return existing;
        }

        // 新建用户
        SysUser user = new SysUser();
        user.setWecomUserId(wecomUserId);
        user.setName(name);
        user.setAvatar(avatar);
        user.setMobile(mobile);
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 查询 SUBMITTER 角色并绑定
        SysRole submitterRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "SUBMITTER")
        );
        if (submitterRole == null) {
            log.error("系统角色 SUBMITTER 不存在，请检查初始化数据");
            throw new BusinessException("系统角色数据异常，请联系管理员");
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(submitterRole.getId());
        sysUserRoleMapper.insert(userRole);

        log.info("新用户创建成功: wecomUserId={}, userId={}", wecomUserId, user.getId());
        return user;
    }

    /**
     * 根据用户 ID 查询用户详情（含角色和部门）
     *
     * @param userId 用户 ID
     * @return 用户视图对象
     */
    public UserVO getUserVO(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(40001, "用户不存在");
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setRoles(sysUserMapper.selectRoleCodesByUserId(userId));

        if (user.getDepartmentId() != null) {
            SysDepartment dept = sysDepartmentMapper.selectById(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartment(dept.getName());
            }
        }
        return vo;
    }

    /**
     * 根据用户 ID 查询角色 code 列表
     *
     * @param userId 用户 ID
     * @return 角色 code 列表
     */
    public List<String> getRoleCodes(Long userId) {
        return sysUserMapper.selectRoleCodesByUserId(userId);
    }

    /**
     * 确保用户拥有指定角色，若尚未拥有则插入关联
     *
     * @param userId   用户 ID
     * @param roleCode 角色代码
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureRole(Long userId, String roleCode) {
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode)
        );
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleCode);
        }

        List<String> existingCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        if (!existingCodes.contains(roleCode)) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            sysUserRoleMapper.insert(userRole);
        }
    }
}
