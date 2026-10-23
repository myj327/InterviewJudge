package com.decade.doj.sandbox.enums;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public enum LanguageEnum {

    PYTHON("python3 %s.py", null, null, "python", "128m", 5, "code-runner-python"),
    JAVA("javac -J-Xmx64m -J-XX:+UseSerialGC -d /work /src/%s.java || exit 2; java -Xmx64m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:-UsePerfData -Xss256k -cp /work %s",
            "javac -J-Xmx64m -J-XX:+UseSerialGC -d /work /src/%s.java",
            "java -Xmx64m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:-UsePerfData -Xss256k -cp /work %s", "java", "128m", 2, "code-runner-java"),
    CPP("sh -c 'g++ -std=c++17 %s.cpp -o %s.out && ./%s.out'", null, null, "cpp", "128m", 1, "code-runner-cpp");

    private final String runCmd;
    private final String compileCmd;
    private final String executeCmd;
    private final String language;
    private final String imageName;

    @Setter
    private String memoryLimit;

    @Setter
    private int timeLimit;

    // 语言名称（小写）到枚举实例的映射
    private static final Map<String, LanguageEnum> NAME_MAP;

    static {
        Map<String, LanguageEnum> map = new HashMap<>();
        for (LanguageEnum le : LanguageEnum.values()) {
            map.put(le.language.toLowerCase(Locale.ROOT), le);
        }
        NAME_MAP = Collections.unmodifiableMap(map);
    }

    LanguageEnum(String runCmd,
                 String compileCmd,
                 String executeCmd,
                 String language,
                 String memoryLimit,
                 int timeLimit,
                 String imageName) {
        this.runCmd = runCmd;
        this.compileCmd = compileCmd;
        this.executeCmd = executeCmd;
        this.language = language;
        this.memoryLimit = memoryLimit;
        this.timeLimit = timeLimit;
        this.imageName = imageName;
    }

    /**
     * 检查给定语言字符串是否不在支持列表中。
     *
     * @param lang 待校验的语言，比如 "java"、"python"、"cpp"
     * @return 如果 lang 不在 NAME_MAP 中，返回 true；否则返回 false
     */
    public static boolean isInValidLanguage(String lang) {
        if (lang == null) {
            return true;
        }
        return !NAME_MAP.containsKey(lang.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 根据语言字符串获取对应的枚举实例，大小写不敏感。
     *
     * @param lang 语言名称，比如 "java"、"python"、"cpp"
     * @return 对应的 LanguageEnum 实例；若找不到则返回 null
     */
    public static LanguageEnum getLanguageEnum(String lang) {
        if (lang == null) {
            return null;
        }
        return NAME_MAP.get(lang.trim().toLowerCase(Locale.ROOT));
    }
}
