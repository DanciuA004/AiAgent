package com.agent.controller;

import com.agent.service.Service;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes the chat service through the application's HTTP API.
 */
@RestController
@RequestMapping("/chat")
public class Controller {

    private final Service service;

    /**
     * Creates the controller with its chat service dependency.
     *
     * @param service service that processes chat messages
     */
    public Controller(Service service) {
        this.service = service;

    }

    /**
     * Accepts a chat message and streams the model's response as server-sent events.
     *
     * @param message message supplied in the request body
     * @return a stream of response text chunks
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody String message) {
        return service.streamChat(message);
    }
}