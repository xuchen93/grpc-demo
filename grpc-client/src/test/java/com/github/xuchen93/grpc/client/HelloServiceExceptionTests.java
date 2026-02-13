package com.github.xuchen93.grpc.client;

import com.github.xuchen93.grpc.api.simple.BidirectionalChatMessage;
import com.github.xuchen93.grpc.api.simple.HelloSimpleRequest;
import com.github.xuchen93.grpc.api.simple.HelloSimpleResponse;
import com.github.xuchen93.grpc.api.simple.HelloSimpleServiceGrpc;
import com.github.xuchen93.grpc.api.simple.StreamRequestChunk;
import com.github.xuchen93.grpc.api.simple.StreamResponseSummary;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
public class HelloServiceExceptionTests {

	@Autowired
	private HelloSimpleServiceGrpc.HelloSimpleServiceBlockingStub helloServiceBlockingStub;

	@Autowired
	private HelloSimpleServiceGrpc.HelloSimpleServiceStub helloServiceAsyncStub;

	private ManagedChannel channel;

	// ======================== 测试前置条件 ========================
	@BeforeEach
	void setUp() {
		// 可以在这里设置测试环境
		log.info("=== 开始执行异常场景测试 ===");
	}

	@AfterEach
	void tearDown() {
		// 可以在这里清理测试环境
		log.info("=== 异常场景测试执行完成 ===\n");
	}

