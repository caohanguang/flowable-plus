package pers.gary.flowable.plus.config;

import lombok.AllArgsConstructor;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.boot.FlowableProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * flowable配置
 *
 * @author Chill
 */
@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(FlowableProperties.class)
@ConditionalOnProperty(name="spring.flowable.plus.enable",havingValue = "true")
@Import(FlowableEngineConfiguration.class)
public class FlowableAutoConfiguration implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {


	private final FlowableProperties flowableProperties;

	@Override
	public void configure(SpringProcessEngineConfiguration engineConfiguration) {
		engineConfiguration.setActivityFontName(flowableProperties.getActivityFontName());
		engineConfiguration.setLabelFontName(flowableProperties.getLabelFontName());
		engineConfiguration.setAnnotationFontName(flowableProperties.getAnnotationFontName());
	}

}
