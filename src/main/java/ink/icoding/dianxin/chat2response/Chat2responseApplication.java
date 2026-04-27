package ink.icoding.dianxin.chat2response;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("ink.icoding.dianxin.chat2response.mapper")
public class Chat2responseApplication {

    public static void main(String[] args) {
        SpringApplication.run(Chat2responseApplication.class, args);
    }

}
