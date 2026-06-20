package com.powerqueue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PowerQueue 启动类
 * 新能源汽车充电桩高并发预约与调度系统
 */
@SpringBootApplication
@MapperScan("com.powerqueue.mapper")
public class PowerQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerQueueApplication.class, args);
        System.out.println("""

                ====================================================
                  PowerQueue 启动成功!
                  接口地址: http://localhost:8080
                  默认账号: admin / 123456   (管理员)
                           user  / 123456   (车主)
                ====================================================
                """);
    }
}
