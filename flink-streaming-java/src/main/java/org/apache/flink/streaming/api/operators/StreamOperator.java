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

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.state.CheckpointListener;
import org.apache.flink.runtime.state.CheckpointStreamFactory;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.util.Disposable;

import java.io.Serializable;

/**
 * Basic interface for stream operators. Implementers would implement one of
 * {@link org.apache.flink.streaming.api.operators.OneInputStreamOperator} or
 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator} to create operators
 * that process elements.
 * <p>流操作符的基本接口。实现者将实现OneInputStreamOperator或TwoInputStreamOperator中
 * 的一个来创建处理元素的操作符。</p>
 * <p>The class {@link org.apache.flink.streaming.api.operators.AbstractStreamOperator}
 * offers default implementation for the lifecycle and properties methods.
 *
 * <p>Methods of {@code StreamOperator} are guaranteed not to be called concurrently. Also, if using
 * the timer service, timer callbacks are also guaranteed not to be called concurrently with
 * methods on {@code StreamOperator}.
 *
 * @param <OUT> The output type of the operator
 */
@PublicEvolving
public interface StreamOperator<OUT> extends CheckpointListener, KeyContext, Disposable, Serializable {

	// ------------------------------------------------------------------------
	//  life cycle
	// ------------------------------------------------------------------------

	/**
	 * This method is called immediately before any elements are processed, it should contain the
	 * operator's initialization logic.
	 * 在处理任何元素之前立即调用此方法，它应该包含操作符的初始化逻辑。
	 * @implSpec In case of recovery, this method needs to ensure that all recovered data is processed before passing
	 * back control, so that the order of elements is ensured during the recovery of an operator chain (operators
	 * are opened from the tail operator to the head operator).
	 * 在恢复的情况下，此方法需要确保在传递回控制之前处理所有恢复的数据，以便在恢复操作符链期间确保元素顺序（操作符从尾部操作符打开到头部操作符）。
	 * @throws java.lang.Exception An exception in this method causes the operator to fail.
	 */
	void open() throws Exception;

	/**
	 * This method is called after all records have been added to the operators via the methods
	 * {@link org.apache.flink.streaming.api.operators.OneInputStreamOperator#processElement(StreamRecord)}, or
	 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator#processElement1(StreamRecord)} and
	 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator#processElement2(StreamRecord)}.
	 * <p>通过方法OneInputStreamOperator.processElement(StreamRecord)或twoinputstreamoperator.processelement1(StreamRecord)
	 * 和twoinputstreamoperator.processelement2(StreamRecord)将所有记录添加到操作符之后，调用此方法。
	 * </p>
	 * <p>The method is expected to flush all remaining buffered data. Exceptions during this
	 * flushing of buffered should be propagated, in order to cause the operation to be recognized
	 * as failed, because the last data items are not processed properly.
	 * 该方法有望刷新所有剩余的缓冲数据。 由于没有正确处理最后的数据项，因此应传播此缓冲刷新期间的异常，以使该操作被识别为失败。
	 * @throws java.lang.Exception An exception in this method causes the operator to fail.
	 */
	// 该方法在所有的元素都进入到operator被处理之后调用
	void close() throws Exception;

	/**
	 * This method is called at the very end of the operator's life, both in the case of a successful
	 * completion of the operation, and in the case of a failure and canceling.
	 * 此方法在操作符生命周期的最后被调用，无论是在操作成功完成的情况下，还是在操作失败和取消的情况下。
	 * <p>This method is expected to make a thorough effort to release all resources
	 * that the operator has acquired.
	 * <p>该方法会释放所有资源</p>
	 */
	@Override
	void dispose() throws Exception;

	// ------------------------------------------------------------------------
	//  state snapshots
	// ------------------------------------------------------------------------

	/**
	 * This method is called when the operator should do a snapshot, before it emits its
	 * own checkpoint barrier.
	 *
	 * <p>This method is intended not for any actual state persistence, but only for emitting some
	 * data before emitting the checkpoint barrier. Operators that maintain some small transient state
	 * that is inefficient to checkpoint (especially when it would need to be checkpointed in a
	 * re-scalable way) but can simply be sent downstream before the checkpoint. An example are
	 * opportunistic pre-aggregation operators, which have small the pre-aggregation state that is
	 * frequently flushed downstream.
	 *
	 * <p><b>Important:</b> This method should not be used for any actual state snapshot logic, because
	 * it will inherently be within the synchronous part of the operator's checkpoint. If heavy work is done
	 * within this method, it will affect latency and downstream checkpoint alignments.
	 *
	 * @param checkpointId The ID of the checkpoint.
	 * @throws Exception Throwing an exception here causes the operator to fail and go into recovery.
	 */
	void prepareSnapshotPreBarrier(long checkpointId) throws Exception;

	/**
	 * Called to draw a state snapshot from the operator.
	 * 从operator处调用以绘制状态快照。
	 * @return a runnable future to the state handle that points to the snapshotted state. For synchronous implementations,
	 * the runnable might already be finished.
	 * 状态句柄的可运行的Future，它指向快照状态。 对于同步实现，可运行对象可能已经完成。
	 * @throws Exception exception that happened during snapshotting.
	 */
	OperatorSnapshotFutures snapshotState(
		long checkpointId,
		long timestamp,
		CheckpointOptions checkpointOptions,
		CheckpointStreamFactory storageLocation) throws Exception;

	/**
	 * Provides a context to initialize all state in the operator.
	 * 提供一个上下文来初始化操作符中的所有状态。
	 */
	void initializeState() throws Exception;

	// ------------------------------------------------------------------------
	//  miscellaneous
	// ------------------------------------------------------------------------

	void setKeyContextElement1(StreamRecord<?> record) throws Exception;

	void setKeyContextElement2(StreamRecord<?> record) throws Exception;

	ChainingStrategy getChainingStrategy();

	void setChainingStrategy(ChainingStrategy strategy);

	MetricGroup getMetricGroup();

	OperatorID getOperatorID();
}
