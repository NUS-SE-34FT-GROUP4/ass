package sg.edu.nus.iss.c2csectrade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("sg.edu.nus.iss.c2csectrade.mapper") // Scan Mapper interfaces
public class C2cPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(C2cPlatformApplication.class, args);
    }
}