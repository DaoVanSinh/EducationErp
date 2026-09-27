package com.eduerp.integrations.cache;

/**
 * Nơi duy nhất một chuỗi key Redis được lắp.
 *
 * <p>Cố ý không biết namespace nào tồn tại: module sở hữu dữ liệu truyền namespace của mình vào
 * (từ constants riêng của nó). Nhờ vậy tên nghiệp vụ không rò vào tầng hạ tầng, mà việc lắp key
 * vẫn chỉ ở một chỗ — hai yêu cầu tưởng như xung đột.
 */
public final class CacheKeyBuilder {

    private static final String SEPARATOR = ":";
    private static final String WILDCARD = "*";

    private CacheKeyBuilder() {
    }

    public static String key(String namespace, Object identifier) {
        return namespace + SEPARATOR + identifier;
    }

    /** Pattern quét mọi key trong một namespace — dùng cho vận hành và test, thay vì tự nối "*". */
    public static String pattern(String namespace) {
        return namespace + SEPARATOR + WILDCARD;
    }

    /** Lấy lại phần identifier từ một key đầy đủ, nghịch đảo của {@link #key}. */
    public static String identifierIn(String namespace, String key) {
        return key.substring(namespace.length() + SEPARATOR.length());
    }
}
