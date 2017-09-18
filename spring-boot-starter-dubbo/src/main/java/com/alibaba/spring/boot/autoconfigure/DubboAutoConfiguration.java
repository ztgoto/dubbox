package com.alibaba.spring.boot.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ConsumerConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.alibaba.dubbo.config.RegistryConfig;

@Configuration
@ConditionalOnProperty(name = "dubbo.enabled")
@EnableConfigurationProperties({ DubboProperties.class })
public class DubboAutoConfiguration {

    private final DubboProperties properties;
	
	public DubboAutoConfiguration(DubboProperties properties) {
		this.properties = properties;
	}
    

    @Bean
    @ConditionalOnMissingBean
    public ApplicationConfig applicationConfig() {
        return properties.getApplication();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "dubbo.module.enabled")
    public ModuleConfig moduleConfig(){
    	return properties.getModule();
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistryConfig registryConfig() {
        return properties.getRegistry();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "dubbo.monitor.enabled")
    public MonitorConfig monitorConfig() {
    	return properties.getMonitor();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ProviderConfig providerConfig() {
    	return properties.getProvider();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ConsumerConfig consumerConfig() {
    	return properties.getConsumer();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolConfig protocolConfig() {
        return properties.getProtocol();
    }
    
    
	@Bean
	@SuppressWarnings("rawtypes")
    public ApplicationListener dubboHolderListener(){
    	DubboHolderListener listener = new DubboHolderListener();
    	return listener;
    }

}
