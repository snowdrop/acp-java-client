package io.quarkiverse.acp.registry;

import picocli.CommandLine;

/**
 * Subcommand that removes a previously installed ACP agent.
 *
 * <p>Usage:
 * <pre>{@code
 * acp reg remove opencode
 * }</pre>
 *
 * <p>Deletes the agent directory under {@code $HOME/.acp/agents/<agent-id>/}.
 */
@CommandLine.Command(
        name = "remove",
        description = "Remove an installed ACP agent"
)
public class RemoveCommand implements Runnable {

    @CommandLine.Parameters(
            index = "0",
            description = "Agent ID to remove (e.g. opencode, claude-acp)")
    String agentId;

    @Override
    public void run() {
        try {
            var manager = new AcpRegistryManager();
            manager.removeAgent(agentId);
        } catch (Exception e) {
            System.err.println("Error removing agent: " + e.getMessage());
            System.exit(1);
        }
    }
}
