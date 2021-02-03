/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.PublicEvolving;

/**
 * Defines the chaining scheme for the operator. When an operator is chained to the
 * predecessor, it means that they run in the same thread. They become one operator
 * consisting of multiple steps.
 * <p>定义操作符的链接模式。当一个操作符被链接到前一个操作符时，
 * 这意味着它们在同一个线程中运行。它们成为一个由多个步骤组成的操作符。</p>
 * <p>The default value used by the StreamOperator is {@link #HEAD}, which means that
 * the operator is not chained to its predecessor. Most operators override this with
 * {@link #ALWAYS}, meaning they will be chained to predecessors whenever possible.
 * <p>
 *     StreamOperator使用的默认值是HEAD，这意味着该操作符没有链接到其前身。
 *     大多数操作符都用ALWAYS覆盖了这个值，这意味着它们将尽可能地与前辈连接在一起。
 * </p>
 *
 */
@PublicEvolving
public enum ChainingStrategy {

	/**
	 * Operators will be eagerly chained whenever possible.
	 *
	 * <p>To optimize performance, it is generally a good practice to allow maximal
	 * chaining and increase operator parallelism.
	 */
	ALWAYS,

	/**
	 * The operator will not be chained to the preceding or succeeding operators.
	 */
	// 操作符不会被链接到前面或后面的操作符。
	NEVER,

	/**
	 * The operator will not be chained to the predecessor, but successors may chain to this
	 * operator.
	 */
	HEAD
}
