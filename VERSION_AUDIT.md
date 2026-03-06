# PDF Forger — Version Audit & Compatibility Report

**Date:** March 2025  
**Sources:** Android Developers, Kotlin/Compose compatibility map, Maven Central, DuckDuckGo search, AGP compatibility table.

---

## 1. Current Versions (from `gradle/libs.versions.toml`)

| Component | Current | Used in |
|-----------|---------|---------|
| **Kotlin** | 1.9.22 | All modules |
| **AGP** (Android Gradle Plugin) | 8.2.2 | Root + app + libraries |
| **Compose BOM** | 2024.02.00 | Feature modules, common/ui |
| **Compose compiler** | 1.5.8 | Hardcoded in app + feature build.gradle.kts |
| **Hilt** | 2.50 | App, features, engine, data |
| **WorkManager** | 2.9.0 | App, worker, pdf_creation |
| **Navigation Compose** | 2.7.6 | App, common/ui, features |
| **DataStore** | 1.0.0 | data/storage |
| **Coroutines** | 1.7.3 | domain/core, data/storage, worker |
| **PDFBox** | 3.0.1 | (referenced in design; engine/converter uses POI) |
| **POI** (poi-ooxml-lite) | 5.2.5 | engine/converter |
| **JUnit 5** | 5.10.1 | domain/core tests |
| **MockK** | 1.13.9 | domain/core tests |
| **Detekt** | 1.23.4 | (in catalog, optional) |
| **Kotlinx Serialization** | 1.6.2 | data/worker, domain/models |

**Gradle:** No `gradle-wrapper.properties` in repo; `.gradle/` showed 8.7, 8.9, 9.0 in cache — ensure wrapper is committed with **Gradle 8.2+** for AGP 8.2.

---

## 2. Compatibility Summary

### 2.1 Kotlin ↔ Compose compiler (official map)

- **Compose compiler 1.5.8** is compatible with **Kotlin 1.9.22** ✅  
- Same row: 1.5.9, 1.5.10 also target Kotlin 1.9.22.  
- For Kotlin 1.9.25 the table lists **Compose compiler 1.5.15**.

**Verdict:** Current pair (1.9.22 + 1.5.8) is valid. Safe optional bump: Kotlin 1.9.25 + compiler 1.5.15.

### 2.2 AGP ↔ Gradle (Android Developers)

| AGP version | Min Gradle |
|-------------|------------|
| 9.1 | 9.3.1 |
| 9.0 | 9.1.0 |
| 8.13 | 8.13 |
| 8.7 | 8.9 |
| **8.2** | **8.2** |

**Verdict:** AGP 8.2.2 requires **Gradle 8.2** minimum. You are compatible. Newer AGP (8.7+, 9.x) would require a higher Gradle version and possibly JDK 17+.

### 2.3 Hilt ↔ AGP

- **Hilt 2.59+** requires **AGP 9.0+** and **Gradle 9.1+**.  
- **Hilt 2.50–2.58** works with AGP 8.x.

**Verdict:** Hilt 2.50 is compatible with AGP 8.2.2. You can safely update to **2.58** (or latest 2.58.x) without moving to AGP 9.

### 2.4 Compose BOM

- **2024.02.00** is from Feb 2024; BOMs like **2024.08.00**, **2025.01.00**, **2025.08.01** exist and are newer.  
- BOM only pins Compose library versions; it does not change the **Compose compiler** or Kotlin version.  
- Compiler 1.5.8 works with Kotlin 1.9.22 regardless of BOM year.

**Verdict:** You can upgrade BOM to a newer 2024 or 2025 BOM (e.g. **2024.08.00** or **2025.01.00**) for newer Compose fixes/features while keeping Kotlin 1.9.22 and compiler 1.5.8.

### 2.5 Navigation / WorkManager / DataStore

- **Navigation Compose** has newer 2.8.x / 2.9.x; 2.7.6 is stable and compatible.  
- **WorkManager 2.9.0** is recent and compatible.  
- **DataStore 1.0.0** is still the stable line.

**Verdict:** All compatible. Optional: bump Navigation to 2.8.x or 2.9.x for fixes.

### 2.6 Apache PDFBox & POI

- **PDFBox:** 3.0.6 is latest (3.x line); 3.0.1 → 3.0.6 is patch/minor.  
- **POI:** 5.5.1 reported (Nov 2025); 5.2.5 → 5.3.x / 5.5.x may have API tweaks; **poi-ooxml-lite** should be checked for same major version.

