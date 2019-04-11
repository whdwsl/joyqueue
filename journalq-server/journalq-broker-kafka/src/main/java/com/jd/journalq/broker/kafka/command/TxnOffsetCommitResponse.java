package com.jd.journalq.broker.kafka.command;

import com.jd.journalq.broker.kafka.KafkaCommandType;
import com.jd.journalq.broker.kafka.model.PartitionMetadataAndError;

import java.util.List;
import java.util.Map;

/**
 * TxnOffsetCommitRequest
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/4/4
 */
public class TxnOffsetCommitResponse extends KafkaRequestOrResponse {

    private Map<String, List<PartitionMetadataAndError>> partitions;

    public Map<String, List<PartitionMetadataAndError>> getPartitions() {
        return partitions;
    }

    public void setPartitions(Map<String, List<PartitionMetadataAndError>> partitions) {
        this.partitions = partitions;
    }

    @Override
    public int type() {
        return KafkaCommandType.TXN_OFFSET_COMMIT.getCode();
    }
}
