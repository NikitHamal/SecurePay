# Default signing keystore

`securepay-release.jks` is a self-signed RSA-2048 keystore committed to the
repository so the CI pipeline can sign release APKs without requiring any
GitHub Actions secrets to be configured.

| Field    | Value         |
| -------- | ------------- |
| Alias    | `securepay`   |
| Store password | `securepayci` |
| Key password  | `securepayci` |
| Algorithm | RSA 2048-bit |
| Validity | 10 000 days |

**This keystore is for CI / smoke-test builds only.** It must not be used to
sign APKs that are distributed to end users.

## Using real release secrets in production

Override the default by configuring these repository / environment secrets
in GitHub Actions; the workflow will prefer them when present:

| Secret               | Purpose                              |
| -------------------- | ------------------------------------ |
| `SIGNING_KEY`        | Base64-encoded release `.jks`        |
| `KEY_ALIAS`          | Alias of the signing key             |
| `KEY_STORE_PASSWORD` | Keystore password                    |
| `KEY_PASSWORD`       | Key password (falls back to store)   |

If any of `SIGNING_KEY`, `KEY_ALIAS`, or `KEY_STORE_PASSWORD` is missing,
the workflow logs a warning and falls back to `keystore/securepay-release.jks`.
