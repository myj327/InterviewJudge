package com.decade.doj.submission.config;

import com.decade.doj.submission.websocket.SubmissionWSServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.websocket.server.ServerEndpointConfig;

@Configuration
public class WebSocketConfig {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public ServerEndpointConfig submissionEndpoint(SubmissionWSServer endpoint) {
        return ServerEndpointConfig.Builder.create(SubmissionWSServer.class, "/ws/submission")
                .configurator(new ServerEndpointConfig.Configurator() {
                    @Override
                    public <T> T getEndpointInstance(Class<T> endpointClass) {
                        return endpointClass.cast(endpoint);
                    }
                }).build();
    }
}
