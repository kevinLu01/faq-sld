package com.sld.faq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sld.faq.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户的角色 code 列表
     *
     * @param userId 用户 ID
     * @return 角色 code 列表
     */
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(Long userId);
}
