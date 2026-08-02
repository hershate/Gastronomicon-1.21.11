package io.github.schntgaispock.gastronomicon.api.loot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;

import io.github.schntgaispock.gastronomicon.util.collections.Pair;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * This class implements a stabilized Vose's Alias Method taken from
 * https://www.keithschwarz.com/darts-dice-coins/ and first published by M.D.
 * Vose in "A linear algorithm for generating random numbers with a
 * given distribution," in IEEE Transactions on Software Engineering
 */
@ToString(of = "drops")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class LootTable<T> {

    // [perf] drops 改为数组：generate() 直接索引，避免 List.get 的方法派发+边界检查，
    // 且去掉一次 ThreadLocalRandom.current() 与 nextDouble()*totalWeight 的浮点乘。
    // 等价性：nextInt(totalWeight) < prob[roll] 与原 nextDouble()*totalWeight < prob[roll]
    // 在 totalWeight>0 时分布恒等（且为别名法的精确离散形式）。
    private final T[] drops;
    private final int totalWeight;
    private final int[] prob;
    private final int[] alias;

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class LootTableBuilder<T> {

        protected int totalWeight = 0;
        protected final LinkedHashMap<T, Integer> weightedDrops = new LinkedHashMap<>();

        @SafeVarargs
        public final LootTableBuilder<T> add(int weight, T... drops) {
            for (final T drop : drops) {
                // 同一 drop 重复添加时，put 会覆盖旧权重；totalWeight 必须同步抵消旧值，
                // 否则总和与表中权重之和不一致，会破坏别名法的概率分布。
                final Integer previous = weightedDrops.put(drop, weight);
                totalWeight += (previous == null) ? weight : (weight - previous);
            }
            return this;
        }

        @SafeVarargs
        public final LootTableBuilder<T> add(T... drops) {
            return add(1, drops);
        }

        public LootTable<T> build() {
            final int length = weightedDrops.size();

            final Deque<Pair<Integer, Integer>> small = new ArrayDeque<>();
            final Deque<Pair<Integer, Integer>> large = new ArrayDeque<>();
            final int[] prob = new int[length];
            final int[] alias = new int[length];

            int i = 0;
            for (int chance : weightedDrops.values()) {
                chance *= length;
                if (chance < totalWeight) {
                    small.push(Pair.of(chance, i));
                } else {
                    large.push(Pair.of(chance, i));
                }
                i++;
            }
            while (!small.isEmpty() && !large.isEmpty()) {
                final Pair<Integer, Integer> l = small.pop();
                final Pair<Integer, Integer> g = large.pop();
                prob[l.second()] = l.first();
                alias[l.second()] = g.second();
                g.first(g.first() + l.first() - totalWeight);
                if (g.first() < totalWeight) {
                    small.push(g);
                } else {
                    large.push(g);
                }
            }
            while (!large.isEmpty()) {
                prob[large.pop().second()] = totalWeight;
            }
            while (!small.isEmpty()) {
                prob[small.pop().second()] = totalWeight;
            }

            @SuppressWarnings("unchecked")
            final T[] dropsArr = weightedDrops.keySet().toArray((T[]) new Object[weightedDrops.size()]);
            return new LootTable<T>(dropsArr, totalWeight, prob, alias);
        }

    }

    public static <T, U> LootTableBuilder<T> builder(Class<T> type) {
        return new LootTableBuilder<T>();
    }

    public static <T, U> ItemLootTableBuilder builder() {
        return new ItemLootTableBuilder();
    }

    public T generate() {
        final T[] d = drops;
        final int n = d.length;
        if (n == 0) {
            return null;
        }
        final int tw = totalWeight;
        // 退化（全零权重）：旧实现恒走 alias 分支返回 drops[0]，保持一致
        if (tw <= 0) {
            return d[0];
        }
        final ThreadLocalRandom r = ThreadLocalRandom.current();
        final int roll = r.nextInt(n);
        final int p = prob[roll];
        // 满概率桶（prob==totalWeight，别名法对高权重项常见）：原比较 nextDouble()*tw<tw 恒真，
        // 故第二次随机抽取纯冗余，跳过。等价且对偏斜掉落表（渔网/陷阱常见一个主导掉落）收益更大。
        if (p >= tw) {
            return d[roll];
        }
        return r.nextInt(tw) < p ? d[roll] : d[alias[roll]];
    }

    public int size() {
        return drops.length;
    }

    public boolean isEmpty() {
        return drops.length == 0;
    }

}
