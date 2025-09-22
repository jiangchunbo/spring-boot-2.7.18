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

package org.springframework.boot.autoconfigure.task;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties.Shutdown;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import java.util.concurrent.ScheduledExecutorService;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link TaskScheduler}.
 *
 * @author Stephane Nicoll
 * @since 2.1.0
 */
@ConditionalOnClass(ThreadPoolTaskScheduler.class)
@AutoConfiguration(after = TaskExecutionAutoConfiguration.class)
@EnableConfigurationProperties(TaskSchedulingProperties.class)
public class TaskSchedulingAutoConfiguration {

	// @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
	// SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME 这个东西是什么 -> 只有你写了 @EnabledScheduling 才会导入
	// ----> 可以处理 bean 里面的 @Scheduled 注解

	/**
	 * Spring Boot 项目会自动导入 ThreadPoolTaskScheduler (TaskScheduler 实现类)
	 * <p>
	 * Scheduled 使用 TaskScheduler 作为底层的执行器
	 * <p>
	 * 如果你的项目使用 spring session 会导致下面这个 ThreadPoolTaskScheduler 不会自动注入
	 */
	@Bean
	@ConditionalOnBean(name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
	@ConditionalOnMissingBean({SchedulingConfigurer.class, TaskScheduler.class, ScheduledExecutorService.class})
	public ThreadPoolTaskScheduler taskScheduler(TaskSchedulerBuilder builder) {
		return builder.build();
	}

	/**
	 * 这个过滤器通常与懒加载相关，用于排除某些 bean 的懒加载行为。就是说，让他不要懒加载。
	 *

3+9	 * @return LazyInitializationExcludeFilter
	 */
	@Bean
	@ConditionalOnBean(name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
	public static LazyInitializationExcludeFilter scheduledBeanLazyInitializationExcludeFilter() {
		return new ScheduledBeanLazyInitializationExcludeFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	public TaskSchedulerBuilder taskSchedulerBuilder(TaskSchedulingProperties properties,
													 ObjectProvider<TaskSchedulerCustomizer> taskSchedulerCustomizers) {
		TaskSchedulerBuilder builder = new TaskSchedulerBuilder();
		builder = builder.poolSize(properties.getPool().getSize());
		Shutdown shutdown = properties.getShutdown();
		builder = builder.awaitTermination(shutdown.isAwaitTermination());
		builder = builder.awaitTerminationPeriod(shutdown.getAwaitTerminationPeriod());
		builder = builder.threadNamePrefix(properties.getThreadNamePrefix());
		builder = builder.customizers(taskSchedulerCustomizers);
		return builder;
	}

}
