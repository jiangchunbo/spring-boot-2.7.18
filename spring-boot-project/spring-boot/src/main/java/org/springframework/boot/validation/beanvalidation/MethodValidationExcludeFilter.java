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

package org.springframework.boot.validation.beanvalidation;

import java.lang.annotation.Annotation;

import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * A filter for excluding types from method validation.
 * <p>
 * 这其实是一个 {@link FunctionalInterface}
 *
 * @author Andy Wilkinson
 * @see FilteredMethodValidationPostProcessor
 * @since 2.4.0
 */
public interface MethodValidationExcludeFilter {

	/**
	 * Evaluate whether to exclude the given {@code type} from method validation.
	 *
	 * @param type the type to evaluate
	 * @return {@code true} to exclude the type from method validation, otherwise
	 * {@code false}.
	 */
	boolean isExcluded(Class<?> type);

	/**
	 * Factory method to create a {@link MethodValidationExcludeFilter} that excludes
	 * classes by annotation found using an {@link SearchStrategy#INHERITED_ANNOTATIONS
	 * inherited annotations search strategy}.
	 * <p>
	 * 传入一个注解类型，得到一个断言对象 -> 按照 inherited_annotations 规则搜索，判断是否包含这个注解
	 *
	 * @param annotationType the annotation to check
	 * @return a {@link MethodValidationExcludeFilter} instance
	 */
	static MethodValidationExcludeFilter byAnnotation(Class<? extends Annotation> annotationType) {
		// 搜索注解
		return byAnnotation(annotationType, SearchStrategy.INHERITED_ANNOTATIONS);
	}

	/**
	 * Factory method to create a {@link MethodValidationExcludeFilter} that excludes
	 * classes by annotation found using the given search strategy.
	 *
	 * @param annotationType the annotation to check
	 * @param searchStrategy the annotation search strategy
	 * @return a {@link MethodValidationExcludeFilter} instance
	 */
	static MethodValidationExcludeFilter byAnnotation(Class<? extends Annotation> annotationType,
													  SearchStrategy searchStrategy) {
		return (type) -> MergedAnnotations.from(type, searchStrategy).isPresent(annotationType);
	}

}
