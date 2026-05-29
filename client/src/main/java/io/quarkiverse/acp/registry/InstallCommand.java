package io.quarkiverse.acp.registry;

import picocli.CommandLine;

/**
 * Subcommand that installs an ACP agent from the remote registry.
 *
 * <p>Usage:
 * <pre>{@code
 * acp reg install opencode
 * acp reg install claude-acp --force
 * }</pre>
 *
 * <p>The agent binary (or npx/uvx metadata) is stored under
 * {@code $HOME/.acp/agents/<agent-id>/}.
 */
@CommandLine.Command(
        name = "install",
        description = "Install an ACP agent from the registry"
)
public class InstallCommand implements Runnable {

    @CommandLine.Parameters(
            index = "0",
            description = "Agent ID from the ACP registry (e.g. opencode, claude-acp, gemini)")
    String agentId;

    @CommandLine.Option(
            names = {"--force", "-f"},
            description = "Force reinstall even if the agent is already installed")
    boolean force;

    @Override
    public void run() {
        try {
            var manager = new AcpRegistryManager();
            manager.installAgent(agentId, force);
        } catch (Exception e) {
            System.err.println("Error installing agent: " + e.getMessage());
            System.exit(1);
        }
    }
}
