package com.powerqueue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerqueue.entity.Reservation;
import com.powerqueue.vo.ReservationVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 预约 / 订单 Mapper。
 * 含看板统计的聚合查询与订单分页 join 查询(自定义 SQL,见 ReservationMapper.xml)。
 */
public interface ReservationMapper extends BaseMapper<Reservation> {

    /** 各订单状态数量统计:返回 [{status, cnt}, ...] */
    List<Map<String, Object>> countByStatus();

    /** 近 N 天每日营收与订单量趋势:返回 [{date, orders, revenue}, ...] */
    List<Map<String, Object>> revenueTrend(@Param("days") int days);

    /** 汇总指标:总订单数、今日订单数、总营收、今日营收 */
    Map<String, Object> summary();

    /** 管理后台:订单分页(join 用户/桩/站点),支持状态与关键字筛选 */
    IPage<ReservationVO> pageReservationVO(Page<ReservationVO> page,
                                           @Param("status") String status,
                                           @Param("keyword") String keyword);

    /** 某充电桩近 N 天已完成订单的平均充电时长(分钟),用于排队等待预估(L1)。 */
    Integer avgDurationMinutes(@Param("pileId") Long pileId, @Param("days") int days);

    /**
     * 近 N 周已完成订单的「按星期 × 小时」聚合(L2 需求预测数据源)。
     * 返回 [{stationId, dow(1=周一), hr(0-23), cnt, distinctDays}, ...],
     * 用于移动平均 + 季节性分解。
     */
    List<Map<String, Object>> hourlyLoadSamples(@Param("weeks") int weeks);
}
