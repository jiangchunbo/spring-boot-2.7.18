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

package org.springframework.boot.autoconfigure.security.servlet;

import java.util.EnumSet;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Security's Filter.
 * Configured separately from {@link SpringBootWebSecurityConfiguration} to ensure that
 * the filter's order is still configured when a user-provided
 * {@link WebSecurityConfiguration} exists.
 *
 * @author Rob Winch
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.3.0
 */
@AutoConfiguration(after = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnClass({AbstractSecurityWebApplicationInitializer.class, SessionCreationPolicy.class})
public class SecurityFilterAutoConfiguration {

	/**
	 * 这个常量字符串是从 Spring Security Web 这个包里面引用的，也就是说这是 Spring Security 规定的特殊的 beanName
	 */
	private static final String DEFAULT_FILTER_NAME = AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME;

	/**
	 * 向容器中注册一个 Filter
	 *
	 * @param securityProperties spring.security 的属性
	 * @return RegistrationBean 动态注册 Filter，而且这是一种特殊的委托类型
	 */
	@Bean
	@ConditionalOnBean(name = DEFAULT_FILTER_NAME)
	public DelegatingFilterProxyRegistrationBean securityFilterChainRegistration(
			SecurityProperties securityProperties) {

		// Spring Boot 提供了两种方式动态注册
		// 用的比较多的是 FilterRegistrationBean

		// 这里使用 DelegatingFilterProxyRegistrationBean 方式，构造器先传入了一个名字，就是 beanName
		DelegatingFilterProxyRegistrationBean registration = new DelegatingFilterProxyRegistrationBean(
				DEFAULT_FILTER_NAME);

		// 设置 order 默认是 -100
		// 这个值很有考虑，默认 Filter order 是 0
		// 这里设置为 0 - 100，将会比一般 filter 更早执行
		// 所以，即使你向 FilterChain 添加了 Filter，同时 Filter 也在 Tomcat filters 里，也不会混乱
		registration.setOrder(securityProperties.getFilter().getOrder());

		// 设置 dispatcherTypes
		registration.setDispatcherTypes(getDispatcherTypes(securityProperties));
		return registration;
	}

	/**
	 * 从 Security 属性中解析出 DispatcherType
	 */
	private EnumSet<DispatcherType> getDispatcherTypes(SecurityProperties securityProperties) {
		// 没有设置 dispatcher type，返回 null
		if (securityProperties.getFilter().getDispatcherTypes() == null) {
			return null;
		}

		// 遍历 DispatcherTypes，每一项也是一个枚举，但是不是 Servlet 枚举，而是 Spring 自己定义的枚举
		// 通过 name() + valueOf() 转换为 Servlet 规范枚举
		return securityProperties.getFilter()
				.getDispatcherTypes()
				.stream()
				.map((type) -> DispatcherType.valueOf(type.name()))
				// 创建一个 EnumSet 空集合，后面应该会向这个集合填充枚举
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(DispatcherType.class)));
	}

}
