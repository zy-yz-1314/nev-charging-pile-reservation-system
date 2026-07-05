package com.powerqueue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 进入等待队列请求(L1)。
 */
@Data
public class EnqueueDTO {

    @NotNull(message = "充电桩ID不能为空")
    private Long pileId;
}
