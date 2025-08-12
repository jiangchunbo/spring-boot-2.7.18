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

package org.springframework.boot.autoconfigure.session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.autoconfigure.web.reactive.WebSessionIdResolverAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.servlet.server.Session.Cookie;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Session.
 * <p>
 * 给 Spring Session 开启自动配置
 *
 * @author Andy Wilkinson
 * @author Tommy Ludwig
 * @author Eddú Meléndez
 * @author Stephane Nicoll
 * @author Vedran Pavic
 * @author Weix Sun
 * @since 1.4.0
 */
// 因为 Spring Session 支持很多存储方式，比如 JDBC Redis Hazelcast MongoD，所以要在这些自动配置之后再执行
@AutoConfiguration(
		after = { DataSourceAutoConfiguration.class, HazelcastAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, MongoDataAutoConfiguration.class,
				MongoReactiveDataAutoConfiguration.class, RedisAutoConfiguration.class,
				RedisReactiveAutoConfiguration.class, WebSessionIdResolverAutoConfiguration.class },
		before = { HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class })
@ConditionalOnClass(Session.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties({ ServerProperties.class, SessionProperties.class, WebFluxProperties.class })
public class SessionAutoConfiguration {

	/**
	 * 导入了 ServletSessionRepositoryValidator，一个验证器
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.SERVLET)
	@Import({ ServletSessionRepositoryValidator.class, SessionRepositoryFilterConfiguration.class })
	static class ServletSessionConfiguration {

		@Bean
		@Conditional(DefaultCookieSerializerCondition.class)
		DefaultCookieSerializer cookieSerializer(ServerProperties serverProperties,
				ObjectProvider<DefaultCookieSerializerCustomizer> cookieSerializerCustomizers) {
			Cookie cookie = serverProperties.getServlet().getSession().getCookie();
			DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(cookie::getName).to(cookieSerializer::setCookieName);
			map.from(cookie::getDomain).to(cookieSerializer::setDomainName);
			map.from(cookie::getPath).to(cookieSerializer::setCookiePath);
			map.from(cookie::getHttpOnly).to(cookieSerializer::setUseHttpOnlyCookie);
			map.from(cookie::getSecure).to(cookieSerializer::setUseSecureCookie);
			map.from(cookie::getMaxAge).asInt(Duration::getSeconds).to(cookieSerializer::setCookieMaxAge);
			map.from(cookie::getSameSite).as(SameSite::attributeValue).to(cookieSerializer::setSameSite);
			cookieSerializerCustomizers.orderedStream().forEach((customizer) -> customizer.customize(cookieSerializer));
			return cookieSerializer;
		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(RememberMeServices.class)
		static class RememberMeServicesConfiguration {

			@Bean
			DefaultCookieSerializerCustomizer rememberMeServicesCookieSerializerCustomizer() {
				return (cookieSerializer) -> cookieSerializer
						.setRememberMeRequestAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnMissingBean(SessionRepository.class)
		@Import({ ServletSessionRepositoryImplementationValidator.class,
				ServletSessionConfigurationImportSelector.class })
		static class ServletSessionRepositoryConfiguration {

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	@Import(ReactiveSessionRepositoryValidator.class)
	static class ReactiveSessionConfiguration {

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnMissingBean(ReactiveSessionRepository.class)
		@Import({ ReactiveSessionRepositoryImplementationValidator.class,
				ReactiveSessionConfigurationImportSelector.class })
		static class ReactiveSessionRepositoryConfiguration {

		}

	}

	/**
	 * Condition to trigger the creation of a {@link DefaultCookieSerializer}. This kicks
	 * in if either no {@link HttpSessionIdResolver} and {@link CookieSerializer} beans
	 * are registered, or if {@link CookieHttpSessionIdResolver} is registered but
	 * {@link CookieSerializer} is not.
	 */
	static class DefaultCookieSerializerCondition extends AnyNestedCondition {

		// 整个是一个 2 选 1 的条件
		// 这是为谁服务的呢？为 Cookie Session 服务

		DefaultCookieSerializerCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		// 条件 1 即不存在 HttpSessionIdResolver 也不存在 CookieSerializer
		// 如果存在 HttpSessionIdResolver 就不会装配，因为开发者可能定义了自己的解析器
		// 如果存在 CookieSerializer 也不会生效，为啥呢？
		@ConditionalOnMissingBean({ HttpSessionIdResolver.class, CookieSerializer.class })
		static class NoComponentsAvailable {

		}

		@ConditionalOnBean(CookieHttpSessionIdResolver.class)
		@ConditionalOnMissingBean(CookieSerializer.class)
		static class CookieHttpSessionIdResolverAvailable {

		}

	}

	/**
	 * {@link ImportSelector} base class to add {@link StoreType} configuration classes.
	 */
	abstract static class SessionConfigurationImportSelector implements ImportSelector {

		protected final String[] selectImports(WebApplicationType webApplicationType) {
			return Arrays.stream(StoreType.values())
					.map((type) -> SessionStoreMappings.getConfigurationClass(webApplicationType, type))
					.toArray(String[]::new);
		}

	}

	/**
	 * {@link ImportSelector} to add {@link StoreType} configuration classes for reactive
	 * web applications.
	 */
	static class ReactiveSessionConfigurationImportSelector extends SessionConfigurationImportSelector {

		@Override
		public String[] selectImports(AnnotationMetadata importingClassMetadata) {
			return super.selectImports(WebApplicationType.REACTIVE);
		}

	}

	/**
	 * {@link ImportSelector} to add {@link StoreType} configuration classes for Servlet
	 * web applications.
	 */
	static class ServletSessionConfigurationImportSelector extends SessionConfigurationImportSelector {

		@Override
		public String[] selectImports(AnnotationMetadata importingClassMetadata) {
			return super.selectImports(WebApplicationType.SERVLET);
		}

	}

	/**
	 * Base class for beans used to validate that only one supported implementation is
	 * available in the classpath when the store-type property is not set.
	 */
	abstract static class AbstractSessionRepositoryImplementationValidator {

		private final List<String> candidates;

		private final ClassLoader classLoader;

		private final SessionProperties sessionProperties;

		AbstractSessionRepositoryImplementationValidator(ApplicationContext applicationContext,
				SessionProperties sessionProperties, List<String> candidates) {
			this.classLoader = applicationContext.getClassLoader();
			this.sessionProperties = sessionProperties;
			this.candidates = candidates;
			checkAvailableImplementations();
		}

		private void checkAvailableImplementations() {
			List<Class<?>> availableCandidates = new ArrayList<>();
			for (String candidate : this.candidates) {
				addCandidateIfAvailable(availableCandidates, candidate);
			}
			StoreType storeType = this.sessionProperties.getStoreType();
			if (availableCandidates.size() > 1 && storeType == null) {
				throw new NonUniqueSessionRepositoryException(availableCandidates);
			}
		}

		private void addCandidateIfAvailable(List<Class<?>> candidates, String type) {
			try {
				candidates.add(Class.forName(type, false, this.classLoader));
			}
			catch (Throwable ex) {
				// Ignore
			}
		}

	}

	/**
	 * Bean used to validate that only one supported implementation is available in the
	 * classpath when the store-type property is not set.
	 */
	static class ServletSessionRepositoryImplementationValidator
			extends AbstractSessionRepositoryImplementationValidator {

		ServletSessionRepositoryImplementationValidator(ApplicationContext applicationContext,
				SessionProperties sessionProperties) {
			super(applicationContext, sessionProperties,
					Arrays.asList("org.springframework.session.hazelcast.HazelcastIndexedSessionRepository",
							"org.springframework.session.jdbc.JdbcIndexedSessionRepository",
							"org.springframework.session.data.mongo.MongoIndexedSessionRepository",
							"org.springframework.session.data.redis.RedisIndexedSessionRepository"));
		}

	}

	/**
	 * Bean used to validate that only one supported implementation is available in the
	 * classpath when the store-type property is not set.
	 */
	static class ReactiveSessionRepositoryImplementationValidator
			extends AbstractSessionRepositoryImplementationValidator {

		ReactiveSessionRepositoryImplementationValidator(ApplicationContext applicationContext,
				SessionProperties sessionProperties) {
			super(applicationContext, sessionProperties,
					Arrays.asList("org.springframework.session.data.redis.ReactiveRedisSessionRepository",
							"org.springframework.session.data.mongo.ReactiveMongoSessionRepository"));
		}

	}

	/**
	 * Base class for validating that a (reactive) session repository bean exists.
	 * <p>
	 * 一个接触类，用于验证 Session Repository bean 是否存在。实现了 InitializingBean，因此 populateBean 将会调用
	 */
	abstract static class AbstractSessionRepositoryValidator implements InitializingBean {

		private final SessionProperties sessionProperties;

		private final ObjectProvider<?> sessionRepositoryProvider;

		protected AbstractSessionRepositoryValidator(SessionProperties sessionProperties,
				ObjectProvider<?> sessionRepositoryProvider) {
			this.sessionProperties = sessionProperties;
			this.sessionRepositoryProvider = sessionRepositoryProvider;
		}

		@Override
		public void afterPropertiesSet() {
			StoreType storeType = this.sessionProperties.getStoreType();

			// storeType != null 意味着用户配置了
			// storeType != NONE 意味着用户配置的不是 NONE，也就是用户希望存储 session
			// 寻找 Session 工厂，但是没找到，就要赶紧抛出异常
			if (storeType != StoreType.NONE && this.sessionRepositoryProvider.getIfAvailable() == null
					&& storeType != null) {
				throw new SessionRepositoryUnavailableException(
						"No session repository could be auto-configured, check your "
								+ "configuration (session store type is '"
								+ storeType.name().toLowerCase(Locale.ENGLISH) + "')",
						storeType);
			}
		}

	}

	/**
	 * Bean used to validate that a {@link SessionRepository} exists and provide a
	 * meaningful message if that's not the case.
	 * <p>
	 * 检查到底存不存在 Session Repository，Servlet 和 Reactive 使用的 Session Repository 是不同的，所以检测类型也是不同
	 */
	static class ServletSessionRepositoryValidator extends AbstractSessionRepositoryValidator {

		ServletSessionRepositoryValidator(SessionProperties sessionProperties,
				ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
			super(sessionProperties, sessionRepositoryProvider);
		}

	}

	/**
	 * Bean used to validate that a {@link ReactiveSessionRepository} exists and provide a
	 * meaningful message if that's not the case.
	 * <p>
	 * 检测是否存在 Reactive 的 Session Repository
	 */
	static class ReactiveSessionRepositoryValidator extends AbstractSessionRepositoryValidator {

		ReactiveSessionRepositoryValidator(SessionProperties sessionProperties,
				ObjectProvider<ReactiveSessionRepository<?>> sessionRepositoryProvider) {
			super(sessionProperties, sessionRepositoryProvider);
		}

	}

}
