package pers.gary.flowable.plus.config;

import com.alibaba.druid.pool.DruidDataSource;
import pers.gary.flowable.plus.common.core.ProcessAroundAspect;
import pers.gary.flowable.plus.common.core.RetryPolicy;
import pers.gary.flowable.plus.common.core.context.ProcessNodeContext;
import pers.gary.flowable.plus.common.core.context.UserContext;
import pers.gary.flowable.plus.common.core.filter.DynamicFlowFilter;
import pers.gary.flowable.plus.common.util.ApplicationContextUtil;
import pers.gary.flowable.plus.config.def.DefaultProcessNodeContext;
import pers.gary.flowable.plus.config.def.DefaultRetryPolicy;
import pers.gary.flowable.plus.config.def.DefaultUserContext;
import lombok.extern.slf4j.Slf4j;
import org.flowable.app.spring.SpringAppEngineConfiguration;
import org.flowable.common.engine.impl.persistence.StrongUuidGenerator;
import org.flowable.spring.boot.AbstractSpringEngineAutoConfiguration;
import org.flowable.spring.boot.FlowableProperties;
import org.flowable.spring.boot.app.FlowableAppProperties;
import org.flowable.spring.boot.condition.ConditionalOnAppEngine;
import org.flowable.spring.boot.idm.FlowableIdmProperties;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Configuration
@ConditionalOnAppEngine
@EnableConfigurationProperties({
        FlowableProperties.class,
        FlowableAppProperties.class,
        FlowableIdmProperties.class
})
public  class FlowableEngineConfiguration extends AbstractSpringEngineAutoConfiguration implements ApplicationContextAware {
    protected final FlowableAppProperties appProperties;
    protected final FlowableIdmProperties idmProperties;

    private ApplicationContext applicationContext;

    public FlowableEngineConfiguration(FlowableProperties flowableProperties, FlowableAppProperties appProperties,
                                       FlowableIdmProperties idmProperties) {
        super(flowableProperties);
        this.appProperties = appProperties;
        this.idmProperties = idmProperties;
    }


    @Bean
    public SpringAppEngineConfiguration springAppEngineConfiguration(PlatformTransactionManager platformTransactionManager) throws IOException {
        Properties properties = null;
        DruidDataSource druidDataSource = new DruidDataSource();

        try{
            properties = (Properties)applicationContext.getBean("dataSourceProperties");
            druidDataSource.configFromPropety(properties);
        }catch (Exception e){
            log.warn("no configuration 4 properties of druid dataSource");
        }
        if(properties == null || properties.getProperty("druid.username") == null){
            Environment environment = applicationContext.getEnvironment();
            String url = environment.getProperty("spring.datasource.url");
            String user = environment.getProperty("spring.datasource.username");
            String pwd = environment.getProperty("spring.datasource.password");
            String dbName = environment.getProperty("server.flowDB");
            if(dbName == null){
                throw new IllegalArgumentException("no db name 4 flowable plus");
            }
            //提取ip port
            Pattern pattern = Pattern.compile(Constant.IP_PORT_REGEX);
            Matcher matcher = pattern.matcher(Optional.ofNullable(url).orElse(""));
            if(!matcher.find()){
                throw new IllegalArgumentException("jdbc url error :" + url);
            }
            String ipPort = matcher.group();
            log.info("jdbc ip:port is {}",ipPort);
            assert url != null;
            url = url.replaceAll(Constant.IP_PORT_REGEX+"/.*[?]",ipPort +"/" + dbName + "?");
            druidDataSource.setUrl(url);
            druidDataSource.setUsername(user);
            druidDataSource.setPassword(pwd);
        }


        SpringAppEngineConfiguration conf = new SpringAppEngineConfiguration();

        List<Resource> resources = this.discoverDeploymentResources(
                appProperties.getResourceLocation(),
                appProperties.getResourceSuffixes(),
                appProperties.isDeployResources()
        );

        if (resources != null && !resources.isEmpty()) {
            conf.setDeploymentResources(resources.toArray(new Resource[0]));
        }

        configureSpringEngine(conf, platformTransactionManager);
        configureEngine(conf, druidDataSource);

        conf.setIdGenerator(new StrongUuidGenerator());

        return conf;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean
    public ApplicationContextUtil applicationContextUtil(){
        return new ApplicationContextUtil();
    }

    @Bean
    public ProcessAroundAspect processAroundAspect(){
        return new ProcessAroundAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserContext userContext(){
        return new DefaultUserContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryPolicy defaultRetryPolicy(){
        return new DefaultRetryPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessNodeContext defaultProcessNodeContext(){
        return new DefaultProcessNodeContext();
    }

    @Bean
    public FilterRegistrationBean<DynamicFlowFilter> dynamicFlowFilterRegister(){
        FilterRegistrationBean<DynamicFlowFilter> bean = new FilterRegistrationBean<>();
        bean.setOrder(Integer.MAX_VALUE);
        bean.setFilter(new DynamicFlowFilter());
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    @ConfigurationProperties(prefix = "flow.db")
    public Properties dataSourceProperties(){
        return new Properties();
    }
}
