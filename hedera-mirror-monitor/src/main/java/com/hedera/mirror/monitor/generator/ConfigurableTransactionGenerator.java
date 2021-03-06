package com.hedera.mirror.monitor.generator;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
public class ConfigurableTransactionGenerator implements TransactionGenerator {

    private final ScenarioProperties properties;
    private final TransactionSupplier<?> transactionSupplier;
    private final RateLimiter rateLimiter;
    private final AtomicLong remaining;
    private final long stopTime;
    private final PublishRequest.PublishRequestBuilder builder;

    public ConfigurableTransactionGenerator(ScenarioProperties properties) {
        this.properties = properties;
        this.transactionSupplier = convert(properties);
        this.rateLimiter = RateLimiter.create(properties.getTps());
        remaining = new AtomicLong(properties.getLimit() > 0 ? properties.getLimit() : Long.MAX_VALUE);
        stopTime = System.nanoTime() + properties.getDuration().toNanos();
        builder = PublishRequest.builder()
                .record(properties.isRecord())
                .receipt(properties.isReceipt())
                .type(properties.getType());
        log.info("Initializing scenario: {}", properties);
    }

    @Override
    public PublishRequest next() {
        if (remaining.getAndDecrement() <= 0) {
            throw new ScenarioException("Reached publish limit of " + properties.getLimit());
        }

        if (stopTime - System.nanoTime() <= 0) {
            throw new ScenarioException("Reached publish duration of " + properties.getDuration());
        }

        rateLimiter.acquire();
        return builder.transactionBuilder(transactionSupplier.get()).build();
    }

    private TransactionSupplier<?> convert(ScenarioProperties p) {
        return new ObjectMapper().convertValue(p.getProperties(), p.getType().getSupplier());
    }
}
