/*
 * Copyright 2012-2022 the original author or authors.
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

package org.springframework.boot.autoconfigure.web.reactive;

import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;
import org.springframework.boot.web.server.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Auto-configuration for {@link WebSessionIdResolver}.
 * <p>
 * 给 WebSessionIdResolver 开启自动配置，这个是做什么的呢？用于从请求解析 SessionId
 *
 * 这是用于 Reactive
 *
 * @author Phillip Webb
 * @author Brian Clozel
 * @author Weix Sun
 * @since 2.6.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ConditionalOnClass({ WebSessionManager.class, Mono.class })
@EnableConfigurationProperties({ WebFluxProperties.class, ServerProperties.class })
public class WebSessionIdResolverAutoConfiguration {

	private final ServerProperties serverProperties;

	private final WebFluxProperties webFluxProperties;

	public WebSessionIdResolverAutoConfiguration(ServerProperties serverProperties,
			WebFluxProperties webFluxProperties) {
		this.serverProperties = serverProperties;
		this.webFluxProperties = webFluxProperties;
		assertNoMutuallyExclusiveProperties(serverProperties, webFluxProperties);
	}

	@SuppressWarnings("deprecation")
	private void assertNoMutuallyExclusiveProperties(ServerProperties serverProperties,
			WebFluxProperties webFluxProperties) {
		MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleNonNullValuesIn((entries) -> {
			entries.put("spring.webflux.session.cookie.same-site",
					webFluxProperties.getSession().getCookie().getSameSite());
			entries.put("server.reactive.session.cookie.same-site",
					serverProperties.getReactive().getSession().getCookie().getSameSite());
		});
	}

	/**
	 * 默认的解析 SessionId 的工具
	 */
	@Bean
	@ConditionalOnMissingBean
	public WebSessionIdResolver webSessionIdResolver() {
		// 创建一个从 Cookie 中解析 SessionId 的计息期
		CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
		String cookieName = this.serverProperties.getReactive().getSession().getCookie().getName();
		if (StringUtils.hasText(cookieName)) {
			resolver.setCookieName(cookieName);
		}
		resolver.addCookieInitializer(this::initializeCookie);
		return resolver;
	}

	private void initializeCookie(ResponseCookieBuilder builder) {
		Cookie cookie = this.serverProperties.getReactive().getSession().getCookie();
		PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
		map.from(cookie::getDomain).to(builder::domain);
		map.from(cookie::getPath).to(builder::path);
		map.from(cookie::getHttpOnly).to(builder::httpOnly);
		map.from(cookie::getSecure).to(builder::secure);
		map.from(cookie::getMaxAge).to(builder::maxAge);
		map.from(getSameSite(cookie)).to(builder::sameSite);
	}

	@SuppressWarnings("deprecation")
	private String getSameSite(Cookie properties) {
		if (properties.getSameSite() != null) {
			return properties.getSameSite().attributeValue();
		}
		WebFluxProperties.Cookie deprecatedProperties = this.webFluxProperties.getSession().getCookie();
		if (deprecatedProperties.getSameSite() != null) {
			return deprecatedProperties.getSameSite().attribute();
		}
		return null;
	}

}
