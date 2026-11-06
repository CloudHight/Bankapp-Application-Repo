import json
import os
import sys
import time
import uuid
import urllib.request
import xml.etree.ElementTree as ET
from decimal import Decimal

BASE_URL = os.environ.get("STAGE_APP_URL", "").rstrip("/")
REPORT = "smoke-test-results.xml"
results = []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


client = urllib.request.build_opener(NoRedirect)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def request(method, path, expected_status=200, payload=None):
    data = None if payload is None else json.dumps(payload).encode()

    req = urllib.request.Request(
        BASE_URL + path,
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )

    with client.open(req, timeout=15) as response:
        require(
            response.status == expected_status,
            f"Expected HTTP {expected_status}, got {response.status}",
        )
        return json.load(response)


def check(name, action):
    started = time.monotonic()

    try:
        action()
        results.append((name, time.monotonic() - started, None))
        print(f"PASS: {name}", flush=True)
    except Exception as error:
        results.append((name, time.monotonic() - started, str(error)))
        print(f"FAIL: {name}: {error}", flush=True)
        raise


def configuration():
    approved_url = os.environ.get("SMOKE_ALLOWED_STAGE_URL", "").rstrip("/")

    require(
        bool(BASE_URL) and bool(approved_url),
        "Set STAGE_APP_URL and SMOKE_ALLOWED_STAGE_URL",
    )
    require(
        BASE_URL == approved_url,
        "Target URL does not match the approved staging URL",
    )


def health():
    for attempt in range(12):
        try:
            response = request("GET", "/actuator/health")
            require(response.get("status") == "UP", "Application is not UP")
            return
        except Exception:
            if attempt == 11:
                raise
            time.sleep(5)


def workflow():
    # Only use synthetic customer data created by this run.
    email = f"ct47-{uuid.uuid4().hex}@example.invalid"

    customer = request(
        "POST",
        "/api/v1/customers",
        expected_status=201,
        payload={
            "firstName": "Smoke",
            "lastName": "Test",
            "email": email,
            "phone": "555-0101",
        },
    )
    customer_id = customer["id"]

    saved_customer = request(
        "GET", f"/api/v1/customers/{customer_id}"
    )
    require(
        saved_customer["email"] == email,
        "Customer read-back mismatch",
    )

    account = request(
        "POST",
        "/api/v1/accounts",
        expected_status=201,
        payload={
            "customerId": customer_id,
            "type": "CHECKING",
            "openingBalance": 0.00,
        },
    )
    number = account["accountNumber"]

    saved_account = request("GET", f"/api/v1/accounts/{number}")
    require(
        saved_account["customerId"] == customer_id,
        "Account belongs to an unexpected customer",
    )
    require(
        Decimal(str(saved_account["balance"])) == Decimal("0.00"),
        "Expected a zero balance",
    )

    accounts = request(
        "GET", f"/api/v1/accounts?customerId={customer_id}"
    )
    require(
        any(a["accountNumber"] == number for a in accounts),
        "New account missing from customer account list",
    )

    statement = request(
        "GET",
        f"/api/v1/accounts/{number}/transactions?page=0&size=20",
    )
    require(
        isinstance(statement["content"], list),
        "Invalid transaction statement response",
    )
    require(
        statement["totalElements"] == 0,
        "Unexpected transactions on the new account",
    )


def write_report():
    failures = sum(error is not None for _, _, error in results)

    suite = ET.Element(
        "testsuite",
        name="BankApp staging smoke tests",
        tests=str(len(results)),
        failures=str(failures),
    )

    for name, duration, error in results:
        case = ET.SubElement(
            suite,
            "testcase",
            classname="BankAppSmoke",
            name=name,
            time=f"{duration:.3f}",
        )
        if error is not None:
            ET.SubElement(case, "failure", message=error).text = error

    ET.ElementTree(suite).write(
        REPORT, encoding="utf-8", xml_declaration=True
    )


if __name__ == "__main__":
    exit_code = 0

    try:
        check("Staging configuration", configuration)
        check("Application health", health)
        check("Synthetic customer and zero-balance account", workflow)
    except Exception:
        exit_code = 1
    finally:
        write_report()

    sys.exit(exit_code)
    