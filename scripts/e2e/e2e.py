"""End-to-end scenario (spec section 31) against the running Docker stack via nginx."""
import base64
import datetime as dt
import hashlib
import hmac
import json
import struct
import time
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8081"
HERE = os.path.dirname(os.path.abspath(__file__))
M2 = os.path.expanduser("~/.m2/repository/com/google/zxing")
CP = os.pathsep.join([f"{M2}/core/3.5.4/core-3.5.4.jar", f"{M2}/javase/3.5.4/javase-3.5.4.jar"])
ENV = dict(line.strip().split("=", 1) for line in open(os.path.join(sys.argv[1], ".env"), encoding="utf-8-sig")
           if "=" in line and not line.startswith("#"))

step_no = 0


def step(msg):
    global step_no
    step_no += 1
    print(f"[{step_no:02d}] {msg}")


def call(method, path, token=None, body=None, raw=None, ctype=None, expect=200):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if raw is not None:
        data, headers["Content-Type"] = raw, ctype
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            status, content, resp_headers = r.status, r.read(), r.headers
    except urllib.error.HTTPError as e:
        status, content, resp_headers = e.code, e.read(), e.headers
    assert status == expect, f"{method} {path} -> {status} (expected {expect}): {content[:400]!r}"
    if resp_headers.get("Content-Type", "").startswith("application/json") and content:
        return json.loads(content)
    return content


