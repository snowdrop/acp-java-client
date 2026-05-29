package io.quarkiverse.acp.registry;

import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand that lists ACP agents — either locally installed or from the
 * remote registry.
 *
 * <p>Usage:
 * <pre>{@code
 * acp reg list              # installed agents
 * acp reg list --registry   # all agents from the remote ACP registry
 * }</pre>
 */
@CommandLine.Command(
        name = "list",
        description = "List ACP agents (installed or from the registry)"
)
public class ListAgentsCommand implements Runnable {

    @CommandLine.Option(
            names = {"--registry", "-r"},
            description = "List all agents from the remote ACP registry instead of installed ones")
    boolean fromRegistry;

    @Override
    public void run() {
        var manager = new AcpRegistryManager();
        try {
            if (fromRegistry) {
                listRegistryAgents(manager);
            } else {
                listInstalledAgents(manager);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    // ── Registry listing ────────────────────────────────────────────────────

    private void listRegistryAgents(AcpRegistryManager manager) throws Exception {
        var registry = manager.fetchRegistry();
        String platform = AcpRegistryManager.detectPlatform();

        System.out.println("ACP Registry v" + registry.version()
                + " - " + registry.agents().size() + " agents available");
        System.out.println("Current platform: " + platform);
        System.out.println();
        System.out.printf("%-25s %-12s %-14s %s%n", "ID", "VERSION", "DISTRIBUTION", "DESCRIPTION");
        System.out.println("-".repeat(90));

        for (var agent : registry.agents()) {
            String distType = describeDistribution(agent, platform);
            boolean installed = manager.isInstalled(agent.id());
            String desc = agent.description() != null ? truncate(agent.description(), 38) : "";
            String idDisplay = agent.website() != null && !agent.website().isEmpty()
                    ? "\033]8;;" + agent.website() + "\033\\" + agent.id() + "\033]8;;\033\\"
                    : agent.id();
            // OSC 8 escape sequences are invisible, so pad based on the raw id length
            int padding = 25 - agent.id().length();
            System.out.printf("%s%-" + padding + "s %-12s %-14s %s%s%n",
                    idDisplay,
                    "",
                    agent.version(),
                    distType,
                    desc,
                    installed ? " [installed]" : "");
        }
    }

    // ── Installed listing ───────────────────────────────────────────────────

    private void listInstalledAgents(AcpRegistryManager manager) {
        var installed = manager.listInstalled();
        if (installed.isEmpty()) {
            System.out.println("No ACP agents installed in " + AcpRegistryManager.ACP_HOME);
            System.out.println();
            System.out.println("  acp reg list --registry   list available agents");
            System.out.println("  acp reg install <id>      install an agent");
            return;
        }

        System.out.println("Installed ACP agents (" + AcpRegistryManager.ACP_HOME + "):");
        System.out.println();
        System.out.printf("%-25s %-12s %-8s %-18s %s%n",
                "ID", "VERSION", "TYPE", "PLATFORM", "COMMAND");
        System.out.println("-".repeat(95));

        for (var agent : installed) {
            System.out.printf("%-25s %-12s %-8s %-18s %s%n",
                    agent.id(),
                    agent.version(),
                    agent.distributionType(),
                    agent.platform(),
                    truncate(agent.cmd(), 30));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String describeDistribution(AcpRegistryManager.Agent agent, String platform) {
        var dist = agent.distribution();
        if (dist == null) return "none";

        List<String> types = new ArrayList<>();
        if (dist.hasBinary()) {
            types.add(dist.binary().containsKey(platform) ? "binary" : "binary(*)");
        }
        if (dist.hasNpx()) types.add("npx");
        if (dist.hasUvx()) types.add("uvx");
        return String.join(", ", types);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
