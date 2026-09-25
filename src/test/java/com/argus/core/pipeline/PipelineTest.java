package com.argus.core.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest {

    private CancellationToken token = new CancellationToken();

    @Test
    void chainPropagatesItemsThenPoisonAndDrains() throws Exception {
        BlockingQueue<Object> raw = new LinkedBlockingQueue<>();
        BlockingQueue<Object> loud = new LinkedBlockingQueue<>();
        List<String> got = new CopyOnWriteArrayList<>();

        Pump rawPump = new Pump(raw, loud, token, 1);
        Pump loudPump = new Pump(loud, null, token, 1);

        Stage source = Stage.runner(Stage.executor("t-source", 1), 1, () -> {
            raw.addAll(List.of("a", "b", "c"));
            raw.add(Pump.POISON);
        }, raw);
        Stage middle = Stage.runner(Stage.executor("t-middle", 1), 1,
                () -> rawPump.run(item -> loud.add(Pump.<String>payload(item).toUpperCase())),
                raw, loud);
        Stage tail = Stage.runner(Stage.executor("t-tail", 1), 1,
                () -> loudPump.run(item -> got.add(Pump.payload(item))),
                loud);

        try (Pipeline p = new Pipeline(token, source, middle, tail)) {
            p.start();
            assertTrue(p.await(2), "pipeline never drained");
            assertEquals(List.of("A", "B", "C"), got);
        }
    }

    @Test
    void parallelWorkersDrainAllItemsExactlyOnce() throws Exception {
        BlockingQueue<Object> in = new LinkedBlockingQueue<>();
        List<Integer> got = new CopyOnWriteArrayList<>();
        int total = 200;
        Pump pump = new Pump(in, null, token, 8);

        Stage source = Stage.runner(Stage.executor("t-src2", 1), 1, () -> {
            for (int i = 0; i < total; i++) {
                in.add(i);
            }
            in.add(Pump.POISON);
        }, in);
        Stage sink = Stage.runner(Stage.executor("t-parallel", 8), 8,
                () -> pump.run(item -> got.add(Pump.payload(item))), in);

        try (Pipeline p = new Pipeline(token, source, sink)) {
            p.start();
            assertTrue(p.await(2), "parallel stage never drained");
            assertEquals(total, got.size());
            assertEquals(total, got.stream().distinct().count(), "no item processed twice");
        }
    }

    @Test
    void cancelInterruptsBlockedWorkersAndDrainsQueues() throws Exception {
        BlockingQueue<Object> in = new LinkedBlockingQueue<>();
        Pump pump = new Pump(in, null, token, 2);

        // two workers park in take() with items still queued upstream
        Stage tail = Stage.runner(Stage.executor("t-blocked", 2), 2,
                () -> pump.run(item -> {
                }), in);
        in.add("late-1");
        in.add("late-2");

        Pipeline p = new Pipeline(token, tail);
        p.start();
        Thread.sleep(50); // let workers park in take()

        long start = System.nanoTime();
        p.cancel();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        p.close();

        assertTrue(elapsedMs < 1000, "cancel took " + elapsedMs + " ms");
        assertTrue(token.isCancelled());
        assertTrue(in.isEmpty(), "queues must be drained on cancel");
    }
}
