package io.quarkiverse.agentclientprotocol.sdk.client.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration for launching an ACP agent process via stdio.
 */
public class AgentParameters {

    private static final List<String> DEFAULT_INHERITED_ENV_VARS = System.getProperty("os.name")
        .toLowerCase()
        .contains("win")
                ? List.of("APPDATA", "HOMEDRIVE", "HOMEPATH", "LOCALAPPDATA", "PATH",
                        "PROCESSOR_ARCHITECTURE", "SYSTEMDRIVE", "SYSTEMROOT", "TEMP", "USERNAME", "USERPROFILE")
                : List.of("HOME", "LOGNAME", "PATH", "SHELL", "TERM", "USER");

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;

    private AgentParameters(String command, List<String> args, Map<String, String> env) {
        this.command = command;
        this.args = args;
        this.env = new HashMap<>(getDefaultEnvironment());
        if (env != null && !env.isEmpty()) {
            this.env.putAll(env);
        }
    }

    public String getCommand() { return command; }
    public List<String> getArgs() { return args; }
    public Map<String, String> getEnv() { return env; }

    public static Builder builder(String command) {
        return new Builder(command);
    }

    public static class Builder {
        private final String command;
        private final List<String> args = new ArrayList<>();
        private final Map<String, String> env = new HashMap<>();

        public Builder(String command) {
            this.command = command;
        }

        public Builder args(String... args) {
            this.args.addAll(Arrays.asList(args));
            return this;
        }

        public Builder arg(String arg) {
            this.args.add(arg);
            return this;
        }

        public Builder addEnvVar(String key, String value) {
            this.env.put(key, value);
            return this;
        }

        public AgentParameters build() {
            return new AgentParameters(command, args, env);
        }
    }

    private static Map<String, String> getDefaultEnvironment() {
        return System.getenv().entrySet().stream()
                .filter(e -> DEFAULT_INHERITED_ENV_VARS.contains(e.getKey()))
                .filter(e -> e.getValue() != null)
                .filter(e -> !e.getValue().startsWith("()"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
