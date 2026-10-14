package io.github.noobcodergrowing.jregistrycore;

/**
 * 一条日志被 apply 之后的结果。提交循环用它给「需要回包的写」（如 CAS）回话。
 *
 * <p>所有副本对同一条日志得出的是同一个结果——apply 本身是确定性的。
 */
public enum ApplyOutcome {

    /** 产生了变更：写入 / 删除 / 状态翻转都算。 */
    APPLIED,

    /**
     * 条件写的前值与期望不符，<b>没有写入</b>。
     *
     * <p>这不是错误，是 CAS 的正常结果之一（例如并发抢锁时抢输了）。
     */
    CONDITION_FAILED,

    /** 被守卫拒绝，或没有产生任何变更（如删除一个本就不存在的 key）。 */
    REJECTED
}