def multipart(field_file, filename, mime, content):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field_file}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def totp(secret_b32, at=None):
    """RFC 6238 TOTP (SHA-1, 6 digits, 30 s), as produced by authenticator apps."""
    key = base64.b32decode(secret_b32 + "=" * (-len(secret_b32) % 8))
    counter = int((at or time.time()) // 30)
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    return f"{(struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


def decode_qr(png):
    path = os.path.join(HERE, "qr.png")
    with open(path, "wb") as f:
        f.write(png)
    return subprocess.check_output(["java", "-cp", CP, os.path.join(HERE, "Decode.java"), path], text=True)


today = dt.date.today()
email = f"john.smith.{uuid.uuid4().hex[:6]}@example.com"
password = "Str0ngPassw0rd!"

# ---------------- Tourist
step("Frontend is served with security headers")
req = urllib.request.urlopen(BASE + "/tourist/dashboard")
assert b'<div id="root">' in req.read()
assert "camera=(self)" in req.headers["Permissions-Policy"] and "frame-ancestors 'none'" in req.headers["Content-Security-Policy"]

step(f"Tourist registers ({email})")
reg = call("POST", "/api/v1/auth/register", body={"email": email, "password": password,
                                                  "firstName": "John", "lastName": "Smith"}, expect=201)
assert reg["user"]["role"] == "TOURIST"

step("Tourist logs in")
tourist = call("POST", "/api/v1/auth/login", body={"email": email, "password": password})["accessToken"]

step("Tourist completes profile")
passport = "X" + uuid.uuid4().hex[:8].upper()
prof = call("PUT", "/api/v1/tourists/me", tourist, {
    "firstName": "John", "lastName": "Smith", "passportNumber": passport, "nationality": "USA",
    "dateOfBirth": "1990-05-14", "gender": "MALE", "phone": "+1 555 0100", "address": "1 Example St",
    "emergencyContact": "Jane +1 555 0101"})
assert prof["complete"] is True

step("Tourist selects a permit-required area")
areas = call("GET", "/api/v1/areas")
area = next(a for a in areas if a["permitRequired"] and a["maximumDays"] >= 6)
print(f"     area: {area['name']} (max {area['maximumDays']} days)")

step("Tourist submits permit application")
app = call("POST", "/api/v1/applications", tourist, {"areaId": area["id"], "purpose": "Guided trek",
                                                      "startDate": str(today), "endDate": str(today + dt.timedelta(days=5))},
           expect=201)
assert app["status"] == "PENDING"
print(f"     {app['applicationNumber']}")

step("Tourist uploads passport document")
body, ctype = multipart("file", "passport.pdf", "application/pdf", b"%PDF-1.4\n% e2e passport scan\n")
call("POST", f"/api/v1/applications/{app['id']}/documents?documentType=PASSPORT", tourist, raw=body, ctype=ctype,
     expect=201)

step("Upload of a disguised executable is rejected (415)")
body, ctype = multipart("file", "evil.pdf", "application/pdf", b"MZ\x90\x00\x03\x00\x00\x00")
call("POST", f"/api/v1/applications/{app['id']}/documents?documentType=OTHER", tourist, raw=body, ctype=ctype,
     expect=415)

step("Tourist sees application as PENDING")
assert call("GET", f"/api/v1/applications/{app['id']}", tourist)["status"] == "PENDING"

step("Tourist cannot reach admin APIs (403)")
call("GET", "/api/v1/admin/dashboard", tourist, expect=403)

# ---------------- Admin
step("Admin logs in")
admin = call("POST", "/api/v1/auth/login", body={"email": ENV["SEED_ADMIN_EMAIL"],
                                                 "password": ENV["SEED_ADMIN_PASSWORD"]})["accessToken"]

step("Admin dashboard shows the pending application")
dash = call("GET", "/api/v1/admin/dashboard", admin)
assert dash["stats"]["pendingApplications"] >= 1
assert any(a["id"] == app["id"] for a in dash["recentApplications"])

step("Admin searches by passport number")
found = call("GET", f"/api/v1/admin/applications?q={passport}", admin)
assert found["totalElements"] == 1 and found["content"][0]["tourist"]["passportNumberMasked"].startswith("****")

step("Admin reviews tourist, application and documents")
detail = call("GET", f"/api/v1/admin/applications/{app['id']}", admin)
doc = detail["documents"][0]
assert call("GET", f"/api/v1/admin/documents/{doc['id']}/content", admin).startswith(b"%PDF")
call("POST", f"/api/v1/admin/documents/{doc['id']}/verify", admin)

fee = float(area["fee"])
if fee > 0:
    step(f"Application carries the NPR {fee:g} fee as UNPAID")
    pay = call("GET", f"/api/v1/applications/{app['id']}", tourist)["payment"]
    assert pay["status"] == "UNPAID" and float(pay["amount"]) == fee
    online = pay["requiredForApproval"]

    if online:
        step("Tourist starts eSewa checkout -> signed form for eSewa UAT")
        checkout = call("POST", f"/api/v1/applications/{app['id']}/payments/esewa", tourist)
        f = checkout["fields"]
        assert "esewa.com.np" in checkout["formUrl"] and f["signed_field_names"] == "total_amount,transaction_uuid,product_code"
        assert f["success_url"] == ENV["APP_BASE_URL"] + "/tourist/payments/esewa/success" and len(f["signature"]) == 44
        csp = urllib.request.urlopen(BASE + "/tourist/dashboard").headers["Content-Security-Policy"]
        assert "https://rc-epay.esewa.com.np" in csp, "CSP must allow posting the form to eSewa"
        print(f"     {f['transaction_uuid']} total={f['total_amount']}")

        step("A forged eSewa callback is rejected (400)")
        forged = base64.b64encode(json.dumps({"transaction_code": "X", "status": "COMPLETE", "total_amount": f["total_amount"],
                                              "transaction_uuid": f["transaction_uuid"], "product_code": f["product_code"],
                                              "signed_field_names": "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names",
                                              "signature": "Zm9yZ2Vk"}).encode()).decode()
        call("POST", "/api/v1/payments/esewa/confirm", tourist, {"data": forged}, expect=400)

        step("Approval is blocked while the fee is unpaid (422)")
        call("POST", f"/api/v1/admin/applications/{app['id']}/approve", admin, expect=422)

    step("Admin records a counter payment")
    paid = call("POST", f"/api/v1/admin/applications/{app['id']}/payments/offline", admin, {"reference": "E2E-RCPT-1"},
                expect=201)
    assert paid["status"] == "COMPLETED"
    assert call("GET", f"/api/v1/applications/{app['id']}", tourist)["payment"]["status"] == "PAID"

step("Admin approves -> permit + secure QR token generated")
permit = call("POST", f"/api/v1/admin/applications/{app['id']}/approve", admin, expect=201)
assert permit["status"] == "ACTIVE" and permit["qrActive"]
print(f"     {permit['permitNumber']}")

# ---------------- Tourist receives permit
step("Tourist sees active permit and downloads QR")
mine = call("GET", "/api/v1/permits?status=ACTIVE", tourist)
assert mine["content"][0]["permitNumber"] == permit["permitNumber"]
png = call("GET", f"/api/v1/permits/{permit['id']}/qr?download=true", tourist)
assert png[:8] == b"\x89PNG\r\n\x1a\n"

step("Tourist is notified in-app (approval, payment)")
inbox = call("GET", "/api/v1/notifications", tourist)
types = {n["type"] for n in inbox["content"]}
assert "APPLICATION_APPROVED" in types and (fee == 0 or "PAYMENT_RECEIVED" in types), types
assert call("GET", "/api/v1/notifications/unread-count", tourist)["count"] >= 1
assert passport not in json.dumps(inbox)

step("Approval email is delivered (Mailpit)")
for _ in range(30):
    try:
        mails = json.loads(urllib.request.urlopen(f"http://localhost:8025/api/v1/search?query=to:{email}").read())
    except urllib.error.URLError:
        mails = {"messages": []}
    subjects = [m["Subject"] for m in mails.get("messages", [])]
    if any("Permit approved" in x for x in subjects):
        break
    time.sleep(2)
print(f"     {subjects}")
assert any("Permit approved" in x for x in subjects), "approval email not delivered"

step("QR contains only the verification URL (no personal data)")
url = decode_qr(png)
print(f"     {url[:48]}…")
assert url.startswith(ENV["APP_BASE_URL"] + "/verify/")
token = url.rsplit("/", 1)[1]
assert len(token) == 43 and passport not in url and "Smith" not in url and str(permit["id"]) != token

step("QR landing page /verify/<token> is served by the SPA")
assert b'<div id="root">' in urllib.request.urlopen(url.replace(ENV["APP_BASE_URL"], BASE)).read()

# ---------------- Officer scans
step("Officer logs in and verifies the QR -> VALID")
officer = call("POST", "/api/v1/auth/login", body={"email": ENV["SEED_OFFICER_EMAIL"],
                                                   "password": ENV["SEED_OFFICER_PASSWORD"]})["accessToken"]
v = call("GET", f"/api/v1/permits/verify/{token}", officer)
print(f"     valid={v['valid']} {v['permitNumber']} {v['touristName']} {v['area']} until {v['validUntil']}")
assert v["valid"] and v["touristName"] == "John Smith" and passport not in json.dumps(v)

step("Fake QR -> INVALID_TOKEN")
fake = call("GET", f"/api/v1/permits/verify/{uuid.uuid4().hex}{uuid.uuid4().hex[:11]}", officer)
assert not fake["valid"] and fake["reason"] == "INVALID_TOKEN"

step("Tourist cannot use the verify endpoint (403)")
call("GET", f"/api/v1/permits/verify/{token}", tourist, expect=403)

step("Admin regenerates QR -> old QR revoked, new QR valid")
call("POST", f"/api/v1/admin/permits/{permit['id']}/qr/regenerate", admin, {"reason": "e2e rotation"})
assert call("GET", f"/api/v1/permits/verify/{token}", officer)["reason"] == "TOKEN_REVOKED"
new_token = decode_qr(call("GET", f"/api/v1/permits/{permit['id']}/qr", tourist)).rsplit("/", 1)[1]
assert call("GET", f"/api/v1/permits/verify/{new_token}", officer)["valid"]

step("Admin cancels permit -> PERMIT_CANCELLED")
call("POST", f"/api/v1/admin/permits/{permit['id']}/cancel", admin, {"reason": "e2e cancellation"})
assert call("GET", f"/api/v1/permits/verify/{new_token}", officer)["reason"] == "PERMIT_CANCELLED"

step("Every verification was recorded in history")
hist = call("GET", f"/api/v1/verifications?permitId={permit['id']}", admin)
results = [h["result"] for h in hist["content"]]
print(f"     {results}")
assert hist["totalElements"] == 4 and results.count("VALID") == 2 and "CANCELLED" in results

step("Audit trail recorded approval, QR generation/revocation and cancellation")
for action in ["PERMIT_APPROVED", "QR_GENERATED", "QR_REVOKED", "PERMIT_CANCELLED", "ADMIN_LOGIN", "QR_VERIFIED"]:
    assert call("GET", f"/api/v1/admin/audit-logs?action={action}", admin)["totalElements"] >= 1, action

# ---------------- Staff management + 2FA
staff_email = f"officer.{uuid.uuid4().hex[:6]}@example.com"
step(f"Admin creates an officer account ({staff_email})")
created = call("POST", "/api/v1/admin/staff", admin, {"email": staff_email, "displayName": "E2E Checkpoint",
                                                      "role": "OFFICER"}, expect=201)
temp = created["temporaryPassword"]
assert created["staff"]["passwordChangeRequired"] and len(temp) >= 12

step("Officer signs in with the temporary password -> must change it first")
first = call("POST", "/api/v1/auth/login", body={"email": staff_email, "password": temp})
assert first["passwordChangeRequired"]
call("GET", "/api/v1/officer/dashboard", first["accessToken"], expect=403)
new_password = "Off1cer-" + uuid.uuid4().hex[:10]
changed = call("POST", "/api/v1/account/password", first["accessToken"],
               {"currentPassword": temp, "newPassword": new_password})
assert not changed["passwordChangeRequired"]
call("GET", "/api/v1/officer/dashboard", changed["accessToken"])

step("Officer turns on two-step verification (TOTP)")
setup = call("POST", "/api/v1/account/mfa/setup", changed["accessToken"])
assert setup["otpauthUri"].startswith("otpauth://totp/YatraPass") and setup["qrPngDataUrl"].startswith("data:image/png")
codes = call("POST", "/api/v1/account/mfa/enable", changed["accessToken"], {"code": totp(setup["secret"])})["recoveryCodes"]
assert len(codes) == 10

step("Sign-in now needs a second factor; wrong code fails, recovery code works")
challenge = call("POST", "/api/v1/auth/login", body={"email": staff_email, "password": new_password})
assert challenge["mfaRequired"] and challenge.get("accessToken") is None
call("POST", "/api/v1/auth/login/mfa", body={"mfaToken": challenge["mfaToken"], "code": "000000"}, expect=401)
session = call("POST", "/api/v1/auth/login/mfa", body={"mfaToken": challenge["mfaToken"], "code": codes[0]})
assert session["accessToken"] and call("GET", "/api/v1/account", session["accessToken"])["recoveryCodesRemaining"] == 9

step("Admin disables the officer -> sign-in refused")
call("PATCH", f"/api/v1/admin/staff/{created['staff']['id']}/status", admin, {"enabled": False})
call("POST", "/api/v1/auth/login", body={"email": staff_email, "password": new_password}, expect=401)

step("Admin payments list and fee totals")
if fee > 0:
    assert call("GET", "/api/v1/admin/payments?status=COMPLETED", admin)["totalElements"] >= 1
assert "fees" in call("GET", "/api/v1/admin/dashboard", admin)

step("Logout revokes the refresh token")
call("POST", "/api/v1/auth/logout", body={"refreshToken": reg["refreshToken"]}, expect=204)
call("POST", "/api/v1/auth/refresh", body={"refreshToken": reg["refreshToken"]}, expect=401)

step("Swagger/OpenAPI is reachable (API_DOCS_ENABLED=true in dev)")
spec = call("GET", "/v3/api-docs")
assert "/api/v1/permits/verify/{token}" in spec["paths"]

print(f"\nE2E PASSED: {step_no} steps")
