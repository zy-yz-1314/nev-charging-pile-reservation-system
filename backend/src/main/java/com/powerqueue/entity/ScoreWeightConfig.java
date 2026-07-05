package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能匹配打分权重配置(L1)。
 * 对应公式:score = w1×(1/距离) + w2×(1/等待) + w3×(1/价格) + w4×功率匹配度。
 * profile 区分 default / urgent(着急)/ economy(省钱),后台可调。
 */
@Data
@TableName("score_weight_config")
public class ScoreWeightConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** default / urgent / economy */
    private String profile;
    private BigDecimal wDistance;
    private BigDecimal wWait;
    private BigDecimal wPrice;
    private BigDecimal wPower;
    private Integer enabled;
    private LocalDateTime updateTime;
}
