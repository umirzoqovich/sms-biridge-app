#!/usr/bin/env python3
"""
Python CRM SMS Mock Server for 'SMS Bridge Pro'
Implements:
  - GET  /api/mobile/ping   -> Ping check
  - GET  /api/mobile/poll   -> Poll for pending SMS tasks
  - POST /api/mobile/report -> Receive delivery reports from Android app
  - GET  /                  -> Web Dashboard for queuing & monitoring SMS
  - POST /api/admin/enqueue -> Admin endpoint to queue new SMS task
"""

import http.server
import json
import socketserver
import time
import urllib.parse
from collections import deque
from datetime import datetime

PORT = 8000
sms_queue = deque()
sent_reports = []
task_counter = 100

# Pre-populate sample Uzbek SMS tasks (both Latin and Cyrillic)
sample_tasks = [
    {
        "phone": "+998901234567",
        "message": "Hurmatli mijoz! Sizning #48291-sonli buyurtmangiz qabul qilindi. Yetkazib berish xizmati tez orada bog'lanadi.",
        "sim_slot": 1
    },
    {
        "phone": "+998939876543",
        "message": "Ҳурматли мижоз! Тўловингиз муваффақиятли амалга оширилди. Харидингиз учун ташаккур!",
        "sim_slot": 2
    },
    {
        "phone": "+998971112233",
        "message": "SMS Bridge Pro testi: Uzun matnli xabarlarni bo'lib yuborish (divideMessage va sendMultipartTextMessage) muvaffaqiyatli sinovdan o'tkazilmoqda.",
        "sim_slot": 1
    }
]

for t in sample_tasks:
    task_counter += 1
    sms_queue.append({
        "task_id": str(task_counter),
        "phone": t["phone"],
        "message": t["message"],
        "sim_slot": t["sim_slot"],
        "created_at": datetime.now().strftime("%H:%M:%S")
    })


