/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySources;
import org.springframework.util.Assert;

/**
 * Utility to deduce the {@link PropertySources} to use for configuration binding.
 * <p>
 * 一个用于推断 PropertySources 的工具类
 *
 * @author Phillip Webb
 */
class PropertySourcesDeducer {

	private static final Log logger = LogFactory.getLog(PropertySourcesDeducer.class);

	private final ApplicationContext applicationContext;

	/**
	 * 唯一构造器。仅被 ConfigurationPropertiesBinder 使用。
	 *
	 * @param applicationContext 应用上下文
	 */
	PropertySourcesDeducer(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * 获取 PropertySources
	 */
	PropertySources getPropertySources() {
		// 找到配置器，通过它获取 PropertySources
		PropertySourcesPlaceholderConfigurer configurer = getSinglePropertySourcesPlaceholderConfigurer();
		if (configurer != null) {
			return configurer.getAppliedPropertySources();
		}

		// 或者从 Environment 中获取 MutablePropertySources
		MutablePropertySources sources = extractEnvironmentPropertySources();
		Assert.state(sources != null,
				"Unable to obtain PropertySources from PropertySourcesPlaceholderConfigurer or Environment");
		return sources;
	}

	/**
	 * 获取唯一一个 PropertySourcesPlaceholderConfigurer
	 */
	private PropertySourcesPlaceholderConfigurer getSinglePropertySourcesPlaceholderConfigurer() {
		// Take care not to cause early instantiation of all FactoryBeans
		// 按照类型寻找 PropertySourcesPlaceholderConfigurer
		Map<String, PropertySourcesPlaceholderConfigurer> beans = this.applicationContext
				.getBeansOfType(PropertySourcesPlaceholderConfigurer.class, false, false);

		// 希望只能找到 1 个
		if (beans.size() == 1) {
			return beans.values().iterator().next();
		}

		// 如果找到多个，那么发出警告
		if (beans.size() > 1 && logger.isWarnEnabled()) {
			logger.warn("Multiple PropertySourcesPlaceholderConfigurer beans registered " + beans.keySet()
					+ ", falling back to Environment");
		}

		// 如果找到多个，或者没有找到，那么返回 null
		return null;
	}

	private MutablePropertySources extractEnvironmentPropertySources() {
		Environment environment = this.applicationContext.getEnvironment();
		if (environment instanceof ConfigurableEnvironment) {
			return ((ConfigurableEnvironment) environment).getPropertySources();
		}
		return null;
	}

}