**Verdict:** PDFBox safe to bump to 3.0.6. POI: stay 5.2.x or test 5.3+ with poi-ooxml-lite.

### 2.7 Coroutines / Serialization / JUnit / MockK

- **Coroutines 1.7.3** works with Kotlin 1.9.22. Newer 1.8.x / 1.9.x are typically compatible.  
- **Kotlinx Serialization 1.6.2** is fine for Kotlin 1.9; 1.10.x targets Kotlin 2.x.  
- **JUnit 5.10.1** and **MockK 1.13.9** are current enough.

**Verdict:** All compatible. Optional: bump coroutines to latest 1.7.x or 1.8.x.

---

## 3. Are We “Updated”? (Short answer)

- **Core toolchain (Kotlin, AGP, Gradle, Compose compiler):** Within a supported, compatible set but not the “latest” (e.g. Kotlin 2.x, AGP 9.x, Compose compiler plugin).  
- **Libraries (Hilt, BOM, Navigation, PDFBox, etc.):** Several have newer stable versions that are still compatible with your current Kotlin/AGP.

So: **you are compatible**, but **not fully up to date** with the latest major lines (Kotlin 2.x, AGP 9.x). For stability, staying on Kotlin 1.9 + AGP 8.2 is reasonable; you can still apply safe dependency updates within that stack.

---

## 4. Recommended Updates (safe, within current stack)

Staying on **AGP 8.2.2**, **Kotlin 1.9.22**, and **Compose compiler 1.5.8**:

| Dependency | Current | Recommended | Note |
|------------|---------|-------------|------|
| **Hilt** | 2.50 | **2.58** | Last version that supports AGP 8.x; bug fixes. |
| **Compose BOM** | 2024.02.00 | **2024.08.00** or **2025.01.00** | Newer Compose libs; same compiler. |
| **Compose compiler** | 1.5.8 | **1.5.10** (optional) | Same Kotlin 1.9.22 row. |
| **Navigation Compose** | 2.7.6 | **2.8.0** or **2.9.0** | Optional; check release notes. |
| **PDFBox** | 3.0.1 | **3.0.6** | Patch updates. |
| **POI** | 5.2.5 | **5.2.5** or **5.3.2** | Keep 5.2.x unless you test poi-ooxml-lite on 5.3+. |
| **Coroutines** | 1.7.3 | **1.8.0** or **1.7.3** | Optional; 1.8.x is compatible with Kotlin 1.9. |

If you later move to **Kotlin 2.0+** and **AGP 9.x**:

- You must switch to the **Compose Compiler Gradle plugin** (no more `kotlinCompilerExtensionVersion` in `composeOptions`).  
- Hilt 2.59+ and Gradle 9.1+ would then be required.

---

## 5. Gradle Wrapper

- Add **`gradle/wrapper/gradle-wrapper.properties`** (and `gradlew` / `gradlew.bat`) if missing.  
- Set **Gradle 8.2** (or 8.7/8.9 if you prefer) for AGP 8.2.2.  
- Example: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip` (or 8.7, 8.9).

---

## 6. Summary Table

| Component | Current | Compatible? | Suggested action |
|-----------|---------|-------------|------------------|
| Kotlin | 1.9.22 | ✅ | Keep or move to 1.9.25 |
| AGP | 8.2.2 | ✅ | Keep (or plan AGP 9 later) |
| Gradle | (none in repo) | — | Add wrapper 8.2+ |
| Compose BOM | 2024.02.00 | ✅ | Bump to 2024.08 or 2025.01 |
| Compose compiler | 1.5.8 | ✅ | Optional: 1.5.10 |
| Hilt | 2.50 | ✅ | Bump to 2.58 |
| WorkManager | 2.9.0 | ✅ | Keep |
| Navigation | 2.7.6 | ✅ | Optional: 2.8/2.9 |
| DataStore | 1.0.0 | ✅ | Keep |
| Coroutines | 1.7.3 | ✅ | Optional: 1.8.x |
| PDFBox | 3.0.1 | ✅ | Bump to 3.0.6 |
| POI | 5.2.5 | ✅ | Keep or test 5.3.x |
| Serialization | 1.6.2 | ✅ | Keep for Kotlin 1.9 |
| JUnit / MockK | 5.10.1 / 1.13.9 | ✅ | Keep |

Everything you use is **compatible**; the main gaps are **Gradle wrapper** and **newer patch/minor versions** of Hilt, Compose BOM, PDFBox, and optionally Navigation/Coroutines/compiler.
