package com.decade.doj.submission.service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Known-pattern redaction only; arbitrary personal data is not safe for real-provider use. */
final class ReviewContentSanitizer {
    private static final Pattern PRIVATE_KEY=Pattern.compile("-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----[\\s\\S]*?-----END (?:[A-Z]+ )?PRIVATE KEY-----");
    private static final String CREDENTIAL_NAME="(?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret)";
    private static final String JAVA_QUOTE=Pattern.quote("\\\"");
    private static final String JAVA_BACKSLASH=Pattern.quote("\\\\");
    // In Java source, JSON quote delimiters have one backslash; escaped value quotes have three.
    // A JSON backslash is four source backslashes. Consume whole escape tokens before the delimiter.
    private static final Pattern JAVA_JSON_CREDENTIAL=Pattern.compile("(?i)("+JAVA_QUOTE+CREDENTIAL_NAME+JAVA_QUOTE+"\\s*:\\s*)"
        +JAVA_QUOTE+"(?:[^\"\\\\]|"+JAVA_BACKSLASH+"(?:"+JAVA_BACKSLASH+"|"+JAVA_QUOTE+"|[^\"\\\\]))*+"+JAVA_QUOTE);
    private static final Pattern CREDENTIAL=Pattern.compile("(?i)([\"']?"+CREDENTIAL_NAME+"[\"']?\\s*[=:]\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[^\\s,;}]+)");
    private static final Pattern KEY=Pattern.compile("(?i)\\bsk-(?:proj-)?[a-z0-9_-]{16,}\\b|\\bBearer\\s+[a-z0-9._/+=-]{16,}");
    private static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ID=Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    static String scrub(String content) {
        if(content==null) return null;
        String result=PRIVATE_KEY.matcher(content).replaceAll("[REDACTED PRIVATE KEY]");
        result=JAVA_JSON_CREDENTIAL.matcher(result).replaceAll("$1"+Matcher.quoteReplacement("\\\"[REDACTED]\\\""));
        result=CREDENTIAL.matcher(result).replaceAll("$1\"[REDACTED]\"");
        for(Pattern pattern:new Pattern[]{KEY,EMAIL,ID,PHONE}) result=pattern.matcher(result).replaceAll("[REDACTED]");
        return result;
    }
}
