package com.fashionshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        System.out.println("--- DB CONNECTION OK ---");
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("DESCRIBE product_colors");
            System.out.println("Columns of product_colors:");
            for (Map<String, Object> col : columns) {
                System.out.println(col);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
