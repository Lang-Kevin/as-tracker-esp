import sys
import types
import streamlit as st
import asyncio
import threading
import queue
import time
from datetime import datetime
from bleak import BleakClient, BleakScanner
import plotly.graph_objs as go

# ---------------- CONFIG ----------------
DEVICE_NAME = "MPU6050_Sensor"
CHAR_UUID = "87654321-4321-4321-4321-0987654321ba"
MAX_POINTS = 300

# sys.modules is truly process-global.
# globals() only covers the current session's __dict__ — Streamlit creates a
# separate namespace per browser session, so globals() guards fail when multiple
# sessions exist. sys.modules is shared across ALL threads in the process.
_KEY = '__arm_swing_ble__'
if _KEY not in sys.modules:
    _m = types.ModuleType(_KEY)
    _m.data_q = queue.Queue()
    _m.status_q = queue.Queue()
    _m.ble_started = threading.Event()
    _m.packet_count = 0
    _m.log_last: dict = {}
    sys.modules[_KEY] = _m

_g = sys.modules[_KEY]


# ---------------- LOGGING ----------------
def log(msg: str):
    now = time.monotonic()
    key = msg.split(']')[0] + ']' if ']' in msg else msg
    if now - _g.log_last.get(key, 0) < 1.0:
        return
    _g.log_last[key] = now
    print(f"[{datetime.now().strftime('%H:%M:%S.%f')[:-3]}] {msg}", flush=True)


# ---------------- BLE CALLBACK ----------------
def _notification_handler(_, data):
    try:
        t, omega = data.decode().strip().split(",")
        _g.data_q.put((float(t) / 1000.0, float(omega)))
        _g.packet_count += 1
        if _g.packet_count % 50 == 0:
            log(f"[DATA] {_g.packet_count} Pakete empfangen – letztes: t={t}ms omega={float(omega):.3f} rad/s")
    except Exception as e:
        log(f"[DATA] Parse-Fehler: {e} – Rohdaten: {data!r}")


# ---------------- BLE LOOP (reconnect) ----------------
async def _ble_loop():
    attempt = 0
    while True:
        attempt += 1
        _g.status_q.put("🔍 Suche ESP32...")
        log(f"[BLE] Scan #{attempt} – suche '{DEVICE_NAME}' (timeout 5s)...")
        try:
            address = None
            found_devices = await BleakScanner.discover(timeout=5.0)
            log(f"[BLE] {len(found_devices)} Gerät(e) gefunden: {[d.name for d in found_devices]}")
            for d in found_devices:
                if d.name == DEVICE_NAME:
                    address = d.address
                    break

            if not address:
                _g.status_q.put("❌ ESP32 nicht gefunden – erneuter Versuch in 3s")
                log(f"[BLE] '{DEVICE_NAME}' nicht in der Geräteliste – warte 3s")
                await asyncio.sleep(3)
                continue

            log(f"[BLE] Gefunden: {DEVICE_NAME} @ {address} – verbinde...")
            _g.status_q.put(f"✔ Verbunden mit {address}")

            async with BleakClient(address) as client:
                log(f"[BLE] Verbindung hergestellt. Registriere Notify auf {CHAR_UUID}...")
                await client.start_notify(CHAR_UUID, _notification_handler)
                log(f"[BLE] Notify aktiv – empfange Daten")
                while client.is_connected:
                    await asyncio.sleep(0.5)
                log(f"[BLE] client.is_connected = False – Verbindung verloren")

            _g.status_q.put("⚠️ Verbindung verloren – reconnect...")
            log(f"[BLE] Verbindung unterbrochen – warte 1s vor Reconnect")
            await asyncio.sleep(1)

        except Exception as e:
            _g.status_q.put(f"❌ Fehler: {e} – retry in 3s")
            log(f"[BLE] Exception: {type(e).__name__}: {e} – warte 3s")
            await asyncio.sleep(3)


def _start_ble_thread():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    loop.run_until_complete(_ble_loop())


# ---------------- THREAD GUARD (start once per process) ----------------
if not _g.ble_started.is_set():
    _g.ble_started.set()
    log(f"[INIT] BLE-Thread startet (Device='{DEVICE_NAME}', CHAR={CHAR_UUID})")
    threading.Thread(target=_start_ble_thread, daemon=True).start()


# ---------------- SESSION STATE ----------------
if "x" not in st.session_state:
    st.session_state.x = []
if "y" not in st.session_state:
    st.session_state.y = []
if "status" not in st.session_state:
    st.session_state.status = "🔄 Starte..."

# ---------------- UI PLACEHOLDERS ----------------
st.set_page_config(page_title="Arm Tracker", layout="wide")
st.title("🏐 Arm Speed Tracker")

status_placeholder = st.empty()
chart_placeholder = st.empty()

# ---------------- DRAIN QUEUES (main thread only) ----------------
while not _g.status_q.empty():
    st.session_state.status = _g.status_q.get()

while not _g.data_q.empty():
    t, omega = _g.data_q.get()
    st.session_state.x.append(t)
    st.session_state.y.append(omega)

st.session_state.x = st.session_state.x[-MAX_POINTS:]
st.session_state.y = st.session_state.y[-MAX_POINTS:]

# ---------------- RENDER ----------------
status_placeholder.text(st.session_state.status)

fig = go.Figure()
fig.add_trace(go.Scatter(
    x=st.session_state.x,
    y=st.session_state.y,
    mode="lines",
    name="Omega (rad/s)"
))
fig.update_layout(
    xaxis_title="Zeit (s)",
    yaxis_title="Winkelgeschwindigkeit (rad/s)",
    margin=dict(l=40, r=40, t=40, b=40),
)
chart_placeholder.plotly_chart(fig, width="stretch")

# ---------------- AUTO RERUN (~10 Hz) ----------------
time.sleep(0.1)
st.rerun()
