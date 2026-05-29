# Module: Testing

Migrate test code that references picocli APIs.

## Gate condition

Search test sources for picocli imports or `CommandLine` usage:

```bash
grep -rn "import picocli\|CommandLine" src/test/
```

If no matches → SKIP this module.

## What to do

- [ ] Replace `CommandLine` test patterns with `AeshRuntimeRunner` patterns
- [ ] Update imports from `picocli.*` to `org.aesh.*`
- [ ] Migrate test assertions for exit codes (`int` → `CommandResult`)
- [ ] Remove picocli test utilities
- [ ] Compile and run tests: `./mvnw test` (Maven) or `./gradlew test` (Gradle)

## Common Test Patterns

### Command Execution in Tests

```java
// BEFORE: Picocli
@Test
void testDeploy() {
    DeployCommand cmd = new DeployCommand();
    CommandLine cli = new CommandLine(cmd);
    int exitCode = cli.execute("--environment", "staging", "myapp");
    assertEquals(0, exitCode);
}

// AFTER: Aesh
@Test
void testDeploy() {
    AeshRuntimeRunner.builder()
            .command(DeployCommand.class)
            .args(new String[]{"--environment", "staging", "myapp"})
            .execute();
    // AeshRuntimeRunner throws on parse errors; reaching here means success
}
```

### Capturing Output

```java
// BEFORE: Picocli — capture with StringWriter
@Test
void testOutput() {
    StringWriter sw = new StringWriter();
    CommandLine cli = new CommandLine(new MyCommand());
    cli.setOut(new PrintWriter(sw));
    cli.execute("--name", "World");
    assertTrue(sw.toString().contains("Hello, World"));
}

// AFTER: Aesh — use AeshRuntimeRunner with output capture
// Aesh routes output through CommandInvocation; test by verifying
// command behavior rather than capturing stdout, or use
// AeshRuntimeRunner which handles output routing
```

### Testing Parse Errors

```java
// BEFORE: Picocli
@Test
void testMissingRequired() {
    CommandLine cli = new CommandLine(new MyCommand());
    int exitCode = cli.execute(); // missing required option
    assertNotEquals(0, exitCode);
}

// AFTER: Aesh — parse errors throw exceptions
@Test
void testMissingRequired() {
    assertThrows(Exception.class, () -> {
        AeshRuntimeRunner.builder()
                .command(MyCommand.class)
                .args(new String[]{})
                .execute();
    });
}
```

## Watch out

- **Exit code assertions**: Picocli returns `int` exit codes. Aesh uses `CommandResult` and may throw exceptions on parse errors. Update assertions accordingly.
- **Output capture**: Picocli allows `setOut()`/`setErr()` on `CommandLine`. Aesh routes through `CommandInvocation` — test output through command behavior or by injecting test-specific `CommandInvocation` implementations.
- **Test dependencies**: Remove any test-scoped picocli dependencies from the build file.
