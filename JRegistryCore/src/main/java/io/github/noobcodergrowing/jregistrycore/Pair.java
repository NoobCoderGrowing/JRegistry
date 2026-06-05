package io.github.noobcodergrowing.jregistrycore;

import lombok.Data;

@Data
public class Pair<T, U>{

    private T left;
    private U right;

    public Pair(T left, U right) {
        this.left = left;
        this.right = right;
    }
}
