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
package com.hazelcast.simulator.protocol.operation;

import com.hazelcast.simulator.test.TestPhase;

/**
 * Starts a {@link TestPhase} of a Simulator test.
 */
public class StartTestPhaseOperation implements SimulatorOperation {

    private final String testPhase;

    public StartTestPhaseOperation(TestPhase testPhase) {
        this.testPhase = testPhase.name();
    }

    public TestPhase getTestPhase() {
        return TestPhase.valueOf(testPhase);
    }
}
