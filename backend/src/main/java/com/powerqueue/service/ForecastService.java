package com.powerqueue.service;

import com.powerqueue.vo.ForecastVO;

import java.util.List;

/**
 * 分时段站点需求预测服务(L2)。
 * 轻量化时间序列:移动平均(近 N 周同槽位均值)+ 季节性(按星期 × 小时分组)。
 */
public interface ForecastService {

    /** 重新计算并落库全量预测(定时任务调用) */
    void recompute();

    /** 某站点未来 hoursAhead 小时的预测(三色展示用) */
    List<ForecastVO> forecastForStation(Long stationId, int hoursAhead);
}
