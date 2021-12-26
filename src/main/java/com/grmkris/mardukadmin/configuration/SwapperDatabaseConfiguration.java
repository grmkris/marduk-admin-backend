package com.grmkris.mardukadmin.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@PropertySource({ "classpath:application.properties" })
@EnableJpaRepositories(
        basePackages = "com.grmkris.mardukadmin.db.balancer",
        entityManagerFactoryRef = "swapperEntityManager",
        transactionManagerRef = "swapperTransactionManager"
)
public class SwapperDatabaseConfiguration {

    @Autowired
    private Environment env;

    @Primary
    @Bean
    @ConfigurationProperties(prefix="spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean swapperEntityManager() {
        LocalContainerEntityManagerFactoryBean em
                = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan(
                "com.grmkris.mardukadmin.db.balancer");

        em.setJpaVendorAdapter(jpaVendorAdapter());
        return em;
    }

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter hibernateJpaVendorAdapter = new HibernateJpaVendorAdapter();
        hibernateJpaVendorAdapter.setShowSql(true);
        hibernateJpaVendorAdapter.setGenerateDdl(true); //Auto creating scheme when true
        hibernateJpaVendorAdapter.setDatabase(Database.H2);//Database type
        return hibernateJpaVendorAdapter;
    }

    @Bean
    public PlatformTransactionManager swapperTransactionManager() {

        JpaTransactionManager transactionManager
                = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(
                swapperEntityManager().getObject());
        return transactionManager;
    }

}
