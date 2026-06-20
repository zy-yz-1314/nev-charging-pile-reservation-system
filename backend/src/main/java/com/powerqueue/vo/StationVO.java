package com.powerqueue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电站视图,附带实时桩位统计。
 */
@Data
public class StationVO {

    private Long id;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String cover;
    private String description;
    private Integer status;

    /** 总桩数 */
    private Integer totalPiles;
    /** 空闲桩数 */
    private Integer idlePiles;
    /** 空闲快充桩数 */
    private Integer fastIdle;
}
