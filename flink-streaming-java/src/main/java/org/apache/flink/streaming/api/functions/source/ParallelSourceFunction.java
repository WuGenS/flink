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

package org.apache.flink.streaming.api.functions.source;

import org.apache.flink.annotation.Public;

/**
 * A stream data source that is executed in parallel. Upon execution, the runtime will
 * execute as many parallel instances of this function as configured parallelism
 * of the source.
 * 并行执行的一种流数据源。在执行时，运行时将执行这个函数的并行实例数量与源程序配置的并行数量相同。
 * <p>This interface acts only as a marker to tell the system that this source may
 * be executed in parallel. When different parallel instances are required to perform
 * different tasks, use the {@link RichParallelSourceFunction} to get access to the runtime
 * context, which reveals information like the number of parallel tasks, and which parallel
 * task the current instance is.
 * <p>这个接口仅作为一个标记，告诉系统这个源可以并行执行。
 * 当需要不同的并行实例来执行不同的任务时，使用RichParallelSourceFunction访问运行时上下文，
 * 该上下文显示并行任务的数量以及当前实例是哪个并行任务等信息。</p>
 * @param <OUT> The type of the records produced by this source.
 */
@Public
public interface ParallelSourceFunction<OUT> extends SourceFunction<OUT> {
}
