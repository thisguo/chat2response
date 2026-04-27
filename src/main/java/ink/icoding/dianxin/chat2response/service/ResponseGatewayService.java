package ink.icoding.dianxin.chat2response.service;

import ink.icoding.dianxin.chat2response.entity.ConversionProject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Responses API 网关服务, 负责将请求转换为 Chat Completions 调用并再转换回 Responses 协议。
 */
@Service
public class ResponseGatewayService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConversionProjectService projectService;
    private final Map<String, List<Map<String, Object>>> responseContextStore = new ConcurrentHashMap<>();

    public ResponseGatewayService(HttpClient httpClient, ObjectMapper objectMapper, ConversionProjectService projectService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
    }

    /**
     * 执行转换调用。
     *
     * @param authHeader     请求头中的 Authorization
     * @param responsesBody  Responses 风格请求体
     * @return Responses 风格响应体
     */
    public Map<String, Object> createResponse(String authHeader, Map<String, Object> responsesBody) {
        String token = extractBearerToken(authHeader);
        String targetModel = asString(responsesBody.get("model"));
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException("model不能为空");
        }

        ConversionProject project = projectService.getEnabledProjectByApiKeyAndModel(token, targetModel);
        Map<String, Object> chatReq = toChatCompletionsRequest(project, responsesBody);

        Map<String, Object> chatResp = invokeChatCompletions(project, chatReq);
        Map<String, Object> responsesResponse = toResponsesResponse(project, responsesBody, chatReq, chatResp);
        rememberResponseContext(responsesResponse, chatReq, responsesResponse.get("output"));
        return responsesResponse;
    }

    private Map<String, Object> toChatCompletionsRequest(ConversionProject project, Map<String, Object> responsesBody) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", project.getSourceModel());

        List<Map<String, Object>> messages = new ArrayList<>();
        Object previousResponseId = responsesBody.get("previous_response_id");
        if (previousResponseId != null) {
            messages.addAll(responseContextStore.getOrDefault(asString(previousResponseId), List.of()));
        }
        messages.addAll(convertInputToMessages(responsesBody.get("input")));
        String instructions = asString(responsesBody.get("instructions"));
        if (instructions != null && !instructions.isBlank()) {
            Map<String, Object> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", instructions);
            messages.add(0, system);
        }
        body.put("messages", messages);

        copyIfPresent(responsesBody, body, "temperature");
        copyIfPresent(responsesBody, body, "top_p");
        copyIfPresent(responsesBody, body, "presence_penalty");
        copyIfPresent(responsesBody, body, "frequency_penalty");
        copyIfPresent(responsesBody, body, "user");

        Object tools = convertResponsesToolsToChatTools(responsesBody.get("tools"));
        if (tools instanceof List<?> toolsList && !toolsList.isEmpty()) {
            body.put("tools", toolsList);
        }
        Object toolChoice = convertResponsesToolChoiceToChatToolChoice(responsesBody.get("tool_choice"));
        if (toolChoice != null) {
            body.put("tool_choice", toolChoice);
        }

        Object maxOutputTokens = responsesBody.get("max_output_tokens");
        if (maxOutputTokens != null) {
            body.put("max_tokens", maxOutputTokens);
        }

        if (Boolean.TRUE.equals(responsesBody.get("stream"))) {
            body.put("stream", true);
        } else {
            body.put("stream", false);
        }
        return body;
    }

    private List<Map<String, Object>> convertInputToMessages(Object input) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (input == null) {
            return messages;
        }

        if (input instanceof String inputText) {
            messages.add(userMessage(inputText));
            return messages;
        }

        if (input instanceof List<?> inputList) {
            for (Object item : inputList) {
                if (item instanceof String text) {
                    messages.add(userMessage(text));
                    continue;
                }
                if (item instanceof Map<?, ?> rawMap) {
                    messages.addAll(convertInputItemToMessages(rawMap));
                }
            }
            return messages;
        }

        throw new IllegalArgumentException("input格式不支持");
    }

    private List<Map<String, Object>> convertInputItemToMessages(Map<?, ?> rawMap) {
        String type = asString(rawMap.get("type"));
        if ("function_call_output".equals(type)) {
            Map<String, Object> toolMessage = new LinkedHashMap<>();
            toolMessage.put("role", "tool");
            toolMessage.put("tool_call_id", rawMap.get("call_id"));
            toolMessage.put("content", stringifyToolOutput(rawMap.get("output")));
            return List.of(toolMessage);
        }
        if ("function_call".equals(type)) {
            Map<String, Object> assistantMessage = new LinkedHashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", null);
            assistantMessage.put("tool_calls", List.of(toChatToolCall(rawMap)));
            return List.of(assistantMessage);
        }
        if ("reasoning".equals(type)) {
            return List.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        String role = asString(rawMap.get("role"));
        normalized.put("role", role == null || role.isBlank() ? "user" : role);
        Object content = rawMap.get("content");
        if (content instanceof List<?> segments) {
            normalized.put("content", mergeSegments(segments));
        } else {
            normalized.put("content", asString(content));
        }
        return List.of(normalized);
    }

    private String mergeSegments(List<?> segments) {
        StringBuilder sb = new StringBuilder();
        for (Object segment : segments) {
            if (segment instanceof String s) {
                sb.append(s);
                continue;
            }
            if (segment instanceof Map<?, ?> part) {
                Object text = part.get("text");
                if (text == null) {
                    text = part.get("refusal");
                }
                if (text != null) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    private Object convertResponsesToolsToChatTools(Object tools) {
        if (!(tools instanceof List<?> toolsList)) {
            return null;
        }
        List<Map<String, Object>> chatTools = new ArrayList<>();
        for (Object item : toolsList) {
            if (!(item instanceof Map<?, ?> tool)) {
                continue;
            }
            if (!"function".equals(asString(tool.get("type")))) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.get("name"));
            if (tool.containsKey("description")) {
                function.put("description", tool.get("description"));
            }
            Object parameters = tool.get("parameters");
            function.put("parameters", parameters == null ? Map.of() : parameters);
            if (tool.containsKey("strict")) {
                function.put("strict", tool.get("strict"));
            }

            Map<String, Object> chatTool = new LinkedHashMap<>();
            chatTool.put("type", "function");
            chatTool.put("function", function);
            chatTools.add(chatTool);
        }
        return chatTools;
    }

    private Object convertResponsesToolChoiceToChatToolChoice(Object toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        if (toolChoice instanceof String choice) {
            if ("auto".equals(choice) || "none".equals(choice) || "required".equals(choice)) {
                return choice;
            }
            return null;
        }
        if (toolChoice instanceof Map<?, ?> choiceMap && "function".equals(asString(choiceMap.get("type")))) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", choiceMap.get("name"));
            Map<String, Object> chatChoice = new LinkedHashMap<>();
            chatChoice.put("type", "function");
            chatChoice.put("function", function);
            return chatChoice;
        }
        return null;
    }

    private Map<String, Object> toChatToolCall(Map<?, ?> functionCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", functionCall.get("name"));
        function.put("arguments", Objects.toString(functionCall.get("arguments"), ""));

        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", functionCall.get("call_id"));
        toolCall.put("type", "function");
        toolCall.put("function", function);
        return toolCall;
    }

    private String stringifyToolOutput(Object output) {
        if (output == null) {
            return "";
        }
        if (output instanceof String text) {
            return text;
        }
        return objectMapper.valueToTree(output).toString();
    }

    private Map<String, Object> invokeChatCompletions(ConversionProject project, Map<String, Object> body) {
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(project.getBaseUrl() + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + project.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return buildErrorResponse(response.statusCode(), response.body());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("调用上游服务失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用被中断", e);
        }
    }

    private Map<String, Object> toResponsesResponse(ConversionProject project, Map<String, Object> responsesBody, Map<String, Object> chatReq, Map<String, Object> chatResponse) {
        if (chatResponse.containsKey("error") && !chatResponse.containsKey("choices")) {
            Map<String, Object> passthrough = new HashMap<>();
            passthrough.put("error", chatResponse.get("error"));
            return passthrough;
        }

        JsonNode root = objectMapper.valueToTree(chatResponse);
        String chatId = root.path("id").asText("chatcmpl_unknown");
        long created = root.has("created") ? root.path("created").asLong() : Instant.now().getEpochSecond();
        JsonNode messageNode = firstChoiceMessage(root);
        String text = extractAssistantText(messageNode);
        List<Map<String, Object>> output = buildResponseOutputFromChatMessage("msg_" + chatId, messageNode, text);

        Map<String, Object> usage = new HashMap<>();
        usage.put("input_tokens", root.path("usage").path("prompt_tokens").asInt(0));
        usage.put("output_tokens", root.path("usage").path("completion_tokens").asInt(0));
        usage.put("output_tokens_details", Map.of("reasoning_tokens", 0));
        usage.put("total_tokens", root.path("usage").path("total_tokens").asInt(0));

        Map<String, Object> responses = buildResponseObject("resp_" + chatId, created, "completed", project.getTargetModel(), responsesBody, output, usage);
        responses.put("output_text", text);
        return responses;
    }

    /**
     * 执行流式转换调用，返回SSE事件流。
     */
    public SseEmitter createStreamingResponse(String authHeader, Map<String, Object> responsesBody) {
        String token = extractBearerToken(authHeader);
        String targetModel = asString(responsesBody.get("model"));
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException("model不能为空");
        }

        ConversionProject project = projectService.getEnabledProjectByApiKeyAndModel(token, targetModel);
        Map<String, Object> chatReq = toChatCompletionsRequest(project, responsesBody);
        chatReq.put("stream", true);

        SseEmitter emitter = new SseEmitter(180_000L);

        new Thread(() -> {
            try {
                processStreamingResponse(project, responsesBody, chatReq, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private void processStreamingResponse(ConversionProject project, Map<String, Object> responsesBody, Map<String, Object> chatReq, SseEmitter emitter) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(chatReq);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(project.getBaseUrl() + "/v1/chat/completions"))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer " + project.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() >= 400) {
            String errorBody = response.body().collect(java.util.stream.Collectors.joining("\n"));
            Map<String, Object> errorResp = buildErrorResponse(response.statusCode(), errorBody);
            emitter.send(SseEmitter.event().name("error").data(errorResp));
            emitter.complete();
            return;
        }

        String chatId = "chatcmpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long created = Instant.now().getEpochSecond();
        StringBuilder accumulatedText = new StringBuilder();
        String responseId = "resp_" + chatId;
        String messageId = "msg_" + chatId;
        StreamState streamState = new StreamState();

        emitEvent(emitter, "response.created", buildResponseCreated(responseId, created, project.getTargetModel(), responsesBody));
        emitEvent(emitter, "response.in_progress", buildResponseInProgress(responseId, created, project.getTargetModel(), responsesBody));

        response.body().forEachOrdered(line -> {
            try {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        return;
                    }
                    JsonNode chunk = objectMapper.readTree(data);
                    String delta = extractDeltaText(chunk);
                    if (delta != null && !delta.isEmpty()) {
                        if (!streamState.messageStarted) {
                            streamState.messageStarted = true;
                            streamState.outputIndex++;
                            emitEvent(emitter, "response.output_item.added", buildOutputItemAdded(messageId, streamState.outputIndex));
                            emitEvent(emitter, "response.content_part.added", buildContentPartAdded(messageId, streamState.outputIndex));
                        }
                        accumulatedText.append(delta);
                        emitEvent(emitter, "response.output_text.delta", buildOutputTextDelta(messageId, streamState.outputIndex, delta));
                    }
                    emitToolCallDeltas(emitter, chunk, streamState);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        String fullText = accumulatedText.toString();
        List<Map<String, Object>> output = new ArrayList<>();

        if (streamState.messageStarted) {
            emitEvent(emitter, "response.output_text.done", buildOutputTextDone(messageId, streamState.outputIndex, fullText));
            emitEvent(emitter, "response.content_part.done", buildContentPartDone(messageId, streamState.outputIndex, fullText));
            emitEvent(emitter, "response.output_item.done", buildOutputItemDone(messageId, streamState.outputIndex, fullText));
            output.add(buildCompletedMessageItem(messageId, fullText));
        }
        for (ToolCallState toolCall : streamState.toolCalls.values()) {
            emitEvent(emitter, "response.function_call_arguments.done", buildFunctionCallArgumentsDone(toolCall));
            emitEvent(emitter, "response.output_item.done", buildFunctionCallOutputItemDone(toolCall));
            output.add(buildFunctionCallItem(toolCall));
        }
        emitEvent(emitter, "response.completed", buildResponseCompleted(responseId, created, project.getTargetModel(), responsesBody, output));
        rememberResponseContext(responseId, chatReq, output);

        emitter.complete();
    }

    private void emitEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        System.out.println("Emitted event: " + eventName + " with data: " + data);
    }

    private Map<String, Object> buildResponseCreated(String responseId, long created, String model, Map<String, Object> responsesBody) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.created");
        event.put("response", buildResponseObject(responseId, created, "in_progress", model, responsesBody, List.of(), null));
        return event;
    }

    private Map<String, Object> buildResponseInProgress(String responseId, long created, String model, Map<String, Object> responsesBody) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.in_progress");
        event.put("response", buildResponseObject(responseId, created, "in_progress", model, responsesBody, List.of(), null));
        return event;
    }

    private Map<String, Object> buildOutputItemAdded(String msgId, int outputIndex) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.output_item.added");
        event.put("output_index", outputIndex);

        Map<String, Object> item = new HashMap<>();
        item.put("id", msgId);
        item.put("type", "message");
        item.put("status", "in_progress");
        item.put("role", "assistant");
        item.put("content", List.of());

        event.put("item", item);
        return event;
    }

    private Map<String, Object> buildContentPartAdded(String msgId, int outputIndex) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.content_part.added");
        event.put("item_id", msgId);
        event.put("output_index", outputIndex);
        event.put("content_index", 0);

        Map<String, Object> part = new HashMap<>();
        part.put("type", "output_text");
        part.put("text", "");
        part.put("annotations", List.of());

        event.put("part", part);
        return event;
    }

    private Map<String, Object> buildOutputTextDelta(String msgId, int outputIndex, String delta) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.output_text.delta");
        event.put("item_id", msgId);
        event.put("output_index", outputIndex);
        event.put("content_index", 0);
        event.put("delta", delta);
        return event;
    }

    private Map<String, Object> buildOutputTextDone(String msgId, int outputIndex, String text) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.output_text.done");
        event.put("item_id", msgId);
        event.put("output_index", outputIndex);
        event.put("content_index", 0);
        event.put("text", text);
        return event;
    }

    private Map<String, Object> buildContentPartDone(String msgId, int outputIndex, String text) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.content_part.done");
        event.put("item_id", msgId);
        event.put("output_index", outputIndex);
        event.put("content_index", 0);

        Map<String, Object> part = new HashMap<>();
        part.put("type", "output_text");
        part.put("text", text);
        part.put("annotations", List.of());

        event.put("part", part);
        return event;
    }

    private Map<String, Object> buildOutputItemDone(String msgId, int outputIndex, String text) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.output_item.done");
        event.put("output_index", outputIndex);

        Map<String, Object> content = new HashMap<>();
        content.put("type", "output_text");
        content.put("text", text);
        content.put("annotations", List.of());

        Map<String, Object> item = new HashMap<>();
        item.put("id", msgId);
        item.put("type", "message");
        item.put("status", "completed");
        item.put("role", "assistant");
        item.put("content", List.of(content));

        event.put("item", item);
        return event;
    }

    private Map<String, Object> buildResponseCompleted(String responseId, long created, String model, Map<String, Object> responsesBody, List<Map<String, Object>> output) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "response.completed");
        event.put("response", buildResponseObject(responseId, created, "completed", model, responsesBody, output, null));
        return event;
    }

    private Map<String, Object> buildResponseObject(String responseId, long created, String status, String model, Map<String, Object> responsesBody, List<Map<String, Object>> output, Map<String, Object> usage) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", responseId);
        resp.put("object", "response");
        resp.put("created_at", created);
        resp.put("status", status);
        resp.put("error", null);
        resp.put("incomplete_details", null);
        resp.put("instructions", responsesBody.getOrDefault("instructions", null));
        resp.put("max_output_tokens", responsesBody.getOrDefault("max_output_tokens", null));
        resp.put("model", model);
        resp.put("output", output);
        resp.put("parallel_tool_calls", responsesBody.getOrDefault("parallel_tool_calls", true));
        resp.put("previous_response_id", responsesBody.getOrDefault("previous_response_id", null));
        resp.put("reasoning", responsesBody.getOrDefault("reasoning", defaultReasoning()));
        resp.put("store", responsesBody.getOrDefault("store", true));
        resp.put("temperature", responsesBody.getOrDefault("temperature", 1.0));
        resp.put("text", responsesBody.getOrDefault("text", defaultTextFormat()));
        resp.put("tool_choice", responsesBody.getOrDefault("tool_choice", "auto"));
        resp.put("tools", responsesBody.getOrDefault("tools", List.of()));
        resp.put("top_p", responsesBody.getOrDefault("top_p", 1.0));
        resp.put("truncation", responsesBody.getOrDefault("truncation", "disabled"));
        resp.put("usage", usage);
        resp.put("user", responsesBody.getOrDefault("user", null));
        resp.put("metadata", responsesBody.getOrDefault("metadata", Map.of()));
        return resp;
    }

    private List<Map<String, Object>> buildResponseOutputFromChatMessage(String messageId, JsonNode messageNode, String text) {
        List<Map<String, Object>> output = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            output.add(buildCompletedMessageItem(messageId, text));
        }
        JsonNode toolCalls = messageNode.path("tool_calls");
        if (toolCalls.isArray()) {
            AtomicInteger index = new AtomicInteger(output.size());
            toolCalls.forEach(toolCall -> output.add(buildFunctionCallItem(toolCall, index.getAndIncrement())));
        }
        return output;
    }

    private Map<String, Object> buildCompletedMessageItem(String messageId, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "output_text");
        content.put("text", text);
        content.put("annotations", List.of());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("type", "message");
        message.put("status", "completed");
        message.put("role", "assistant");
        message.put("content", List.of(content));
        return message;
    }

    private Map<String, Object> buildFunctionCallItem(JsonNode toolCall, int outputIndex) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "fc_" + safeId(toolCall.path("id").asText("call_" + outputIndex)));
        item.put("type", "function_call");
        item.put("status", "completed");
        item.put("arguments", toolCall.path("function").path("arguments").asText(""));
        item.put("call_id", toolCall.path("id").asText("call_" + outputIndex));
        item.put("name", toolCall.path("function").path("name").asText(""));
        return item;
    }

    private Map<String, Object> buildFunctionCallItem(ToolCallState toolCall) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", toolCall.itemId);
        item.put("type", "function_call");
        item.put("status", "completed");
        item.put("arguments", toolCall.arguments.toString());
        item.put("call_id", toolCall.callId);
        item.put("name", toolCall.name);
        return item;
    }

    private void emitToolCallDeltas(SseEmitter emitter, JsonNode chunk, StreamState streamState) throws IOException {
        JsonNode toolCalls = chunk.path("choices").path(0).path("delta").path("tool_calls");
        if (!toolCalls.isArray()) {
            return;
        }
        for (JsonNode toolCallDelta : toolCalls) {
            int chatIndex = toolCallDelta.path("index").asInt(0);
            ToolCallState toolCall = streamState.toolCalls.get(chatIndex);
            if (toolCall == null) {
                toolCall = new ToolCallState();
                toolCall.outputIndex = ++streamState.outputIndex;
                toolCall.callId = toolCallDelta.path("id").asText("call_" + UUID.randomUUID().toString().replace("-", ""));
                toolCall.itemId = "fc_" + safeId(toolCall.callId);
                streamState.toolCalls.put(chatIndex, toolCall);
                emitEvent(emitter, "response.output_item.added", buildFunctionCallOutputItemAdded(toolCall));
            }

            JsonNode function = toolCallDelta.path("function");
            if (function.hasNonNull("name")) {
                toolCall.name = function.path("name").asText("");
            }
            if (function.hasNonNull("arguments")) {
                String delta = function.path("arguments").asText("");
                if (!delta.isEmpty()) {
                    toolCall.arguments.append(delta);
                    emitEvent(emitter, "response.function_call_arguments.delta", buildFunctionCallArgumentsDelta(toolCall, delta));
                }
            }
        }
    }

    private Map<String, Object> buildFunctionCallOutputItemAdded(ToolCallState toolCall) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "response.output_item.added");
        event.put("output_index", toolCall.outputIndex);
        event.put("item", buildFunctionCallItem(toolCall));
        return event;
    }

    private Map<String, Object> buildFunctionCallArgumentsDelta(ToolCallState toolCall, String delta) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "response.function_call_arguments.delta");
        event.put("item_id", toolCall.itemId);
        event.put("output_index", toolCall.outputIndex);
        event.put("delta", delta);
        return event;
    }

    private Map<String, Object> buildFunctionCallArgumentsDone(ToolCallState toolCall) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "response.function_call_arguments.done");
        event.put("item_id", toolCall.itemId);
        event.put("output_index", toolCall.outputIndex);
        event.put("arguments", toolCall.arguments.toString());
        return event;
    }

    private Map<String, Object> buildFunctionCallOutputItemDone(ToolCallState toolCall) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "response.output_item.done");
        event.put("output_index", toolCall.outputIndex);
        event.put("item", buildFunctionCallItem(toolCall));
        return event;
    }

    private Map<String, Object> defaultReasoning() {
        Map<String, Object> reasoning = new HashMap<>();
        reasoning.put("effort", null);
        reasoning.put("summary", null);
        return reasoning;
    }

    private Map<String, Object> defaultTextFormat() {
        return Map.of("format", Map.of("type", "text"));
    }

    private String extractDeltaText(JsonNode chunk) {
        JsonNode choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("delta").path("content").asText(null);
    }

    private void rememberResponseContext(Map<String, Object> response, Map<String, Object> chatReq, Object output) {
        rememberResponseContext(asString(response.get("id")), chatReq, output);
    }

    private void rememberResponseContext(String responseId, Map<String, Object> chatReq, Object output) {
        if (responseId == null || responseId.isBlank()) {
            return;
        }
        List<Map<String, Object>> context = new ArrayList<>();
        Object messages = chatReq.get("messages");
        if (messages instanceof List<?> messageList) {
            for (Object message : messageList) {
                if (message instanceof Map<?, ?> messageMap) {
                    context.add(toStringObjectMap(messageMap));
                }
            }
        }
        context.addAll(convertOutputToMessages(output));
        responseContextStore.put(responseId, context);
    }

    private List<Map<String, Object>> convertOutputToMessages(Object output) {
        if (!(output instanceof List<?> outputList)) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> pendingToolCalls = new ArrayList<>();
        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> outputItem)) {
                continue;
            }
            String type = asString(outputItem.get("type"));
            if ("message".equals(type)) {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("role", "assistant");
                message.put("content", mergeSegments(asList(outputItem.get("content"))));
                messages.add(message);
            } else if ("function_call".equals(type)) {
                pendingToolCalls.add(toChatToolCall(outputItem));
            }
        }
        if (!pendingToolCalls.isEmpty()) {
            Map<String, Object> assistantToolMessage = new LinkedHashMap<>();
            assistantToolMessage.put("role", "assistant");
            assistantToolMessage.put("content", null);
            assistantToolMessage.put("tool_calls", pendingToolCalls);
            messages.add(assistantToolMessage);
        }
        return messages;
    }

    private JsonNode firstChoiceMessage(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        return choices.get(0).path("message");
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization必须是Bearer Token");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Bearer Token不能为空");
        }
        return token;
    }

    private String extractAssistantText(JsonNode messageNode) {
        JsonNode content = messageNode.path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            content.forEach(part -> {
                if (part.hasNonNull("text")) {
                    text.append(part.path("text").asText());
                }
            });
            return text.toString();
        }
        return "";
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(asString(key), value));
        return target;
    }

    private String safeId(String id) {
        String safe = id == null ? UUID.randomUUID().toString() : id;
        return safe.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private Map<String, Object> userMessage(String text) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", text);
        return msg;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String asString(Object value) {
        return Objects.toString(value, null);
    }

    private Map<String, Object> buildErrorResponse(int statusCode, String body) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "upstream_error");
        detail.put("code", statusCode);
        detail.put("message", body);
        error.put("error", detail);
        return error;
    }

    private static class StreamState {
        private boolean messageStarted;
        private int outputIndex = -1;
        private final Map<Integer, ToolCallState> toolCalls = new LinkedHashMap<>();
    }

    private static class ToolCallState {
        private int outputIndex;
        private String itemId;
        private String callId;
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }
}
