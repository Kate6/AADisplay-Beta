# Kia Soul Head Unit Information

## How to Find This Information

The head unit information for your Kia Soul is stored in Android Auto's local SQLite database: `carservicedata.db`, located at:

```
/data/data/com.google.android.projection.gearhead/databases/carservicedata.db
```

You can pull this file using ADB with root access:

```bash
adb shell "su -c 'cat /data/data/com.google.android.projection.gearhead/databases/carservicedata.db'" > carservicedata.db
```

## Table Structure

The database contains a table named `allowedcars` that tracks all cars that have been connected to Android Auto. The relevant columns for the head unit are:

```sql
headUnitMake TEXT,
headUnitModel TEXT,
headUnitSoftwareBuild TEXT,
headUnitSoftwareVersion TEXT,
vehicleType INTEGER DEFAULT 0
```

Alongside the standard columns:

```sql
id INTEGER PRIMARY KEY,
manufacturer TEXT,
model TEXT,
modelyear TEXT,
vehicleid TEXT,
vehicleidclient TEXT,
headUnitProtocolMajorVersionNumber INTEGER,
headUnitProtocolMinorVersionNumber INTEGER,
bluetoothConnectionAllowed INTEGER,
connectiontime INTEGER,
nickname TEXT,
bluetoothaddress TEXT,
wifissid TEXT,
wifibssid TEXT,
wifipassword TEXT,
wifisecurity INTEGER,
wifiProjectionProtocolOnTcp INTEGER DEFAULT 0,
wifiProjectionProtocolIpAddress TEXT DEFAULT '',
wifiProjectionProtocolTcpPort INTEGER DEFAULT -1
```

## Query

```sql
SELECT
  manufacturer,
  model,
  modelyear,
  headUnitMake,
  headUnitModel,
  headUnitSoftwareBuild,
  headUnitSoftwareVersion,
  vehicleType
FROM allowedcars;
```

## Results for Kia Soul (SK3PE 2022)

| Field | Value |
|-------|-------|
| Manufacturer | KIA |
| Model | SK3PE |
| Model Year | 2022 |
| **Head Unit Make** | **Mobis** |
| **Head Unit Model** | **standard_m_5** |
| **Head Unit Software Build** | **KIA_US** |
| **Head Unit Software Version** | **V001.220718** |
| Vehicle Type | 1 |

## What This Means

- **Mobis** is the Tier-1 automotive supplier (Hyundai Mobis) that manufactures the infotainment hardware for Kia vehicles. It is a subsidiary of Hyundai Motor Group.
- **standard_m_5** is the internal model designation for the head unit hardware.
- **V001.220718** is the firmware version running on the head unit, dated July 18, 2022.
- **KIA_US** indicates the firmware variant targeted for the US market.

## Notes

- The raw `vehicleid` field contains a hashed identifier, not the actual VIN.
- The head unit make/model is populated from the car's USB descriptor or wireless handshake during the Android Auto connection negotiation, not from Android system properties.
- This information is persisted locally in the `carservicedata.db` after the first successful connection to the car.
- If you reconnect to a different car, a new row will be inserted into `allowedcars`.
- The `rejectedcars` table stores cars that failed the handshake/compatibility check.
