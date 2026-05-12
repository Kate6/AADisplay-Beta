# Android Auto Head Unit Make Spoofing - Xposed Module Guide

This document contains technical analysis of Android Auto's head unit information flow, specifically targeting the `hu_make` field that can be spoofed via an Xposed/LSPosed module.

---

## Overview

During the Android Auto handshake with a head unit, the app receives car/head unit information that is stored in `com.google.android.gms.car.CarInfo` objects. The `hu_make` field represents the head unit manufacturer (e.g., "Pioneer", "Kenwood", "Sony").

---

## Key Classes and Data Flow

### 1. `com.google.android.gms.car.CarInfo` (Primary Target)

**File**: `smali/com/google/android/gms/car/CarInfo.smali`

This is the main data container for car/head unit information. The class extends `AbstractSafeParcelable` and contains the following relevant fields:

| Field | Type | Smali Name | Description |
|-------|------|------------|-------------|
| `a` | String | `->a` | Car manufacturer (e.g., "Toyota") |
| `b` | String | `->b` | Car model |
| `c` | String | `->c` | Model year |
| `d` | String | `->d` | Vehicle ID client |
| `e` | int | `->e` | Protocol major version |
| `f` | int | `->f` | Protocol minor version |
| `g` | boolean | `->g` | Bluetooth allowed |
| `h` | int | `->h` | Driver position |
| **`i`** | **String** | **`->i`** | **Head Unit Make (hu_make)** |
| `j` | String | `->j` | Head Unit Model (hu_model) |
| `k` | String | `->k` | Head Unit SW Build |
| `l` | String | `->l` | Head Unit SW Version |

**Field Offset Summary**:
```
CarInfo.i = Head Unit Make (target for spoofing)
CarInfo.j = Head Unit Model
CarInfo.k = Head Unit Software Build
CarInfo.l = Head Unit Software Version
```

### 2. `com.google.android.gms.carsetup.CarInfoInternal`

**File**: `smali/com/google/android/gms/carsetup/CarInfoInternal.smali`

Wrapper class containing `CarInfo` plus additional metadata:

| Field | Type | Smali Name | Description |
|-------|------|------------|-------------|
| `a` | CarInfo | `->a` | The CarInfo object |
| `f` | String | `->f` | Head Unit Make (mirror) |
| `g` | String | `->g` | Head Unit Model (mirror) |
| `h` | String | `->h` | Head Unit SW Build (mirror) |
| `i` | String | `->i` | Head Unit SW Version (mirror) |

---

## Primary Hook Points for Xposed/LSPosed

### Option 1: Hook `CarInfo` Field Access (Recommended)

The most reliable approach is to hook field reads/writes on the `CarInfo` class:

```java
// Hook target class
Class<?> carInfoClass = XposedHelpers.findClass("com.google.android.gms.car.CarInfo", lpparam.classLoader);

// Hook field modification - CarInfo.i (hu_make)
XposedHelpers.findAndHookMethod(carInfoClass, "getHeadUnitMake", new XC_MethodHook() {
    @Override
    protected beforeHookedMethod(MethodHookParam param) throws Throwable {
        // param.thisObject is the CarInfo instance
        // Return spoofed value
        param.setResult("CustomHU");
    }
});
```

However, `CarInfo` doesn't have named getter methods - fields are accessed directly via obfuscated names. Use field hook instead:

```java
// Hook the field directly
XposedHelpers.findAndHookField(carInfoClass, "i", new XC_FieldHook() {
    @Override
    protected void beforeGetField(FieldHookParam param) throws Throwable {
        // Return spoofed hu_make
        param.setResult("MySpoofedMake");
    }
});
```

### Option 2: Hook Constructor

Hook the `CarInfo` constructor to modify values during object creation:

```java
XposedHelpers.findAndHookConstructor(carInfoClass,
    String.class,  // a: carMake
    String.class,  // b: carModel
    String.class,  // c: modelYear
    String.class,  // d: vehicleIdClient
    int.class,     // e: protocolMajor
    int.class,     // f: protocolMinor
    boolean.class, // g: bluetoothAllowed
    int.class,     // h: driverPosition
    String.class,  // i: hu_make (PARAMETER 9)
    String.class,  // j: hu_model
    String.class,  // k: hu_sw_build
    String.class,  // l: hu_sw_version
    boolean.class, // m
    boolean.class, // n
    boolean.class, // o
    String.class,  // p
    String.class,  // q
    int.class,     // r
    new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // param.args[8] is the hu_make (i field)
            String originalMake = (String) param.args[8];
            param.args[8] = "CustomMake"; // Spoof it
        }
    }
);
```

### Option 3: Hook `PersistableBundle.putString()` (Telemetry Hook)

The `hu_make` value is put into a `PersistableBundle` for telemetry. Hooking this is visible in `pzc.smali`:

**File**: `smali/pzc.smali` (around line 1328)

```java
// Hook when hu_make is written to telemetry bundle
XposedHelpers.findAndHookMethod("android.os.PersistableBundle", lpparam.classLoader,
    "putString", String.class, String.class, new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if ("hu_make".equals(param.args[0])) {
                // param.args[1] is the value being written
                param.args[1] = "SpoofedMake";
            }
        }
    }
);
```

### Option 4: Hook Database Writes (Persistence Layer)

