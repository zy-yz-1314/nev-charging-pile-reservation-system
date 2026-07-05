package com.powerqueue.service;

import com.powerqueue.dto.RecommendDTO;
import com.powerqueue.vo.PileScoreVO;

import java.util.List;

/**
 * 智能匹配推荐引擎(L1)。
 * 用户仅需「我要充电」,系统按四因子加权输出综合得分 Top-N 充电桩:
 * <pre>score = w1×(1/距离) + w2×(1/等待时间) + w3×(1/价格) + w4×功率匹配度</pre>
 */
public interface RecommendService {

    /** 智能匹配 Top-N */
    List<PileScoreVO> recommend(RecommendDTO dto);
}
