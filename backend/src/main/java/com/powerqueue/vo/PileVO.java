package com.powerqueue.vo;

import com.powerqueue.common.enums.PileStatus;
import com.powerqueue.entity.ChargingPile;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电桩视图(用于实时状态展示,可被缓存)。
 */
@Data
public class PileVO {

    private Long id;
    private Long stationId;
    private String pileNo;
    private String type;
    private String typeDesc;
    private BigDecimal power;
    private BigDecimal price;
    private String status;
    private String statusDesc;
    private Integer version;

    public static PileVO from(ChargingPile p) {
        PileVO v = new PileVO();
        v.setId(p.getId());
        v.setStationId(p.getStationId());
        v.setPileNo(p.getPileNo());
        v.setType(p.getType());
        v.setTypeDesc("FAST".equals(p.getType()) ? "快充" : "慢充");
        v.setPower(p.getPower());
        v.setPrice(p.getPrice());
        v.setStatus(p.getStatus());
        v.setStatusDesc(toStatusDesc(p.getStatus()));
        v.setVersion(p.getVersion());
        return v;
    }

    private static String toStatusDesc(String status) {
        try {
            return PileStatus.valueOf(status).getDesc();
        } catch (Exception e) {
            return status;
        }
    }
}
