package com.sankuai.inf.leaf.snowflake;

import com.google.common.base.Preconditions;
import com.sankuai.inf.leaf.IDGen;
import com.sankuai.inf.leaf.common.Result;
import com.sankuai.inf.leaf.common.Status;
import com.sankuai.inf.leaf.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * todo：二进制为31位1，整数2147483647, 时间为：2038-01-19 11:14:07的时间戳
 * todo：二进制为41位1，41-bit的时间可以表示（1L<<41）/(1000L*3600*24*365)近69年的时间
 * @link <https://tech.meituan.com/2017/04/21/mt-leaf.html/>
 */
public class SnowflakeIDGenImpl implements IDGen {

    @Override
    public boolean init() {
        return true;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeIDGenImpl.class);

    private final long twepoch;
    private final long workerIdBits = 10L;
    private final long maxWorkerId = ~(-1L << workerIdBits);//最大能够分配的workerid =1023
    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits;
    private final long sequenceMask = ~(-1L << sequenceBits);
    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    private static final Random RANDOM = new Random();

    public SnowflakeIDGenImpl(String zkAddress, int port) {
        //todo：Thu Nov 04 2010 09:42:54 GMT+0800 (中国标准时间)
        this(zkAddress, port, 1288834974657L);
    }

    /**
     * @param zkAddress zk地址
     * @param port      snowflake监听端口
     * @param twepoch   起始的时间戳
     */
    public SnowflakeIDGenImpl(String zkAddress, int port, long twepoch) {
        this.twepoch = twepoch;
        Preconditions.checkArgument(timeGen() > twepoch, "Snowflake not support twepoch gt currentTime");
        final String ip = Utils.getIp();
        SnowflakeZookeeperHolder holder = new SnowflakeZookeeperHolder(ip, String.valueOf(port), zkAddress);
        LOGGER.info("twepoch:{} ,ip:{} ,zkAddress:{} port:{}", twepoch, ip, zkAddress, port);
        boolean initFlag = holder.init();
        if (initFlag) {
            workerId = holder.getWorkerID();
            LOGGER.info("START SUCCESS USE ZK WORKERID-{}", workerId);
        } else {
            Preconditions.checkArgument(initFlag, "Snowflake Id Gen is not init ok");
        }
        Preconditions.checkArgument(workerId >= 0 && workerId <= maxWorkerId, "workerID must gte 0 and lte 1023");
    }

    /* synchronized 同步锁*/
    @Override
    public synchronized Result get(String key) {
        long timestamp = timeGen();
        /**
         * todo：由于强依赖时钟，对时间的要求比较敏感，在机器工作时NTP同步也会造成秒级别的回退，建议可以直接关闭NTP同步。要么在时钟回拨的时候直接不提供服务直接返回ERROR_CODE，等时钟追上即可。或者做一层重试，然后上报报警系统，更或者是发现有时钟回拨之后自动摘除本身节点并报警，如下：
         * todo：2017闰秒回拨，回拨一秒，这个时间不可用
         */
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        return new Result(-1, Status.EXCEPTION);
                    }
                } catch (InterruptedException e) {
                    LOGGER.error("wait interrupted");
                    return new Result(-2, Status.EXCEPTION);
                }
            } else {
                return new Result(-3, Status.EXCEPTION);
            }
        }
        /**
         * todo：同一毫秒内可以并发4095个，一秒内4.095百万个
         */
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                //todo:seq 为0的时候表示是下一毫秒时间开始对seq做随机
                sequence = RANDOM.nextInt(100);
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            //如果是新的ms开始
            sequence = RANDOM.nextInt(100);
        }
        lastTimestamp = timestamp;
        /**
         * todo：id长度为时间戳13位整数( < 41bit)  + 22bit <= 64 bit
         */
        long id = ((timestamp - twepoch) << timestampLeftShift) | (workerId << workerIdShift) | sequence;
        return new Result(id, Status.SUCCESS);

    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }

    public long getWorkerId() {
        return workerId;
    }

}
