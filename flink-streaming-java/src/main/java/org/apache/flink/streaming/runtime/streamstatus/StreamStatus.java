/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.streamstatus;

import org.apache.flink.annotation.Internal;
import org.apache.flink.streaming.api.operators.StreamSource;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.tasks.SourceStreamTask;
import org.apache.flink.streaming.runtime.tasks.StreamTask;

/**
 * A Stream Status element informs stream tasks whether or not they should continue to expect records and watermarks
 * from the input stream that sent them. There are 2 kinds of status, namely {@link StreamStatus#IDLE} and
 * {@link StreamStatus#ACTIVE}. Stream Status elements are generated at the sources, and may be propagated through
 * the tasks of the topology. They directly infer the current status of the emitting task; a {@link SourceStreamTask} or
 * {@link StreamTask} emits a {@link StreamStatus#IDLE} if it will temporarily halt to emit any records or watermarks
 * (i.e. is idle), and emits a {@link StreamStatus#ACTIVE} once it resumes to do so (i.e. is active). Tasks are
 * responsible for propagating their status further downstream once they toggle between being idle and active. The cases
 * that source tasks and downstream tasks are considered either idle or active is explained below:
 * <p>流状态元素通知下游流任务（tasks）是否应该继续期待输入流发送记录和水印。StreamStatus有两种状态，即空闲和活动。
 * 流状态元素在源上生成，并且可以通过任务的拓扑结构传播。它们直接推断发出任务的当前状态，如果任务(Task)将暂时停止发射任何记录或水印，
 * SourceStreamTask或者StreamTask将发出一个状态为IDLE的StreamStatus，一旦任务恢复，则发出一个状态为ACTIVE的StreamStatus。
 * 一旦任务在空闲和活动之间切换时，任务（Tasks）负责向下游传播其状态
 *
 * <p>下面说明将源任务和下游任务视为空闲或活动的情况：
 * <ul>
 *     <li>Source tasks: A source task is considered to be idle if its head operator, i.e. a {@link StreamSource}, will
 *         not emit records for an indefinite amount of time. This is the case, for example, for Flink's Kafka Consumer,
 *         where sources might initially have no assigned partitions to read from, or no records can be read from the
 *         assigned partitions. Once the head {@link StreamSource} operator detects that it will resume emitting data,
 *         the source task is considered to be active. {@link StreamSource}s are responsible for toggling the status
 *         of the containing source task and ensuring that no records (and possibly watermarks, in the case of Flink's
 *         Kafka Consumer which can generate watermarks directly within the source) will be emitted while the task is
 *         idle. This guarantee should be enforced on sources through
 *         {@link org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext} implementations.</li>
 *		<p>如果源任务的头运算符（即StreamSource）在不确定的时间内不发出记录，则认为该源任务处于空闲状态。
 *		例如，对于Flink的Kafka Consumer，就是这种情况，其中源最初可能没有要读取的已分配分区，或者无法从已分配的分区读取记录。
 *		一旦头StreamSource运算符检测到它将恢复发射数据，就认为源任务处于活动状态。
 *		StreamSources负责切换包含源任务的状态，并确保在任务空闲时不会发出任何记录（如果是Flink的Kafka Consumer，可能会直接在源内生成水印）。
 *		应该通过SourceContext实现在源上强制执行此保证。</p>
 *     <li>Downstream tasks: a downstream task is considered to be idle if all its input streams are idle, i.e. the last
 *         received Stream Status element from all input streams is a {@link StreamStatus#IDLE}. As long as one of its
 *         input streams is active, i.e. the last received Stream Status element from the input stream is
 *         {@link StreamStatus#ACTIVE}, the task is active.</li>
 *     <li>如果下游任务的所有输入流都空闲，则认为下游任务是空闲的，即，从所有输入流中最后接收到的流状态元素是IDLE。
 *     只要其输入流之一是活动的，即从输入流中最后收到的流状态元素是活动的，任务就处于活动状态。</li>
 * </ul>
 *
 * <p>Stream Status elements received at downstream tasks also affect and control how their operators process and advance
 * their watermarks. The below describes the effects (the logic is implemented as a {@link StatusWatermarkValve} which
 * downstream tasks should use for such purposes):
 * <p>在下游任务处接收到的“流状态”元素还影响并控制其算子（operators）如何处理和改进其水印</p>
 * <ul>
 *     <li>Since source tasks guarantee that no records will be emitted between a {@link StreamStatus#IDLE} and
 *         {@link StreamStatus#ACTIVE}, downstream tasks can always safely process and propagate records through their
 *         operator chain when they receive them, without the need to check whether or not the task is currently idle or
 *         active. However, for watermarks, since there may be watermark generators that might produce watermarks
 *         anywhere in the middle of topologies regardless of whether there are input data at the operator, the current
 *         status of the task must be checked before forwarding watermarks emitted from
 *         an operator. If the status is actually idle, the watermark must be blocked.
 *	   <p>由于source task保证在空闲和活动之间不会发送任何记录，下游任务在收到记录后，
 *	   始终可以通过operator链安全地处理和传播记录，而无需检查任务当前是否处于空闲或活动状态。
 *	   但是，对于水印，由于可能会在拓扑中间的任何位置产生水印生成器，而不管操作员是否有输入数据，
 *	   因此在转发从操作员发出的水印之前必须检查任务的当前状态。
 *	   如果状态实际上是空闲的，则必须阻止水印。</p>
 *     <li>For downstream tasks with multiple input streams, the watermarks of input streams that are temporarily idle,
 *         or has resumed to be active but its watermark is behind the overall min watermark of the operator, should not
 *         be accounted for when deciding whether or not to advance the watermark and propagated through the operator
 *         chain.
 *         对于具有多个输入流的下游任务，输入流的水印暂时处于空闲状态或已恢复活动状态，但其水印落后于该算子的整体最小水印
 *         ，在决定是否推进水印并在运营商链中传播时，不应考虑（落入水印不发送）。
 * </ul>
 *
 * <p>Note that to notify downstream tasks that a source task is permanently closed and will no longer send any more
 * elements, the source should still send a {@link Watermark#MAX_WATERMARK} instead of {@link StreamStatus#IDLE}.
 * Stream Status elements only serve as markers for temporary status.
 * 请注意，要通知下游任务源任务已永久关闭并且不再发送任何元素，源仍应发送Watermark.MAX_WATERMARK而不是IDLE。 流状态元素仅用作临时状态的标记。
 */
@Internal
public final class StreamStatus extends StreamElement {

	public static final int IDLE_STATUS = -1;
	public static final int ACTIVE_STATUS = 0;

	public static final StreamStatus IDLE = new StreamStatus(IDLE_STATUS);
	public static final StreamStatus ACTIVE = new StreamStatus(ACTIVE_STATUS);

	public final int status;

	public StreamStatus(int status) {
		if (status != IDLE_STATUS && status != ACTIVE_STATUS) {
			throw new IllegalArgumentException("Invalid status value for StreamStatus; " +
				"allowed values are " + ACTIVE_STATUS + " (for ACTIVE) and " + IDLE_STATUS + " (for IDLE).");
		}

		this.status = status;
	}

	public boolean isIdle() {
		return this.status == IDLE_STATUS;
	}

	public boolean isActive() {
		return !isIdle();
	}

	public int getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		return this == o ||
			o != null && o.getClass() == StreamStatus.class && ((StreamStatus) o).status == this.status;
	}

	@Override
	public int hashCode() {
		return status;
	}

	@Override
	public String toString() {
		String statusStr = (status == ACTIVE_STATUS) ? "ACTIVE" : "IDLE";
		return "StreamStatus(" + statusStr + ")";
	}
}
