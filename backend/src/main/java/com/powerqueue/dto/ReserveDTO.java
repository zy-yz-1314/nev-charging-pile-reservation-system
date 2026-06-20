package com.powerqueue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 抢桩请求。
 */
@Data
public class ReserveDTO {

    @NotNull(message = "充电桩ID不能为空")
    private Long pileId;
}
