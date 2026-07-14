# Publishing Steve AI Releases

This fork does not commit compiled JARs or other binary assets. Release artifacts are produced by GitHub Actions from source.

## Publish from a tag

```bash
git tag v1.5.1
git push origin v1.5.1
```

## Publish manually

Run the **Release compiled mod jar** workflow from GitHub Actions and provide a tag such as `v1.5.1`.

The workflow builds the project with Java 17, verifies that `build/libs/steve-ai-mod-<version>.jar` contains every required runtime library, and uploads the installable JAR with its `.sha256` checksum to the GitHub Release. The `-slim.jar` artifact is not published because it cannot run by itself.
