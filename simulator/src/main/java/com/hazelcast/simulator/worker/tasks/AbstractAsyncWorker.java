/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.worker.tasks;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.simulator.utils.ExceptionReporter;
import com.hazelcast.simulator.worker.selector.OperationSelectorBuilder;

import static com.hazelcast.simulator.utils.CommonUtils.rethrow;

/**
 * Asynchronous version of {@link AbstractWorker}.
 *
 * The operation counter is automatically increased after call of {@link ExecutionCallback#onResponse}.
 * The throwable is automatically reported after call of {@link ExecutionCallback#onFailure(Throwable)}
 *
 * @param <O> Type of Enum used by the {@link com.hazelcast.simulator.worker.selector.OperationSelector}
 * @param <V> Type of {@link ExecutionCallback}
 */
public abstract class AbstractAsyncWorker<O extends Enum<O>, V> extends AbstractWorker<O> implements ExecutionCallback<V> {

    public AbstractAsyncWorker(OperationSelectorBuilder<O> operationSelectorBuilder) {
        super(operationSelectorBuilder);
    }

    @Override
    public final void run() {
        while (!testContext.isStopped() && !isWorkerStopped) {
            try {
                timeStep(selector.select());
            } catch (Exception e) {
                throw rethrow(e);
            }
        }
    }

    @Override
    public final void onResponse(V response) {
        workerProbe.done();
        increaseIteration();
        handleResponse(response);
    }

    @Override
    public final void onFailure(Throwable t) {
        ExceptionReporter.report(testContext.getTestId(), t);
        handleFailure(t);
    }

    /**
     * Override this method if you need to execute code on each worker after the iteration has been increased in
     * {@link ExecutionCallback#onResponse(Object)}.
     *
     * @param response the result of the successful execution
     */
    @SuppressWarnings("unused")
    protected void handleResponse(V response) {
    }

    /**
     * Override this method if you need to execute code on each worker after the throwable has been reported in
     * {@link ExecutionCallback#onFailure(Throwable)}.
     *
     * @param t the exception that is thrown
     */
    @SuppressWarnings("unused")
    protected void handleFailure(Throwable t) {
    }
}
