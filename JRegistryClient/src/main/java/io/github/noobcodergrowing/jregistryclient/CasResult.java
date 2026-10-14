package io.github.noobcodergrowing.jregistryclient;

/**
 * {@link JRegistryClient#cas} 的结果。
 *
 * <p>之所以是三态而不是 boolean：CAS 走的是共识路径，领导权变更或超时时调用方可能<b>收不到答复</b>，
 * 而"没收到答复"和"确定没写入"是两件性质完全不同的事，混成一个 {@code false} 会让调用方
 * 把一次可能已经生效的写入当成失败。
 */
public enum CasResult {

    /** 前值与期望一致，已写入。 */
    APPLIED,

    /** 前值与期望不符（或期望不存在但已存在），<b>确定没有写入</b>。 */
    NOT_APPLIED,

    /**
     * 超时没收到答复，<b>写入与否未知</b>（可能已提交但回包丢了）。
     *
     * <p>重试是安全的：CAS 会在那时按当前值重新判定。但不要把它当失败来处理。
     */
    UNKNOWN;

    /** 仅当确定写入成功时为 {@code true}。 */
    public boolean isApplied() {
        return this == APPLIED;
    }
}
