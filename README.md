# Gull and Zubair Naswar Dealers (Android)

Native Android business accounting, production, sales, and inventory management application built with Kotlin, Jetpack Compose, Material 3, and Cloud Firestore.

## Architecture

- **UI Layer**: Jetpack Compose (`Material 3`), adaptive `NavigationBar` / `NavigationRail`, type-safe Navigation Compose (`@Serializable AppRoute`), and dark/light Obsidian-Emerald theming with bundled Google Fonts.
- **State Management**: `SarimViewModel` and `SarimActionDispatcher` exposing `StateFlow` / `collectAsStateWithLifecycle`, backed by pure Kotlin business engines (`FormulaEngine`, `FinanceEngine`, `OverviewEngine`, `InputGuards`, `DateUtils`).
- **Authentication & Security**: Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption` / `GetGoogleIdOption`), Firebase Authentication, workspace role gating (`admin` / `member` / feature roles), pending approval queue for non-admin edits, and zero-trust Firestore Security Rules (`firestore.rules`).
- **Persistence**: Cloud Firestore workspace collections (`stores`, `formulas`, `customers`, `entities`, `inventory`, `production`, `sales`, `rep_records`, `calculator`, `factory`, `transactions`, `ledger_entries`, `pending`, `settings`) and verified-email team invites (`workspace_invites`).
- **Native Capabilities**: Biometric app lock (`androidx.biometric`), PDF statement/invoice export via `PdfDocument` + `FileProvider`, and GPS route capture via `LocationManager`.

## Verification

- **Build APK**: `gradle assembleDebug`
- **Firestore Rules Tests**: Start the Firestore emulator and run `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
