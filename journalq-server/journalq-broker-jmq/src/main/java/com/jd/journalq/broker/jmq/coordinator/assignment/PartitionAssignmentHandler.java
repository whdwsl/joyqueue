package com.jd.journalq.broker.jmq.coordinator.assignment;

import com.google.common.collect.Sets;
import com.jd.journalq.broker.jmq.config.JMQConfig;
import com.jd.journalq.broker.jmq.coordinator.GroupMetadataManager;
import com.jd.journalq.broker.jmq.coordinator.assignment.delay.MemberTimeoutDelayedOperation;
import com.jd.journalq.broker.jmq.coordinator.domain.GroupMemberMetadata;
import com.jd.journalq.broker.jmq.coordinator.domain.GroupMetadata;
import com.jd.journalq.broker.jmq.coordinator.domain.PartitionAssignment;
import com.jd.journalq.broker.jmq.exception.JMQException;
import com.jd.journalq.domain.PartitionGroup;
import com.jd.journalq.exception.JMQCode;
import com.jd.journalq.toolkit.delay.DelayedOperationKey;
import com.jd.journalq.toolkit.delay.DelayedOperationManager;
import com.jd.journalq.toolkit.service.Service;
import com.jd.journalq.toolkit.time.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PartitionAssignmentHandler
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/5
 */
public class PartitionAssignmentHandler extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(PartitionAssignmentHandler.class);

    private JMQConfig config;
    private GroupMetadataManager coordinatorGroupManager;

    private PartitionAssignorResolver partitionAssignorResolver;
    private DelayedOperationManager memberTimeoutDelayedOperationManager;

    public PartitionAssignmentHandler(JMQConfig config, GroupMetadataManager coordinatorGroupManager) {
        this.config = config;
        this.coordinatorGroupManager = coordinatorGroupManager;
    }

    public PartitionAssignment assign(String topic, String app, String connectionId, String connectionHost, int sessionTimeout, List<PartitionGroup> partitionGroups) {
        GroupMetadata group = coordinatorGroupManager.getGroup(app);
        if (group == null) {
            group = coordinatorGroupManager.getOrCreateGroup(new GroupMetadata(app));
        }
        synchronized (group) {
            return doAssign(group, topic, connectionId, connectionHost, sessionTimeout, partitionGroups);
        }
    }

    protected PartitionAssignment doAssign(GroupMetadata group, String topic, String connectionId, String connectionHost, int sessionTimeout, List<PartitionGroup> partitionGroups) {
        GroupMemberMetadata member = (GroupMemberMetadata) group.getMembers().get(connectionId);
        DelayedOperationKey memberTimeoutDelayedOperationKey = new DelayedOperationKey(connectionId);

        if (member == null) {
            member = new GroupMemberMetadata(connectionId, group.getId(), connectionId, connectionHost, sessionTimeout);
            group.addMember(member);
        } else {
            memberTimeoutDelayedOperationManager.checkAndComplete(memberTimeoutDelayedOperationKey);
        }

        PartitionAssignment assignment = partitionAssignorResolver.assign(group, member, topic, partitionGroups);
        if (assignment == null) {
            throw new JMQException(JMQCode.FW_COORDINATOR_PARTITION_ASSIGNOR_ERROR.getCode());
        }

        member.setLatestHeartbeat(SystemClock.now());
        member.setAssignedTopicPartitions(topic, assignment.getPartitions());

        memberTimeoutDelayedOperationManager.tryCompleteElseWatch(new MemberTimeoutDelayedOperation(group, member,sessionTimeout + config.getCoordinatorPartitionAssignTimeoutOverflow()),
                Sets.newHashSet(memberTimeoutDelayedOperationKey));
        return assignment;
    }

    @Override
    protected void validate() throws Exception {
        partitionAssignorResolver = new PartitionAssignorResolver(config);
        memberTimeoutDelayedOperationManager = new DelayedOperationManager("jmqMemberTimeout");
    }

    @Override
    protected void doStart() throws Exception {
        memberTimeoutDelayedOperationManager.start();
    }

    @Override
    protected void doStop() {
        if (memberTimeoutDelayedOperationManager != null) {
            memberTimeoutDelayedOperationManager.shutdown();
        }
    }
}