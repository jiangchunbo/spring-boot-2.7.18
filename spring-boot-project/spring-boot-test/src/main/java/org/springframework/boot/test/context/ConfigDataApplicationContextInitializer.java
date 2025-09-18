/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.test.context;

import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ContextConfiguration;

/**
 * {@link ApplicationContextInitializer} that can be used with the
 * {@link ContextConfiguration#initializers()} to trigger loading of {@link ConfigData}
 * such as {@literal application.properties}.
 *
 * @author Phillip Webb
 * @since 2.4.0
 * @see ConfigDataEnvironmentPostProcessor
 */
public class ConfigDataApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	// @@@@@@@@@@@@@@@@@
	// 某个时机增强 ConfigurableApplicationContext

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		// 获取 Environment
		ConfigurableEnvironment environment = applicationContext.getEnvironment();
		RandomValuePropertySource.addToEnvironment(environment);

		// 创建了一个临时 BootstrapContext
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();

		// 这个静态方法中会创建一个 ConfigDataEnvironmentPostProcessor
		// 这个对象用于给 environment 填充属性
		ConfigDataEnvironmentPostProcessor.applyTo(environment, applicationContext, bootstrapContext);

		// 关闭这个临时的 Context
		bootstrapContext.close(applicationContext);

		// 将 Default 属性源填充到 end
		DefaultPropertiesPropertySource.moveToEnd(environment);
	}

}