**File**: `smali_classes2/rtz.smali` (around line 452)

The `headUnitMake` is read from a SQLite database. Hook ContentValues or database operations:

```java
XposedHelpers.findAndHookMethod("android.content.ContentValues", lpparam.classLoader,
    "put", String.class, Object.class, new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if ("headUnitMake".equals(param.args[0])) {
                param.args[1] = "SpoofedMake";
            }
        }
    }
);
```

---

## Alternative Hook Targets

### CarInfoInternal Fields

**File**: `smali/com/google/android/gms/carsetup/CarInfoInternal.smali`

Hook field `f` (headUnitMake mirror):

```java
Class<?> carInfoInternalClass = XposedHelpers.findClass("com.google.android.gms.carsetup.CarInfoInternal", lpparam.classLoader);
XposedHelpers.findAndHookField(carInfoInternalClass, "f", /* hook */);
```

### Database Creation (Migration Hook)

**File**: `smali/a.smali` (line 6673)

The database schema includes `headUnitMake TEXT`. Hook SQLite database creation or the migration logic.

### CarInfoWrapper$Core

**File**: `smali_classes2/com/google/android/apps/auto/components/connectivity/models/CarInfoWrapper$Core.smali`

Another intermediate class that may wrap CarInfo.

---

## Recommended Implementation Approach

### Step 1: Find the Class

```java
// In your Xposed module's initZygote or loadPackage
Class<?> carInfoClass = null;
try {
    carInfoClass = lpparam.classLoader.loadClass("com.google.android.gms.car.CarInfo");
} catch (ClassNotFoundException e) {
    // Try alternative
    carInfoClass = XposedHelpers.findClass("com.google.android.gms.car.CarInfo", lpparam.classLoader);
}
```

### Step 2: Hook Field Access

```java
// Hook CarInfo.i field (hu_make)
XposedBridge.hookAllMethods(carInfoClass, "finalize", new XC_MethodHook() {
    // This won't work - need different approach
});

// Better: Use field hook via reflection
Field huMakeField = carInfoClass.getDeclaredField("i");
huMakeField.setAccessible(true);

// Add a hook to the field via helper
XposedHelpers.findAndHookMethod(carInfoClass, "clone", /* ... */);
```

### Step 3: Actual Field Interception

```java
// Use XSharedPreferences to store spoof value
XSharedPreferences prefs = new XSharedPreferences("com.yourmodule", "settings");
String spoofedMake = prefs.getString("hu_make", null);

if (spoofedMake != null) {
    // Hook when any method accesses or sets the hu_make field
    
    // Method 1: Hook all constructors
    for (Constructor<?> ctor : carInfoClass.getDeclaredConstructors()) {
        XposedBridge.hookMethod(ctor, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // p9 is the hu_make argument (index 8, 0-based)
                if (param.args.length > 8 && param.args[8] != null) {
                    param.args[8] = spoofedMake;
                }
            }
        });
    }
}
```

---

## Key Observations from Smali Analysis

1. **Field i is hu_make**: Confirmed in `pzc.smali` line 1328 - the value from `CarInfo->i` is put into bundle with key "hu_make"

2. **Two-way sync**: Both `CarInfo` and `CarInfoInternal` store the head unit make, suggesting multiple validation points

3. **Null checks**: `kwp.smali` shows validation - if `CarInfo.i` is null, it throws `NullPointerException` with message "Null headUnitMake"

4. **Database persistence**: Values stored in SQLite database `allowedcars` table with `headUnitMake` column

5. **Protocol versioning**: The `hu_make` is used in combination with protocol versions for compatibility checks

---

## Potential Gotchas

1. **ProGuard/R8 obfuscation**: Field names (`i`, `j`, `k`) are obfuscated. Class and method names may change between app versions.

2. **Multi-dex**: `CarInfo` is in the primary DEX, but `CarInfoInternal` references it from multiple DEX files.

3. **Timing**: The hook must execute before the value is used for critical decisions (like feature gating based on head unit make).

4. **String encryption**: Some strings may be obfuscated/encrypted using `sgq.N()` (visible in `CarInfo.a()` method) - this appears to be an encoding function, not encryption.

5. **Null safety**: Cannot spoof to null if the app expects a non-null value - may cause crashes.

---

## Files Referenced

| File | Lines | Purpose |
|------|-------|---------|
| `smali/com/google/android/gms/car/CarInfo.smali` | Full | Main data class with hu_make field |
| `smali/com/google/android/gms/carsetup/CarInfoInternal.smali` | Full | Wrapper with additional metadata |
| `smali/pzc.smali` | ~1328 | Telemetry bundle creation using hu_make |
| `smali/kwp.smali` | ~110 | Validation - "Null headUnitMake" exception |
| `smali_classes2/rtz.smali` | ~452 | Database read of headUnitMake |
| `smali/a.smali` | ~6673 | Database schema definition |
| `smali/qup.smali` | ~2145 | CarInfo accessor method |
| `smali_classes2/rro.smali` | ~1591 | CarInfo instantiation from protocol data |

---

## Version Compatibility Notes

- Target app: Android Auto 16.7.661654-release
- Package: `com.google.android.projection.gearhead`
- GMS Core classes: `com.google.android.gms.car.*`
- Min SDK: 32 (Android 12L)

Field names and class structure may vary in different app versions. Always verify against the specific APK being targeted.