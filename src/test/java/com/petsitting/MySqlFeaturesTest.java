package com.petsitting;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={
    "spring.datasource.url=${MYSQL_TEST_URL}",
    "spring.datasource.username=${MYSQL_TEST_USERNAME:root}",
    "spring.datasource.password=${MYSQL_TEST_PASSWORD:}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.flyway.locations=classpath:db/migration/mysql,classpath:db/migration/common",
    "app.uploads-dir=target/mysql-test-uploads"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".+")
class MySqlFeaturesTest extends PlatformFeaturesTest {}
