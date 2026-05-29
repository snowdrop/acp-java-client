# Picocli to Aesh Execution Model Map

## Execution Entry Points

| Picocli | Aesh | Notes |
|---|---|---|
| `new CommandLine(cmd).execute(args)` | `AeshRuntimeRunner.builder().command(Cmd.class).args(args).execute()` | One-shot CLI execution |
| `new CommandLine(cmd)` + interactive loop | `AeshConsoleRunner.builder().command(Cmd.class).prompt("$ ").start()` | Interactive shell mode |

## Command Interface

| Picocli | Aesh | Notes |
|---|---|---|
| `implements Runnable` | `implements Command<CommandInvocation>` | Override `execute(CommandInvocation)` |
| `implements Callable<Integer>` | `implements Command<CommandInvocation>` | Return `CommandResult` instead of `Integer` |
| `void run()` | `CommandResult execute(CommandInvocation invocation)` | Method signature change |
| `Integer call()` | `CommandResult execute(CommandInvocation invocation)` | Method signature change |

## Return Values / Exit Codes

| Picocli | Aesh | Notes |
|---|---|---|
| `return 0` | `return CommandResult.SUCCESS` | Success exit code |
| `return 1` (or any non-zero) | `return CommandResult.FAILURE` | Failure exit code |
| `return exitCode` | `return CommandResult.valueOf(exitCode)` | Custom exit code |
| Parse errors → configurable | Parse errors → exit code 2 | POSIX convention, automatic |

## Output

| Picocli | Aesh | Notes |
|---|---|---|
| `System.out.println(msg)` | `invocation.println(msg)` | Routes through terminal connection |
| `System.err.println(msg)` | `invocation.println(msg)` | Use `invocation` for all output |
| `@Command(header = "...")` | Use `invocation.println()` in execute method | No direct header equivalent |

**Why `invocation.println()`?** Aesh routes output through the terminal connection, allowing the same command to work across local terminals, SSH, telnet, and WebSocket connections.

## Group Commands

```java
// Picocli
@Command(name = "remote", subcommands = {AddCommand.class, RemoveCommand.class})
public class RemoteCommand implements Runnable {
    @Override
    public void run() { /* parent logic */ }
}

// Aesh
@GroupCommandDefinition(name = "remote",
        groupCommands = {AddCommand.class, RemoveCommand.class})
public class RemoteCommand implements Command<CommandInvocation> {
    @Override
    public CommandResult execute(CommandInvocation invocation) {
        /* parent logic */
        return CommandResult.SUCCESS;
    }
}
```

## Complete Before/After Example

### Picocli Version

```java
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "deploy", description = "Deploy an application",
         mixinStandardHelpOptions = true, version = "1.0")
public class DeployCommand implements Callable<Integer> {

    @Option(names = {"-e", "--environment"}, defaultValue = "production",
            description = "Target environment")
    private String environment;

    @Option(names = {"-f", "--force"}, description = "Force deployment")
    private boolean force;

    @Option(names = {"-t", "--tags"}, split = ",",
            description = "Deployment tags")
    private List<String> tags;

    @Option(names = "-D", description = "Properties")
    private Map<String, String> properties;

    @Parameters(index = "0", description = "Application name")
    private String application;

    @Override
    public Integer call() {
        System.out.println("Deploying " + application + " to " + environment);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DeployCommand()).execute(args);
        System.exit(exitCode);
    }
}
```

### Aesh Version

```java
import org.aesh.command.*;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.*;
import org.aesh.AeshRuntimeRunner;

import java.util.List;
import java.util.Map;

@CommandDefinition(name = "deploy", description = "Deploy an application",
                   generateHelp = true, version = "1.0")
public class DeployCommand implements Command<CommandInvocation> {

    @Option(shortName = 'e', name = "environment", defaultValue = "production",
            description = "Target environment")
    private String environment;

    @Option(shortName = 'f', name = "force", hasValue = false,
            description = "Force deployment")
    private boolean force;

    @OptionList(shortName = 't', name = "tags", valueSeparator = ',',
                description = "Deployment tags")
    private List<String> tags;

    @OptionGroup(shortName = 'D', description = "Properties")
    private Map<String, String> properties;

    @Argument(description = "Application name", required = true)
    private String application;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Deploying " + application + " to " + environment);
        return CommandResult.SUCCESS;
    }

    public static void main(String[] args) {
        AeshRuntimeRunner.builder()
                .command(DeployCommand.class)
                .args(args)
                .execute();
    }
}
```

### What Changed (Checklist)

1. `@Command` → `@CommandDefinition` with `generateHelp = true`
2. `implements Callable<Integer>` → `implements Command<CommandInvocation>`
3. `@Option(names = {"-f", "--force"})` → `@Option(shortName = 'f', name = "force", hasValue = false)` — boolean flags require `hasValue = false`
4. `@Option(split = ",") List<String>` → `@OptionList(valueSeparator = ',')`
5. `Map<String, String>` with `@Option` → `@OptionGroup`
6. `@Parameters` → `@Argument`
7. `System.out.println` → `invocation.println`
8. Return `CommandResult.SUCCESS` instead of integer exit code
9. `CommandLine.execute(args)` → `AeshRuntimeRunner.builder()...execute()`

## Aesh-Only Features (No Picocli Equivalent)

These can optionally be adopted during migration for enhanced CLI behavior:

| Feature | Annotation/API | Description |
|---|---|---|
| Negatable booleans | `@Option(negatable = true)` | Auto-generates `--no-verbose` |
| Exclusive options | `@Option(exclusiveWith = "json")` | Enforces mutual exclusion at parse time |
| Option visibility | `OptionVisibility.BRIEF/FULL/HIDDEN` | Controls appearance in `--help` and tab completion |
| Interactive prompting | `@Option(askIfNotSet = true)` | Prompts users for missing required values |
| Ghost text suggestions | Built-in | Inline suggestions as the user types |
| Selectors | Built-in | Interactive list/checkbox selection |
| Sub-command mode | Built-in | Context mode without repeating parent name |
| Multi-shell completion | `--aesh-completion` | Auto-generates bash, zsh, and fish completions |

## Shell Completion Migration

Picocli's `AutoComplete` only supports bash. Aesh auto-generates bash, zsh, and fish completion scripts:

```bash
# Generate completion script (auto-detects shell)
$ myapp --aesh-completion

# Generate for a specific shell
$ myapp --aesh-completion bash

# Auto-detect, generate, and install
$ myapp --aesh-completion-install
```

Remove any picocli `AutoComplete` code and rely on Aesh's built-in completion support.
