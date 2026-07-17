package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.core.Ordered;
import java.util.Comparator;
import java.util.List;

final class TaskRealtimeOrder {
    private TaskRealtimeOrder() {
    }

    static <T> void sort(List<T> values) {
        values.sort(Comparator.comparingInt(value -> ((Ordered) value).getOrder()));
    }

    static int value(Object candidate) {
        return ((Ordered) candidate).getOrder();
    }
}
