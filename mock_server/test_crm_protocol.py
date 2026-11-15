#!/usr/bin/env python3
"""
Unit test to verify CRM Mock Server protocol compliance
"""
import threading
import time
import urllib.request
import json
import socketserver
from crm_mock_server import CrmMockHandler

def run_tests():
    # Start server in background thread on test port 8089
    port = 8089
    server = socketserver.TCPServer(("127.0.0.1", port), CrmMockHandler)
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()
    time.sleep(0.5)

    base_url = f"http://127.0.0.1:{port}"

    print("[1] Testing GET /api/mobile/ping...")
    with urllib.request.urlopen(f"{base_url}/api/mobile/ping", timeout=3) as res:
        assert res.status == 200
        data = json.loads(res.read().decode("utf-8"))
        print("  Ping response:", data)
        assert data.get("status") == "ok"
        assert "Python CRM Server" in data.get("message", "")

    print("[2] Testing GET /api/mobile/poll...")
    with urllib.request.urlopen(f"{base_url}/api/mobile/poll?sim_slot=1", timeout=3) as res:
        assert res.status == 200
        data = json.loads(res.read().decode("utf-8"))
        print("  Poll response:", data)
        assert data.get("has_task") is True
        task_id = data.get("task_id")
        phone = data.get("phone")
        assert phone is not None
        assert data.get("message") is not None

    print(f"[3] Testing POST /api/mobile/report for Task #{task_id}...")
    req_body = json.dumps({
        "task_id": task_id,
        "phone": phone,
        "status": "sent",
        "error": ""
    }).encode("utf-8")

    req = urllib.request.Request(
        f"{base_url}/api/mobile/report",
        data=req_body,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=3) as res:
        assert res.status == 200
        data = json.loads(res.read().decode("utf-8"))
        print("  Report response:", data)
        assert data.get("status") == "received"

    print("\nALL CRM PROTOCOL TESTS PASSED! Mock server is 100% compliant with SMS Bridge Pro protocol.")
    server.shutdown()

if __name__ == "__main__":
    run_tests()
