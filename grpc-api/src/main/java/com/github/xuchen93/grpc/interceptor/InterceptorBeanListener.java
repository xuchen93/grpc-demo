package com.github.xuchen93.grpc.interceptor;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * @author xuchen.wang
 * @date 2026/2/13
 */
@Slf4j
@Component
public class InterceptorBeanListener implements BeanPostProcessor {

	@Override
	public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof ServerInterceptor) {
			log.info("[注入ServerInterceptor]beanName={},class={}", beanName, bean.getClass().getSimpleName());
		}
		if (bean instanceof ClientInterceptor) {
			log.info("[注入ClientInterceptor]beanName={},class={}", beanName, bean.getClass().getSimpleName());
		}
		return bean;
	}
}
