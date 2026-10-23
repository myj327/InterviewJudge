package com.decade.doj.submission.mq;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.service.VerdictCompletion;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class ResultListenerTest {
    @Test void storageErrorsAreNotSwallowedAsSuccessfulConsumption() {
        VerdictCompletion completion=mock(VerdictCompletion.class);
        doThrow(new IllegalStateException("synthetic storage error")).when(completion).complete(any());
        assertThrows(IllegalStateException.class,()->new ResultListener(completion,new ReliableDelivery()).onMessage(Map.of()));
    }
    @Test void malformedMessageIsRejectedWithoutWritingOrAcknowledging() throws Exception {
        VerdictCompletion completion=mock(VerdictCompletion.class);Channel channel=mock(Channel.class);
        MessageProperties props=new MessageProperties();props.setDeliveryTag(4);
        new ResultListener(completion,new ReliableDelivery()).receive(new Message("not-json".getBytes(),props),channel);
        verifyNoInteractions(completion);verify(channel).basicReject(4,false);verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
}
