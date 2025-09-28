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

package org.springframework.boot.context.properties.source;

import org.springframework.core.env.AbstractPropertyResolver;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/**
 * Alternative {@link PropertySourcesPropertyResolver} implementation that recognizes
 * {@link ConfigurationPropertySourcesPropertySource} and saves duplicate calls to the
 * underlying sources if the name is a value {@link ConfigurationPropertyName}.
 *
 * @author Phillip Webb
 */
class ConfigurationPropertySourcesPropertyResolver extends AbstractPropertyResolver {

	private final MutablePropertySources propertySources;

	private final DefaultResolver defaultResolver;

	ConfigurationPropertySourcesPropertyResolver(MutablePropertySources propertySources) {
		this.propertySources = propertySources;
		this.defaultResolver = new DefaultResolver(propertySources);
	}

	@Override
	public boolean containsProperty(String key) {
		ConfigurationPropertySourcesPropertySource attached = getAttached();
		if (attached != null) {
			ConfigurationPropertyName name = ConfigurationPropertyName.of(key, true);
			if (name != null) {
				try {
					return attached.findConfigurationProperty(name) != null;
				}
				catch (Exception ex) {
				}
			}
		}
		return this.defaultResolver.containsProperty(key);
	}

	@Override
	public String getProperty(String key) {
		// 期望返回值类型 String
		return getProperty(key, String.class, true);
	}

	@Override
	public <T> T getProperty(String key, Class<T> targetValueType) {
		return getProperty(key, targetValueType, true);
	}

	@Override
	protected String getPropertyAsRawString(String key) {
		return getProperty(key, String.class, false);
	}

	private <T> T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
		// 寻找属性得到 value
		Object value = findPropertyValue(key);
		if (value == null) {
			return null;
		}

		// 解析内嵌的占位符
		if (resolveNestedPlaceholders && value instanceof String) {
			value = resolveNestedPlaceholders((String) value);
		}

		// 转换类型
		return convertValueIfNecessary(value, targetValueType);
	}

	private Object findPropertyValue(String key) {
		ConfigurationPropertySourcesPropertySource attached = getAttached();
		if (attached != null) {
			ConfigurationPropertyName name = ConfigurationPropertyName.of(key, true);
			if (name != null) {
				try {
					ConfigurationProperty configurationProperty = attached.findConfigurationProperty(name);
					return (configurationProperty != null) ? configurationProperty.getValue() : null;
				}
				catch (Exception ex) {
				}
			}
		}
		return this.defaultResolver.getProperty(key, Object.class, false);
	}

	private ConfigurationPropertySourcesPropertySource getAttached() {
		// 从所有的属性源中找到一个名字叫 configurationProperties
		// 🧸: 类型一定是 ConfigurationPropertySourcesPropertySource 直接 "自信强转"
		// 🧸: ConfigurationPropertySourcesPropertySource 有点类似一个聚合体
		ConfigurationPropertySourcesPropertySource attached = (ConfigurationPropertySourcesPropertySource) ConfigurationPropertySources
			.getAttached(this.propertySources);

		// 获取 PropertySource 底层的泛型 T
		// 🧸: 对于 ConfigurationPropertySourcesPropertySource 来说，包装起来的是一堆 PropertySource
		Iterable<ConfigurationPropertySource> attachedSource = (attached != null) ? attached.getSource() : null;

		// 检查一下是否内部聚合的东西就是它
		if ((attachedSource instanceof SpringConfigurationPropertySources)
				&& ((SpringConfigurationPropertySources) attachedSource).isUsingSources(this.propertySources)) {
			return attached;
		}
		return null;
	}

	/**
	 * Default {@link PropertySourcesPropertyResolver} used if
	 * {@link ConfigurationPropertySources} is not attached.
	 */
	static class DefaultResolver extends PropertySourcesPropertyResolver {

		DefaultResolver(PropertySources propertySources) {
			super(propertySources);
		}

		@Override
		public <T> T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
			return super.getProperty(key, targetValueType, resolveNestedPlaceholders);
		}

	}

}
