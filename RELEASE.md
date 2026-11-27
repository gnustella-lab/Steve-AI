# Publishing Steve AI Releases

This fork does not commit compiled JARs or other binary assets. Release artifacts are produced by GitHub Actions from source.

## Publish from a tag

```bash
git tag v1.8.2
git push origin v1.8.2
```

## Publish manually

Run the **Release compiled mod jar** workflow from GitHub Actions and provide a tag such as `v1.8.2`.

The workflow builds the project with Java 25 / Gradle 9.7.1 against Minecraft 26.3 and Forge 66.1.9, verifies that `build/libs/steve-ai-mod-<version>.jar` contains every required runtime library, and uploads the installable JAR with its `.sha256` checksum to the GitHub Release. The `-slim.jar` artifact is not published because it cannot run by itself.
