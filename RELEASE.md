# Publishing Steve AI Releases

This fork does not commit compiled JARs or other binary assets. Release artifacts are produced by GitHub Actions from source.

## Publish from a tag

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Publish manually

Run the **Release compiled mod jar** workflow from GitHub Actions and provide a tag such as `v1.0.0`.

The workflow builds the project with Java 17, creates `build/libs/steve-ai-mod-<version>.jar`, and uploads both the JAR and its `.sha256` checksum to the GitHub Release.
