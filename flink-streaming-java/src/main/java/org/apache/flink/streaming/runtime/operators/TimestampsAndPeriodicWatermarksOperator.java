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

package org.apache.flink.streaming.runtime.operators;

import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.operators.AbstractUdfStreamOperator;
import org.apache.flink.streaming.api.operators.ChainingStrategy;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeCallback;

/**
 * A stream operator that extracts timestamps from stream elements and
 * generates periodic watermarks.
 * 从流元素中提取时间戳并生成周期水印的流操作符。
 * @param <T> The type of the input elements
 */
public class TimestampsAndPeriodicWatermarksOperator<T>
		extends AbstractUdfStreamOperator<T, AssignerWithPeriodicWatermarks<T>>
		implements OneInputStreamOperator<T, T>, ProcessingTimeCallback {

	private static final long serialVersionUID = 1L;

	private transient long watermarkInterval;

	private transient long currentWatermark;

	public TimestampsAndPeriodicWatermarksOperator(AssignerWithPeriodicWatermarks<T> assigner) {
		super(assigner);
		this.chainingStrategy = ChainingStrategy.ALWAYS;
	}

	@Override
	public void open() throws Exception {
		super.open();
		// 初始化当前水印的时间戳
		currentWatermark = Long.MIN_VALUE;
		// 获取周期水印发射的时间间隔
		watermarkInterval = getExecutionConfig().getAutoWatermarkInterval();

		if (watermarkInterval > 0) {
			long now = getProcessingTimeService().getCurrentProcessingTime();
			// 注册一个watermarkInterval后触发的定时器，传入回调参数是this，也就是会调用当前对象的onProcessingTime方法
			getProcessingTimeService().registerTimer(now + watermarkInterval, this);
		}
	}

	// 每一个元素都要调用
	@Override
	public void processElement(StreamRecord<T> element) throws Exception {
		final long newTimestamp = userFunction.extractTimestamp(element.getValue(),
				element.hasTimestamp() ? element.getTimestamp() : Long.MIN_VALUE);

		output.collect(element.replace(element.getValue(), newTimestamp));
	}

	// 另外该方法与processElement方法是两个互斥的方法，内部使用了同一把锁做同步控制。
	@Override
	public void onProcessingTime(long timestamp) throws Exception {
		// register next timer
		// 获取当前水印
		Watermark newWatermark = userFunction.getCurrentWatermark();
		// 如果当前水印的时间戳大于当前算子的水印时间戳，就发出一个新的水印
		if (newWatermark != null && newWatermark.getTimestamp() > currentWatermark) {
			currentWatermark = newWatermark.getTimestamp();
			// emit watermark
			output.emitWatermark(newWatermark);
		}

		long now = getProcessingTimeService().getCurrentProcessingTime();
		// 注册基于系统时间的下一次触发时间的定时器
		getProcessingTimeService().registerTimer(now + watermarkInterval, this);
	}

	/**
	 *
	 * Override the base implementation to completely ignore watermarks propagated from
	 * upstream (we rely only on the {@link AssignerWithPeriodicWatermarks} to emit
	 * watermarks from here).
	 * <p>以完全忽略从上游传播的水印（我们仅依靠AssignerWithPeriodicWatermarks从此处发出水印）。</p>
	 */
	@Override
	public void processWatermark(Watermark mark) throws Exception {
		// 用来处理上游发送过来的watermark，可以认为不做任何处理，下游的watermark只与其上游最近的生成方式相关。
		// if we receive a Long.MAX_VALUE watermark we forward it since it is used
		// to signal the end of input and to not block watermark progress downstream
		// 如果我们收到Long.MAX_VALUE水印，则将其转发,它用于表示输入结束并且不阻止下游的水印进度
		if (mark.getTimestamp() == Long.MAX_VALUE && currentWatermark != Long.MAX_VALUE) {
			currentWatermark = Long.MAX_VALUE;
			output.emitWatermark(mark);
		}
	}

	@Override
	public void close() throws Exception {
		super.close();

		// emit a final watermark
		Watermark newWatermark = userFunction.getCurrentWatermark();
		if (newWatermark != null && newWatermark.getTimestamp() > currentWatermark) {
			currentWatermark = newWatermark.getTimestamp();
			// emit watermark
			output.emitWatermark(newWatermark);
		}
	}
}
