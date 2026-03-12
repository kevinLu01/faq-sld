package com.sld.faq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sld.faq.module.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联 Mapper
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 删除指定用户的所有角色关联
     *
     * @param userId 用户 ID
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteByUserId(Long userId);
}
