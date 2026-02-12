package com.github.xuchen93.grpc.client;

import cn.hutool.core.collection.IterUtil;
import com.github.xuchen93.grpc.api.simple.BidirectionalChatMessage;
import com.github.xuchen93.grpc.api.simple.HelloSimpleRequest;
import com.github.xuchen93.grpc.api.simple.HelloSimpleResponse;
import com.github.xuchen93.grpc.api.simple.HelloSimpleServiceGrpc;
import com.github.xuchen93.grpc.api.simple.StreamRequestChunk;
import com.github.xuchen93.grpc.api.simple.StreamResponseSummary;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
public class HelloServiceTests {

	@Autowired
	private HelloSimpleServiceGrpc.HelloSimpleServiceBlockingStub helloServiceBlockingStub;

	@Autowired(required = false)
	private HelloSimpleServiceGrpc.HelloSimpleServiceStub helloServiceAsyncStub; // 异步Stub（用于流式调用）

	// ======================== 1. 测试简单RPC（Unary RPC） ========================
	@Test
	void sayHello() {
		try {
			// 正常场景测试
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("grpc client")
					.build();
			HelloSimpleResponse response = helloServiceBlockingStub.sayHello(request);
			log.info("【简单RPC】正常响应：{}", response.getMessage());
		} catch (Exception e) {
			log.error("【简单RPC】测试异常", e);
		}
	}

	// ======================== 2. 测试服务端流式RPC（Server Streaming） ========================
	@Test
	void streamHello() {
		try {
			HelloSimpleRequest request = HelloSimpleRequest.newBuilder()
					.setName("grpc client stream")
					.build();

			// 调用服务端流式接口，获取响应迭代器
			log.info("【服务端流式RPC】开始接收响应...");
			Iterator<HelloSimpleResponse> responseIterator = helloServiceBlockingStub.streamHello(request);

			// 遍历接收服务端推送的流式响应
			IterUtil.asIterable(responseIterator).forEach(response -> {
				log.info("【服务端流式RPC】接收响应：{}", response.getMessage());
			});
			log.info("【服务端流式RPC】所有响应接收完成");
		} catch (StatusRuntimeException e) {
			log.error("【服务端流式RPC】服务端返回异常：{}", e.getStatus().getDescription(), e);
		} catch (Exception e) {
			log.error("【服务端流式RPC】测试异常", e);
		}
	}

	// ======================== 3. 测试客户端流式RPC（Client Streaming） ========================
	@Test
	void clientStreamHello() throws InterruptedException {
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			// 创建响应观察者（处理服务端最终的汇总响应）
			StreamObserver<StreamResponseSummary> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(StreamResponseSummary summary) {
					log.info("【客户端流式RPC】接收汇总响应：");
					log.info("  - 总分片数：{}", summary.getChunkCount());
					log.info("  - 数字总和：{}", summary.getTotalNumber());
					log.info("  - 平均值：{}", summary.getAverageNumber());
					log.info("  - 提示信息：{}", summary.getMessage());
				}

				@Override
				public void onError(Throwable t) {
					log.error("【客户端流式RPC】服务端返回异常", t);
					finishLatch.countDown(); // 异常时释放锁
				}

				@Override
				public void onCompleted() {
					log.info("【客户端流式RPC】服务端响应完成");
					finishLatch.countDown(); // 完成时释放锁
				}
			};

			// 调用客户端流式接口，获取请求观察者（用于发送分片）
			StreamObserver<StreamRequestChunk> requestObserver = helloServiceAsyncStub.clientStreamHello(responseObserver);

			// 模拟客户端分批次发送流式请求（5个分片）
			int totalChunks = 5;
			log.info("【客户端流式RPC】开始发送{}个分片...", totalChunks);
			for (int i = 1; i <= totalChunks; i++) {
				StreamRequestChunk chunk = StreamRequestChunk.newBuilder()
						.setNumber(i * 10) // 发送数字：10,20,30,40,50
						.build();
				requestObserver.onNext(chunk);
				log.info("【客户端流式RPC】发送第{}个分片，数字：{}", i, i * 10);
				TimeUnit.MILLISECONDS.sleep(200); // 模拟发送间隔
			}

			// 完成客户端请求发送
			requestObserver.onCompleted();
			log.info("【客户端流式RPC】所有分片发送完成，等待服务端汇总响应...");

			// 等待响应完成（最多等待10秒）
			if (!finishLatch.await(10, TimeUnit.SECONDS)) {
				log.error("【客户端流式RPC】等待响应超时");
			}
		} catch (Exception e) {
			log.error("【客户端流式RPC】测试异常", e);
		} finally {
			finishLatch.countDown(); // 确保锁释放
		}
	}

	// ======================== 4. 测试双向流式RPC（Bidirectional Streaming） ========================
	@Test
	void bidirectionalChat() throws InterruptedException {
		CountDownLatch finishLatch = new CountDownLatch(1);

		try {
			// 创建响应观察者（处理服务端推送的聊天响应）
			StreamObserver<BidirectionalChatMessage> responseObserver = new StreamObserver<>() {
				@Override
				public void onNext(BidirectionalChatMessage message) {
					if (message.getIsError()) {
						log.error("【双向流式RPC】服务端错误响应：{}", message.getMessage());
					} else {
						log.info("【双向流式RPC】收到服务端消息 [{}]: {}", message.getUsername(), message.getMessage());

						// 收到退出指令时，结束聊天
						if (message.getMessage().contains("聊天即将结束")) {
							finishLatch.countDown();
						}
					}
				}

				@Override
				public void onError(Throwable t) {
					log.error("【双向流式RPC】服务端返回异常", t);
					finishLatch.countDown();
				}

				@Override
				public void onCompleted() {
					log.info("【双向流式RPC】服务端已结束聊天");
					finishLatch.countDown();
				}
			};

			// 调用双向流式接口，获取请求观察者（用于发送聊天消息）
			StreamObserver<BidirectionalChatMessage> requestObserver = helloServiceAsyncStub.bidirectionalChat(responseObserver);

			// 模拟客户端发送多条聊天消息
			String[] messages = {"你好，服务端！", "ping", "测试双向流式RPC", "聊天即将结束"};
			for (String msg : messages) {
				BidirectionalChatMessage chatMsg = BidirectionalChatMessage.newBuilder()
						.setUsername("grpc-client")
						.setMessage(msg)
						.setIsError(false)
						.build();
				requestObserver.onNext(chatMsg);
				log.info("【双向流式RPC】发送客户端消息 [grpc-client]: {}", msg);
				TimeUnit.SECONDS.sleep(1); // 模拟发送间隔
			}

			// 完成客户端消息发送
			requestObserver.onCompleted();
			log.info("【双向流式RPC】客户端消息发送完成，等待聊天结束...");

			// 等待聊天结束（最多等待15秒）
			if (!finishLatch.await(15, TimeUnit.SECONDS)) {
				log.error("【双向流式RPC】等待聊天结束超时");
			}
		} catch (Exception e) {
			log.error("【双向流式RPC】测试异常", e);
		} finally {
			finishLatch.countDown();
		}
	}
}
