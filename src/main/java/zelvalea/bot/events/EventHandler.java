package zelvalea.bot.events;

import zelvalea.utils.ConcurrentEnumMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class EventHandler {
    private final ConcurrentMap<EventPriority, EventConsumers> handlers =
            new ConcurrentEnumMap<>(EventPriority.class);

    @SuppressWarnings("unchecked")
    public void registerEvent(Listener listener) {
        Class<?> klass = listener.getClass();
        for (Method method : klass.getDeclaredMethods()) {
            method.setAccessible(true);
            if (!method.isAnnotationPresent(EventLabel.class) ||
                    method.getParameterCount() < 1) {
                continue;
            }
            Class<?> e_type = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(e_type)) {
                continue;
            }
            EventLabel label = method.getAnnotation(EventLabel.class);
            handlers.computeIfAbsent(
                    label.priority(), f -> new EventConsumers()
            ).addConsumer((Class<? extends Event>) e_type,
                    event -> {
                        if (event instanceof Cancellable r &&
                                r.isCancelled() &&
                                label.ignoreCancelled()) {
                            return;
                        }
                        try {
                            method.invoke(listener, event);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
            });
        }
    }
    public void callEvent(Event e) {
        // keeps order
        handlers.forEach((p,u) -> {
            Queue<Consumer<Event>> q = u.getConsumers(e);
            if (q == null)
                return;
            q.forEach(x -> x.accept(e));
        });
    }


    private static class EventConsumers {
        final ConcurrentMap<Class<? extends Event>, Queue<Consumer<Event>>> map
                = new ConcurrentHashMap<>();

        void addConsumer(Class<? extends Event> type, Consumer<Event> handler) {
            map.computeIfAbsent(
                    type,
                    x -> new ConcurrentLinkedQueue<>()
            ).add(handler);
        }
        Queue<Consumer<Event>> getConsumers(Event event) {
            return map.get(event.getClass());
        }
    }
}
