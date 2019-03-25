Kontalk test server
===================

Integration tests must be executed against a real server. Here you can find
scripts and configuration files for setting up a Kontalk test server quickly.

You can edit the configuration files in any way you like. The defaults will
run a more or less complete server accepting 123456 as verification code.

The GPG keys (the .key files) are hard-coded because the integration tests have
ready-to-use keys already signed with the test server key, so don't change them
unless you really know what you're doing.

You'll need of course git, Docker and Docker Compose. Just run this command and
everything will be set up and running in a few minutes:

```bash
./server
```

Hit CTRL+C to terminate it.
