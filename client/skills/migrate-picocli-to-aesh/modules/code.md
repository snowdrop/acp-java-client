# Module: Code Migration

Migrate all Java source code from picocli annotations and patterns to Aesh equivalents.

Load [references/annotation-map.md](../references/annotation-map.md) and [references/execution-map.md](../references/execution-map.md) before starting. They contain the complete annotation and execution model mapping tables.

## What to do

- [ ] Create a Quarkus Main class if quarkus-aesh is not present
- [ ] Migrate command class annotations (`@Command` → `@CommandDefinition` / `@GroupCommandDefinition`)
- [ ] Migrate command interfaces (`Runnable`/`Callable<Integer>` → `Command<CommandInvocation>`)
- [ ] Migrate option annotations (`@Option` → Aesh `@Option`, `@OptionList`, `@OptionGroup`)
- [ ] Migrate positional parameter annotations (`@Parameters` → `@Argument` / `@Arguments`)
- [ ] Migrate boolean flags (add `hasValue = false`)
- [ ] Migrate list options (`split` → `@OptionList` with `valueSeparator`)
- [ ] Migrate map options (`Map` with `@Option` → `@OptionGroup`)
- [ ] Migrate inherited options (`scope = INHERIT` → `inherited = true`)
- [ ] Migrate group/subcommand structure (`subcommands = {...}` → `@GroupCommandDefinition`)
- [ ] Migrate execution entry point (`CommandLine.execute()` → `AeshRuntimeRunner`)
- [ ] Migrate output (`System.out.println` → `invocation.println`)
- [ ] Migrate return values (integer exit codes → `CommandResult`)
- [ ] Update all imports (`picocli.CommandLine.*` → `org.aesh.command.*`)
- [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

## Quarkus integration
 
If quarkus-aesh is not present, create a Quarkus Main class where you create the AeshRuntime using its builder. See the following example.

```java
@QuarkusMain
public class AcpMain implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        AeshRuntimeRunner.builder()
                .command(AcpClientCommand.class)
                .args(args)
                .execute();
        return 0;
    }
}
```

## Command Class Migration

### Simple Commands

```java
// BEFORE: Picocli
@Command(name = "greet", description = "Say hello",
         mixinStandardHelpOptions = true, version = "1.0")
public class GreetCommand implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Name to greet")
    private String name;

    @Override
    public Integer call() {
        System.out.println("Hello, " + name + "!");
        return 0;
    }
}

// AFTER: Aesh
@CommandDefinition(name = "greet", description = "Say hello",
                   generateHelp = true, version = "1.0")
public class GreetCommand implements Command<CommandInvocation> {

    @Option(shortName = 'n', name = "name", description = "Name to greet")
    private String name;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        invocation.println("Hello, " + name + "!");
        return CommandResult.SUCCESS;
    }
}
```

### Group Commands (with Subcommands)

```java
// BEFORE: Picocli
@Command(name = "remote", subcommands = {AddCommand.class, RemoveCommand.class})
public class RemoteCommand implements Runnable {
    @Override
    public void run() { /* parent logic */ }
}

// AFTER: Aesh
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

## Option Migration

### Boolean Flags

Boolean flags in picocli are inferred from the field type. In Aesh, you must explicitly set `hasValue = false`:

```java
// BEFORE: Picocli — infers boolean from field type
@Option(names = {"-f", "--force"}, description = "Force operation")
private boolean force;

// AFTER: Aesh — must specify hasValue = false
@Option(shortName = 'f', name = "force", hasValue = false, description = "Force operation")
private boolean force;
```

### Short and Long Names

Picocli uses a string array for names. Aesh separates short (`char`) and long (`String`) names:

```java
// BEFORE: Picocli
@Option(names = {"-v", "--verbose"})
private boolean verbose;

// AFTER: Aesh
@Option(shortName = 'v', name = "verbose", hasValue = false)
private boolean verbose;
```

If only a long name exists (`--output`), omit `shortName`. If only a short name exists (`-v`), the long name defaults to the field name.

### List Options

```java
// BEFORE: Picocli — uses split on @Option
@Option(names = {"-t", "--tags"}, split = ",", description = "Tags")
private List<String> tags;

// AFTER: Aesh — dedicated @OptionList annotation
@OptionList(shortName = 't', name = "tags", valueSeparator = ',', description = "Tags")
private List<String> tags;
```

### Map / Property Options

```java
// BEFORE: Picocli — infers from Map type
@Option(names = "-D", description = "System properties")
private Map<String, String> properties;

// AFTER: Aesh — dedicated @OptionGroup annotation
@OptionGroup(shortName = 'D', description = "System properties")
private Map<String, String> properties;
```

## Positional Parameters Migration

```java
// BEFORE: Picocli — single parameter
@Parameters(index = "0", description = "Input file")
private String inputFile;

// AFTER: Aesh — single argument
@Argument(index = "0", description = "Input file")
private String inputFile;

// BEFORE: Picocli — multiple parameters
@Parameters(index = "1..*", description = "Additional files")
private List<String> extraFiles;

// AFTER: Aesh — multiple arguments
@Arguments(index = "1..*", description = "Additional files")
private List<String> extraFiles;
```

## Execution Entry Point

```java
// BEFORE: Picocli — one-shot execution
public static void main(String[] args) {
    int exitCode = new CommandLine(new MyCommand()).execute(args);
    System.exit(exitCode);
}

// AFTER: Aesh — one-shot execution
public static void main(String[] args) {
    AeshRuntimeRunner.builder()
            .command(MyCommand.class)
            .args(args)
            .execute();
}

// BEFORE: Picocli — interactive shell (with JLine)
// Custom interactive loop with CommandLine

// AFTER: Aesh — interactive shell (built-in)
public static void main(String[] args) {
    AeshConsoleRunner.builder()
            .command(MyCommand.class)
            .prompt("$ ")
            .start();
}
```

## Watch out

- **Boolean flags**: Every `boolean` field option **must** have `hasValue = false` in Aesh. Missing this causes runtime parsing errors.
- **Short name type**: Aesh `shortName` is `char`, not `String`. Use `'v'` not `"-v"`.
- **List vs Option**: Any picocli `@Option` with `split` on a `List` field must become `@OptionList`. Leaving it as `@Option` will not split values.
- **Map vs Option**: Any picocli `@Option` on a `Map` field must become `@OptionGroup`.
- **Output routing**: Replace all `System.out.println` with `invocation.println` for proper terminal routing.
- **Exit codes**: Replace integer returns with `CommandResult.SUCCESS` / `CommandResult.FAILURE`.
- **`@Parameters` index**: Aesh uses `@Argument` for single values and `@Arguments` for collections. Check the index and field type to choose correctly.