	// ======================== 1. 简单RPC异常场景测试 ========================
	@Test
	void testSayHelloWithSpecialCharacters() {
		// 测试特殊字符参数
		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("!@#$%^&*()")
					.build();
			HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);
			log.info("【简单RPC】特殊字符参数响应：{}", response.getMessage());
		} catch (StatusRuntimeException e) {
			log.warn("【简单RPC】特殊字符参数异常：{}", e.getStatus().getDescription());
		}
	}

	@Test
	void testSayHelloWithVeryLongName() {
		// 测试超长参数
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			longName.append("a");
		}

		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName(longName.toString())
					.build();
			HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);
			log.info("【简单RPC】超长参数响应：{}", response.getMessage());
		} catch (StatusRuntimeException e) {
			log.warn("【简单RPC】超长参数异常：{}", e.getStatus().getDescription());
		} catch (Exception e) {
			log.error("【简单RPC】超长参数处理异常：{}", e.getMessage());
		}
	}

	@Test
	void testSayHelloWithTimeout() {
		// 测试超时场景（创建一个新的channel，设置较短的超时时间）
		channel = ManagedChannelBuilder.forAddress("localhost", 19090)
				.usePlaintext()
				.build();

		HelloSimpleServiceGrpc.HelloSimpleServiceBlockingStub timeoutStub =
				HelloSimpleServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.MILLISECONDS);

		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("timeout-test")
					.build();
			HelloSimpleResponse response = timeoutStub.sayHello(request);
			log.info("【简单RPC】超时测试响应：{}", response.getMessage());
		} catch (StatusRuntimeException e) {
			if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
				log.warn("【简单RPC】超时测试成功捕获超时异常");
			} else {
				log.warn("【简单RPC】超时测试捕获到其他异常：{}", e.getStatus().getDescription());
			}
		} finally {
			if (channel != null) {
				channel.shutdown();
			}
		}
	}

	// ======================== 2. 服务端流式RPC异常场景测试 ========================
	@Test
	void testStreamHelloWithInvalidParameter() {
		// 测试服务端流式RPC的无效参数
		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("") // 空参数
					.build();
			Iterator<HelloSimpleResponse> responseIterator = helloServiceBlockingStub.streamHello(request);
			while (responseIterator.hasNext()) {
				HelloSimpleResponse response = responseIterator.next();
				log.info("【服务端流式RPC】无效参数响应：{}", response.getMessage());
			}
		} catch (StatusRuntimeException e) {
			log.warn("【服务端流式RPC】无效参数异常：{}", e.getStatus().getDescription());
			assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode(), "应该返回参数无效错误");
		}
	}

	@Test
	void testStreamHelloWithInterruption() {
		// 测试服务端流式RPC被中断的情况
		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("interruption-test")
					.build();
			Iterator<HelloSimpleResponse> responseIterator = helloServiceBlockingStub.streamHello(request);

			// 只接收前2个响应，然后中断
			int count = 0;
			while (responseIterator.hasNext() && count < 2) {
				HelloSimpleResponse response = responseIterator.next();
				log.info("【服务端流式RPC】接收响应：{}", response.getMessage());
				count++;
			}

			// 中断当前线程
			Thread.currentThread().interrupt();
			log.info("【服务端流式RPC】测试被中断");

		} catch (StatusRuntimeException e) {
			log.warn("【服务端流式RPC】中断后异常：{}", e.getStatus().getDescription());
		} finally {
			// 清除中断状态
			Thread.interrupted();
		}
	}

	// ======================== 3. 客户端流式RPC异常场景测试 ========================
	@Test
	void testClientStreamHelloWithErrorDuringSending() {
		// 测试客户端流式RPC发送过程中出错
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			StreamObserver<StreamResponseSummary> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(StreamResponseSummary summary) {
					log.info("【客户端流式RPC】接收汇总响应：{}", summary.getMessage());
				}

				@Override
				public void onError(Throwable t) {
					log.warn("【客户端流式RPC】服务端返回异常：{}", t.getMessage());
					finishLatch.countDown();
				}

				@Override
				public void onCompleted() {
					log.info("【客户端流式RPC】服务端响应完成");
					finishLatch.countDown();
				}
			};

			StreamObserver<StreamRequestChunk> requestObserver = helloServiceAsyncStub.clientStreamHello(responseObserver);

			// 发送几个正常的分片
			for (int i = 1; i <= 3; i++) {
				StreamRequestChunk chunk = StreamRequestChunk.newBuilder()
						.setNumber(i * 10)
						.build();
				requestObserver.onNext(chunk);
				log.info("【客户端流式RPC】发送第{}个分片，数字：{}", i, i * 10);
				TimeUnit.MILLISECONDS.sleep(100);
			}

			// 发送一个错误的分片（比如超大数字）
			try {
				StreamRequestChunk invalidChunk = StreamRequestChunk.newBuilder()
						.setNumber(Long.MAX_VALUE) // 可能导致服务端溢出
						.build();
				requestObserver.onNext(invalidChunk);
				log.info("【客户端流式RPC】发送超大数字分片");
			} catch (Exception e) {
				log.error("【客户端流式RPC】发送超大数字分片异常：{}", e.getMessage());
			}

			// 继续发送正常分片
			for (int i = 4; i <= 5; i++) {
				StreamRequestChunk chunk = StreamRequestChunk.newBuilder()
						.setNumber(i * 10)
						.build();
				requestObserver.onNext(chunk);
				log.info("【客户端流式RPC】发送第{}个分片，数字：{}", i, i * 10);
				TimeUnit.MILLISECONDS.sleep(100);
			}

			requestObserver.onCompleted();

			if (!finishLatch.await(10, TimeUnit.SECONDS)) {
				log.error("【客户端流式RPC】等待响应超时");
			}

		} catch (Exception e) {
			log.error("【客户端流式RPC】测试异常：{}", e.getMessage());
		} finally {
			finishLatch.countDown();
		}
	}

	@Test
	void testClientStreamHelloWithCancel() {
		// 测试客户端流式RPC被取消
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			StreamObserver<StreamResponseSummary> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(StreamResponseSummary summary) {
					log.info("【客户端流式RPC】接收汇总响应：{}", summary.getMessage());
				}

				@Override
				public void onError(Throwable t) {
					log.warn("【客户端流式RPC】服务端返回异常：{}", t.getMessage());
					finishLatch.countDown();
				}

				@Override
				public void onCompleted() {
					log.info("【客户端流式RPC】服务端响应完成");
					finishLatch.countDown();
				}
			};

			StreamObserver<StreamRequestChunk> requestObserver = helloServiceAsyncStub.clientStreamHello(responseObserver);

			// 发送几个分片
			for (int i = 1; i <= 3; i++) {
				StreamRequestChunk chunk = StreamRequestChunk.newBuilder()
						.setNumber(i * 10)
						.build();
				requestObserver.onNext(chunk);
				log.info("【客户端流式RPC】发送第{}个分片，数字：{}", i, i * 10);
				TimeUnit.MILLISECONDS.sleep(100);
			}

			// 取消请求
			requestObserver.onError(new RuntimeException("客户端主动取消请求"));
			log.info("【客户端流式RPC】主动取消请求");

			if (!finishLatch.await(5, TimeUnit.SECONDS)) {
				log.error("【客户端流式RPC】等待响应超时");
			}

		} catch (Exception e) {
			log.error("【客户端流式RPC】测试异常：{}", e.getMessage());
		} finally {
			finishLatch.countDown();
		}
	}

	// ======================== 4. 双向流式RPC异常场景测试 ========================
	@Test
	void testBidirectionalChatWithInvalidMessages() {
		// 测试双向流式RPC发送无效消息
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			StreamObserver<BidirectionalChatMessage> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(BidirectionalChatMessage message) {
					if (message.getIsError()) {
						log.error("【双向流式RPC】服务端错误响应：{}", message.getMessage());
					} else {
						log.info("【双向流式RPC】收到服务端消息 [{}]: {}", message.getUsername(), message.getMessage());
					}
				}

				@Override
				public void onError(Throwable t) {
					log.warn("【双向流式RPC】服务端返回异常：{}", t.getMessage());
					finishLatch.countDown();
				}

				@Override
				public void onCompleted() {
					log.info("【双向流式RPC】服务端已结束聊天");
					finishLatch.countDown();
				}
			};

			StreamObserver<BidirectionalChatMessage> requestObserver = helloServiceAsyncStub.bidirectionalChat(responseObserver);

			// 发送正常消息
			BidirectionalChatMessage normalMsg = BidirectionalChatMessage.newBuilder()
					.setUsername("client")
					.setMessage("正常消息")
					.setIsError(false)
					.build();
			requestObserver.onNext(normalMsg);
			log.info("【双向流式RPC】发送正常消息");
			TimeUnit.MILLISECONDS.sleep(500);

			// 发送空消息
			BidirectionalChatMessage emptyMsg = BidirectionalChatMessage.newBuilder()
					.setUsername("client")
					.setMessage("")
					.setIsError(false)
					.build();
			requestObserver.onNext(emptyMsg);
			log.info("【双向流式RPC】发送空消息");
			TimeUnit.MILLISECONDS.sleep(500);

			// 发送超长消息
			StringBuilder longMsgContent = new StringBuilder();
			for (int i = 0; i < 2000; i++) {
				longMsgContent.append("a");
			}
			BidirectionalChatMessage longMsg = BidirectionalChatMessage.newBuilder()
					.setUsername("client")
					.setMessage(longMsgContent.toString())
					.setIsError(false)
					.build();
			requestObserver.onNext(longMsg);
			log.info("【双向流式RPC】发送超长消息");
			TimeUnit.MILLISECONDS.sleep(500);

			// 发送错误标记的消息
			BidirectionalChatMessage errorMsg = BidirectionalChatMessage.newBuilder()
					.setUsername("client")
					.setMessage("错误测试消息")
					.setIsError(true)
					.build();
			requestObserver.onNext(errorMsg);
			log.info("【双向流式RPC】发送错误标记消息");
			TimeUnit.MILLISECONDS.sleep(500);

			// 发送退出消息
			BidirectionalChatMessage exitMsg = BidirectionalChatMessage.newBuilder()
					.setUsername("client")
					.setMessage("聊天即将结束")
					.setIsError(false)
					.build();
			requestObserver.onNext(exitMsg);
			log.info("【双向流式RPC】发送退出消息");

			requestObserver.onCompleted();

			if (!finishLatch.await(15, TimeUnit.SECONDS)) {
				log.error("【双向流式RPC】等待响应超时");
			}

		} catch (Exception e) {
			log.error("【双向流式RPC】测试异常：{}", e.getMessage());
		} finally {
			finishLatch.countDown();
		}
	}

	@Test
	void testBidirectionalChatWithClientError() {
		// 测试双向流式RPC客户端发送错误
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			StreamObserver<BidirectionalChatMessage> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(BidirectionalChatMessage message) {
					log.info("【双向流式RPC】收到服务端消息 [{}]: {}", message.getUsername(), message.getMessage());
				}

				@Override
				public void onError(Throwable t) {
					log.warn("【双向流式RPC】服务端返回异常：{}", t.getMessage());
					finishLatch.countDown();
				}

				@Override
				public void onCompleted() {
					log.info("【双向流式RPC】服务端已结束聊天");
					finishLatch.countDown();
				}
			};

			StreamObserver<BidirectionalChatMessage> requestObserver = helloServiceAsyncStub.bidirectionalChat(responseObserver);

			// 发送几条正常消息
			for (int i = 1; i <= 3; i++) {
				BidirectionalChatMessage msg = BidirectionalChatMessage.newBuilder()
						.setUsername("client")
						.setMessage("消息 " + i)
						.setIsError(false)
						.build();
				requestObserver.onNext(msg);
				log.info("【双向流式RPC】发送消息 {}", i);
				TimeUnit.MILLISECONDS.sleep(200);
			}

			// 客户端发送错误
			log.info("【双向流式RPC】客户端主动发送错误");
			requestObserver.onError(new RuntimeException("客户端内部错误"));

			if (!finishLatch.await(10, TimeUnit.SECONDS)) {
				log.error("【双向流式RPC】等待响应超时");
			}

		} catch (Exception e) {
			log.error("【双向流式RPC】测试异常：{}", e.getMessage());
		} finally {
			finishLatch.countDown();
		}
	}

	// ======================== 5. 并发测试场景 ========================
	@Test
	void testConcurrentSimpleRpcCalls() {
		// 测试并发简单RPC调用
		int threadCount = 10;
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch finishLatch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger errorCount = new AtomicInteger(0);

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		try {
			for (int i = 0; i < threadCount; i++) {
				final int threadId = i;
				executor.submit(() -> {
					try {
						startLatch.await(); // 等待所有线程准备就绪

						HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
								.setName("concurrent-user-" + threadId)
								.build();
						HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);
						log.info("【并发测试】线程{}: 响应成功 - {}", threadId, response.getMessage());
						successCount.incrementAndGet();
					} catch (Exception e) {
						log.error("【并发测试】线程{}: 调用失败 - {}", threadId, e.getMessage());
						errorCount.incrementAndGet();
					} finally {
						finishLatch.countDown();
					}
				});
			}

			// 启动所有线程
			log.info("【并发测试】启动{}个并发线程...", threadCount);
			startLatch.countDown();

			// 等待所有线程完成
			if (!finishLatch.await(30, TimeUnit.SECONDS)) {
				log.error("【并发测试】等待线程完成超时");
			}

			log.info("【并发测试】完成 - 成功: {}, 失败: {}", successCount.get(), errorCount.get());

		} catch (Exception e) {
			log.error("【并发测试】测试异常：{}", e.getMessage());
		} finally {
			executor.shutdown();
		}
	}
}
