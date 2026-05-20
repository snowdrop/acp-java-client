## ACP client usage

```shell
mvn clean package
mvn exec:java -Dexec.mainClass=io.quarkiverse.acp.OpenCodeAcp          
mvn exec:java -Dexec.mainClass=io.quarkiverse.acp.OpenCodeAcp -Dexec.args="What is 66+1000?"
mvn exec:java -Dexec.mainClass=io.quarkiverse.acp.OpenCodeAcp -Dexec.args="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project"         
```
