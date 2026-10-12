package io.github.noobcodergrowing.jregistrycore;

/**
 * Key 路径规则：{@code /} 是层级分隔符，除它以外的任意字符都合法（{@code .} 只是普通字符）。
 */
public final class KeyPath {

    public static final String SEPARATOR = "/";

    private KeyPath() {
    }

    /**
     * 剥离首尾的 {@value #SEPARATOR} 后按分隔符切分，返回各层级名字。
     * 空 key、全分隔符、或含空段（如 {@code a//b}）都会抛出 {@link IllegalArgumentException}。
     */
    public static String[] split(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }

        int start = 0;
        int end = key.length();
        while (start < end && key.charAt(start) == '/') {
            start++;
        }
        while (end > start && key.charAt(end - 1) == '/') {
            end--;
        }
        if (start == end) {
            throw new IllegalArgumentException("key is required");
        }

        String body = key.substring(start, end);
        if (body.contains("//")) {
            throw new IllegalArgumentException("key contains an empty path segment");
        }
        return body.split(SEPARATOR);
    }

    /** key 是否合法：非空且不存在空段。 */
    public static boolean isValid(String key) {
        try {
            split(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
