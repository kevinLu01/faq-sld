package com.sld.faq.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应体
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> items;

    /**
     * 静态工厂方法
     */
    public static <T> PageResult<T> of(long total, List<T> items) {
        return new PageResult<>(total, items);
    }
}
