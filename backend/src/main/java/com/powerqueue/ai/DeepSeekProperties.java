package com.powerqueue.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 大模型配置(L4)。
 * api-key 走环境变量 ${DEEPSEEK_API_KEY},留空时助手自动降级到智能匹配列表。
 */
@Data
@ConfigurationProperties(prefix = "powerqueue.deepseek")
public class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String model = "deepseek-chat";
    /** 阶段一(意图/槽位提取)超时 */
    private int stage1TimeoutMs = 3000;
    /** 阶段二(语言组织)超时 */
    private int stage2TimeoutMs = 3000;
    /** 每用户每分钟提问上限 */
    private int rateLimitPerMinute = 10;
}
