package com.github.xuchen93.grpc.interceptor.util;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class GrpcInterceptorUtil {


	private static final AtomicLong TRACE_SEQ = new AtomicLong(RandomUtil.randomLong(1000000000L, 10000000000L));
	private static final AtomicLong REQUEST_SEQ = new AtomicLong(RandomUtil.randomLong(1000000000L, 10000000000L));

	/**
	 * 生成TraceId
	 * 格式: [appName]timestamp-random
	 */
	public static String generateTraceId() {
		return StrUtil.format("{}-{}", TRACE_SEQ.incrementAndGet(), RandomUtil.randomInt(100, 1000));
	}

	/**
	 * 生成RequestId
	 * 格式: timestamp-自增序号
	 */
	public static long generateRequestId() {
		return REQUEST_SEQ.incrementAndGet();
	}

}
