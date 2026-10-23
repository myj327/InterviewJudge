package com.decade.doj.submission.mq;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.service.ReviewCompletion;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.web.client.ResourceAccessException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ReviewRequestedListenerTest {
    @Test void commitsReviewBeforeAcknowledging() throws Exception {
        ReviewCompletion completion=mock(ReviewCompletion.class);Channel channel=mock(Channel.class);
        MessageProperties props=new MessageProperties();props.setDeliveryTag(5);
        new ReviewRequestedListener(completion,new ReliableDelivery()).receive(new Message("{\"submissionId\":91}".getBytes(),props),channel);
        var order=inOrder(completion,channel);order.verify(completion).complete(any(ReviewEvidence.class));order.verify(channel).basicAck(5,false);
    }
    @Test void providerTimeoutIsBoundedAndDoesNotAcknowledge() throws Exception {
        ReviewCompletion completion=mock(ReviewCompletion.class);Channel channel=mock(Channel.class);
        doThrow(new ResourceAccessException("synthetic timeout")).when(completion).complete(any());
        MessageProperties props=new MessageProperties();props.setDeliveryTag(5);
        new ReviewRequestedListener(completion,new ReliableDelivery()).receive(new Message("{\"submissionId\":91}".getBytes(),props),channel);
        verify(completion,times(3)).complete(any());verify(channel).basicReject(5,false);verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
}
