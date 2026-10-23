package com.decade.doj.submission.review;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.io.IOException;
import java.util.*;
@Component
@ConditionalOnProperty(name="doj.review.provider", havingValue="openai-compatible")
public class OpenAiCompatibleReviewProvider implements ReviewProvider {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> FIELDS = Set.of("summary","strengths","risks","suggestions");
    private final RestTemplate rest;
    private final String url, model, key;
    private final int maxTokens;
    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiCompatibleReviewProvider(
            @Value("${doj.review.openai-compatible.base-url}") String url,
            @Value("${doj.review.openai-compatible.model}") String model,
            @Value("${doj.review.openai-compatible.timeout-ms}") int timeout,
            @Value("${doj.review.openai-compatible.max-output-tokens}") int tokens,
            @Value("${doj.review.openai-compatible.api-key}") String key) {
        this(client(timeout),url,model,tokens,key);
    }
    OpenAiCompatibleReviewProvider(RestTemplate rest,String url,String model,int tokens,String key) {
        if (url==null || url.isBlank() || model==null || model.isBlank() || key==null || key.isBlank()
                || tokens<1 || tokens>1000) throw new IllegalArgumentException("Invalid provider configuration");
        this.rest=rest; this.url=url; this.model=model; this.maxTokens=tokens; this.key=key;
        // Handle status without accumulating or exposing an error response body.
        rest.setErrorHandler(new ResponseErrorHandler() {
            public boolean hasError(ClientHttpResponse response) { return false; }
            public void handleError(ClientHttpResponse response) { }
        });
    }
    @Override public ReviewReport review(ReviewEvidence evidence) {
        if (evidence==null || evidence.getSubmissionId()==null || !"1".equals(evidence.getSchemaVersion()))
            throw new IllegalArgumentException("Invalid review evidence");
        JsonNode response=rest.execute(url,HttpMethod.POST,request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.getHeaders().setBearerAuth(key);
            JSON.writeValue(request.getBody(),body(evidence));
        }, reply -> {
            int status=reply.getRawStatusCode();
            if (status==429 || status>=500) throw new ResourceAccessException("Provider temporarily unavailable");
            if (status!=200) throw new IllegalArgumentException("Provider rejected request");
            byte[] bytes=reply.getBody().readNBytes(65537);
            if (bytes.length>65536) throw new IllegalArgumentException("Provider response limit exceeded");
            try { return JSON.readTree(bytes); }
            catch (IOException malformed) { throw new IllegalArgumentException("Invalid provider JSON"); }
        });
        JsonNode choices=response==null?null:response.get("choices");
        if (choices==null || !choices.isArray() || choices.size()!=1)
            throw new IllegalArgumentException("Invalid provider choices");
        JsonNode content=choices.get(0).path("message").path("content");
        if (!content.isTextual()) throw new IllegalArgumentException("Invalid provider content");
        JsonNode report;
        try { report=JSON.readTree(content.textValue()); }
        catch (IOException malformed) { throw new IllegalArgumentException("Invalid provider JSON"); }
        Set<String> fields=new HashSet<>();
        if (report==null || !report.isObject()) throw new IllegalArgumentException("Invalid provider report");
        report.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(FIELDS)) throw new IllegalArgumentException("Invalid provider report fields");
        return new ReviewReport().setSchemaVersion("1").setPromptVersion("r1-v1").setModel(model)
                .setRubricVersion("recruitment-v1").setSubmissionId(evidence.getSubmissionId()).setStatus("SUCCEEDED")
                .setSummary(text(report.get("summary"))).setStrengths(texts(report.get("strengths")))
                .setRisks(texts(report.get("risks"))).setSuggestions(texts(report.get("suggestions")));
    }
    private Map<String,Object> body(ReviewEvidence evidence) throws IOException {
        return Map.of("model",model,"temperature",0,"max_tokens",maxTokens,
                "stream",false,"thinking",Map.of("type","disabled"),
                "response_format",Map.of("type","json_object"),"messages",List.of(
                Map.of("role","system","content","Return JSON with summary, strengths, risks, suggestions."),
                Map.of("role","user","content",JSON.writeValueAsString(evidence))));
    }
    private String text(JsonNode value) {
        if (value==null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException("Invalid provider report text");
        return value.textValue();
    }
    private List<String> texts(JsonNode values) {
        if (values==null || !values.isArray() || values.isEmpty())
            throw new IllegalArgumentException("Invalid provider report list");
        List<String> result=new ArrayList<>(); values.forEach(value -> result.add(text(value))); return result;
    }
    private static RestTemplate client(int timeout) {
        if (timeout<1 || timeout>30000) throw new IllegalArgumentException("Invalid provider timeout");
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout); factory.setReadTimeout(timeout); return new RestTemplate(factory);
    }
}
