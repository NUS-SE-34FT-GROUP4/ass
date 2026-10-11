package sg.edu.nus.iss.c2csectrade.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"sg.edu.nus.iss.c2csectrade.chat", "sg.edu.nus.iss.c2csectrade.event", "sg.edu.nus.iss.c2csectrade.security"})
@MapperScan("sg.edu.nus.iss.c2csectrade.chat.mapper")
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
