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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean.BindMethod;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Delegate used by {@link EnableConfigurationPropertiesRegistrar} and
 * {@link ConfigurationPropertiesScanRegistrar} to register a bean definition for a
 * {@link ConfigurationProperties @ConfigurationProperties} class.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
final class ConfigurationPropertiesBeanRegistrar {

	private final BeanDefinitionRegistry registry;

	private final BeanFactory beanFactory;

	ConfigurationPropertiesBeanRegistrar(BeanDefinitionRegistry registry) {
		this.registry = registry;
		this.beanFactory = (BeanFactory) this.registry;
	}

	void register(Class<?> type) {
		// 就是看 type 表示的那个属性类上面有没有 @ConfigurationProperties 注解，因为可能配置了一些前缀 prefix
		MergedAnnotation<ConfigurationProperties> annotation = MergedAnnotations
			.from(type, SearchStrategy.TYPE_HIERARCHY)
			.get(ConfigurationProperties.class);

		// 根据 type 以及注解注册
		register(type, annotation);
	}

	void register(Class<?> type, MergedAnnotation<ConfigurationProperties> annotation) {
		// 得到一个 name，所以，这些配置类也有自己独特的 beanName 生成方式
		String name = getName(type, annotation);

		// 如果没有这个 beanName，那么就注册
		if (!containsBeanDefinition(name)) {
			registerBeanDefinition(name, type, annotation);
		}
	}

	/**
	 * 这个方法是关于 ConfigurationPropertiesBean 如何命名的策略方法
	 */
	private String getName(Class<?> type, MergedAnnotation<ConfigurationProperties> annotation) {
		// 配置属性 bean 可能有一个前缀，也可能没有，要看注解是否配置了 prefix

		// 1. <prefix> + "-" + <typeName>
		// 2. <typeName>
		String prefix = annotation.isPresent() ? annotation.getString("prefix") : "";
		return (StringUtils.hasText(prefix) ? prefix + "-" + type.getName() : type.getName());
	}

	private boolean containsBeanDefinition(String name) {
		return containsBeanDefinition(this.beanFactory, name);
	}

	private boolean containsBeanDefinition(BeanFactory beanFactory, String name) {
		if (beanFactory instanceof ListableBeanFactory
				&& ((ListableBeanFactory) beanFactory).containsBeanDefinition(name)) {
			return true;
		}
		if (beanFactory instanceof HierarchicalBeanFactory) {
			return containsBeanDefinition(((HierarchicalBeanFactory) beanFactory).getParentBeanFactory(), name);
		}
		return false;
	}

	private void registerBeanDefinition(String beanName, Class<?> type,
			MergedAnnotation<ConfigurationProperties> annotation) {
		Assert.state(annotation.isPresent(), () -> "No " + ConfigurationProperties.class.getSimpleName()
				+ " annotation found on  '" + type.getName() + "'.");

		// 创建一个 BeanDefinition
		this.registry.registerBeanDefinition(beanName, createBeanDefinition(beanName, type));
	}

	private BeanDefinition createBeanDefinition(String beanName, Class<?> type) {
		// 这可不是什么 Java 反射 Method
		// 指的是，使用何种 way 进行绑定：Java Bean 还是使用构造器绑定
		BindMethod bindMethod = BindMethod.forType(type);

		// 使用 type 创建一个 RootBeanDefinition，很正常的操作
		RootBeanDefinition definition = new RootBeanDefinition(type);

		// 设置一个属性，不要忘了，BeanDefinition 实现了 AttributeAccessor，因此有属性存取能力
		definition.setAttribute(BindMethod.class.getName(), bindMethod);

		// 如果是构造器绑定，那么使用 InstanceSupplier
		if (bindMethod == BindMethod.VALUE_OBJECT) {
			definition.setInstanceSupplier(() -> createValueObject(beanName, type));
		}
		return definition;
	}

	private Object createValueObject(String beanName, Class<?> beanType) {
		ConfigurationPropertiesBean bean = ConfigurationPropertiesBean.forValueObject(beanType, beanName);

		// 从 bean factory 获得 ConfigurationPropertiesBinder 对象
		ConfigurationPropertiesBinder binder = ConfigurationPropertiesBinder.get(this.beanFactory);
		try {
			// 借助 ConfigurationPropertiesBinder 的能力，绑定或创建 bean
			return binder.bindOrCreate(bean);
		}
		catch (Exception ex) {
			throw new ConfigurationPropertiesBindException(bean, ex);
		}
	}

}