class CrmMockHandler(http.server.BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        # Clean custom logger
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [CRM Server] {args[0]} - {args[1]} ({args[2]})")

    def _send_json(self, status_code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        # 1. PING ENDPOINT
        if path == "/api/mobile/ping":
            self._send_json(200, {
                "status": "ok",
                "message": "Python CRM Server faol",
                "server_time": datetime.now().isoformat(),
                "pending_tasks": len(sms_queue)
            })
            return

        # 2. POLL ENDPOINT
        if path == "/api/mobile/poll":
            if sms_queue:
                task = sms_queue.popleft()
                print(f"\n[>> POLL] SMS Task berildi -> ID: #{task['task_id']} | Tel: {task['phone']} | SIM: {task.get('sim_slot')}")
                self._send_json(200, {
                    "has_task": True,
                    "task_id": str(task["task_id"]),
                    "phone": task["phone"],
                    "message": task["message"],
                    "sim_slot": task.get("sim_slot", 1)
                })
            else:
                self._send_json(200, {
                    "has_task": False,
                    "wait_seconds": 3
                })
            return

        # 3. WEB DASHBOARD
        if path == "/":
            self._serve_dashboard()
            return

        # 4. API STATUS
        if path == "/api/admin/status":
            self._send_json(200, {
                "queue": list(sms_queue),
                "reports": sent_reports[-30:]
            })
            return

        self.send_error(404, "Endpoint topilmadi")

    def do_POST(self):
        global task_counter
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"

        try:
            data = json.loads(body)
        except Exception:
            data = {}

        # 1. REPORT ENDPOINT
        if path == "/api/mobile/report":
            task_id = str(data.get("task_id", "-"))
            phone = data.get("phone", "")
            status = data.get("status", "unknown")
            error = data.get("error", "")

            report_entry = {
                "task_id": task_id,
                "phone": phone,
                "status": status,
                "error": error,
                "reported_at": datetime.now().strftime("%H:%M:%S")
            }
            sent_reports.append(report_entry)

            status_icon = "SUCCESS" if status == "sent" else f"FAILED ({error})"
            print(f"\n[<< REPORT] Holat qabul qilindi -> Task #{task_id}: {phone} -> {status_icon}")

            self._send_json(200, {
                "status": "received",
                "task_id": task_id,
                "message": "Hisobot muvaffaqiyatli qabul qilindi"
            })
            return

        # 2. ENQUEUE NEW SMS (ADMIN)
        if path == "/api/admin/enqueue":
            phone = data.get("phone", "").strip()
            message = data.get("message", "").strip()
            sim_slot = int(data.get("sim_slot", 1))

            if not phone or not message:
                self._send_json(400, {"error": "phone va message maydonlari majburiy"})
                return

            task_counter += 1
            new_task = {
                "task_id": str(task_counter),
                "phone": phone,
                "message": message,
                "sim_slot": sim_slot,
                "created_at": datetime.now().strftime("%H:%M:%S")
            }
            sms_queue.append(new_task)
            print(f"[+ ENQUEUE] Yangi SMS navbatga qo'shildi: #{new_task['task_id']} ({phone})")

            self._send_json(200, {
                "status": "enqueued",
                "task": new_task
            })
            return

        self.send_error(404, "Endpoint topilmadi")

    def _serve_dashboard(self):
        html = """<!DOCTYPE html>
<html lang="uz">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Python CRM Mock SMS Server</title>
    <style>
        body {
            background: #090d16;
            color: #f9fafb;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            margin: 0;
            padding: 30px;
        }
        .container {
            max-width: 900px;
            margin: 0 auto;
        }
        h1 {
            color: #10b981;
            display: flex;
            align-items: center;
            gap: 10px;
            margin-bottom: 5px;
        }
        .badge {
            background: rgba(16, 185, 129, 0.15);
            color: #34d399;
            border: 1px solid #10b981;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 14px;
        }
        .card {
            background: #111827;
            border: 1px solid #1f2937;
            border-radius: 12px;
            padding: 20px;
            margin-top: 20px;
        }
        input, textarea, select, button {
            background: #0d131f;
            border: 1px solid #1f2937;
            color: #f9fafb;
            padding: 10px 14px;
            border-radius: 8px;
            font-size: 14px;
            box-sizing: border-box;
            width: 100%;
            margin-top: 6px;
        }
        input:focus, textarea:focus, select:focus {
            outline: none;
            border-color: #10b981;
        }
        button {
            background: #10b981;
            color: #090d16;
            font-weight: bold;
            cursor: pointer;
            border: none;
            margin-top: 15px;
            transition: all 0.2s;
        }
        button:hover {
            background: #34d399;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 15px;
            font-size: 13px;
        }
        th, td {
            text-align: left;
            padding: 10px;
            border-bottom: 1px solid #1f2937;
        }
        th {
            color: #9ca3af;
        }
        .status-sent {
            color: #34d399;
            font-weight: bold;
        }
        .status-failed {
            color: #ef4444;
            font-weight: bold;
        }
        .console {
            background: #030712;
            border: 1px solid rgba(16, 185, 129, 0.2);
            border-radius: 8px;
            padding: 12px;
            font-family: monospace;
            font-size: 12px;
            color: #34d399;
            max-height: 200px;
            overflow-y: auto;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>SMS Bridge Pro <span class="badge">CRM Test Server</span></h1>
        <p style="color: #9ca3af;">Server URL manzili: <code style="color: #34d399;">http://&lt;IP&gt;:{{PORT}}</code></p>

        <div class="card">
            <h3 style="margin-top: 0; color: #10b981;">Yangi SMS task qo'shish</h3>
            <form onsubmit="enqueueSms(event)">
                <label>Telefon raqam:</label>
                <input type="text" id="phone" value="+998901234567" required>

                <div style="margin-top: 10px;">
                    <label>SIM Slot:</label>
                    <select id="simSlot">
                        <option value="1">SIM 1</option>
                        <option value="2">SIM 2</option>
                    </select>
                </div>

                <div style="margin-top: 10px;">
                    <label>SMS Matni (Uzbek Latin/Cyrillic):</label>
                    <textarea id="message" rows="3" required>Hurmatli mijoz! Sizning xizmatingiz muvaffaqiyatli faollashtirildi.</textarea>
                </div>

                <button type="submit">+ SMS Navbatiga qo'shish</button>
            </form>
        </div>

        <div class="card">
            <h3 style="margin-top: 0; color: #10b981;">Navbatdagi SMSlar (<span id="queueCount">0</span> ta)</h3>
            <div id="queueBox" class="console">Yuklanmoqda...</div>
        </div>

        <div class="card">
            <h3 style="margin-top: 0; color: #10b981;">Android ilovadan kelgan hisobotlar (<span id="reportsCount">0</span> ta)</h3>
            <div id="reportsBox" class="console">Hozircha hisobotlar yo'q...</div>
        </div>
    </div>

    <script>
        async function fetchStatus() {
            try {
                const res = await fetch('/api/admin/status');
                const data = await res.json();
                document.getElementById('queueCount').innerText = data.queue.length;
                document.getElementById('reportsCount').innerText = data.reports.length;

                if (data.queue.length === 0) {
                    document.getElementById('queueBox').innerText = "Navbat bo'sh (barcha SMSlar yuborilgan)";
                } else {
                    document.getElementById('queueBox').innerHTML = data.queue.map(q =>
                        `[${q.created_at}] Task #${q.task_id} -> ${q.phone} (SIM ${q.sim_slot}): "${q.message.substring(0, 50)}..."`
                    ).join('<br>');
                }

                if (data.reports.length === 0) {
                    document.getElementById('reportsBox').innerText = "Hozircha ilovadan hisobot kelmadi";
                } else {
                    document.getElementById('reportsBox').innerHTML = data.reports.slice().reverse().map(r =>
                        `[${r.reported_at}] Task #${r.task_id} -> ${r.phone}: <span class="status-${r.status}">${r.status.toUpperCase()}</span> ${r.error ? '(' + r.error + ')' : ''}`
                    ).join('<br>');
                }
            } catch (e) {
                console.error(e);
            }
        }

        async function enqueueSms(e) {
            e.preventDefault();
            const phone = document.getElementById('phone').value;
            const message = document.getElementById('message').value;
            const sim_slot = parseInt(document.getElementById('simSlot').value);

            await fetch('/api/admin/enqueue', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, message, sim_slot })
            });

            document.getElementById('message').value = '';
            fetchStatus();
        }

        setInterval(fetchStatus, 1500);
        fetchStatus();
    </script>
</body>
</html>
""".replace("{{PORT}}", str(PORT))
        body = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def run_server():
    print(f"""
=============================================================
   SMS BRIDGE PRO - PYTHON CRM MOCK SERVER
   Listening on: http://0.0.0.0:{PORT}
   Web Dashboard: http://localhost:{PORT}
=============================================================
Endpoints:
   GET  /api/mobile/ping   -> Ping tekshirish
   GET  /api/mobile/poll   -> SMS navbatini tekshirish
   POST /api/mobile/report -> Yuborilgan SMS hisobotini olish
   POST /api/admin/enqueue -> Yangi SMS qo'shish
=============================================================
""")
    with socketserver.TCPServer(("", PORT), CrmMockHandler) as httpd:
        httpd.serve_forever()


if __name__ == "__main__":
    run_server()
