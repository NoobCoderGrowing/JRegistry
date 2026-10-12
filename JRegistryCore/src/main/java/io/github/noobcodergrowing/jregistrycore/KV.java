package io.github.noobcodergrowing.jregistrycore;

import lombok.Data;

/**
 * 一个 key/value 对：{@code key} 是状态机中的绝对完整路径（不含 {@code root} 前缀，
 * 形如 {@code app/config/name}，可直接再传给 {@code get}），{@code data} 与 {@code dataType} 对应该节点上存的值及其类型。
 */
@Data
public class KV {

    private String key;
    private byte[] data;
    private String dataType;

    public KV() {
    }

    public KV(String key, byte[] data, String dataType) {
        this.key = key;
        this.data = data;
        this.dataType = dataType;
    }
}
