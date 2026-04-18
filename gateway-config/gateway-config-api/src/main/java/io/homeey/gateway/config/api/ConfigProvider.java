package io.homeey.gateway.config.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface ConfigProvider {
    CompletionStage<String> get(String dataId, String group);

    CompletionStage<Void> subscribe(String dataId, String group, Consumer<String> listener);
}