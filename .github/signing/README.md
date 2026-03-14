# CI Debug Signing

`.github/signing/ci-debug.keystore.enc` is the encrypted keystore used by `build-debug.yaml`.

Required GitHub Actions secrets:

- `CI_DEBUG_SIGNING_KEYSTORE_DECRYPT_PASSWORD`
- `CI_DEBUG_SIGNING_STORE_PASSWORD`

The keystore alias is fixed as `ci-debug`. This keystore uses the same password for both the store and the private key.
