package com.learn.mistakeservice.vo;

/**
 * Dashboard 错因分布中的一个分组。
 *
 * @param type  错因名称，例如“概念不清”
 * @param count 当前用户该错因下的错题数量
 */
public record MistakeTypeCountVO(
        String type,
        long count
) {
}
