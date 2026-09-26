package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 基础信息批量导入结果。导入按主键幂等 upsert，故区分「新建」与「覆盖更新」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {

    private Integer created;
    private Integer updated;
    private Integer total;
    /** 被跳过的条目及原因，例如站点不存在、类型不合法 */
    private List<String> errors;
}
