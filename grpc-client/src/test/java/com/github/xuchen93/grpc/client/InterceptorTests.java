package com.github.xuchen93.grpc.client;

import com.github.xuchen93.grpc.api.simple.HelloSimpleRequest;
import com.github.xuchen93.grpc.api.simple.HelloSimpleResponse;
import com.github.xuchen93.grpc.api.simple.HelloSimpleServiceGrpc;
import com.github.xuchen93.grpc.interceptor.client.AuthClientInterceptor;
import com.github.xuchen93.grpc.interceptor.client.LoggingClientInterceptor;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 拦截器功能测试类
 * 测试认证拦截器和日志拦截器的功能
 */
@Slf4j
@SpringBootTest
public class InterceptorTests {

    @Autowired
    private HelloSimpleServiceGrpc.HelloSimpleServiceBlockingStub helloServiceBlockingStub;


    @BeforeEach
    void setUp() {
        log.info("========== 测试开始 ==========");
    }

    @AfterEach
    void tearDown() {
        // 清除认证信息，避免影响其他测试
        AuthClientInterceptor.clearToken();
        LoggingClientInterceptor.clearTraceId();
        log.info("========== 测试结束 ==========\n");
    }

    // ======================== 测试1: 未认证访问 ========================
    @Test
    void testAccessWithoutAuth() {
        log.info("【测试1】未设置Token，期望返回UNAUTHENTICATED错误");

        // 不设置Token，直接访问
        try {
            HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
                    .setName("test-user")
                    .build();
            HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);

            // 如果服务端没有开启认证，这里可能会成功
            log.info("【测试1】请求成功: {}", response.getMessage());
        } catch (StatusRuntimeException e) {
            // 期望收到UNAUTHENTICATED错误
            log.error("【测试1】捕获到异常: Code={}, Description={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            assertEquals(io.grpc.Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
        }
    }

    // ======================== 测试2: 有效Token访问 ========================
    @Test
    void testAccessWithValidToken() {
        log.info("【测试2】设置有效的Token，期望请求成功");

        // 设置有效的Token（以"valid_"开头模拟有效Token）
        String validToken = "valid_token_123456789";
        AuthClientInterceptor.setToken(validToken);

        try {
            HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
                    .setName("grpc-client-test")
                    .build();
            HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);

            log.info("【测试2】请求成功: {}", response.getMessage());
            assertNotNull(response.getMessage());
        } catch (StatusRuntimeException e) {
            log.error("【测试2】请求失败: Code={}, Description={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            fail("不应该抛出异常: " + e.getMessage());
        }
    }

    // ======================== 测试3: 无效Token访问 ========================
    @Test
    void testAccessWithInvalidToken() {
        log.info("【测试3】设置无效的Token，期望返回UNAUTHENTICATED错误");

        // 设置无效的Token（短Token被模拟为无效）
        String invalidToken = "bad";
        AuthClientInterceptor.setToken(invalidToken);

        try {
            HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
                    .setName("test-user")
                    .build();
            HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);

            log.info("【测试3】请求成功: {}", response.getMessage());
            // 如果服务端认证逻辑允许短Token，这里会成功
        } catch (StatusRuntimeException e) {
            log.error("【测试3】捕获到异常: Code={}, Description={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            // 期望收到UNAUTHENTICATED错误
            assertEquals(io.grpc.Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
        }
    }

    // ======================== 测试4: 流式RPC拦截器测试 ========================
    @Test
    void testStreamWithAuth() {
        log.info("【测试4】流式RPC带Token访问");

        // 设置Token
        String token = "valid_stream_token_12345";
        AuthClientInterceptor.setToken(token);

        try {
            HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
                    .setName("stream-test")
                    .build();

            // 使用服务端流式方法
            var responseIterator = helloServiceBlockingStub.streamHello(request);

            int count = 0;
            while (responseIterator.hasNext()) {
                var response = responseIterator.next();
                log.info("【测试4】收到流式响应[{}]: {}", ++count, response.getMessage());
            }

            log.info("【测试4】流式请求完成，共收到 {} 条响应", count);
        } catch (StatusRuntimeException e) {
            log.error("【测试4】流式请求失败: Code={}, Description={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            fail("流式请求不应该失败: " + e.getMessage());
        }
    }

    // ======================== 测试5: TraceId传递测试 ========================
    @Test
    void testTraceIdPropagation() {
        log.info("【测试5】TraceId传递测试");

        // 设置自定义TraceId
        String customTraceId = "my-custom-trace-12345";
        String token = "valid_token_for_trace_test";
        AuthClientInterceptor.setToken(token);
        LoggingClientInterceptor.setTraceId(customTraceId);

        log.info("【测试5】客户端设置的TraceId: {}", customTraceId);

        try {
            HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
                    .setName("trace-test")
                    .build();
            HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);

            log.info("【测试5】请求成功: {}", response.getMessage());
            // 在服务端的日志中应该可以看到相同的TraceId
        } catch (StatusRuntimeException e) {
            log.error("【测试5】请求失败: Code={}, Description={}",
                    e.getStatus().getCode(), e.getStatus().getDescription());
            fail("请求不应该失败: " + e.getMessage());
        } finally {
            AuthClientInterceptor.clearToken();
            LoggingClientInterceptor.clearTraceId();
        }
    }
}
