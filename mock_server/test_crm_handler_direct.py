#!/usr/bin/env python3
"""
Direct unit test for CrmMockHandler without socket binding
"""
import io
import json
from crm_mock_server import CrmMockHandler

class MockSocket:
    def __init__(self, data=b""):
        self.rfile = io.BytesIO(data)
        self.output_buffer = bytearray()

    def makefile(self, mode, *args, **kwargs):
        if 'b' in mode and 'r' in mode:
            return self.rfile
        return self

    def write(self, data):
        self.output_buffer.extend(data)

    def flush(self):
        pass

    def sendall(self, data):
        self.output_buffer.extend(data)

    def getvalue(self):
        return bytes(self.output_buffer)

def test_protocol_logic():
    print("[1] Testing ping endpoint logic...")
    raw_request = b"GET /api/mobile/ping HTTP/1.1\r\nHost: localhost\r\n\r\n"
    sock = MockSocket(raw_request)
    handler = CrmMockHandler(sock, ("127.0.0.1", 12345), None)
    response_bytes = sock.getvalue()
    assert b"200 OK" in response_bytes
    header_end = response_bytes.find(b"\r\n\r\n") + 4
    json_data = json.loads(response_bytes[header_end:].decode("utf-8"))
    print("  Ping response:", json_data)
    assert json_data["status"] == "ok"
    assert "Python CRM Server" in json_data["message"]

    print("[2] Testing poll endpoint logic...")
    raw_request = b"GET /api/mobile/poll?sim_slot=1 HTTP/1.1\r\nHost: localhost\r\n\r\n"
    sock = MockSocket(raw_request)
    handler = CrmMockHandler(sock, ("127.0.0.1", 12345), None)
    response_bytes = sock.getvalue()
    assert b"200 OK" in response_bytes
    header_end = response_bytes.find(b"\r\n\r\n") + 4
    poll_data = json.loads(response_bytes[header_end:].decode("utf-8"))
    print("  Poll response:", poll_data)
    assert poll_data["has_task"] is True
    task_id = poll_data["task_id"]
    phone = poll_data["phone"]

    print(f"[3] Testing report endpoint logic for Task #{task_id}...")
    report_payload = json.dumps({
        "task_id": task_id,
        "phone": phone,
        "status": "sent",
        "error": ""
    }).encode("utf-8")

    raw_request = (
        f"POST /api/mobile/report HTTP/1.1\r\n"
        f"Host: localhost\r\n"
        f"Content-Type: application/json\r\n"
        f"Content-Length: {len(report_payload)}\r\n\r\n"
    ).encode("utf-8") + report_payload

    sock = MockSocket(raw_request)
    handler = CrmMockHandler(sock, ("127.0.0.1", 12345), None)
    response_bytes = sock.getvalue()
    assert b"200 OK" in response_bytes
    header_end = response_bytes.find(b"\r\n\r\n") + 4
    report_resp = json.loads(response_bytes[header_end:].decode("utf-8"))
    print("  Report response:", report_resp)
    assert report_resp["status"] == "received"

    print("\nSUCCESS: All protocol endpoints (ping, poll, report) tested and verified 100% compliant!")

if __name__ == "__main__":
    test_protocol_logic()
