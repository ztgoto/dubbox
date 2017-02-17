package com.alibaba.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ConsumerConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.alibaba.dubbo.config.RegistryConfig;

@ConfigurationProperties(prefix="dubbo")
public class DubboProperties {

    private String scan;

    private ApplicationConfig application = new ApplicationConfig();
    
    private ModuleConfig module = new ModuleConfig();

    private RegistryConfig registry = new RegistryConfig();
    
    private MonitorConfig monitor = new MonitorConfig();
    
    private ProviderConfig provider =new ProviderConfig();
    
    private ConsumerConfig consumer = new ConsumerConfig();

    private ProtocolConfig protocol = new ProtocolConfig();

    public String getScan() {
        return scan;
    }

    public void setScan(String scan) {
        this.scan = scan;
    }

	public ApplicationConfig getApplication() {
		return application;
	}

	public ModuleConfig getModule() {
		return module;
	}

	public RegistryConfig getRegistry() {
		return registry;
	}

	public MonitorConfig getMonitor() {
		return monitor;
	}

	public ProviderConfig getProvider() {
		return provider;
	}

	public ConsumerConfig getConsumer() {
		return consumer;
	}

	public ProtocolConfig getProtocol() {
		return protocol;
	}

    
}
