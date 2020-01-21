package com.sankuai.inf.leaf.server;

import com.sankuai.inf.leaf.server.druid.stat.DruidStatViewServletConfiguration;
import com.sankuai.inf.leaf.server.druid.stat.DruidWebStatFilterConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        DruidStatViewServletConfiguration.class,
        DruidWebStatFilterConfiguration.class
})
public class LeafServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeafServerApplication.class, args);
    }
}
