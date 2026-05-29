# Picocli to Aesh Annotation Map

## Commands

| Picocli | Aesh | Notes |
|---|---|---|
| `@Command(name = "deploy", description = "...")` | `@CommandDefinition(name = "deploy", description = "...")` | Direct mapping |
| `@Command(subcommands = {Sub.class})` | `@GroupCommandDefinition(name = "grp", groupCommands = {Sub.class})` | Aesh uses a separate annotation for group commands |
| `@Command(mixinStandardHelpOptions = true)` | `@CommandDefinition(generateHelp = true)` | Auto-generates `--help` |
| `@Command(version = "1.0")` | `@CommandDefinition(version = "1.0")` | Direct mapping |
| `implements Runnable` | `implements Command<CommandInvocation>` | Different execution interface |
| `implements Callable<Integer>` | `implements Command<CommandInvocation>` | Return `CommandResult` instead of `Integer` |

**Key differences:**
- Picocli uses `@Command` for both simple and group commands. Aesh separates these: group commands use `@GroupCommandDefinition` with a `groupCommands` attribute listing subcommand classes.
- The `description` attribute in Aesh is a single string, not a string array.
- Don't miss to migrate an annotation attribute like `mixinStandardHelpOptions = true` to `generateHelp = true` no matter to which annotation it belongs: `@CommandDefintion` or `@GroupCommandDefinition`

## Options

| Picocli | Aesh | Notes |
|---|---|---|
| `@Option(names = {"-v", "--verbose"})` | `@Option(shortName = 'v', name = "verbose")` | `shortName` is a `char`, not a string |
| `@Option(names = "-v", arity = "0")` | `@Option(shortName = 'v', hasValue = false)` | Boolean flags need `hasValue = false` |
| `@Option(names = "--count", defaultValue = "10")` | `@Option(name = "count", defaultValue = "10")` | Direct mapping |
| `@Option(names = "--out", required = true)` | `@Option(name = "out", required = true)` | Direct mapping |
| `@Option(names = "--items", split = ",") List<String>` | `@OptionList(name = "items", valueSeparator = ',')` | Dedicated annotation for list options |
| `@Option(names = "-D") Map<String,String>` | `@OptionGroup(shortName = 'D')` | Dedicated annotation for map/property options |
| `@Option mapFallbackValue = ""` | `@OptionGroup(defaultValue = "")` | Map fallback on `@OptionGroup` |

**Key differences:**
- Aesh `shortName` is a `char`, not a string array. The long name defaults to the field name or is set via `name`.
- Boolean flags **must** have `hasValue = false` in Aesh; picocli infers this from the field type or `arity = "0"`.
- List-type options use the dedicated `@OptionList` annotation instead of `split` on `@Option`.
- Map/property-style options use `@OptionGroup` instead of picocli's type inference on `Map` fields.

## Arguments (Positional Parameters)

| Picocli | Aesh | Notes |
|---|---|---|
| `@Parameters(index = "0")` | `@Argument(index = "0")` | Single positional value |
| `@Parameters(index = "1..*") List<String>` | `@Arguments(index = "1..*")` | Multiple positional values |
| `@Parameters(description = "...")` | `@Argument(description = "...")` | Direct mapping |
| `@Parameters(paramLabel = "FILE")` | `@Argument(paramLabel = "FILE")` | Customizes help display |
| `@Parameters(arity = "1..*")` | `@Arguments(arity = "1..*")` | Constrains accepted value counts |
| `@Parameters(arity = "2")` | `@Arguments(arity = "2")` | Fixed arity |

**Key differences:**
- Aesh distinguishes `@Argument` (single positional) from `@Arguments` (multiple positional).
- Both support explicit `index` ranges mapping directly from picocli's `@Parameters(index = ...)`.

## Shared Annotations (Identical in Both)

| Picocli | Aesh | Notes |
|---|---|---|
| `@Mixin` | `@Mixin` | Functions identically |
| `@ParentCommand` | `@ParentCommand` | Functions identically |

## Inherited Options

| Picocli | Aesh | Notes |
|---|---|---|
| `@Option(scope = INHERIT)` | `@Option(inherited = true)` | Inherited options can appear before or after the subcommand name |

In Aesh, both `app --verbose run` and `app run --verbose` set `verbose = true` on the child command.

## Import Mapping

| Picocli Import | Aesh Import |
|---|---|
| `picocli.CommandLine` | `org.aesh.command.*` |
| `picocli.CommandLine.Command` | `org.aesh.command.CommandDefinition` or `org.aesh.command.GroupCommandDefinition` |
| `picocli.CommandLine.Option` | `org.aesh.command.option.Option` |
| `picocli.CommandLine.Parameters` | `org.aesh.command.option.Argument` or `org.aesh.command.option.Arguments` |
| `picocli.CommandLine.Mixin` | `org.aesh.command.option.Mixin` |
| `picocli.CommandLine.ParentCommand` | `org.aesh.command.option.ParentCommand` |
