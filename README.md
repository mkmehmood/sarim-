# Gull and Zubair Naswar Dealers (Android)

Native Android business accounting, production, sales, and inventory management application built with Kotlin, Jetpack Compose, Material 3, and Cloud Firestore.

## Architecture

- **UI Layer**: Jetpack Compose (`Material 3`), adaptive `NavigationBar` / `NavigationRail`, type-safe Navigation Compose (`@Serializable AppRoute`), and a **Material 3 Expressive** design system (tonal emerald/teal/gold colour roles in light and dark, an expanded shape scale with 20–28dp corners, Material Rounded icons, and an Android 13 themed launcher icon) with bundled Google Fonts. Theme tokens live in `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`).
- **State Management**: `SarimViewModel` and `SarimActionDispatcher` exposing `StateFlow` / `collectAsStateWithLifecycle`, backed by pure Kotlin business engines (`FormulaEngine`, `FinanceEngine`, `OverviewEngine`, `InputGuards`, `DateUtils`).
- **Authentication & Security**: Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption` / `GetGoogleIdOption`), Firebase Authentication, workspace role gating (`admin` / `member` / feature roles), pending approval queue for non-admin edits, and zero-trust Firestore Security Rules (`firestore.rules`).
- **Persistence**: Cloud Firestore workspace collections (`stores`, `formulas`, `customers`, `entities`, `inventory`, `production`, `sales`, `rep_records`, `calculator`, `factory`, `transactions`, `ledger_entries`, `pending`, `settings`) and verified-email team invites (`workspace_invites`).
- **Native Capabilities**: Biometric app lock (`androidx.biometric`), PDF statement/invoice export via `PdfDocument` + `FileProvider`, and GPS route capture via `LocationManager`.

## Verification

- **Build APK**: `gradle assembleDebug` (or `gradle assembleRelease`)
- **GitHub Releases (Single Latest APK)**: Pushing to `main`/`master` or triggering **Release Latest Android APK** updates a single `latest` GitHub Release in place (`GullZubair-Sarim-release.apk`, `GullZubair-Sarim-debug.apk`, `SHA256SUMS.txt`). Because AI Studio omits `app/google-services.json` and `debug.keystore.base64` when exporting to GitHub, add `GOOGLE_SERVICES_JSON` (contents of `app/google-services.json`) and `DEBUG_KEYSTORE_BASE64` (contents of `debug.keystore.base64`) as GitHub Actions repository secrets. Alternatively, add `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` to sign with a dedicated release keystore (register that keystore's SHA-1 in Firebase Console for Google Sign-In).
- **Firestore Rules Tests**: Start the Firestore emulator and run `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
