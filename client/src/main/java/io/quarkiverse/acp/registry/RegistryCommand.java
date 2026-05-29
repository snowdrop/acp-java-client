package io.quarkiverse.acp.registry;

import picocli.CommandLine;

/**
 * Parent subcommand grouping ACP registry operations.
 *
 * <p>Usage:
 * <pre>{@code
 * acp reg list                # installed agents
 * acp reg list --registry     # remote registry
 * acp reg install opencode    # install an agent
 * acp reg remove opencode     # remove an installed agent
 * }</pre>
 */
@CommandLine.Command(
        name = "reg",
        description = "Manage ACP agents via the registry (list, install, remove)",
        subcommands = {ListAgentsCommand.class, InstallCommand.class, RemoveCommand.class}
)
public class RegistryCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
