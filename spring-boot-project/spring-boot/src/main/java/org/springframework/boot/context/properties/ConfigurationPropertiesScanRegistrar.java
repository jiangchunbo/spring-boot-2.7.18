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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ImportBeanDefinitionRegistrar} for registering
 * {@link ConfigurationProperties @ConfigurationProperties} bean definitions through
 * scanning.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
class ConfigurationPropertiesScanRegistrar implements ImportBeanDefinitionRegistrar {

	// 这是一种扫描的方案，扫描带有注解 @ConfigurationProperties 的类，并注册

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	ConfigurationPropertiesScanRegistrar(Environment environment, ResourceLoader resourceLoader) {
		this.environment = environment;
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		// 从注解中获取需要扫描的包
		Set<String> packagesToScan = getPackagesToScan(importingClassMetadata);

		// bean factory + packages 传参修改
		scan(registry, packagesToScan);
	}

	/**
	 * 从注解中解析出需要扫描的 package
	 *
	 * @param metadata 注解
	 * @return packages
	 */
	private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
		// 从一个 Map 结构解析出 AnnotationAttributes
		AnnotationAttributes attributes = AnnotationAttributes
				.fromMap(metadata.getAnnotationAttributes(ConfigurationPropertiesScan.class.getName()));

		// 硬编码找到 basePackage
		String[] basePackages = attributes.getStringArray("basePackages");
		Class<?>[] basePackageClasses = attributes.getClassArray("basePackageClasses");

		// String[] 类型的 basePackages 直接放到初始的 Set 中
		Set<String> packagesToScan = new LinkedHashSet<>(Arrays.asList(basePackages));
		// 遍历 basePackageClasses 这些类型，获取他们的包
		for (Class<?> basePackageClass : basePackageClasses) {
			packagesToScan.add(ClassUtils.getPackageName(basePackageClass));
		}

		// 如果都没有设置，就使用注解所在的 package name
		if (packagesToScan.isEmpty()) {
			packagesToScan.add(ClassUtils.getPackageName(metadata.getClassName()));
		}

		// 删除一些空文本
		packagesToScan.removeIf((candidate) -> !StringUtils.hasText(candidate));
		return packagesToScan;
	}

	private void scan(BeanDefinitionRegistry registry, Set<String> packages) {
		ConfigurationPropertiesBeanRegistrar registrar = new ConfigurationPropertiesBeanRegistrar(registry);

		// 获得核心扫描器
		ClassPathScanningCandidateComponentProvider scanner = getScanner(registry);

		// 很简单，遍历一些 package:String
		for (String basePackage : packages) {
			// 通过组件的支持，扫描得到一些 BeanDefinition
			for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
				register(registrar, candidate.getBeanClassName());
			}
		}
	}

	private ClassPathScanningCandidateComponentProvider getScanner(BeanDefinitionRegistry registry) {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.setEnvironment(this.environment);
		scanner.setResourceLoader(this.resourceLoader);

		// 比较关键，添加一个基于注解的过滤器，关注 @ConfigurationProperties
		scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));

		// 关键，添加一个排除过滤器
		TypeExcludeFilter typeExcludeFilter = new TypeExcludeFilter();
		typeExcludeFilter.setBeanFactory((BeanFactory) registry); // 为什么这里
		scanner.addExcludeFilter(typeExcludeFilter);
		return scanner;
	}

	private void register(ConfigurationPropertiesBeanRegistrar registrar, String className) throws LinkageError {
		try {
			register(registrar, ClassUtils.forName(className, null));
		} catch (ClassNotFoundException ex) {
			// Ignore
		}
	}

	private void register(ConfigurationPropertiesBeanRegistrar registrar, Class<?> type) {
		// 如果没有 @Component 注解才可以注解
		if (!isComponent(type)) {
			registrar.register(type);
		}
	}

	/**
	 * 简单判断是否有注解 @Component
	 *
	 * @param type 类型
	 * @return 是否是 Component
	 */
	private boolean isComponent(Class<?> type) {
		return MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY).isPresent(Component.class);
	}

}
