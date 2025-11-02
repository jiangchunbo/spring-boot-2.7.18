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

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} that checks if properties are defined in environment.
 *
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @see ConditionalOnProperty
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class OnPropertyCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		List<AnnotationAttributes> allAnnotationAttributes = metadata.getAnnotations()
				// 过滤 ConditionalOnProperty 注解
				.stream(ConditionalOnProperty.class.getName())
				.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
				.map(MergedAnnotation::asAnnotationAttributes)
				.collect(Collectors.toList());
		List<ConditionMessage> noMatch = new ArrayList<>();
		List<ConditionMessage> match = new ArrayList<>();

		// 遍历每个属性条件注解
		for (AnnotationAttributes annotationAttributes : allAnnotationAttributes) {
			ConditionOutcome outcome = determineOutcome(annotationAttributes, context.getEnvironment());
			(outcome.isMatch() ? match : noMatch).add(outcome.getConditionMessage());
		}
		if (!noMatch.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.of(noMatch));
		}
		return ConditionOutcome.match(ConditionMessage.of(match));
	}

	private ConditionOutcome determineOutcome(AnnotationAttributes annotationAttributes, PropertyResolver resolver) {
		// 把每个注解的属性 AnnotationAttributes 都传给 Spec
		Spec spec = new Spec(annotationAttributes);

		// 创建两个容器，传给 collectProperties 方法，用于收集结果
		List<String> missingProperties = new ArrayList<>();
		List<String> nonMatchingProperties = new ArrayList<>();

		// 进行收集
		spec.collectProperties(resolver, missingProperties, nonMatchingProperties);

		// 如果存在 missing，那么不匹配
		if (!missingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.didNotFind("property", "properties")
					.items(Style.QUOTE, missingProperties));
		}

		// 如果没有 missing，但是不匹配
		if (!nonMatchingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.found("different value in property", "different value in properties")
					.items(Style.QUOTE, nonMatchingProperties));
		}
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnProperty.class, spec).because("matched"));
	}

	private static class Spec {

		private final String prefix;

		private final String havingValue;

		private final String[] names;

		private final boolean matchIfMissing;

		Spec(AnnotationAttributes annotationAttributes) {
			// 获取前缀 prefix (也可能没有)
			String prefix = annotationAttributes.getString("prefix").trim();
			if (StringUtils.hasText(prefix) && !prefix.endsWith(".")) {
				prefix = prefix + ".";
			}

			// 前缀
			this.prefix = prefix;
			// 获取 havingValue
			this.havingValue = annotationAttributes.getString("havingValue");
			// 注解属性 value or name
			this.names = getNames(annotationAttributes);
			// 获取 matchIfMissing
			this.matchIfMissing = annotationAttributes.getBoolean("matchIfMissing");
		}

		/**
		 * 要么获取 value 的值，要么获取 name 的值
		 */
		private String[] getNames(Map<String, Object> annotationAttributes) {
			// 总之 value 和 name 有且仅有 1 个是有值的
			String[] value = (String[]) annotationAttributes.get("value");
			String[] name = (String[]) annotationAttributes.get("name");
			Assert.state(value.length > 0 || name.length > 0,
					"The name or value attribute of @ConditionalOnProperty must be specified");
			Assert.state(value.length == 0 || name.length == 0,
					"The name and value attributes of @ConditionalOnProperty are exclusive");
			return (value.length > 0) ? value : name;
		}

		private void collectProperties(PropertyResolver resolver, List<String> missing, List<String> nonMatching) {
			for (String name : this.names) {
				// 名字前面都要加上 prefix(也可能没有，所以 name 直接就是完全名)
				String key = this.prefix + name;

				// PropertyResolver 其实就是 Environment，检查是否包含这个属性
				if (resolver.containsProperty(key)) {
					if (!isMatch(resolver.getProperty(key), this.havingValue)) {
						nonMatching.add(name);
					}
				} else {
					if (!this.matchIfMissing) {
						missing.add(name);
					}
				}
			}
		}

		/**
		 * 如何才算匹配上
		 *
		 * @param value         从 Environment 获取的属性值
		 * @param requiredValue 注解里面的 havingValue 值
		 * @return 是否匹配
		 */
		private boolean isMatch(String value, String requiredValue) {
			// 如果用户定义了 havingValue，检查 havingValue 是否等于 value
			// 纯纯是字符串比较
			if (StringUtils.hasLength(requiredValue)) {
				return requiredValue.equalsIgnoreCase(value);
			}

			// 如果没有定义 havingValue，那么只要 value 不是 false 就匹配
			return !"false".equalsIgnoreCase(value);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			result.append("(");
			result.append(this.prefix);
			if (this.names.length == 1) {
				result.append(this.names[0]);
			} else {
				result.append("[");
				result.append(StringUtils.arrayToCommaDelimitedString(this.names));
				result.append("]");
			}
			if (StringUtils.hasLength(this.havingValue)) {
				result.append("=").append(this.havingValue);
			}
			result.append(")");
			return result.toString();
		}

	}

}
