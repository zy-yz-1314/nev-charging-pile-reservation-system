package com.powerqueue.service;

import com.powerqueue.vo.StationVO;

import java.util.List;

/**
 * 充电站服务。
 */
public interface StationService {

    /** 站点列表(支持名称/地址关键字搜索),含实时桩位统计 */
    List<StationVO> listStations(String keyword);

    /** 站点详情 */
    StationVO getDetail(Long id);
}
