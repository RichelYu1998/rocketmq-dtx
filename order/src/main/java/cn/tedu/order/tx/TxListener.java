package cn.tedu.order.tx;

import cn.tedu.order.entity.Order;
import cn.tedu.order.mapper.TxMapper;
import cn.tedu.order.service.TxOrderService;
import cn.tedu.order.util.JsonUtil;
import com.baomidou.mybatisplus.extension.api.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@RocketMQTransactionListener
public class TxListener implements RocketMQLocalTransactionListener {
    @Resource
    private TxOrderService orderService;
    @Resource
    private TxMapper txMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object o) {
        log.info("事务监听 - 开始执行本地事务");
        String json = new String((byte[]) message.getPayload());
        String xid = JsonUtil.getString(json, "xid");
        log.info("事务监听 - " + json);
        log.info("事务监听 - xid: " + xid);
        RocketMQLocalTransactionState state;
        int status = 0;
        Order order = (Order) o;
        try {
            orderService.doCreate(order, xid);
            log.info("本地事务执行成功，提交消息");
            state = RocketMQLocalTransactionState.COMMIT;
            status = 0;
        } catch (Exception e) {
            e.printStackTrace();
            log.info("本地事务执行失败，回滚消息");
            state = RocketMQLocalTransactionState.ROLLBACK;
            status = 1;
        }
        TxInfo txInfo = new TxInfo(xid, System.currentTimeMillis(), status);
        txMapper.insert(txInfo);
        return state;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        log.info("事务监听 - 回查事务状态");
        String json = new String((byte[]) message.getPayload());
        String xid = JsonUtil.getString(json, "xid");
        TxInfo txInfo = txMapper.selectById(xid);
        if (txInfo == null) {
            log.info("事务监听 - 回查事务状态 - 事务不存在：" + xid);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        log.info("事务监听 - 回查事务状态 - " + txInfo.getStatus());
        switch (txInfo.getStatus()) {
            case 0:
                return RocketMQLocalTransactionState.COMMIT;
            case 1:
                return RocketMQLocalTransactionState.ROLLBACK;
            default:
                return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
