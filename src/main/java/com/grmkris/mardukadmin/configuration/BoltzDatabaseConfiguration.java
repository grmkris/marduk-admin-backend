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
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@PropertySource({ "classpath:application.properties" })
@EnableJpaRepositories(
        basePackages = "com.grmkris.mardukadmin.db.boltz",
        entityManagerFactoryRef = "boltzEntityManager",
        transactionManagerRef = "boltzTransactionManager"
)
public class BoltzDatabaseConfiguration {

    @Autowired
    private Environment env;

    @Primary
    @Bean
    @ConfigurationProperties(prefix="spring.second-datasource")
    public DataSource boltzDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean boltzEntityManager() {
        LocalContainerEntityManagerFactoryBean em
                = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(boltzDataSource());
        em.setPackagesToScan(
                "com.grmkris.mardukadmin.db.boltz");

        HibernateJpaVendorAdapter vendorAdapter
                = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto",
                "none");
        properties.put("hibernate.dialect",
             "org.hibernate.dialect.SQLiteDialect");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Primary
    @Bean
    public PlatformTransactionManager boltzTransactionManager() {

        JpaTransactionManager transactionManager
                = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(
                boltzEntityManager().getObject());
        return transactionManager;
    }
}
