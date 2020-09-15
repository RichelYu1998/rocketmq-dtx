package cn.tedu.order.service;

import cn.tedu.order.entity.Order;
import cn.tedu.order.feign.EasyIdGeneratorClient;
import cn.tedu.order.mapper.OrderMapper;
import cn.tedu.order.mapper.TxMapper;
import cn.tedu.order.tx.TxAccountMessage;
import cn.tedu.order.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.UUID;

@Slf4j
@Primary
@Service
public class TxOrderService implements OrderService {
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private TxMapper txMapper;
    @Resource
    EasyIdGeneratorClient easyIdGeneratorClient;

    @Override
    public void create(Order order) {
        String xid = UUID.randomUUID().toString().replace("-", "");
        TxAccountMessage sMsg = new TxAccountMessage(order.getUserId(), order.getMoney(), xid);
        String json  = JsonUtil.to(sMsg);
        Message<String> msg = MessageBuilder.withPayload(json).build();
        rocketMQTemplate.sendMessageInTransaction("order-topic:account",msg,order);
        log.info("事务消息已发送");
    }
    @Transactional
    public void doCreate(Order order,String xid){
        log.info("执行本地事务，保存订单");
        Long orderId = easyIdGeneratorClient.nextId("order_business");
        order.setId(orderId);
        orderMapper.create(order);
        log.info("订单已保存！ 事务日志已保存");
    }
}
