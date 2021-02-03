/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io;

import org.apache.flink.annotation.Internal;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining couple of essential methods for asynchronous and non blocking data polling.
 * 接口定义了一对用于异步和非阻塞数据轮询的基本方法。
 * <p>For the most efficient usage, user of this class is supposed to call {@link #pollNext()}
 * until it returns that no more elements are available. If that happens, he should check if
 * input {@link #isFinished()}. If not, he should wait for {@link #getAvailableFuture()}
 * {@link CompletableFuture} to be completed. For example:
 * <p>为了最有效的使用，这个类的用户应该调用pollNext()，直到它返回没有更多的元素可用为止。如果出现这种情况，他应该检查输入是否已完成()。
 * 如果没有，他应该等待getAvailableFuture()被完成。例如:</p>
 * <pre>
 * {@code
 *	AsyncDataInput<T> input = ...;
 *	while (!input.isFinished()) {
 *		Optional<T> next;
 *
 *		while (true) {
 *			next = input.pollNext();
 *			if (!next.isPresent()) {
 *				break;
 *			}
 *			// do something with next
 *		}
 *
 *		input.getAvailableFuture().get();
 *	}
 * }
 * </pre>
 */
@Internal
public interface PullingAsyncDataInput<T> extends AvailabilityProvider {
	/**
	 * Poll the next element. This method should be non blocking.
	 *
	 * @return {@code Optional.empty()} will be returned if there is no data to return or
	 * if {@link #isFinished()} returns true. Otherwise {@code Optional.of(element)}.
	 */
	Optional<T> pollNext() throws Exception;

	/**
	 * @return true if is finished and for example end of input was reached, false otherwise.
	 */
	boolean isFinished();
}
