package com.powerqueue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PowerQueue 启动类
 * 新能源汽车充电桩高并发预约与调度系统
 *
 * <p>{@code @EnableScheduling} 开启定时任务:
 * L2 需求预测预计算、L3 充电计划自动预约。
 */
@SpringBootApplication
@MapperScan("com.powerqueue.mapper")
@EnableScheduling
public class PowerQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerQueueApplication.class, args);
        System.out.println("""

                ====================================================
                  PowerQueue 启动成功!
                  接口地址: http://localhost:8080
                  演示账号密码请查看 README / 环境变量 POWERQUEUE_DEMO_PASSWORD
                ====================================================
                """);
    }
}
