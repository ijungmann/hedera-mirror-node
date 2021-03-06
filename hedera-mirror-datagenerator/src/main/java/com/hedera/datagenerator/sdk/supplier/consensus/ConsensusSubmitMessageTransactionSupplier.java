package com.hedera.datagenerator.sdk.supplier.consensus;

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

import com.google.common.primitives.Longs;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Builder
@Value
public class ConsensusSubmitMessageTransactionSupplier implements TransactionSupplier<ConsensusMessageSubmitTransaction> {

    private static final List<String> requiredFields = Arrays.asList("topicId");

    //Required
    private final String topicId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000;

    @Builder.Default
    private final String message = StringUtils.EMPTY;

    @Builder.Default
    private final int messageSize = 256;

    @Override
    public ConsensusMessageSubmitTransaction get() {

        if (StringUtils.isBlank(topicId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        return new ConsensusMessageSubmitTransaction()
                .setMaxTransactionFee(maxTransactionFee)
                .setMessage(getMessage())
                .setTopicId(ConsensusTopicId.fromString(topicId))
                .setTransactionMemo(Utility.getMemo("Mirror node submitted test message"));
    }

    private String getMessage() {
        String encodedTimestamp = Utility.getEncodedTimestamp();
        //If a custom message is entered, append the timestamp to the front and leave the message unaltered
        if (StringUtils.isNotBlank(message)) {
            return encodedTimestamp + "_" + message;
        }
        //Generate a message from the timestamp and a random alphanumeric String
        byte[] timeRefBytes = Longs.toByteArray(Instant.now().toEpochMilli());
        int additionalBytes = messageSize <= timeRefBytes.length ? 0 : messageSize - timeRefBytes.length;
        String randomAlphanumeric = RandomStringUtils.randomAlphanumeric(additionalBytes);
        return encodedTimestamp + randomAlphanumeric;
    }
}
